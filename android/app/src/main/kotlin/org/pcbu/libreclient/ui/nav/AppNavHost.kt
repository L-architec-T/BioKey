package org.pcbu.libreclient.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.first
import org.pcbu.libreclient.pairing.PairingRepository
import org.pcbu.libreclient.storage.PreferencesStore
import org.pcbu.libreclient.ui.devices.DeviceDetailScreen
import org.pcbu.libreclient.ui.devices.MacroEditorScreen
import org.pcbu.libreclient.ui.onboarding.OnboardingScreen
import org.pcbu.libreclient.ui.pairing.PairingScanScreen
import org.pcbu.libreclient.ui.home.HomeShell

private const val ROUTE_GATE = "gate"
private const val ROUTE_ONBOARDING = "onboarding"
private const val ROUTE_DEVICES = "devices"
private const val ROUTE_DEVICE_DETAIL = "devices/{deviceId}"
private const val ROUTE_DEVICE_MACRO = "devices/{deviceId}/macro"
private const val ROUTE_PAIRING = "pairing"

@Composable
fun AppNavHost(pairingRepository: PairingRepository, preferencesStore: PreferencesStore) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = ROUTE_GATE) {
        composable(ROUTE_GATE) {
            OnboardingGate(navController, preferencesStore)
        }
        composable(ROUTE_ONBOARDING) {
            OnboardingScreen(preferencesStore) {
                navController.navigate(ROUTE_DEVICES) { popUpTo(ROUTE_ONBOARDING) { inclusive = true } }
            }
        }
        composable(ROUTE_DEVICES) {
            // Home shell: "Appareils" + "Réglages" tabs, top-bar menu (foundation components).
            HomeShell(
                pairingRepository = pairingRepository,
                preferencesStore = preferencesStore,
                onDeviceClick = { deviceId -> navController.navigate("devices/$deviceId") },
                onAddDeviceClick = { navController.navigate(ROUTE_PAIRING) },
            )
        }
        composable(ROUTE_DEVICE_DETAIL) { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString("deviceId") ?: return@composable
            DeviceDetailScreen(
                pairingRepository = pairingRepository,
                deviceId = deviceId,
                onBack = { navController.popBackStack() },
                onDeleted = { navController.popBackStack() },
                onEditMacro = { navController.navigate("devices/$deviceId/macro") },
            )
        }
        composable(ROUTE_DEVICE_MACRO) { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString("deviceId") ?: return@composable
            MacroEditorScreen(
                pairingRepository = pairingRepository,
                deviceId = deviceId,
                onBack = { navController.popBackStack() },
            )
        }
        composable(ROUTE_PAIRING) {
            PairingScanScreen(
                pairingRepository = pairingRepository,
                onPaired = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }
    }
}

@Composable
private fun OnboardingGate(navController: NavHostController, preferencesStore: PreferencesStore) {
    LaunchedEffect(Unit) {
        val complete = preferencesStore.onboardingComplete.first()
        val destination = if (complete) ROUTE_DEVICES else ROUTE_ONBOARDING
        navController.navigate(destination) { popUpTo(ROUTE_GATE) { inclusive = true } }
    }
    Box(modifier = Modifier.fillMaxSize())
}
