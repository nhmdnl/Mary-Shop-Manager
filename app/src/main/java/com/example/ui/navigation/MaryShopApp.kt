package com.example.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.screens.auth.PinLockScreen
import com.example.ui.screens.dashboard.DashboardScreen
import com.example.ui.screens.inventory.InventoryScreen
import com.example.ui.screens.inventory.ProductDetailScreen
import com.example.ui.screens.parties.PartiesScreen
import com.example.ui.screens.parties.PartyDetailScreen
import com.example.ui.screens.reports.ReportsScreen
import com.example.ui.screens.sales.SalesScreen
import com.example.ui.screens.settings.MoreScreen
import com.example.ui.viewmodel.AppViewModelProvider
import com.example.ui.viewmodel.DashboardViewModel
import com.example.ui.viewmodel.InventoryViewModel
import com.example.ui.viewmodel.PartiesViewModel
import com.example.ui.viewmodel.PartyDetailViewModel
import com.example.ui.viewmodel.ProductDetailViewModel
import com.example.ui.viewmodel.ReportsViewModel
import com.example.ui.viewmodel.SalesViewModel
import com.example.ui.viewmodel.SettingsViewModel

@Composable
fun MaryShopApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val dashboardViewModel: DashboardViewModel = viewModel(factory = AppViewModelProvider.Factory)
    val partiesViewModel: PartiesViewModel = viewModel(factory = AppViewModelProvider.Factory)
    val inventoryViewModel: InventoryViewModel = viewModel(factory = AppViewModelProvider.Factory)
    val salesViewModel: SalesViewModel = viewModel(factory = AppViewModelProvider.Factory)
    val settingsViewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory)
    val reportsViewModel: ReportsViewModel = viewModel(factory = AppViewModelProvider.Factory)

    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val isAppLocked by settingsViewModel.isAppLocked.collectAsStateWithLifecycle()
    val lowStockCount by inventoryViewModel.lowStockCount.collectAsStateWithLifecycle()

    if (isAppLocked) {
        PinLockScreen(
            shopName = settings.shopName,
            onPinEntered = { pin ->
                settingsViewModel.unlockApp(pin)
            }
        )
    } else {
        val isTopLevelDestination = Screen.bottomNavItems.any { it.route == currentRoute }

        Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (isTopLevelDestination) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ) {
                    Screen.bottomNavItems.forEach { screen ->
                        val selected = currentRoute == screen.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (currentRoute != screen.route) {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                if (screen == Screen.Inventory && lowStockCount > 0) {
                                    BadgedBox(
                                        badge = {
                                            Badge(
                                                containerColor = MaterialTheme.colorScheme.error,
                                                contentColor = MaterialTheme.colorScheme.onError
                                            ) {
                                                Text(
                                                    text = "$lowStockCount",
                                                    modifier = Modifier.testTag("inventory_low_stock_badge")
                                                )
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                                            contentDescription = screen.title
                                        )
                                    }
                                } else {
                                    Icon(
                                        imageVector = if (selected) screen.selectedIcon else screen.unselectedIcon,
                                        contentDescription = screen.title
                                    )
                                }
                            },
                            label = {
                                Text(
                                    text = screen.title,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            modifier = Modifier.testTag("nav_item_${screen.route}"),
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    viewModel = dashboardViewModel,
                    partiesViewModel = partiesViewModel,
                    onNavigateToParty = { partyId ->
                        navController.navigate("party_detail/$partyId")
                    },
                    onNavigateToPartiesList = {
                        navController.navigate(Screen.Parties.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                        }
                    },
                    onNavigateToReports = {
                        navController.navigate(Screen.Reports.route)
                    },
                    onNavigateToSale = {
                        navController.navigate(Screen.Sales.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                        }
                    },
                    onNavigateToInventory = {
                        navController.navigate(Screen.Inventory.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(Screen.Parties.route) {
                PartiesScreen(
                    viewModel = partiesViewModel,
                    onNavigateToDetail = { partyId ->
                        navController.navigate("party_detail/$partyId")
                    }
                )
            }

            composable(Screen.Sales.route) {
                SalesScreen(
                    viewModel = salesViewModel,
                    onNavigateToParties = {
                        navController.navigate(Screen.Parties.route)
                    }
                )
            }

            composable(Screen.Inventory.route) {
                InventoryScreen(
                    viewModel = inventoryViewModel,
                    onProductClick = { productId ->
                        navController.navigate("product_detail/$productId")
                    }
                )
            }

            composable(Screen.More.route) {
                MoreScreen(
                    viewModel = settingsViewModel,
                    onNavigateToReports = {
                        navController.navigate(Screen.Reports.route)
                    }
                )
            }

            composable(Screen.Reports.route) {
                ReportsScreen(
                    viewModel = reportsViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToParty = { partyId ->
                        navController.navigate("party_detail/$partyId")
                    }
                )
            }

            composable(
                route = "party_detail/{partyId}",
                arguments = listOf(
                    navArgument("partyId") { type = NavType.LongType }
                )
            ) { backStackEntry ->
                val partyId = backStackEntry.arguments?.getLong("partyId") ?: 0L
                val detailViewModel: PartyDetailViewModel = viewModel(
                    factory = AppViewModelProvider.partyDetailFactory(partyId)
                )

                PartyDetailScreen(
                    viewModel = detailViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(
                route = "product_detail/{productId}",
                arguments = listOf(
                    navArgument("productId") { type = NavType.LongType }
                )
            ) { backStackEntry ->
                val productId = backStackEntry.arguments?.getLong("productId") ?: 0L
                val detailViewModel: ProductDetailViewModel = viewModel(
                    factory = AppViewModelProvider.productDetailFactory(productId)
                )

                ProductDetailScreen(
                    viewModel = detailViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
}

