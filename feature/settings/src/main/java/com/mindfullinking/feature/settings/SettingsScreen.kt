package com.mindfullinking.feature.settings

import android.net.Uri
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mindfullinking.core.ui.component.FluxBadge
import com.mindfullinking.core.ui.component.FluxBadgeVariant
import com.mindfullinking.core.ui.component.FluxButton
import com.mindfullinking.core.ui.component.FluxButtonVariant
import com.mindfullinking.core.ui.component.FluxCard
import com.mindfullinking.core.ui.component.FluxCardVariant
import com.mindfullinking.core.ui.component.FluxDialog
import com.mindfullinking.core.ui.component.FluxDialogStyle
import com.mindfullinking.core.ui.component.FluxInput
import com.mindfullinking.core.ui.component.FluxStat
import com.mindfullinking.core.ui.component.FluxSwitch
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
    var showExportPassphraseDialog by remember { mutableStateOf(false) }
    var backupPassphrase by remember { mutableStateOf("") }
    var backupPassphraseConfirmation by remember { mutableStateOf("") }
    var pendingExportPassphrase by remember { mutableStateOf<CharArray?>(null) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var restorePassphrase by remember { mutableStateOf("") }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            val passphrase = pendingExportPassphrase
            if (passphrase == null) return@rememberLauncherForActivityResult
            viewModel.exportEncryptedBackup(uri, context, passphrase) { success, message ->
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
            pendingExportPassphrase = null
        } else {
            pendingExportPassphrase?.fill('\u0000')
            pendingExportPassphrase = null
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        pendingRestoreUri = uri
    }

    pendingRestoreUri?.let { restoreUri ->
        FluxDialog(
            onDismissRequest = {
                restorePassphrase = ""
                pendingRestoreUri = null
            },
            title = "RESTORE ENCRYPTED BACKUP",
            body = "Restoring replaces every current link, budget record, event, and recommendation session on this device. This cannot be undone.",
            primaryButtonText = "Replace With Backup",
            onPrimaryClick = {
                if (restorePassphrase.isNotBlank()) {
                    viewModel.restoreEncryptedBackup(restoreUri, context, restorePassphrase.toCharArray()) { success, message ->
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                    restorePassphrase = ""
                    pendingRestoreUri = null
                }
            },
            secondaryButtonText = "Cancel",
            onSecondaryClick = {
                restorePassphrase = ""
                pendingRestoreUri = null
            },
            style = FluxDialogStyle.Charcoal
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(FluxTheme.spacing.md)) {
                FluxInput(
                    value = restorePassphrase,
                    onValueChange = { restorePassphrase = it },
                    label = "Backup passphrase",
                    placeholder = "Enter backup passphrase",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation()
                )
            }
        }
    }

    if (showExportPassphraseDialog) {
        val passphrasesMatch = backupPassphrase.isNotBlank() && backupPassphrase == backupPassphraseConfirmation
        FluxDialog(
            onDismissRequest = {
                backupPassphrase = ""
                backupPassphraseConfirmation = ""
                showExportPassphraseDialog = false
            },
            title = "PROTECT BACKUP",
            body = "Choose a passphrase to encrypt this complete local backup. It is not stored by the app, so it cannot be recovered if lost.",
            primaryButtonText = "Choose File",
            onPrimaryClick = {
                if (passphrasesMatch) {
                    pendingExportPassphrase = backupPassphrase.toCharArray()
                    backupPassphrase = ""
                    backupPassphraseConfirmation = ""
                    showExportPassphraseDialog = false
                    createDocumentLauncher.launch("mindful_linking_backup_${System.currentTimeMillis()}.mlbk")
                }
            },
            secondaryButtonText = "Cancel",
            onSecondaryClick = {
                backupPassphrase = ""
                backupPassphraseConfirmation = ""
                showExportPassphraseDialog = false
            },
            style = FluxDialogStyle.Charcoal
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(FluxTheme.spacing.md)) {
                FluxInput(
                    value = backupPassphrase,
                    onValueChange = { backupPassphrase = it },
                    label = "Backup passphrase",
                    placeholder = "Enter passphrase",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation()
                )
                FluxInput(
                    value = backupPassphraseConfirmation,
                    onValueChange = { backupPassphraseConfirmation = it },
                    label = "Confirm passphrase",
                    placeholder = "Re-enter passphrase",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation()
                )
                if (backupPassphraseConfirmation.isNotEmpty() && !passphrasesMatch) {
                    Text(text = "Passphrases do not match.", style = FluxTheme.typography.bodySmall, color = FluxTheme.colors.statusWarning)
                }
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
            .background(FluxTheme.colors.background)
            .verticalScroll(rememberScrollState())
            .padding(FluxTheme.spacing.lg)
    ) {
        Text(
            text = "APP SETTINGS & PRIVACY",
            style = FluxTheme.typography.titleLarge,
            color = FluxTheme.colors.contentPrimary
        )
        Text(
            text = "Local-first privacy, metadata controls, local export & data wipe",
            style = FluxTheme.typography.bodySmall,
            color = FluxTheme.colors.contentSecondary
        )

        Spacer(modifier = Modifier.height(FluxTheme.spacing.xl))



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
                        text = "Local Activity Summary",
                            style = FluxTheme.typography.titleMedium,
                            color = FluxTheme.colors.contentPrimary
                        )
                        Spacer(modifier = Modifier.height(FluxTheme.spacing.xs))
                        Text(
                            text = "Show the on-device activity summary. Link lifecycle events remain local operational history and are never sent anywhere.",
                            style = FluxTheme.typography.bodySmall,
                            color = FluxTheme.colors.contentSecondary
                        )
                    }
                    Spacer(modifier = Modifier.padding(start = FluxTheme.spacing.md))
                    FluxSwitch(
                        checked = uiState.analyticsOptIn,
                        onCheckedChange = { viewModel.setAnalyticsOptIn(it) },
                        label = "Show or hide local activity summary"
                    )
                }

                if (uiState.analyticsOptIn) {
                    Spacer(modifier = Modifier.height(FluxTheme.spacing.lg))
                    Text(
                        text = "LOCAL EVENT SUMMARY",
                        style = FluxTheme.typography.labelSmall,
                        color = FluxTheme.colors.contentSecondary
                    )
                    Spacer(modifier = Modifier.height(FluxTheme.spacing.sm))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(FluxTheme.spacing.md),
                        verticalArrangement = Arrangement.spacedBy(FluxTheme.spacing.md),
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

        Spacer(modifier = Modifier.height(FluxTheme.spacing.lg))

        // Card 3: Encrypted Backup Export
        FluxCard(
            variant = FluxCardVariant.Paper,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text(
                    text = "Encrypted Local Backup Export",
                    style = FluxTheme.typography.titleMedium,
                    color = FluxTheme.colors.contentPrimary
                )
                Spacer(modifier = Modifier.height(FluxTheme.spacing.xs))
                Text(
                    text = "Export all links, budget state, event history, and recommendation sessions in a passphrase-encrypted local backup file.",
                    style = FluxTheme.typography.bodySmall,
                    color = FluxTheme.colors.contentSecondary
                )
                Spacer(modifier = Modifier.height(FluxTheme.spacing.lg))
                FluxButton(
                    text = "Export Encrypted Backup",
                    onClick = {
                        showExportPassphraseDialog = true
                    },
                    variant = FluxButtonVariant.Primary
                )
                Spacer(modifier = Modifier.height(FluxTheme.spacing.md))
                FluxButton(
                    text = "Restore Encrypted Backup",
                    onClick = { openDocumentLauncher.launch(arrayOf("application/octet-stream", "application/*")) },
                    variant = FluxButtonVariant.Outlined
                )
            }
        }

        Spacer(modifier = Modifier.height(FluxTheme.spacing.lg))

        // Card 4: Permanent Data Wipe Danger Zone
        FluxCard(
            variant = FluxCardVariant.Charcoal,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text(
                    text = "DANGER ZONE",
                    style = FluxTheme.typography.labelSmall,
                    color = FluxTheme.colors.accentAction
                )
                Spacer(modifier = Modifier.height(FluxTheme.spacing.xs))
                Text(
                    text = "Permanent Local Data Wipe",
                    style = FluxTheme.typography.titleMedium,
                    color = FluxTheme.colors.contentOnInverted
                )
                Spacer(modifier = Modifier.height(FluxTheme.spacing.xs))
                Text(
                    text = "Deletes all links, budget records, events, settings, and pending notifications from this device.",
                    style = FluxTheme.typography.bodySmall,
                    color = FluxTheme.colors.contentOnInverted.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(FluxTheme.spacing.lg))
                FluxButton(
                    text = "Wipe All Data",
                    onClick = { showDataWipeDialog = true },
                    variant = FluxButtonVariant.Primary
                )
            }
        }

        Spacer(modifier = Modifier.height(FluxTheme.spacing.xxxl))
    }
}
