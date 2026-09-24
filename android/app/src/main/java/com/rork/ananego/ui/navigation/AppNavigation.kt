package com.rork.ananego.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.DisposableEffect
import com.rork.ananego.data.model.AppRole
import com.rork.ananego.data.model.LocationStatus
import com.rork.ananego.ui.components.BrandLockup
import com.rork.ananego.ui.screens.AccountScreen
import com.rork.ananego.ui.screens.AdminReviewScreen
import com.rork.ananego.ui.screens.DriverAvailabilityBar
import com.rork.ananego.ui.screens.DriverDashboardScreen
import com.rork.ananego.ui.screens.DriverRegistrationScreen
import com.rork.ananego.ui.screens.PassengerHomeScreen
import com.rork.ananego.ui.screens.PublishingRideScreen
import com.rork.ananego.ui.screens.RequestDetailScreen
import com.rork.ananego.ui.screens.RideTrackingScreen
import com.rork.ananego.ui.screens.SubscriptionScreen
import com.rork.ananego.ui.screens.TripsScreen
import com.rork.ananego.ui.state.AppViewModel
import com.rork.ananego.ui.theme.GoldAccent
import com.rork.ananego.ui.theme.JungleCanvas
import com.rork.ananego.ui.theme.JungleDeep
import com.rork.ananego.ui.theme.JungleSurfaceHigh
import com.rork.ananego.ui.theme.TextSecondary

private object Routes {
    const val HOME = "home"
    const val TRIPS = "trips"
    const val ACCOUNT = "account"
    const val RIDE = "ride"
    const val REGISTRATION = "registration"
    const val SUBSCRIPTION = "subscription"
    const val ADMIN_REVIEW = "admin_review"
    const val REQUEST = "request/{requestId}"
    fun request(id: String): String = "request/$id"
}

private data class TabDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val tabs = listOf(
    TabDestination(Routes.HOME, "Inicio", Icons.Filled.Home),
    TabDestination(Routes.TRIPS, "Viajes", Icons.Filled.DirectionsCar),
    TabDestination(Routes.ACCOUNT, "Cuenta", Icons.Filled.Person)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val viewModel: AppViewModel = viewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isTabRoute = currentRoute in tabs.map { it.route }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { viewModel.refreshLocationPermission() }

    val requestLocationPermission: () -> Unit = {
        locationPermissionLauncher.launch(
            arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    // Ask once on first launch so the map opens on the real position.
    LaunchedEffect(Unit) {
        if (state.locationStatus == LocationStatus.PERMISSION_REQUIRED) {
            requestLocationPermission()
        }
    }

    // Re-check when the user returns from the system settings screen.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshLocationPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.lastMessage) {
        state.lastMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        containerColor = JungleCanvas,
        topBar = {
            if (isTabRoute) {
                TopAppBar(
                    title = { BrandLockup() },
                    actions = {
                        CircleAction(icon = Icons.Filled.Notifications, description = "Notificaciones")
                        Spacer(Modifier.width(8.dp))
                        CircleAction(
                            icon = Icons.Filled.Person,
                            description = "Cuenta",
                            onClick = { navController.navigateToTab(Routes.ACCOUNT) }
                        )
                        Spacer(Modifier.width(12.dp))
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = JungleCanvas,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = isTabRoute,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                Column {
                    if (currentRoute == Routes.TRIPS && state.role == AppRole.DRIVER) {
                        DriverAvailabilityBar(
                            isOnline = state.isDriverOnline,
                            enabled = state.isLive,
                            onToggle = viewModel::toggleDriverOnline
                        )
                    }
                    BottomTabBar(navController = navController, currentRoute = currentRoute)
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(Routes.HOME) {
                PassengerHomeScreen(
                    state = state,
                    contentPadding = innerPadding,
                    onSelectService = viewModel::selectService,
                    onSearchChange = viewModel::updateSearchQuery,
                    onRequestRide = { place, proposedFare ->
                        viewModel.requestRide(place, proposedFare)
                        navController.navigate(Routes.RIDE)
                    },
                    onOpenActiveRide = { navController.navigate(Routes.RIDE) },
                    onOpenDriver = { navController.navigateToTab(Routes.TRIPS) },
                    onRequestLocationPermission = requestLocationPermission
                )
            }

            composable(Routes.TRIPS) {
                if (state.role == AppRole.DRIVER) {
                    DriverDashboardScreen(
                        state = state,
                        contentPadding = innerPadding,
                        onToggleOnline = viewModel::toggleDriverOnline,
                        onOpenRequest = { request -> navController.navigate(Routes.request(request.id)) },
                        onOpenSubscription = { navController.navigate(Routes.SUBSCRIPTION) },
                        onOpenRegistration = { navController.navigate(Routes.REGISTRATION) }
                    )
                } else {
                    TripsScreen(
                        state = state,
                        contentPadding = innerPadding,
                        onOpenActiveRide = { navController.navigate(Routes.RIDE) }
                    )
                }
            }

            composable(Routes.ACCOUNT) {
                AccountScreen(
                    state = state,
                    contentPadding = innerPadding,
                    onRoleChange = viewModel::setRole,
                    onOpenRegistration = { navController.navigate(Routes.REGISTRATION) },
                    onOpenSubscription = { navController.navigate(Routes.SUBSCRIPTION) },
                    onSaveProfile = viewModel::savePassengerProfile,
                    onOpenAdminReview = { navController.navigate(Routes.ADMIN_REVIEW) }
                )
            }

            composable(Routes.RIDE) {
                val ride = state.activeRide
                if (ride == null) {
                    if (state.isRequestingRide) {
                        PublishingRideScreen(onBack = { navController.popBackStack() })
                    } else {
                        LaunchedEffect(Unit) { navController.popBackStack() }
                    }
                } else {
                    RideTrackingScreen(
                        ride = ride,
                        offers = state.offers,
                        onBack = { navController.popBackStack() },
                        onCancel = {
                            viewModel.cancelRide()
                            navController.popBackStack()
                        },
                        onAdvance = viewModel::advanceRide,
                        onAcceptOffer = viewModel::acceptOffer,
                        onRejectOffer = viewModel::rejectOffer,
                        onPassengerCountChange = viewModel::setPassengerCount,
                        onTogglePreference = viewModel::togglePreference
                    )
                }
            }

            composable(Routes.REGISTRATION) {
                DriverRegistrationScreen(
                    viewModel = viewModel,
                    onClose = { navController.popBackStack() }
                )
            }

            composable(Routes.SUBSCRIPTION) {
                SubscriptionScreen(
                    subscription = state.subscription,
                    onBack = { navController.popBackStack() },
                    onRenew = {
                        viewModel.renewSubscription()
                        navController.popBackStack()
                    }
                )
            }

            composable(Routes.ADMIN_REVIEW) {
                AdminReviewScreen(
                    viewModel = viewModel,
                    onClose = { navController.popBackStack() }
                )
            }

            composable(Routes.REQUEST) { entry ->
                val requestId = entry.arguments?.getString("requestId")
                val request = state.liveRequests.firstOrNull { it.id == requestId }
                if (request == null) {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                } else {
                    RequestDetailScreen(
                        request = request,
                        onBack = { navController.popBackStack() },
                        onAccept = {
                            viewModel.acceptRequest(request.id)
                            navController.popBackStack()
                        },
                        onDecline = {
                            viewModel.declineRequest(request.id)
                            navController.popBackStack()
                        },
                        onCounterOffer = { amount -> viewModel.offerRide(request.id, amount) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomTabBar(navController: NavHostController, currentRoute: String?) {
    NavigationBar(
        containerColor = JungleDeep,
        tonalElevation = 0.dp
    ) {
        tabs.forEach { tab ->
            val selected = currentRoute == tab.route
            NavigationBarItem(
                selected = selected,
                onClick = { navController.navigateToTab(tab.route) },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = GoldAccent,
                    selectedTextColor = GoldAccent,
                    unselectedIconColor = TextSecondary,
                    unselectedTextColor = TextSecondary,
                    indicatorColor = JungleSurfaceHigh
                )
            )
        }
    }
}

@Composable
private fun CircleAction(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit = {}
) {
    Surface(
        shape = CircleShape,
        color = JungleSurfaceHigh,
        modifier = Modifier.size(42.dp)
    ) {
        androidx.compose.material3.IconButton(onClick = onClick) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(21.dp)
            )
        }
    }
}

private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
