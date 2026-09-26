/**
 * SatipoNetwork — the single live authority for the Añane Go city network.
 *
 * It owns every user profile, every driver's live GPS position, the open ride
 * requests and the rides in progress, and it pushes a personalised snapshot to
 * each connected device over WebSocket.
 *
 * A small pool of server-driven demo drivers keeps the city from looking empty
 * while the real fleet is still signing up; they are flagged `simulated` so the
 * app can label them honestly, and real drivers always take priority.
 */
import { DurableObject } from "cloudflare:workers";

type Env = { DO: Fetcher; ADMIN_PIN?: string };

export type VehicleType = "MOTOTAXI" | "INTERCITY_CAR";
export type ServiceKind = "LOCAL_MOTOTAXI" | "INTERCITY";
export type RideStatus =
  | "SEARCHING"
  | "ACCEPTED"
  | "ARRIVED"
  | "ON_TRIP"
  | "COMPLETED"
  | "CANCELLED";
export type VerificationStatus = "NOT_STARTED" | "PENDING" | "VERIFIED" | "REJECTED";

/** Document photos a driver uploads for the official identity review. */
export type PhotoKind = "PROFILE" | "DNI_FRONT" | "DNI_BACK" | "VEHICLE_CARD" | "PLATE";
export const PHOTO_KINDS: readonly PhotoKind[] = [
  "PROFILE",
  "DNI_FRONT",
  "DNI_BACK",
  "VEHICLE_CARD",
  "PLATE",
];
/** Optional photos that are stored but never block the identity review. */
const OPTIONAL_PHOTO_KINDS: readonly string[] = ["PAYMENT_QR"];
export type PaymentMethod = "CASH" | "YAPE_PLIN";
const PHOTO_LABELS: Record<string, string> = {
  PROFILE: "foto de perfil",
  DNI_FRONT: "DNI frente",
  DNI_BACK: "DNI reverso",
  VEHICLE_CARD: "tarjeta de propiedad",
  PLATE: "placa del vehículo",
};
/** Cleans a Peruvian mobile number down to its 9 digits (drops +51). */
function cleanPhone(raw: unknown): string {
  const digits = String(raw ?? "").replace(/\D/g, "");
  const local = digits.length === 11 && digits.startsWith("51") ? digits.slice(2) : digits;
  return local.slice(0, 9);
}

/** Rough cap (base64 characters) so one upload cannot blow up the SQL row. */
const MAX_PHOTO_BASE64 = 3_000_000;

const CITY_CENTER = { lat: -11.25283, lng: -74.63757 };
const DRIVER_STALE_MS = 50_000;
const REQUEST_RADIUS_KM = 8;
const TICK_MS = 3_000;
const SIM_TARGET_FLEET = 6;

/** Minimum price a passenger may propose per service. */
const FARE_FLOORS: Record<ServiceKind, number> = {
  LOCAL_MOTOTAXI: 2,
  INTERCITY: 5,
};

/** Flat Satipo route fares (autos de ruta), matched by keyword. No Directions API calls. */
const FIXED_INTERCITY_FARES: ReadonlyArray<{ keys: string[]; fare: number }> = [
  { keys: ["mazamari"], fare: 5 },
  { keys: ["pangoa"], fare: 8 },
  { keys: ["pichanaqui", "pichanaki"], fare: 20 },
  { keys: ["la merced"], fare: 40 },
  { keys: ["huancayo"], fare: 90 },
  { keys: ["lima"], fare: 140 },
];
/** Demo drivers counter-offer after this quiet period (real drivers first). */
const SIM_OFFER_GRACE_ONLINE_MS = 30_000;
const SIM_OFFER_GRACE_OFFLINE_MS = 10_000;
/** If even the demo offer is ignored this long, the ride closes with it. */
const SIM_AUTO_CLOSE_MS = 60_000;
const WEEK_MS = 7 * 24 * 60 * 60 * 1000;

interface UserRow {
  id: string;
  name: string;
  phone: string;
  dni: string;
  rating: number;
  trip_count: number;
  role: string;
  is_verified: number;
  vehicle_type: string;
  plate: string;
  driver_status: string;
  driver_submitted_at: number;
  subscription_expires_at: number;
  paid_this_week: number;
  default_passenger_count: number;
  default_preferences: string;
  payout_phone: string;
  vehicle_model: string;
  lat: number | null;
  lng: number | null;
  is_online: number;
  last_seen_at: number;
}

interface RideRow {
  id: string;
  passenger_id: string;
  driver_id: string | null;
  service_kind: string;
  origin_name: string;
  origin_lat: number;
  origin_lng: number;
  dest_name: string;
  dest_detail: string;
  dest_lat: number;
  dest_lng: number;
  fare: number;
  distance_km: number;
  status: string;
  passenger_count: number;
  preferences: string;
  created_at: number;
  updated_at: number;
  stage_at: number;
  payment_method: string;
  reference: string;
}

interface PhotoRow {
  kind: string;
  mime: string;
  data: string;
}

interface OfferRow {
  ride_id: string;
  driver_id: string;
  amount: number;
  created_at: number;
}

interface SimRow {
  id: string;
  name: string;
  initials: string;
  rating: number;
  trip_count: number;
  vehicle_type: string;
  plate: string;
  vehicle_model?: string;
  lat: number;
  lng: number;
  target_lat: number;
  target_lng: number;
  ride_id: string | null;
}

type Attachment = { userId: string; role: string };

function toRadians(value: number): number {
  return (value * Math.PI) / 180;
}

function distanceKm(
  a: { lat: number; lng: number },
  b: { lat: number; lng: number },
): number {
  const earthRadiusKm = 6371;
  const dLat = toRadians(b.lat - a.lat);
  const dLng = toRadians(b.lng - a.lng);
  const h =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRadians(a.lat)) * Math.cos(toRadians(b.lat)) * Math.sin(dLng / 2) ** 2;
  return earthRadiusKm * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
}

function etaMinutes(km: number, vehicleType: string): number {
  const speedKmh = vehicleType === "INTERCITY_CAR" ? 42 : 19;
  return Math.max(1, Math.round((km / speedKmh) * 60));
}

function moveToward(
  from: { lat: number; lng: number },
  to: { lat: number; lng: number },
  fraction: number,
): { lat: number; lng: number } {
  return {
    lat: from.lat + (to.lat - from.lat) * fraction,
    lng: from.lng + (to.lng - from.lng) * fraction,
  };
}

function initialsOf(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return "AG";
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
}

function serviceFor(vehicleType: string): ServiceKind {
  return vehicleType === "INTERCITY_CAR" ? "INTERCITY" : "LOCAL_MOTOTAXI";
}

function vehicleFor(service: string): VehicleType {
  return service === "INTERCITY" ? "INTERCITY_CAR" : "MOTOTAXI";
}

function estimateFare(service: string, km: number, destinationName: string): number {
  if (service === "INTERCITY") {
    const name = destinationName.toLowerCase();
    const fixed = FIXED_INTERCITY_FARES.find(({ keys }) =>
      keys.some((key) => new RegExp(`\\b${key}\\b`).test(name)),
    );
    if (fixed) return fixed.fare;
    return Math.max(5, Math.round(5 + km * 0.45));
  }
  // S/ 2.00 base covers the first 1.5 km, then S/ 1.00 per extra km.
  const raw = 2 + Math.max(0, km - 1.5);
  return Math.round(Math.min(Math.max(raw, 2), 15) * 2) / 2;
}

const SIM_SEED: ReadonlyArray<{
  name: string;
  rating: number;
  trips: number;
  vehicleType: VehicleType;
  plate: string;
  model: string;
}> = [
  { name: "Elmer Quispe", rating: 4.9, trips: 1284, vehicleType: "MOTOTAXI", plate: "M1-2483", model: "Bajaj RE" },
  { name: "Rosa Camarena", rating: 4.8, trips: 962, vehicleType: "MOTOTAXI", plate: "M3-7741", model: "TVS King" },
  { name: "Javier Ñaupari", rating: 4.7, trips: 640, vehicleType: "MOTOTAXI", plate: "M2-1190", model: "Honda Cargo" },
  { name: "Lucía Marín", rating: 5.0, trips: 418, vehicleType: "MOTOTAXI", plate: "M4-3025", model: "Bajaj RE" },
  { name: "Carlos Bardales", rating: 4.9, trips: 1533, vehicleType: "INTERCITY_CAR", plate: "V6L-882", model: "Toyota Yaris" },
  { name: "Teresa Ponce", rating: 4.8, trips: 727, vehicleType: "INTERCITY_CAR", plate: "W2K-450", model: "Hyundai Accent" },
];

function simModel(plate: string): string {
  return SIM_SEED.find((seed) => seed.plate === plate)?.model ?? "";
}

export class SatipoNetwork extends DurableObject<Env> {
  private heartbeat: ReturnType<typeof setTimeout> | null = null;
  private lastTickAt = 0;

  private readonly adminPin: string;

  constructor(ctx: DurableObjectState, env: Env) {
    super(ctx, env);
    this.adminPin = (env.ADMIN_PIN ?? "").trim() || "1520";
    const sql = this.ctx.storage.sql;
    sql.exec(`
      CREATE TABLE IF NOT EXISTS users (
        id TEXT PRIMARY KEY,
        name TEXT NOT NULL DEFAULT '',
        phone TEXT NOT NULL DEFAULT '',
        dni TEXT NOT NULL DEFAULT '',
        rating REAL NOT NULL DEFAULT 5.0,
        trip_count INTEGER NOT NULL DEFAULT 0,
        role TEXT NOT NULL DEFAULT 'PASSENGER',
        is_verified INTEGER NOT NULL DEFAULT 0,
        vehicle_type TEXT NOT NULL DEFAULT 'MOTOTAXI',
        plate TEXT NOT NULL DEFAULT '',
        driver_status TEXT NOT NULL DEFAULT 'NOT_STARTED',
        driver_submitted_at INTEGER NOT NULL DEFAULT 0,
        subscription_expires_at INTEGER NOT NULL DEFAULT 0,
        paid_this_week REAL NOT NULL DEFAULT 0,
        default_passenger_count INTEGER NOT NULL DEFAULT 1,
        default_preferences TEXT NOT NULL DEFAULT '[]',
        driver_rejection_reason TEXT NOT NULL DEFAULT '',
        lat REAL,
        lng REAL,
        is_online INTEGER NOT NULL DEFAULT 0,
        last_seen_at INTEGER NOT NULL DEFAULT 0
      )
    `);
    // Older deployments created the tables without these columns.
    const migrations = [
      "ALTER TABLE users ADD COLUMN driver_rejection_reason TEXT NOT NULL DEFAULT ''",
      "ALTER TABLE users ADD COLUMN payout_phone TEXT NOT NULL DEFAULT ''",
      "ALTER TABLE users ADD COLUMN vehicle_model TEXT NOT NULL DEFAULT ''",
    ];
    for (const statement of migrations) {
      try {
        sql.exec(statement);
      } catch {
        // Column already exists.
      }
    }
    sql.exec(`
      CREATE TABLE IF NOT EXISTS offers (
        ride_id TEXT NOT NULL,
        driver_id TEXT NOT NULL,
        amount REAL NOT NULL,
        created_at INTEGER NOT NULL,
        updated_at INTEGER NOT NULL,
        PRIMARY KEY (ride_id, driver_id)
      )
    `);
    sql.exec(`
      CREATE TABLE IF NOT EXISTS rides (
        id TEXT PRIMARY KEY,
        passenger_id TEXT NOT NULL,
        driver_id TEXT,
        service_kind TEXT NOT NULL,
        origin_name TEXT NOT NULL,
        origin_lat REAL NOT NULL,
        origin_lng REAL NOT NULL,
        dest_name TEXT NOT NULL,
        dest_detail TEXT NOT NULL DEFAULT '',
        dest_lat REAL NOT NULL,
        dest_lng REAL NOT NULL,
        fare REAL NOT NULL,
        distance_km REAL NOT NULL,
        status TEXT NOT NULL,
        passenger_count INTEGER NOT NULL DEFAULT 1,
        preferences TEXT NOT NULL DEFAULT '[]',
        created_at INTEGER NOT NULL,
        updated_at INTEGER NOT NULL,
        stage_at INTEGER NOT NULL DEFAULT 0,
        payment_method TEXT NOT NULL DEFAULT 'CASH',
        reference TEXT NOT NULL DEFAULT ''
      )
    `);
    for (const statement of [
      "ALTER TABLE rides ADD COLUMN payment_method TEXT NOT NULL DEFAULT 'CASH'",
      "ALTER TABLE rides ADD COLUMN reference TEXT NOT NULL DEFAULT ''",
    ]) {
      try {
        sql.exec(statement);
      } catch {
        // Column already exists.
      }
    }
    sql.exec(`
      CREATE TABLE IF NOT EXISTS declines (
        ride_id TEXT NOT NULL,
        driver_id TEXT NOT NULL,
        PRIMARY KEY (ride_id, driver_id)
      )
    `);
    sql.exec(`
      CREATE TABLE IF NOT EXISTS verification_photos (
        user_id TEXT NOT NULL,
        kind TEXT NOT NULL,
        mime TEXT NOT NULL,
        data TEXT NOT NULL,
        uploaded_at INTEGER NOT NULL,
        PRIMARY KEY (user_id, kind)
      )
    `);
    sql.exec(`
      CREATE TABLE IF NOT EXISTS sim_drivers (
        id TEXT PRIMARY KEY,
        name TEXT NOT NULL,
        initials TEXT NOT NULL,
        rating REAL NOT NULL,
        trip_count INTEGER NOT NULL,
        vehicle_type TEXT NOT NULL,
        plate TEXT NOT NULL,
        lat REAL NOT NULL,
        lng REAL NOT NULL,
        target_lat REAL NOT NULL,
        target_lng REAL NOT NULL,
        ride_id TEXT
      )
    `);
  }

  // ---------------------------------------------------------------- transport

  override async fetch(request: Request): Promise<Response> {
    const url = new URL(request.url);

    // Serves a stored verification photo back to the app for preview.
    if (url.pathname === "/verification-photo" && request.method === "GET") {
      return this.servePhoto(url.searchParams);
    }

    if (request.headers.get("Upgrade") === "websocket") {
      const userId = url.searchParams.get("userId") ?? "";
      if (!userId) return new Response("userId required", { status: 400 });
      const role = url.searchParams.get("role") ?? "PASSENGER";

      const pair = new WebSocketPair();
      const [client, server] = Object.values(pair);
      this.ctx.acceptWebSocket(server);
      server.serializeAttachment({ userId, role } satisfies Attachment);

      this.ensureUser(userId);
      this.touch(userId, role);
      this.maybeTick();
      server.send(JSON.stringify(this.snapshotFor(userId, role)));
      this.ensureHeartbeat();
      return new Response(null, { status: 101, webSocket: client });
    }

    const body = request.method === "POST" ? await this.readJson(request) : {};
    return this.handleCommand(url.pathname, body);
  }

  private async readJson(request: Request): Promise<Record<string, unknown>> {
    try {
      return (await request.json()) as Record<string, unknown>;
    } catch {
      return {};
    }
  }

  override webSocketMessage(ws: WebSocket, raw: string | ArrayBuffer): void {
    const attachment = ws.deserializeAttachment() as Attachment | null;
    if (!attachment) return;
    const text = typeof raw === "string" ? raw : new TextDecoder().decode(raw);

    let payload: Record<string, unknown>;
    try {
      payload = JSON.parse(text) as Record<string, unknown>;
    } catch {
      return;
    }

    const type = String(payload.type ?? "");
    let role = attachment.role;

    if (type === "role" && typeof payload.role === "string") {
      role = payload.role;
      ws.serializeAttachment({ userId: attachment.userId, role } satisfies Attachment);
      this.setRole(attachment.userId, role);
    } else if (type === "location") {
      const lat = Number(payload.lat);
      const lng = Number(payload.lng);
      if (Number.isFinite(lat) && Number.isFinite(lng)) {
        this.setLocation(attachment.userId, lat, lng);
      }
    } else if (type === "ping") {
      this.touch(attachment.userId, role);
    } else {
      const result = this.handleCommandSync(type, {
        ...payload,
        userId: attachment.userId,
      });
      if (result !== null) {
        ws.send(JSON.stringify({ type: "notice", message: result }));
      }
    }

    this.touch(attachment.userId, role);
    this.ensureHeartbeat();
    this.broadcast();
  }

  override webSocketClose(ws: WebSocket): void {
    const attachment = ws.deserializeAttachment() as Attachment | null;
    if (attachment) {
      this.ctx.storage.sql.exec(
        "UPDATE users SET is_online = 0 WHERE id = ?",
        attachment.userId,
      );
    }
    if (this.ctx.getWebSockets().length <= 1 && this.heartbeat !== null) {
      clearTimeout(this.heartbeat);
      this.heartbeat = null;
    }
  }

  /** Runs a simulation step at most once per tick window. */
  private maybeTick(): void {
    const now = Date.now();
    if (now - this.lastTickAt < TICK_MS) return;
    this.lastTickAt = now;
    try {
      this.tick();
    } catch (error: unknown) {
      console.error("tick failed", error);
    }
  }

  private ensureHeartbeat(): void {
    if (this.heartbeat !== null || this.ctx.getWebSockets().length === 0) return;
    this.heartbeat = setTimeout(() => {
      this.heartbeat = null;
      this.lastTickAt = Date.now();
      try {
        this.tick();
        this.broadcast();
      } catch (error: unknown) {
        console.error("tick failed", error);
      }
      this.ensureHeartbeat();
    }, TICK_MS);
  }

  private broadcast(): void {
    for (const peer of this.ctx.getWebSockets()) {
      const attachment = peer.deserializeAttachment() as Attachment | null;
      if (!attachment) continue;
      try {
        peer.send(JSON.stringify(this.snapshotFor(attachment.userId, attachment.role)));
      } catch (error: unknown) {
        console.warn("snapshot send failed", error);
      }
    }
  }

  // ----------------------------------------------------------------- commands

  private async handleCommand(
    pathname: string,
    body: Record<string, unknown>,
  ): Promise<Response> {
    const action = pathname.replace(/^\/+/, "");
    const userId = typeof body.userId === "string" ? body.userId : "";

    if (action === "health") {
      return Response.json({ ok: true, now: Date.now() });
    }
    // Admin review routes authenticate with the PIN instead of a userId.
    if (action === "admin-login" || action === "admin-list" || action === "admin-decide") {
      return this.handleAdmin(action, body);
    }
    if (!userId) {
      return Response.json({ ok: false, error: "userId required" }, { status: 400 });
    }

    this.ensureUser(userId);
    const notice = this.handleCommandSync(action, body);
    // HTTP callers keep the city moving too, so the network progresses even
    // while a device's realtime socket is reconnecting.
    this.maybeTick();
    this.broadcast();
    const role = typeof body.role === "string" ? body.role : this.roleOf(userId);
    return Response.json({
      ok: notice === null,
      notice,
      snapshot: this.snapshotFor(userId, role),
    });
  }

  /** Returns null on success, or a user-facing message when refused. */
  private handleCommandSync(action: string, body: Record<string, unknown>): string | null {
    const userId = String(body.userId ?? "");
    if (!userId) return "Sesión no válida";
    this.ensureUser(userId);

    // Commands may carry the caller's latest fix so the network still knows
    // where they are when the realtime socket is reconnecting.
    const lat = Number(body.lat);
    const lng = Number(body.lng);
    if (Number.isFinite(lat) && Number.isFinite(lng) && (lat !== 0 || lng !== 0)) {
      this.setLocation(userId, lat, lng);
    }

    switch (action) {
      case "profile":
        return this.updateProfile(userId, body);
      case "role":
        this.setRole(userId, String(body.role ?? "PASSENGER"));
        return null;
      case "online":
        return this.setOnline(userId, body.isOnline === true);
      case "request-ride":
        return this.createRide(userId, body);
      case "ride-preferences":
        return this.updateRidePreferences(userId, body);
      case "accept-ride":
        return this.acceptRide(String(body.rideId ?? ""), userId);
      case "offer-ride":
        return this.offerRide(userId, body);
      case "accept-offer":
        return this.acceptOffer(userId, body);
      case "reject-offer":
        return this.rejectOffer(userId, body);
      case "decline-ride":
        this.declineRide(String(body.rideId ?? ""), userId);
        return null;
      case "advance-ride":
        return this.advanceRide(String(body.rideId ?? ""), userId);
      case "cancel-ride":
        return this.cancelRide(String(body.rideId ?? ""), userId);
      case "submit-verification":
        return this.submitVerification(userId, body);
      case "verification-photo":
        return this.storePhoto(userId, body);
      case "payout":
        return this.updatePayout(userId, body);
      case "renew-subscription":
        return this.renewSubscription(userId);
      case "sync":
        return null;
      default:
        return null;
    }
  }

  // -------------------------------------------------------------------- users

  private ensureUser(userId: string): void {
    this.ctx.storage.sql.exec(
      "INSERT INTO users (id, last_seen_at) VALUES (?, ?) ON CONFLICT(id) DO NOTHING",
      userId,
      Date.now(),
    );
  }

  private user(userId: string): UserRow {
    this.ensureUser(userId);
    const rows = this.ctx.storage.sql
      .exec<UserRow>("SELECT * FROM users WHERE id = ?", userId)
      .toArray();
    return rows[0];
  }

  private roleOf(userId: string): string {
    return this.user(userId).role;
  }

  private touch(userId: string, role: string): void {
    this.ctx.storage.sql.exec(
      "UPDATE users SET last_seen_at = ?, role = ? WHERE id = ?",
      Date.now(),
      role,
      userId,
    );
  }

  private setRole(userId: string, role: string): void {
    const safe = role === "DRIVER" ? "DRIVER" : "PASSENGER";
    this.ctx.storage.sql.exec("UPDATE users SET role = ? WHERE id = ?", safe, userId);
  }

  private setLocation(userId: string, lat: number, lng: number): void {
    this.ctx.storage.sql.exec(
      "UPDATE users SET lat = ?, lng = ?, last_seen_at = ? WHERE id = ?",
      lat,
      lng,
      Date.now(),
      userId,
    );
    this.ctx.storage.put("anchor", { lat, lng }, { allowUnconfirmed: true });
  }

  private updateProfile(userId: string, body: Record<string, unknown>): string | null {
    const current = this.user(userId);
    const name = typeof body.name === "string" && body.name.trim() ? body.name.trim() : current.name;
    const phone = typeof body.phone === "string" ? body.phone : current.phone;
    const dni = typeof body.dni === "string" ? body.dni : current.dni;
    const passengerCount = Number.isFinite(Number(body.defaultPassengerCount))
      ? Math.min(4, Math.max(1, Number(body.defaultPassengerCount)))
      : current.default_passenger_count;
    const preferences = Array.isArray(body.defaultPreferences)
      ? JSON.stringify(body.defaultPreferences)
      : current.default_preferences;
    const verified = dni.length >= 8 && name.length > 2 ? 1 : current.is_verified;

    this.ctx.storage.sql.exec(
      `UPDATE users SET name = ?, phone = ?, dni = ?, default_passenger_count = ?,
         default_preferences = ?, is_verified = ? WHERE id = ?`,
      name,
      phone,
      dni,
      passengerCount,
      preferences,
      verified,
      userId,
    );
    return null;
  }

  private setOnline(userId: string, isOnline: boolean): string | null {
    const user = this.user(userId);
    if (isOnline) {
      if (user.driver_status !== "VERIFIED") return "Completa tu verificación para recibir viajes";
      if (user.subscription_expires_at < Date.now()) return "Renueva tu suscripción para recibir viajes";
      if (user.lat === null || user.lng === null) return "Activa tu ubicación para recibir viajes cercanos";
    }
    this.ctx.storage.sql.exec(
      "UPDATE users SET is_online = ?, last_seen_at = ? WHERE id = ?",
      isOnline ? 1 : 0,
      Date.now(),
      userId,
    );
    return null;
  }

  private submitVerification(userId: string, body: Record<string, unknown>): string | null {
    const name = typeof body.name === "string" ? body.name.trim() : "";
    const dni = typeof body.dni === "string" ? body.dni.trim() : "";
    const plate = typeof body.plate === "string" ? body.plate.trim() : "";
    const vehicleType: VehicleType = body.vehicleType === "INTERCITY_CAR" ? "INTERCITY_CAR" : "MOTOTAXI";
    if (!name || dni.length < 8 || !plate) return "Faltan datos obligatorios en tu registro";
    const current = this.user(userId);
    const phone = body.phone !== undefined ? cleanPhone(body.phone) : current.phone;
    if (phone.length !== 9) return "Ingresa tu número de celular (9 dígitos)";
    const vehicleModel = typeof body.vehicleModel === "string"
      ? body.vehicleModel.trim().slice(0, 40)
      : current.vehicle_model;
    const payoutPhone = body.payoutPhone !== undefined ? cleanPhone(body.payoutPhone) : current.payout_phone;

    // Official review needs the real document photos on file, not just ticked boxes.
    const uploaded = new Set(this.photoKinds(userId));
    const missing = PHOTO_KINDS.filter((kind) => !uploaded.has(kind));
    if (missing.length > 0) {
      return `Falta subir: ${missing.map((kind) => PHOTO_LABELS[kind]).join(", ")}`;
    }

    this.ctx.storage.sql.exec(
      `UPDATE users SET name = ?, dni = ?, plate = ?, vehicle_type = ?, phone = ?, vehicle_model = ?,
         payout_phone = ?, role = 'DRIVER', driver_status = 'PENDING', driver_rejection_reason = '',
         driver_submitted_at = ?, is_verified = 1 WHERE id = ?`,
      name,
      dni,
      plate,
      vehicleType,
      phone,
      vehicleModel,
      payoutPhone,
      Date.now(),
      userId,
    );
    return null;
  }

  /** Saves the driver's Yape / Plin number without resetting their review. */
  private updatePayout(userId: string, body: Record<string, unknown>): string | null {
    const payoutPhone = cleanPhone(body.payoutPhone);
    if (payoutPhone.length !== 0 && payoutPhone.length !== 9) {
      return "El número de Yape / Plin debe tener 9 dígitos";
    }
    this.ctx.storage.sql.exec("UPDATE users SET payout_phone = ? WHERE id = ?", payoutPhone, userId);
    return null;
  }

  // -------------------------------------------------- verification photos

  /** Persists (or replaces) one document photo for official review. */
  private storePhoto(userId: string, body: Record<string, unknown>): string | null {
    const kind = String(body.kind ?? "") as PhotoKind;
    const data = typeof body.image === "string" ? body.image : "";
    const mime = String(body.mime ?? "image/jpeg");

    if (!PHOTO_KINDS.includes(kind) && !OPTIONAL_PHOTO_KINDS.includes(kind)) {
      return "Ese documento no es válido";
    }
    if (!data) return "No pudimos leer la foto, intenta de nuevo";
    if (data.length > MAX_PHOTO_BASE64) return "La foto es muy pesada, toma una más cercana";
    if (!/^image\/(jpeg|png|webp)$/.test(mime)) return "Formato de imagen no soportado";

    this.ctx.storage.sql.exec(
      `INSERT INTO verification_photos (user_id, kind, mime, data, uploaded_at)
         VALUES (?, ?, ?, ?, ?)
       ON CONFLICT(user_id, kind) DO UPDATE SET mime = excluded.mime,
         data = excluded.data, uploaded_at = excluded.uploaded_at`,
      userId,
      kind,
      mime,
      data,
      Date.now(),
    );
    return null;
  }

  /** Streams a stored document photo so the app can preview it. */
  private servePhoto(params: URLSearchParams): Response {
    const userId = params.get("userId") ?? "";
    const kind = params.get("kind") ?? "";
    if (!userId || !kind) return new Response("userId and kind required", { status: 400 });

    const rows = this.ctx.storage.sql
      .exec<PhotoRow>(
        "SELECT kind, mime, data FROM verification_photos WHERE user_id = ? AND kind = ?",
        userId,
        kind,
      )
      .toArray();
    const row = rows[0];
    if (!row) return new Response("not found", { status: 404 });

    const bytes = Uint8Array.from(atob(row.data), (char) => char.charCodeAt(0));
    return new Response(bytes, {
      headers: {
        "Content-Type": row.mime,
        "Cache-Control": "no-store",
      },
    });
  }

  private photoKinds(userId: string): string[] {
    return this.ctx.storage.sql
      .exec<{ kind: string }>(
        "SELECT kind FROM verification_photos WHERE user_id = ?",
        userId,
      )
      .toArray()
      .map((row) => row.kind);
  }

  private renewSubscription(userId: string): string | null {
    const user = this.user(userId);
    const base = Math.max(user.subscription_expires_at, Date.now());
    const fee = user.vehicle_type === "INTERCITY_CAR" ? 10 : 5;
    this.ctx.storage.sql.exec(
      "UPDATE users SET subscription_expires_at = ?, paid_this_week = ? WHERE id = ?",
      base + WEEK_MS,
      fee,
      userId,
    );
    return null;
  }

  // -------------------------------------------------------------------- rides

  private createRide(userId: string, body: Record<string, unknown>): string | null {
    const user = this.user(userId);
    const originLat = Number(body.originLat);
    const originLng = Number(body.originLng);
    const destLat = Number(body.destLat);
    const destLng = Number(body.destLng);
    if (![originLat, originLng, destLat, destLng].every(Number.isFinite)) {
      return "No pudimos leer el punto de recojo";
    }

    const existing = this.activeRideFor(userId, "PASSENGER");
    if (existing) return "Ya tienes un viaje en curso";

    const service: ServiceKind = body.serviceKind === "INTERCITY" ? "INTERCITY" : "LOCAL_MOTOTAXI";
    const destName = String(body.destName ?? "Destino");
    const km = distanceKm({ lat: originLat, lng: originLng }, { lat: destLat, lng: destLng });
    // The passenger sets their own price; the suggested fare is the fallback.
    const floor = FARE_FLOORS[service] ?? 2;
    const proposed = Math.round(Number(body.proposedFare) * 2) / 2;
    const fare = Number.isFinite(proposed) && proposed >= floor
      ? proposed
      : estimateFare(service, km, destName);
    const now = Date.now();
    const id = `ride_${now.toString(36)}_${Math.random().toString(36).slice(2, 8)}`;
    const paymentMethod: PaymentMethod = body.paymentMethod === "YAPE_PLIN" ? "YAPE_PLIN" : "CASH";
    const reference = String(body.reference ?? "").trim().slice(0, 120);

    this.ctx.storage.sql.exec(
      `INSERT INTO rides (id, passenger_id, driver_id, service_kind, origin_name, origin_lat,
         origin_lng, dest_name, dest_detail, dest_lat, dest_lng, fare, distance_km, status,
         passenger_count, preferences, created_at, updated_at, stage_at, payment_method, reference)
       VALUES (?, ?, NULL, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'SEARCHING', ?, ?, ?, ?, ?, ?, ?)`,
      id,
      userId,
      service,
      String(body.originName ?? "Mi ubicación actual"),
      originLat,
      originLng,
      destName,
      String(body.destDetail ?? ""),
      destLat,
      destLng,
      fare,
      km,
      Number.isFinite(Number(body.passengerCount))
        ? Math.min(4, Math.max(1, Number(body.passengerCount)))
        : user.default_passenger_count,
      Array.isArray(body.preferences) ? JSON.stringify(body.preferences) : user.default_preferences,
      now,
      now,
      now,
      paymentMethod,
      reference,
    );
    return null;
  }

  /** Passengers may retune the trip's needs while the ride is still running. */
  private updateRidePreferences(userId: string, body: Record<string, unknown>): string | null {
    const ride = this.ride(String(body.rideId ?? ""));
    if (!ride) return null;
    if (ride.passenger_id !== userId) return "Solo el pasajero puede cambiar las preferencias";

    const passengerCount = Number.isFinite(Number(body.passengerCount))
      ? Math.min(4, Math.max(1, Number(body.passengerCount)))
      : ride.passenger_count;
    const preferences = Array.isArray(body.preferences)
      ? JSON.stringify(body.preferences)
      : ride.preferences;

    this.ctx.storage.sql.exec(
      "UPDATE rides SET passenger_count = ?, preferences = ?, updated_at = ? WHERE id = ?",
      passengerCount,
      preferences,
      Date.now(),
      ride.id,
    );
    this.ctx.storage.sql.exec(
      "UPDATE users SET default_passenger_count = ?, default_preferences = ? WHERE id = ?",
      passengerCount,
      preferences,
      userId,
    );
    return null;
  }

  private acceptRide(rideId: string, driverId: string): string | null {
    const ride = this.ride(rideId);
    if (!ride) return "Esa solicitud ya no está disponible";
    if (ride.status !== "SEARCHING") return "Otro conductor tomó este viaje";
    const driver = this.user(driverId);
    if (driver.driver_status !== "VERIFIED") return "Completa tu verificación para aceptar viajes";
    if (driver.subscription_expires_at < Date.now()) return "Renueva tu suscripción para aceptar viajes";

    const now = Date.now();
    this.ctx.storage.sql.exec(
      "UPDATE rides SET driver_id = ?, status = 'ACCEPTED', updated_at = ?, stage_at = ? WHERE id = ? AND status = 'SEARCHING'",
      driverId,
      now,
      now,
      rideId,
    );
    this.ctx.storage.sql.exec("DELETE FROM offers WHERE ride_id = ?", rideId);
    return null;
  }

  private declineRide(rideId: string, driverId: string): void {
    this.ctx.storage.sql.exec(
      `INSERT INTO declines (ride_id, driver_id) VALUES (?, ?) ON CONFLICT DO NOTHING`,
      rideId,
      driverId,
    );
  }

  /** A driver proposes (or improves) their single price for an open request. */
  private offerRide(driverId: string, body: Record<string, unknown>): string | null {
    const ride = this.ride(String(body.rideId ?? ""));
    if (!ride) return "Esa solicitud ya no está disponible";
    if (ride.status !== "SEARCHING") return "Este viaje ya no está abierto";
    const driver = this.user(driverId);
    if (driver.driver_status !== "VERIFIED") return "Completa tu verificación para ofertar";
    if (driver.subscription_expires_at < Date.now()) {
      return "Renueva tu suscripción para ofertar";
    }

    const floor = FARE_FLOORS[ride.service_kind as ServiceKind] ?? 2;
    const amount = Math.round(Number(body.amount) * 2) / 2;
    if (!Number.isFinite(amount) || amount < floor) {
      return `El monto mínimo es S/ ${floor.toFixed(2)}`;
    }

    const now = Date.now();
    this.ctx.storage.sql.exec(
      `INSERT INTO offers (ride_id, driver_id, amount, created_at, updated_at)
         VALUES (?, ?, ?, ?, ?)
       ON CONFLICT(ride_id, driver_id) DO UPDATE SET amount = excluded.amount,
         updated_at = excluded.updated_at`,
      ride.id,
      driverId,
      amount,
      now,
      now,
    );
    return null;
  }

  /** The passenger picks one offer; that price becomes the official fare. */
  private acceptOffer(passengerId: string, body: Record<string, unknown>): string | null {
    const ride = this.ride(String(body.rideId ?? ""));
    if (!ride) return "Ese viaje ya no existe";
    if (ride.passenger_id !== passengerId) return "Solo el pasajero puede aceptar ofertas";
    if (ride.status !== "SEARCHING") return "Ya elegiste un conductor";

    const driverId = String(body.driverId ?? "");
    const rows = this.ctx.storage.sql
      .exec<OfferRow>("SELECT * FROM offers WHERE ride_id = ? AND driver_id = ?", ride.id, driverId)
      .toArray();
    const offer = rows[0];
    if (!offer) return "Esa oferta ya no está disponible";

    const now = Date.now();
    this.ctx.storage.sql.exec(
      "UPDATE rides SET driver_id = ?, fare = ?, status = 'ACCEPTED', updated_at = ?, stage_at = ? WHERE id = ? AND status = 'SEARCHING'",
      driverId,
      offer.amount,
      now,
      now,
      ride.id,
    );
    this.ctx.storage.sql.exec("DELETE FROM offers WHERE ride_id = ?", ride.id);
    return null;
  }

  /** The passenger passes on one offer; that driver stops seeing the request. */
  private rejectOffer(passengerId: string, body: Record<string, unknown>): string | null {
    const ride = this.ride(String(body.rideId ?? ""));
    if (!ride || ride.passenger_id !== passengerId) return null;
    const driverId = String(body.driverId ?? "");
    this.ctx.storage.sql.exec(
      "DELETE FROM offers WHERE ride_id = ? AND driver_id = ?",
      ride.id,
      driverId,
    );
    this.ctx.storage.sql.exec(
      `INSERT INTO declines (ride_id, driver_id) VALUES (?, ?) ON CONFLICT DO NOTHING`,
      ride.id,
      driverId,
    );
    return null;
  }

  private offersForRide(rideId: string): OfferRow[] {
    return this.ctx.storage.sql
      .exec<OfferRow>("SELECT * FROM offers WHERE ride_id = ?", rideId)
      .toArray();
  }

  private myOfferAmount(rideId: string, driverId: string): number | null {
    const rows = this.ctx.storage.sql
      .exec<{ amount: number }>(
        "SELECT amount FROM offers WHERE ride_id = ? AND driver_id = ?",
        rideId,
        driverId,
      )
      .toArray();
    return rows[0] ? rows[0].amount : null;
  }

  /** Resolves a driver id (real user or demo fleet) into a driver payload. */
  private driverById(
    driverId: string,
    reference: { lat: number; lng: number } | null,
  ): Record<string, unknown> | null {
    const simRows = this.ctx.storage.sql
      .exec<SimRow>("SELECT * FROM sim_drivers WHERE id = ?", driverId)
      .toArray();
    if (simRows[0]) return this.driverPayload({ kind: "sim", row: simRows[0] }, reference);
    const realRows = this.ctx.storage.sql
      .exec<UserRow>("SELECT * FROM users WHERE id = ?", driverId)
      .toArray();
    if (realRows[0] && realRows[0].lat !== null && realRows[0].lng !== null) {
      return this.driverPayload({ kind: "real", row: realRows[0] }, reference);
    }
    return null;
  }

  // ----------------------------------------------------------- admin review

  /** PIN-gated document review: list pending drivers, approve or reject. */
  private handleAdmin(action: string, body: Record<string, unknown>): Response {
    const pin = String(body.pin ?? "");
    if (pin !== this.adminPin) {
      return Response.json({ ok: false, error: "PIN incorrecto" }, { status: 401 });
    }

    if (action === "admin-login") return Response.json({ ok: true });

    if (action === "admin-list") {
      const rows = this.ctx.storage.sql
        .exec<UserRow>(
          `SELECT * FROM users WHERE driver_status IN ('PENDING','REJECTED')
             ORDER BY driver_submitted_at ASC`,
        )
        .toArray();
      const pending = rows.map((row) => ({
        id: row.id,
        name: row.name || "Conductor Añane",
        dni: row.dni,
        plate: row.plate,
        vehicleType: row.vehicle_type,
        driverStatus: row.driver_status,
        rejectionReason: row.driver_rejection_reason ?? "",
        submittedAt: row.driver_submitted_at,
        photos: this.photoKinds(row.id),
      }));
      return Response.json({ ok: true, pending });
    }

    // admin-decide
    const targetId = String(body.targetUserId ?? "");
    const decision = String(body.decision ?? "");
    const reason = String(body.reason ?? "").trim();
    const target = this.user(targetId);
    if (target.driver_status === "NOT_STARTED") {
      return Response.json({ ok: false, error: "Ese usuario no ha enviado documentos" });
    }
    if (decision === "approve") {
      this.ctx.storage.sql.exec(
        "UPDATE users SET driver_status = 'VERIFIED', driver_rejection_reason = '' WHERE id = ?",
        targetId,
      );
      return Response.json({ ok: true, notice: "Conductor aprobado" });
    }
    if (decision === "reject") {
      if (reason.length < 3) {
        return Response.json({ ok: false, error: "Escribe el motivo del rechazo" });
      }
      this.ctx.storage.sql.exec(
        `UPDATE users SET driver_status = 'REJECTED', driver_rejection_reason = ?,
           driver_submitted_at = 0 WHERE id = ?`,
        reason,
        targetId,
      );
      return Response.json({ ok: true, notice: "Conductor rechazado" });
    }
    return Response.json({ ok: false, error: "Decisión no válida" });
  }

  private advanceRide(rideId: string, userId: string): string | null {
    const ride = this.ride(rideId);
    if (!ride) return "El viaje ya no existe";
    if (ride.driver_id !== userId && ride.passenger_id !== userId) return "No puedes actualizar este viaje";

    const next: Record<string, RideStatus> = {
      ACCEPTED: "ARRIVED",
      ARRIVED: "ON_TRIP",
      ON_TRIP: "COMPLETED",
    };
    const target = next[ride.status];
    if (!target) return null;

    const now = Date.now();
    this.ctx.storage.sql.exec(
      "UPDATE rides SET status = ?, updated_at = ?, stage_at = ? WHERE id = ?",
      target,
      now,
      now,
      rideId,
    );
    if (target === "COMPLETED") this.releaseSimDriver(rideId, true);
    return null;
  }

  private cancelRide(rideId: string, userId: string): string | null {
    const ride = this.ride(rideId);
    if (!ride) return null;
    if (ride.driver_id !== userId && ride.passenger_id !== userId) return "No puedes cancelar este viaje";
    const now = Date.now();
    this.ctx.storage.sql.exec(
      "UPDATE rides SET status = 'CANCELLED', updated_at = ?, stage_at = ? WHERE id = ?",
      now,
      now,
      rideId,
    );
    this.ctx.storage.sql.exec("DELETE FROM offers WHERE ride_id = ?", rideId);
    this.releaseSimDriver(rideId, false);
    return null;
  }

  private ride(rideId: string): RideRow | null {
    if (!rideId) return null;
    const rows = this.ctx.storage.sql
      .exec<RideRow>("SELECT * FROM rides WHERE id = ?", rideId)
      .toArray();
    return rows[0] ?? null;
  }

  private activeRideFor(userId: string, role: string): RideRow | null {
    const column = role === "DRIVER" ? "driver_id" : "passenger_id";
    const rows = this.ctx.storage.sql
      .exec<RideRow>(
        `SELECT * FROM rides WHERE ${column} = ?
           AND status IN ('SEARCHING','ACCEPTED','ARRIVED','ON_TRIP')
         ORDER BY created_at DESC LIMIT 1`,
        userId,
      )
      .toArray();
    return rows[0] ?? null;
  }

  // ------------------------------------------------------------------ the sim

  private anchorPoint(): { lat: number; lng: number } {
    const rows = this.ctx.storage.sql
      .exec<{ lat: number; lng: number }>(
        "SELECT lat, lng FROM users WHERE lat IS NOT NULL ORDER BY last_seen_at DESC LIMIT 1",
      )
      .toArray();
    const row = rows[0];
    return row ? { lat: row.lat, lng: row.lng } : CITY_CENTER;
  }

  private realOnlineDriverCount(): number {
    const rows = this.ctx.storage.sql
      .exec<{ total: number }>(
        "SELECT COUNT(*) AS total FROM users WHERE is_online = 1 AND lat IS NOT NULL AND last_seen_at > ?",
        Date.now() - DRIVER_STALE_MS,
      )
      .toArray();
    return rows[0]?.total ?? 0;
  }

  private simDrivers(): SimRow[] {
    return this.ctx.storage.sql.exec<SimRow>("SELECT * FROM sim_drivers").toArray();
  }

  private ensureSimFleet(): void {
    const anchor = this.anchorPoint();
    const existing = this.simDrivers();

    if (existing.length < SIM_TARGET_FLEET) {
      for (let index = existing.length; index < SIM_TARGET_FLEET; index += 1) {
        const seed = SIM_SEED[index % SIM_SEED.length];
        const spot = this.randomNear(anchor, 0.4, 2.4);
        this.ctx.storage.sql.exec(
          `INSERT INTO sim_drivers (id, name, initials, rating, trip_count, vehicle_type, plate,
             lat, lng, target_lat, target_lng, ride_id)
           VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL)`,
          `sim_${index}`,
          seed.name,
          initialsOf(seed.name),
          seed.rating,
          seed.trips,
          seed.vehicleType,
          seed.plate,
          spot.lat,
          spot.lng,
          spot.lat,
          spot.lng,
        );
      }
      return;
    }

    // Re-anchor the demo fleet when the real users are in another area.
    for (const driver of existing) {
      if (driver.ride_id) continue;
      if (distanceKm({ lat: driver.lat, lng: driver.lng }, anchor) > 12) {
        const spot = this.randomNear(anchor, 0.4, 2.4);
        this.ctx.storage.sql.exec(
          "UPDATE sim_drivers SET lat = ?, lng = ?, target_lat = ?, target_lng = ? WHERE id = ?",
          spot.lat,
          spot.lng,
          spot.lat,
          spot.lng,
          driver.id,
        );
      }
    }
  }

  private randomNear(
    center: { lat: number; lng: number },
    minKm: number,
    maxKm: number,
  ): { lat: number; lng: number } {
    const angle = Math.random() * Math.PI * 2;
    const radiusKm = minKm + Math.random() * (maxKm - minKm);
    const latDelta = (radiusKm / 111) * Math.cos(angle);
    const lngDelta = (radiusKm / (111 * Math.cos(toRadians(center.lat)))) * Math.sin(angle);
    return { lat: center.lat + latDelta, lng: center.lng + lngDelta };
  }

  private releaseSimDriver(rideId: string, countTrip: boolean): void {
    if (countTrip) {
      this.ctx.storage.sql.exec(
        "UPDATE sim_drivers SET trip_count = trip_count + 1 WHERE ride_id = ?",
        rideId,
      );
    }
    this.ctx.storage.sql.exec("UPDATE sim_drivers SET ride_id = NULL WHERE ride_id = ?", rideId);
  }

  /** One simulation step: demo fleet movement, negotiation backup, ride progression. */
  private tick(): void {
    const now = Date.now();

    // Drop devices that stopped reporting.
    this.ctx.storage.sql.exec(
      "UPDATE users SET is_online = 0 WHERE is_online = 1 AND last_seen_at < ?",
      now - DRIVER_STALE_MS,
    );

    // Expire stale searches that nobody picked up.
    this.ctx.storage.sql.exec(
      "DELETE FROM rides WHERE status IN ('COMPLETED','CANCELLED') AND updated_at < ?",
      now - 12 * 60 * 60 * 1000,
    );

    // Offers only make sense on open requests; sweep the rest.
    this.ctx.storage.sql.exec(
      `DELETE FROM offers WHERE ride_id NOT IN
         (SELECT id FROM rides WHERE status = 'SEARCHING')`,
    );

    this.ensureSimFleet();
    this.simNegotiate(now);
    this.simMove(now);
  }

  /**
   * Demo drivers are the backup lane of the negotiation: when no real driver
   * has offered after a quiet period, one sends a counteroffer slightly above
   * the passenger's ask, always labelled "demo". If even that is ignored the
   * ride closes with the demo offer so searches never hang forever.
   */
  private simNegotiate(now: number): void {
    const waiting = this.ctx.storage.sql
      .exec<RideRow>("SELECT * FROM rides WHERE status = 'SEARCHING'")
      .toArray();
    if (waiting.length === 0) return;

    const graceMs =
      this.realOnlineDriverCount() > 0
        ? SIM_OFFER_GRACE_ONLINE_MS
        : SIM_OFFER_GRACE_OFFLINE_MS;

    for (const ride of waiting) {
      const age = now - ride.created_at;
      const offers = this.offersForRide(ride.id);
      if (offers.some((offer) => !offer.driver_id.startsWith("sim_"))) continue;

      if (age >= graceMs + SIM_AUTO_CLOSE_MS) {
        const simOffer =
          offers.find((offer) => offer.driver_id.startsWith("sim_")) ?? this.createSimOffer(ride);
        if (!simOffer) continue;
        const sim = this.simDrivers().find((driver) => driver.id === simOffer.driver_id);
        if (!sim) continue;
        this.ctx.storage.sql.exec(
          "UPDATE sim_drivers SET ride_id = ?, target_lat = ?, target_lng = ? WHERE id = ?",
          ride.id,
          ride.origin_lat,
          ride.origin_lng,
          sim.id,
        );
        this.ctx.storage.sql.exec(
          "UPDATE rides SET driver_id = ?, fare = ?, status = 'ACCEPTED', updated_at = ?, stage_at = ? WHERE id = ?",
          sim.id,
          simOffer.amount,
          now,
          now,
          ride.id,
        );
        this.ctx.storage.sql.exec("DELETE FROM offers WHERE ride_id = ?", ride.id);
        continue;
      }

      if (age >= graceMs && !offers.some((offer) => offer.driver_id.startsWith("sim_"))) {
        this.createSimOffer(ride);
      }
    }
  }

  /** Places a demo counteroffer a little above the passenger's ask. */
  private createSimOffer(ride: RideRow): OfferRow | null {
    const wanted = vehicleFor(ride.service_kind);
    const candidates = this.simDrivers()
      .filter((driver) => driver.ride_id === null && driver.vehicle_type === wanted)
      .sort(
        (a, b) =>
          distanceKm({ lat: a.lat, lng: a.lng }, { lat: ride.origin_lat, lng: ride.origin_lng }) -
          distanceKm({ lat: b.lat, lng: b.lng }, { lat: ride.origin_lat, lng: ride.origin_lng }),
      );
    const chosen = candidates[0];
    if (!chosen) return null;

    const floor = FARE_FLOORS[ride.service_kind as ServiceKind] ?? 2;
    const amount = Math.max(floor, Math.ceil((ride.fare + 0.5 + Math.random()) * 2) / 2);
    const now = Date.now();
    this.ctx.storage.sql.exec(
      `INSERT INTO offers (ride_id, driver_id, amount, created_at, updated_at)
         VALUES (?, ?, ?, ?, ?)
       ON CONFLICT(ride_id, driver_id) DO UPDATE SET amount = excluded.amount,
         updated_at = excluded.updated_at`,
      ride.id,
      chosen.id,
      amount,
      now,
      now,
    );
    return { ride_id: ride.id, driver_id: chosen.id, amount, created_at: now };
  }

  private simMove(now: number): void {
    const anchor = this.anchorPoint();

    for (const driver of this.simDrivers()) {
      const position = { lat: driver.lat, lng: driver.lng };

      if (!driver.ride_id) {
        let target = { lat: driver.target_lat, lng: driver.target_lng };
        if (distanceKm(position, target) < 0.12) target = this.randomNear(anchor, 0.3, 2.6);
        const next = moveToward(position, target, 0.1);
        this.ctx.storage.sql.exec(
          "UPDATE sim_drivers SET lat = ?, lng = ?, target_lat = ?, target_lng = ? WHERE id = ?",
          next.lat,
          next.lng,
          target.lat,
          target.lng,
          driver.id,
        );
        continue;
      }

      const ride = this.ride(driver.ride_id);
      if (!ride || ride.status === "COMPLETED" || ride.status === "CANCELLED") {
        this.ctx.storage.sql.exec(
          "UPDATE sim_drivers SET ride_id = NULL WHERE id = ?",
          driver.id,
        );
        continue;
      }

      if (ride.status === "ARRIVED") {
        // Wait at the pickup point, then start the trip.
        if (now - ride.stage_at > 9_000) {
          this.ctx.storage.sql.exec(
            "UPDATE rides SET status = 'ON_TRIP', updated_at = ?, stage_at = ? WHERE id = ?",
            now,
            now,
            ride.id,
          );
        }
        continue;
      }

      const target =
        ride.status === "ON_TRIP"
          ? { lat: ride.dest_lat, lng: ride.dest_lng }
          : { lat: ride.origin_lat, lng: ride.origin_lng };
      const step = ride.status === "ON_TRIP" ? 0.16 : 0.2;
      const next = moveToward(position, target, step);
      const remainingKm = distanceKm(next, target);

      this.ctx.storage.sql.exec(
        "UPDATE sim_drivers SET lat = ?, lng = ?, target_lat = ?, target_lng = ? WHERE id = ?",
        next.lat,
        next.lng,
        target.lat,
        target.lng,
        driver.id,
      );

      if (ride.status === "ACCEPTED" && remainingKm < 0.07) {
        this.ctx.storage.sql.exec(
          "UPDATE rides SET status = 'ARRIVED', updated_at = ?, stage_at = ? WHERE id = ?",
          now,
          now,
          ride.id,
        );
      } else if (ride.status === "ON_TRIP" && remainingKm < 0.09) {
        this.ctx.storage.sql.exec(
          "UPDATE rides SET status = 'COMPLETED', updated_at = ?, stage_at = ? WHERE id = ?",
          now,
          now,
          ride.id,
        );
        this.releaseSimDriver(ride.id, true);
      }
    }
  }

  // ---------------------------------------------------------------- snapshots

  private driverPayload(
    source:
      | { kind: "real"; row: UserRow }
      | { kind: "sim"; row: SimRow },
    reference: { lat: number; lng: number } | null,
  ): Record<string, unknown> {
    const isSim = source.kind === "sim";
    const row = source.row as UserRow & SimRow;
    const lat = Number(row.lat);
    const lng = Number(row.lng);
    const km = reference ? distanceKm({ lat, lng }, reference) : 0;
    const vehicleType = row.vehicle_type;
    const name = isSim ? row.name : row.name || "Conductor Añane";
    return {
      id: row.id,
      name,
      initials: isSim ? row.initials : initialsOf(name),
      rating: row.rating,
      tripCount: row.trip_count,
      vehicleType,
      plate: row.plate,
      vehicleModel: isSim ? simModel(row.plate) : row.vehicle_model ?? "",
      lat,
      lng,
      distanceKm: km,
      etaMinutes: etaMinutes(km, vehicleType),
      verified: true,
      simulated: isSim,
    };
  }

  private ridePayload(ride: RideRow, viewerId: string): Record<string, unknown> {
    const passenger = this.user(ride.passenger_id);
    const passengerName = passenger.name || "Pasajero Añane";
    let driver: Record<string, unknown> | null = null;

    if (ride.driver_id) {
      driver = this.driverById(ride.driver_id, { lat: ride.origin_lat, lng: ride.origin_lng });
      // Only the passenger of this ride gets the driver's phone, for the contact buttons.
      // Payout details (Yape / Plin number + QR) travel only in this ride payload.
      if (driver && viewerId === ride.passenger_id && !ride.driver_id.startsWith("sim_")) {
        const assigned = this.user(ride.driver_id);
        driver = {
          ...driver,
          phone: assigned.phone,
          payoutPhone: assigned.payout_phone || assigned.phone,
          hasPaymentQr: this.photoKinds(ride.driver_id).includes("PAYMENT_QR"),
        };
      }
    }

    return {
      id: ride.id,
      passengerId: ride.passenger_id,
      passengerName,
      passengerInitials: initialsOf(passengerName),
      passengerRating: passenger.rating,
      passengerVerified: passenger.is_verified === 1,
      serviceKind: ride.service_kind,
      originName: ride.origin_name,
      originLat: ride.origin_lat,
      originLng: ride.origin_lng,
      destName: ride.dest_name,
      destDetail: ride.dest_detail,
      destLat: ride.dest_lat,
      destLng: ride.dest_lng,
      fare: ride.fare,
      distanceKm: ride.distance_km,
      status: ride.status,
      passengerCount: ride.passenger_count,
      preferences: JSON.parse(ride.preferences || "[]") as string[],
      createdAt: ride.created_at,
      updatedAt: ride.updated_at,
      paymentMethod: ride.payment_method || "CASH",
      reference: ride.reference ?? "",
      driver,
      myOffer: this.myOfferAmount(ride.id, viewerId),
      isMine: ride.passenger_id === viewerId || ride.driver_id === viewerId,
    };
  }

  private snapshotFor(userId: string, role: string): Record<string, unknown> {
    const now = Date.now();
    const user = this.user(userId);
    const reference = user.lat !== null && user.lng !== null
      ? { lat: user.lat, lng: user.lng }
      : CITY_CENTER;

    const realDrivers = this.ctx.storage.sql
      .exec<UserRow>(
        `SELECT * FROM users WHERE is_online = 1 AND lat IS NOT NULL AND last_seen_at > ? AND id != ?`,
        now - DRIVER_STALE_MS,
        userId,
      )
      .toArray()
      .map((row) => this.driverPayload({ kind: "real", row }, reference));

    const simDrivers = this.simDrivers()
      .filter((row) => row.ride_id === null)
      .map((row) => this.driverPayload({ kind: "sim", row }, reference))
      .filter((payload) => Number(payload.distanceKm) < REQUEST_RADIUS_KM);

    const drivers = [...realDrivers, ...simDrivers].sort(
      (a, b) => Number(a.distanceKm) - Number(b.distanceKm),
    );

    const openRides =
      role === "DRIVER"
        ? this.ctx.storage.sql
            .exec<RideRow>(
              `SELECT r.* FROM rides r
                 WHERE r.status = 'SEARCHING'
                   AND r.passenger_id != ?
                   AND r.service_kind = ?
                   AND NOT EXISTS (SELECT 1 FROM declines d WHERE d.ride_id = r.id AND d.driver_id = ?)
                 ORDER BY r.created_at DESC LIMIT 20`,
              userId,
              serviceFor(user.vehicle_type),
              userId,
            )
            .toArray()
            .map((ride) => this.ridePayload(ride, userId))
            .filter(
              (payload) =>
                distanceKm(
                  { lat: Number(payload.originLat), lng: Number(payload.originLng) },
                  reference,
                ) < REQUEST_RADIUS_KM,
            )
        : [];

    const activeRide = this.activeRideFor(userId, role) ?? this.activeRideFor(userId, "PASSENGER");

    // Incoming offers on the passenger's open request, cheapest first.
    const offers =
      activeRide && activeRide.status === "SEARCHING" && activeRide.passenger_id === userId
        ? this.offersForRide(activeRide.id)
            .map((offer) => {
              const driver = this.driverById(offer.driver_id, {
                lat: activeRide.origin_lat,
                lng: activeRide.origin_lng,
              });
              return driver
                ? { rideId: offer.ride_id, driverId: offer.driver_id, amount: offer.amount, driver }
                : null;
            })
            .filter((offer) => offer !== null)
            .sort((a, b) => a.amount - b.amount)
        : [];

    const history = this.ctx.storage.sql
      .exec<RideRow>(
        `SELECT * FROM rides WHERE (passenger_id = ? OR driver_id = ?)
           AND status IN ('COMPLETED','CANCELLED')
         ORDER BY updated_at DESC LIMIT 20`,
        userId,
        userId,
      )
      .toArray()
      .map((ride) => this.ridePayload(ride, userId));

    const todayStart = new Date().setHours(0, 0, 0, 0);
    const earned = this.ctx.storage.sql
      .exec<{ trips: number; total: number }>(
        `SELECT COUNT(*) AS trips, COALESCE(SUM(fare), 0) AS total FROM rides
           WHERE driver_id = ? AND status = 'COMPLETED' AND updated_at > ?`,
        userId,
        todayStart,
      )
      .toArray()[0];

    const subscriptionMsLeft = user.subscription_expires_at - now;

    return {
      type: "snapshot",
      serverTime: now,
      profile: {
        id: user.id,
        name: user.name,
        phone: user.phone,
        dni: user.dni,
        rating: user.rating,
        role: user.role,
        verified: user.is_verified === 1,
        defaultPassengerCount: user.default_passenger_count,
        defaultPreferences: JSON.parse(user.default_preferences || "[]") as string[],
        vehicleType: user.vehicle_type,
        plate: user.plate,
        vehicleModel: user.vehicle_model ?? "",
        payoutPhone: user.payout_phone ?? "",
        driverStatus: user.driver_status as VerificationStatus,
        rejectionReason: user.driver_rejection_reason ?? "",
        isOnline: user.is_online === 1,
        subscriptionActive: subscriptionMsLeft > 0,
        subscriptionDaysLeft: Math.max(0, Math.ceil(subscriptionMsLeft / (24 * 60 * 60 * 1000))),
        subscriptionExpiresAt: user.subscription_expires_at,
        paidThisWeek: subscriptionMsLeft > 0 ? user.paid_this_week : 0,
        uploadedPhotos: this.photoKinds(userId),
      },
      drivers,
      openRides,
      offers,
      activeRide: activeRide ? this.ridePayload(activeRide, userId) : null,
      history,
      todayTrips: earned?.trips ?? 0,
      todayEarnings: earned?.total ?? 0,
      onlineDrivers: drivers.length,
    };
  }
}
