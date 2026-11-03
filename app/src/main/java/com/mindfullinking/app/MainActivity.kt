package com.mindfullinking.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
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
        containerColor = FluxTheme.colors.background
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

    val selectedIndex = when (currentRoute) {
        AppDestination.ARCHIVE.route -> 1
        AppDestination.SETTINGS.route -> 2
        else -> 0
    }

    val animatedIndex by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
        label = "navPillSlide"
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .background(FluxTheme.colors.background)
            .padding(horizontal = FluxTheme.spacing.md, vertical = FluxTheme.spacing.xs)
            .height(56.dp)
    ) {
        val totalWidth = maxWidth
        val tabCount = AppDestination.entries.size
        val tabWidth = totalWidth / tabCount

        // Slim background rail / track behind buttons (~34dp height)
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .height(34.dp)
                .clip(FluxTheme.shapes.button)
                .background(FluxTheme.colors.surfaceInverted)
        )

        // Animated overlapping sliding Action Lime pill (~50dp height)
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = tabWidth * animatedIndex)
                .width(tabWidth)
                .height(50.dp)
                .padding(horizontal = 4.dp)
                .clip(FluxTheme.shapes.button)
                .background(FluxTheme.colors.accentAction)
        )

        // Interactive tab items row maintaining >=48dp touch target
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppDestination.entries.forEach { destination ->
                val isSelected = currentRoute == destination.route

                val textColor by animateColorAsState(
                    targetValue = if (isSelected) FluxTheme.colors.contentOnAction else FluxTheme.colors.contentOnInverted,
                    animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                    label = "navTextColor_${destination.name}"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(FluxTheme.shapes.button)
                        .clickable {
                            if (currentRoute != destination.route) {
                                navController.navigate(destination.route) {
                                    popUpTo(AppDestination.LEDGER.route) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                        .semantics {
                            role = Role.Tab
                            contentDescription = "Navigate to ${destination.label} tab"
                            stateDescription = if (isSelected) "Selected" else "Not selected"
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = destination.label,
                        style = FluxTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = textColor
                    )
                }
            }
        }
    }
}

/** Visual baseline for white labels on the structural-charcoal navigation surface in dark mode. */
@Preview(name = "Bottom navigation — dark", showBackground = true, backgroundColor = 0xFF222124)
@Composable
private fun DarkBottomNavigationPreview() {
    FluxTheme(darkTheme = true) {
        BauhausBottomNavigationBar(navController = rememberNavController())
    }
}
