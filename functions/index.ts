// functions/index.ts — Añane Go backend entrypoint.
//
// Every device connects to one city-wide Durable Object ("satipo") that owns
// the live driver positions, the open ride requests and the rides in progress.
// Real time flows over WebSocket; the HTTP routes exist so the app can issue
// commands even when the socket is reconnecting. The /admin page is the
// PIN-protected console where document reviews are approved or rejected.

export { SatipoNetwork } from "./satipo-network";

type Env = { DO: Fetcher };

const CITY_ID = "satipo";

const CORS: Record<string, string> = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type, Authorization",
};

/** Commands the app may POST to /api/<action>. */
const ACTIONS = new Set([
  "sync",
  "profile",
  "role",
  "online",
  "request-ride",
  "ride-preferences",
  "offer-ride",
  "accept-offer",
  "reject-offer",
  "accept-ride",
  "decline-ride",
  "advance-ride",
  "cancel-ride",
  "submit-verification",
  "verification-photo",
  "renew-subscription",
  "admin-login",
  "admin-list",
  "admin-decide",
]);

function dispatch(request: Request, env: Env, url: string): Promise<Response> {
  const wrapped = new Request(url, request);
  wrapped.headers.set("X-Rork-DO-Class", "SatipoNetwork");
  wrapped.headers.set("X-Rork-DO-Id", CITY_ID);
  return env.DO.fetch(wrapped);
}

async function withCors(response: Promise<Response>): Promise<Response> {
  const result = await response;
  if (result.status === 101) return result;
  const headers = new Headers(result.headers);
  for (const [key, value] of Object.entries(CORS)) headers.set(key, value);
  return new Response(result.body, { status: result.status, headers });
}

/** PIN-protected web console for the manual document review. */
const ADMIN_HTML = `<!doctype html>
<html lang="es">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1" />
<title>Añane Go · Revisión de conductores</title>
<style>
  :root { color-scheme: dark; }
  body { margin:0; background:#09240F; color:#F5F1E6; font-family: system-ui, -apple-system, sans-serif; }
  main { max-width: 760px; margin: 0 auto; padding: 24px 16px 64px; }
  h1 { font-size: 20px; margin: 8px 0 2px; }
  p.sub { color:#BFDDBB; margin:0 0 20px; font-size:14px; }
  .card { background:#164A1F; border:1px solid #2A6A38; border-radius:16px; padding:16px; margin-bottom:16px; }
  .row { display:flex; align-items:center; gap:12px; flex-wrap:wrap; }
  .badge { font-size:11px; padding:3px 9px; border-radius:999px; background:#C9A344; color:#051A0A; font-weight:700; }
  .badge.rejected { background:#E5484D; color:#fff; }
  .photos { display:flex; gap:8px; overflow-x:auto; margin:12px 0; }
  .photos figure { margin:0; text-align:center; }
  .photos img { width:104px; height:104px; object-fit:cover; border-radius:10px; border:1px solid #2A6A38; display:block; cursor:zoom-in; }
  .photos figcaption { font-size:10px; color:#8FB28F; margin-top:4px; }
  .actions { display:flex; gap:10px; }
  button.apr { flex:1; background:#35A847; border:0; color:#051A0A; font-weight:700; padding:12px; border-radius:12px; font-size:14px; cursor:pointer; }
  button.rej { flex:1; background:transparent; border:1px solid #E5484D; color:#E5484D; font-weight:700; padding:12px; border-radius:12px; font-size:14px; cursor:pointer; }
  input, button.pin { padding:12px; border-radius:12px; font-size:16px; }
  input { background:#0F3D17; border:1px solid #2A6A38; color:#F5F1E6; width:220px; }
  button.pin { background:#E9C15C; border:0; color:#051A0A; font-weight:700; cursor:pointer; }
  .err { color:#E5484D; font-size:13px; margin-top:8px; min-height:16px; }
  dialog { background:#164A1F; color:#F5F1E6; border:1px solid #2A6A38; border-radius:16px; max-width:560px; width:92%; }
  dialog::backdrop { background:rgba(0,0,0,.65); }
  textarea { width:100%; min-height:80px; background:#0F3D17; color:#F5F1E6; border:1px solid #2A6A38; border-radius:12px; padding:10px; font:inherit; box-sizing:border-box; }
  .big img { width:min(92vw,480px); max-height:70vh; object-fit:contain; display:block; margin:0 auto; }
</style>
</head>
<body>
<main>
  <h1>Añane Go · Revisión de conductores</h1>
  <p class="sub">Aprueba o rechaza los documentos enviados por la flota.</p>
  <div id="gate" class="card">
    <div class="row">
      <input id="pin" type="password" inputmode="numeric" placeholder="PIN de administrador" />
      <button class="pin" id="enter">Entrar</button>
    </div>
    <div class="err" id="gateErr"></div>
  </div>
  <div id="list"></div>
</main>
<dialog id="viewer"><div class="big" id="viewerBody"></div></dialog>
<dialog id="reject">
  <h3 style="margin-top:0">Motivo del rechazo</h3>
  <textarea id="reason" placeholder="Ej. La foto del DNI está borrosa, vuelve a subirla"></textarea>
  <div class="err" id="rejErr"></div>
  <div class="actions" style="margin-top:12px">
    <button class="rej" id="rejCancel">Cancelar</button>
    <button class="apr" id="rejSend">Rechazar</button>
  </div>
</dialog>
<script>
let pin = "";
const $ = (id) => document.getElementById(id);
const KINDS = [["PROFILE","Perfil"],["DNI_FRONT","DNI frente"],["DNI_BACK","DNI reverso"],["VEHICLE_CARD","Tarjeta"],["PLATE","Placa"]];
async function api(action, body) {
  const res = await fetch("/api/" + action, { method:"POST", headers:{ "Content-Type":"application/json" }, body: JSON.stringify(body) });
  return res.json();
}
async function load() {
  const data = await api("admin-list", { pin });
  if (!data.ok) { $("gateErr").textContent = data.error || "PIN incorrecto"; return; }
  $("gate").style.display = "none";
  const list = $("list");
  list.innerHTML = "";
  if (!data.pending.length) {
    list.innerHTML = '<div class="card">No hay documentos pendientes de revisión.</div>';
  }
  for (const d of data.pending) {
    const card = document.createElement("div");
    card.className = "card";
    const rejected = d.driverStatus === "REJECTED";
    const photos = KINDS.filter(([k]) => d.photos.includes(k))
      .map(([k, label]) => '<figure><img src="/api/verification-photo?userId=' + encodeURIComponent(d.id) + '&kind=' + k + '" data-label="' + label + '" /><figcaption>' + label + '</figcaption></figure>').join("");
    card.innerHTML =
      '<div class="row"><strong style="font-size:16px">' + d.name + '</strong>' +
      '<span class="badge ' + (rejected ? "rejected" : "") + '">' + (rejected ? "Rechazado" : "En revisión") + '</span></div>' +
      '<div style="color:#BFDDBB;font-size:13px;margin-top:4px">DNI ' + (d.dni || "—") + ' · ' +
      (d.vehicleType === "INTERCITY_CAR" ? "Auto interprovincial" : "Mototaxi") + ' · Placa ' + (d.plate || "—") + '</div>' +
      (rejected && d.rejectionReason ? '<div style="color:#E5484D;font-size:13px;margin-top:6px">Motivo anterior: ' + d.rejectionReason + '</div>' : "") +
      '<div class="photos">' + (photos || '<span style="color:#8FB28F;font-size:13px">Sin fotos</span>') + '</div>' +
      '<div class="actions"><button class="apr">Aprobar</button><button class="rej">Rechazar</button></div>';
    card.querySelector(".apr").onclick = async () => {
      const res = await api("admin-decide", { pin, targetUserId: d.id, decision: "approve" });
      if (res.ok) load(); else alert(res.error || "No se pudo aprobar");
    };
    card.querySelector(".rej").onclick = () => {
      $("reason").value = d.rejectionReason || "";
      $("rejErr").textContent = "";
      $("reject").dataset.user = d.id;
      $("reject").showModal();
    };
    list.appendChild(card);
  }
}
$("enter").onclick = () => { pin = $("pin").value.trim(); if (!pin) return; load(); };
$("pin").addEventListener("keydown", (e) => { if (e.key === "Enter") $("enter").click(); });
$("rejCancel").onclick = () => $("reject").close();
$("rejSend").onclick = async () => {
  const reason = $("reason").value.trim();
  if (reason.length < 3) { $("rejErr").textContent = "Escribe el motivo del rechazo"; return; }
  const res = await api("admin-decide", { pin, targetUserId: $("reject").dataset.user, decision: "reject", reason });
  if (res.ok) { $("reject").close(); load(); } else $("rejErr").textContent = res.error || "No se pudo rechazar";
};
document.addEventListener("click", (e) => {
  const img = e.target.closest(".photos img");
  if (!img) return;
  $("viewerBody").innerHTML = '<img src="' + img.src + '" alt="' + img.dataset.label + '" /><p style="text-align:center">' + img.dataset.label + '</p>';
  $("viewer").showModal();
});
setInterval(() => { if (pin) load(); }, 15000);
</script>
</body>
</html>`;

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);

    if (request.method === "OPTIONS") {
      return new Response(null, { status: 204, headers: CORS });
    }

    if (url.pathname === "/ping") {
      return Response.json({ ok: true, service: "ananego", now: Date.now() }, { headers: CORS });
    }

    // Document review console, protected by the admin PIN inside the page.
    if (url.pathname === "/admin" && request.method === "GET") {
      return new Response(ADMIN_HTML, {
        headers: { "Content-Type": "text/html; charset=utf-8", ...CORS },
      });
    }

    // Realtime stream: /live?userId=...&role=PASSENGER|DRIVER
    if (url.pathname === "/live") {
      if (request.headers.get("Upgrade") !== "websocket") {
        return new Response("expected websocket", { status: 426, headers: CORS });
      }
      return dispatch(request, env, request.url);
    }

    // Verification photos: GET /api/verification-photo?userId=...&kind=DNI_FRONT
    // streams the stored image back so the app can preview what was uploaded.
    if (url.pathname === "/api/verification-photo" && request.method === "GET") {
      const target = new URL(request.url);
      target.pathname = "/verification-photo";
      return withCors(dispatch(request, env, target.toString()));
    }

    // Commands: POST /api/<action>
    if (url.pathname.startsWith("/api/") && request.method === "POST") {
      const action = url.pathname.slice("/api/".length);
      if (!ACTIONS.has(action)) {
        return Response.json({ ok: false, error: "unknown action" }, { status: 404, headers: CORS });
      }
      const target = new URL(request.url);
      target.pathname = `/${action}`;
      return withCors(dispatch(request, env, target.toString()));
    }

    return Response.json({ ok: false, error: "not found" }, { status: 404, headers: CORS });
  },
} satisfies ExportedHandler<Env>;
