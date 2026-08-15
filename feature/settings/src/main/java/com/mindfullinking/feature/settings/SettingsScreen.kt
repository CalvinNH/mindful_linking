package com.mindfullinking.feature.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mindfullinking.core.ui.component.FluxBadge
import com.mindfullinking.core.ui.component.FluxBadgeVariant
import com.mindfullinking.core.ui.component.FluxButton
import com.mindfullinking.core.ui.component.FluxButtonVariant
import com.mindfullinking.core.ui.component.FluxCard
import com.mindfullinking.core.ui.component.FluxCardVariant
import com.mindfullinking.core.ui.component.FluxDialog
import com.mindfullinking.core.ui.component.FluxDialogStyle
import com.mindfullinking.core.ui.component.FluxStat
import com.mindfullinking.core.ui.component.FluxSwitch
import com.mindfullinking.core.ui.theme.FluxPalette
import com.mindfullinking.core.ui.theme.FluxTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var showDataWipeDialog by remember { mutableStateOf(false) }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportEncryptedBackup(uri, context) { success, message ->
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    if (showDataWipeDialog) {
        FluxDialog(
            onDismissRequest = { showDataWipeDialog = false },
            title = "PERMANENT DATA WIPE",
            body = "Are you sure you want to permanently erase all active/archived links, attention budget state, event history, and settings? This action CANNOT be undone.",
            primaryButtonText = "Wipe Everything",
            onPrimaryClick = {
                viewModel.performPermanentDataWipe {
                    showDataWipeDialog = false
                    Toast.makeText(context, "All local data wiped successfully.", Toast.LENGTH_SHORT).show()
                }
            },
            secondaryButtonText = "Cancel",
            onSecondaryClick = { showDataWipeDialog = false },
            style = FluxDialogStyle.Charcoal
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FluxPalette.Surface)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "APP SETTINGS & PRIVACY",
            style = FluxTheme.typography.titleLarge,
            color = FluxPalette.Void
        )
        Text(
            text = "Local-first privacy, metadata controls, local export & data wipe",
            style = FluxTheme.typography.bodySmall,
            color = FluxPalette.CharcoalMuted
        )

        Spacer(modifier = Modifier.height(20.dp))



        // Card 2: Privacy Analytics & Event Summary
        FluxCard(
            variant = FluxCardVariant.Paper,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Local-First Privacy Analytics",
                            style = FluxTheme.typography.titleMedium,
                            color = FluxPalette.Void
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Track link management metrics locally on device. No telemetry or analytics are ever sent to remote servers.",
                            style = FluxTheme.typography.bodySmall,
                            color = FluxPalette.CharcoalMuted
                        )
                    }
                    Spacer(modifier = Modifier.padding(start = 12.dp))
                    FluxSwitch(
                        checked = uiState.analyticsOptIn,
                        onCheckedChange = { viewModel.setAnalyticsOptIn(it) },
                        modifier = Modifier.semantics { contentDescription = "Toggle local privacy analytics" }
                    )
                }

                if (uiState.analyticsOptIn) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "LOCAL EVENT SUMMARY",
                        style = FluxTheme.typography.labelSmall,
                        color = FluxPalette.CharcoalMuted
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FluxStat(label = "Saves", value = "${uiState.eventCounts.saveCount}")
                        FluxStat(label = "Archives", value = "${uiState.eventCounts.archiveCount}")
                        FluxStat(label = "Resolves", value = "${uiState.eventCounts.resolveCount}")
                        FluxStat(label = "Restores", value = "${uiState.eventCounts.restoreCount}")
                        FluxStat(label = "Deletes", value = "${uiState.eventCounts.deleteCount}")
                        FluxStat(label = "Skips", value = "${uiState.eventCounts.skipCount}")
                        FluxStat(label = "Opens", value = "${uiState.eventCounts.openCount}")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Card 3: Encrypted Backup Export
        FluxCard(
            variant = FluxCardVariant.Paper,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text(
                    text = "Encrypted Local Export",
                    style = FluxTheme.typography.titleMedium,
                    color = FluxPalette.Void
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Export your entire link database, budget history, and events to a local JSON document file.",
                    style = FluxTheme.typography.bodySmall,
                    color = FluxPalette.CharcoalMuted
                )
                Spacer(modifier = Modifier.height(16.dp))
                FluxButton(
                    text = "Export Backup JSON",
                    onClick = {
                        createDocumentLauncher.launch("mindful_linking_backup_${System.currentTimeMillis()}.json")
                    },
                    variant = FluxButtonVariant.Primary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Card 4: Permanent Data Wipe Danger Zone
        FluxCard(
            variant = FluxCardVariant.Charcoal,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text(
                    text = "DANGER ZONE",
                    style = FluxTheme.typography.labelSmall,
                    color = FluxPalette.Signal
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Permanent Local Data Wipe",
                    style = FluxTheme.typography.titleMedium,
                    color = FluxPalette.Paper
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Deletes all links, budget records, events, settings, and pending notifications from this device.",
                    style = FluxTheme.typography.bodySmall,
                    color = FluxPalette.Paper.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                FluxButton(
                    text = "Wipe All Data",
                    onClick = { showDataWipeDialog = true },
                    variant = FluxButtonVariant.Primary
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}
