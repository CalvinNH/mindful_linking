package com.mindfullinking.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.mindfullinking.core.ui.theme.FluxPalette
import com.mindfullinking.core.ui.theme.FluxTheme
import com.mindfullinking.feature.archive.ArchiveScreen
import com.mindfullinking.feature.now.MainLedgerScreen
import com.mindfullinking.feature.settings.SettingsScreen

enum class AppDestination(val route: String, val label: String) {
    LEDGER("ledger", "Ledger"),
    ARCHIVE("archive", "Archive"),
    SETTINGS("settings", "Settings")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SanitizingLogTree.i("MainActivity", "MindfulLinking MainActivity started")

        setContent {
            FluxTheme {
                MindfulLinkingMainApp()
            }
        }
    }
}

@Composable
fun MindfulLinkingMainApp() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            BauhausBottomNavigationBar(navController = navController)
        },
        containerColor = FluxPalette.Surface
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = AppDestination.LEDGER.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            composable(AppDestination.LEDGER.route) {
                MainLedgerScreen()
            }
            composable(AppDestination.ARCHIVE.route) {
                ArchiveScreen()
            }
            composable(AppDestination.SETTINGS.route) {
                SettingsScreen()
            }
        }
    }
}

@Composable
fun BauhausBottomNavigationBar(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: AppDestination.LEDGER.route

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(FluxPalette.Void)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppDestination.entries.forEach { destination ->
                val isSelected = currentRoute == destination.route

                val backgroundColor = if (isSelected) FluxPalette.Signal else FluxPalette.Void
                val textColor = if (isSelected) FluxPalette.Void else FluxPalette.Paper

                Box(
                    modifier = Modifier
                        .defaultMinSize(minWidth = 56.dp, minHeight = 48.dp)
                        .clip(FluxTheme.shapes.button)
                        .background(backgroundColor)
                        .clickable {
                            if (currentRoute != destination.route) {
                                navController.navigate(destination.route) {
                                    popUpTo(AppDestination.LEDGER.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .semantics {
                            role = Role.Tab
                            contentDescription = "Navigate to ${destination.label} tab"
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = destination.label,
                        style = FluxTheme.typography.labelMedium,
                        color = textColor
                    )
                }
            }
        }
    }
}
