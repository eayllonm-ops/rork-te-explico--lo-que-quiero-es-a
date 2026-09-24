package com.rork.ananego.ui.components

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.RoundCap
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.GoogleMapComposable
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import com.rork.ananego.data.installedSigningSha1
import com.rork.ananego.data.model.Coordinate
import com.rork.ananego.ui.theme.GoldAccent
import com.rork.ananego.ui.theme.JungleCanvas
import com.rork.ananego.ui.theme.JungleDeep
import com.rork.ananego.ui.theme.TextPrimary
import com.rork.ananego.ui.theme.TextSecondary
import kotlinx.coroutines.delay

private const val MAP_TAG = "AnaneMap"

/** How long the tiles may take before we tell the user the map failed. */
private const val MAP_LOAD_TIMEOUT_MS = 12_000L

/** Default zoom used for the city-level passenger view. */
const val CITY_ZOOM = 14.5f

/**
 * Remembers a camera state anchored on [center]. Google Maps is the single
 * source of truth for the viewport, so screens share this helper.
 */
@Composable
fun rememberSatipoCamera(center: Coordinate, zoom: Float = CITY_ZOOM): CameraPositionState =
    rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(center.toLatLng(), zoom)
    }

/**
 * Google Maps view styled with the Añane Go jungle palette. Markers and routes
 * are supplied by callers as map composables using real coordinates.
 */
@Composable
fun SatipoMap(
    modifier: Modifier = Modifier,
    cameraPositionState: CameraPositionState = rememberSatipoCamera(Coordinate(-11.25283, -74.63757)),
    routeFrom: Coordinate? = null,
    routeTo: Coordinate? = null,
    showMyLocation: Boolean = false,
    onMapLoaded: () -> Unit = {},
    content: @Composable @GoogleMapComposable () -> Unit = {}
) {
    val mapStyle = remember { MapStyleOptions(JUNGLE_MAP_STYLE_JSON) }
    val properties = remember(showMyLocation) {
        MapProperties(
            mapType = MapType.NORMAL,
            mapStyleOptions = mapStyle,
            isMyLocationEnabled = showMyLocation,
            minZoomPreference = 5f
        )
    }
    val uiSettings = remember {
        MapUiSettings(
            compassEnabled = false,
            mapToolbarEnabled = false,
            myLocationButtonEnabled = false,
            zoomControlsEnabled = false,
            tiltGesturesEnabled = false,
            rotationGesturesEnabled = false
        )
    }

    val context = LocalContext.current
    val playServicesStatus = remember {
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
    }
    val signingSha1 = remember { installedSigningSha1(context) }
    var attempt by remember { mutableIntStateOf(0) }
    var isLoaded by remember { mutableStateOf(false) }
    var hasTimedOut by remember { mutableStateOf(false) }

    LaunchedEffect(attempt) {
        if (playServicesStatus != ConnectionResult.SUCCESS) {
            Log.e(MAP_TAG, "Google Play Services no disponible: código $playServicesStatus")
            return@LaunchedEffect
        }
        delay(MAP_LOAD_TIMEOUT_MS)
        if (!isLoaded) {
            hasTimedOut = true
            Log.e(
                MAP_TAG,
                "El mapa no cargó en ${MAP_LOAD_TIMEOUT_MS / 1000}s. Revisa que 'Maps SDK for Android' " +
                    "esté habilitado y permitido en la clave, y el SHA-1 del certificado instalado: $signingSha1"
            )
        }
    }

    Box(modifier = modifier.background(JungleCanvas)) {
        if (playServicesStatus == ConnectionResult.SUCCESS) {
            key(attempt) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    properties = properties,
                    uiSettings = uiSettings,
                    onMapLoaded = {
                        Log.i(MAP_TAG, "Mapa cargado correctamente")
                        isLoaded = true
                        hasTimedOut = false
                        onMapLoaded()
                    }
                ) {
                    if (routeFrom != null && routeTo != null) {
                        RouteLine(from = routeFrom, to = routeTo)
                    }
                    content()
                }
            }
        }

        when {
            playServicesStatus != ConnectionResult.SUCCESS -> MapProblemOverlay(
                title = "Falta Google Play Services",
                detail = "Este dispositivo necesita Google Play Services actualizado para mostrar el mapa.",
                actionLabel = "Actualizar",
                onAction = {
                    val intent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("market://details?id=com.google.android.gms")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(intent) }
                }
            )
            hasTimedOut && !isLoaded -> MapProblemOverlay(
                title = "El mapa no pudo cargar",
                detail = "Revisa tu conexión a internet e inténtalo otra vez.",
                footnote = signingSha1?.let { "Huella SHA-1 de esta instalación:\n$it" },
                actionLabel = "Reintentar",
                onAction = {
                    hasTimedOut = false
                    attempt += 1
                }
            )
        }
    }
}

/** Replaces a blank map with a clear explanation and a recovery action. */
@Composable
private fun MapProblemOverlay(
    title: String,
    detail: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    footnote: String? = null
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(JungleCanvas.copy(alpha = 0.94f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Map,
                contentDescription = null,
                tint = GoldAccent,
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(
                    containerColor = GoldAccent,
                    contentColor = JungleDeep
                )
            ) {
                Text(actionLabel)
            }
            if (footnote != null) {
                SelectionContainer {
                    Text(
                        text = footnote,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

/** Gold dashed route drawn between two real positions. */
@Composable
@GoogleMapComposable
private fun RouteLine(from: Coordinate, to: Coordinate) {
    val points = remember(from, to) { listOf(from.toLatLng(), to.toLatLng()) }
    Polyline(
        points = points,
        color = JungleDeep.copy(alpha = 0.75f),
        width = 18f,
        startCap = RoundCap(),
        endCap = RoundCap(),
        geodesic = true,
        zIndex = 1f
    )
    Polyline(
        points = points,
        color = GoldAccent,
        width = 9f,
        pattern = listOf(Dash(26f), Gap(14f)),
        startCap = RoundCap(),
        endCap = RoundCap(),
        geodesic = true,
        zIndex = 2f
    )
}

/**
 * Places arbitrary composable content as a marker at a real coordinate.
 * [keys] must include everything that changes the rendered content.
 */
@Composable
@GoogleMapComposable
fun MapMarker(
    position: Coordinate,
    vararg keys: Any,
    title: String? = null,
    zIndex: Float = 3f,
    onClick: () -> Unit = {},
    content: @Composable () -> Unit
) {
    val markerState = rememberUpdatedMarkerState(position = position.toLatLng())
    MarkerComposable(
        keys = keys,
        state = markerState,
        title = title,
        anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f),
        zIndex = zIndex,
        onClick = {
            onClick()
            true
        },
        content = content
    )
}

/** Smoothly frames both ends of a route once the map has finished loading. */
@Composable
fun FitRouteEffect(
    cameraPositionState: CameraPositionState,
    isMapLoaded: Boolean,
    from: Coordinate?,
    to: Coordinate?,
    paddingPx: Int = 140
) {
    LaunchedEffect(isMapLoaded, from, to) {
        if (!isMapLoaded || from == null || to == null) return@LaunchedEffect
        val bounds = LatLngBounds.builder()
            .include(from.toLatLng())
            .include(to.toLatLng())
            .build()
        runCatching {
            cameraPositionState.animate(
                update = CameraUpdateFactory.newLatLngBounds(bounds, paddingPx),
                durationMs = 900
            )
        }
    }
}

/** Keeps the camera centered on the live user position. */
@Composable
fun FollowUserEffect(
    cameraPositionState: CameraPositionState,
    isMapLoaded: Boolean,
    location: Coordinate?,
    zoom: Float = CITY_ZOOM
) {
    var hasCentered by remember { mutableStateOf(false) }
    LaunchedEffect(isMapLoaded, location) {
        val target = location ?: return@LaunchedEffect
        if (!isMapLoaded || hasCentered) return@LaunchedEffect
        hasCentered = true
        runCatching {
            cameraPositionState.animate(
                update = CameraUpdateFactory.newLatLngZoom(target.toLatLng(), zoom),
                durationMs = 800
            )
        }
    }
}

/** Animates the camera to a coordinate on demand (recenter button). */
suspend fun CameraPositionState.recenterOn(target: LatLng, zoom: Float = CITY_ZOOM) {
    runCatching {
        animate(
            update = CameraUpdateFactory.newLatLngZoom(target, zoom),
            durationMs = 700
        )
    }
}

/** Pulsing blue dot showing the user's own position. */
@Composable
fun UserLocationDot(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Restart),
        label = "scale"
    )
    val fade by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Restart),
        label = "fade"
    )
    Box(modifier = modifier.size(52.dp), contentAlignment = Alignment.Center) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier
                .size(44.dp)
                .scale(scale)
                .alpha(fade)
        ) {}
        Surface(
            shape = CircleShape,
            color = Color(0xFF2E6FA8),
            border = androidx.compose.foundation.BorderStroke(3.dp, Color.White),
            modifier = Modifier.size(20.dp)
        ) {}
    }
}

/** Static dot used inside markers, where infinite animations are not rendered. */
@Composable
fun StaticLocationDot(modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(26.dp), contentAlignment = Alignment.Center) {
        Surface(
            shape = CircleShape,
            color = Color(0xFF2E6FA8).copy(alpha = 0.28f),
            modifier = Modifier.size(26.dp)
        ) {}
        Surface(
            shape = CircleShape,
            color = Color(0xFF2E6FA8),
            border = androidx.compose.foundation.BorderStroke(3.dp, Color.White),
            modifier = Modifier.size(16.dp)
        ) {}
    }
}
