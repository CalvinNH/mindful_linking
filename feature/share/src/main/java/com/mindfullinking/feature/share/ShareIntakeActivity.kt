package com.mindfullinking.feature.share

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mindfullinking.core.database.LinkEventEntity
import com.mindfullinking.core.database.LinkLedgerDatabase
import com.mindfullinking.core.database.toDomainModel
import com.mindfullinking.core.database.toEntity
import com.mindfullinking.core.domain.BudgetAction
import com.mindfullinking.core.domain.BudgetEngine
import com.mindfullinking.core.domain.UrlNormalizer
import com.mindfullinking.core.model.BudgetState
import com.mindfullinking.core.model.CreatedFrom
import com.mindfullinking.core.model.IntentCategory
import com.mindfullinking.core.model.Link
import com.mindfullinking.core.model.LinkEventType
import com.mindfullinking.core.model.LinkStatus
import com.mindfullinking.core.model.Priority
import com.mindfullinking.core.notifications.NotificationScheduler
import com.mindfullinking.core.ui.component.FluxBadge
import com.mindfullinking.core.ui.component.FluxBadgeVariant
import com.mindfullinking.core.ui.component.FluxButton
import com.mindfullinking.core.ui.component.FluxButtonVariant
import com.mindfullinking.core.ui.component.FluxDialog
import com.mindfullinking.core.ui.component.FluxDialogStyle
import com.mindfullinking.core.ui.component.FluxInput
import com.mindfullinking.core.ui.component.OverBudgetModal
import com.mindfullinking.core.ui.component.OverBudgetResolution
import com.mindfullinking.core.ui.theme.FluxPalette
import com.mindfullinking.core.ui.theme.FluxTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID

class ShareIntakeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rawText = extractSharedText(intent)
        if (rawText.isNullOrBlank()) {
            Toast.makeText(this, "No URL shared", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val canonicalUrl = try {
            UrlNormalizer.normalize(rawText)
        } catch (e: Exception) {
            Toast.makeText(this, "Invalid HTTP/HTTPS URL", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val db = LinkLedgerDatabase.getInstance(applicationContext)

        setContent {
            FluxTheme {
                ShareIntakeContent(
                    rawUrl = canonicalUrl,
                    db = db,
                    onFinish = { finish() },
                    onOpenUrl = { url ->
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        startActivity(browserIntent)
                        finish()
                    }
                )
            }
        }
    }

    private fun extractSharedText(intent: Intent?): String? {
        if (intent == null || intent.action != Intent.ACTION_SEND) return null
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: intent.getStringExtra(Intent.EXTRA_SUBJECT)
        if (!text.isNullOrBlank()) return text
        return intent.dataString
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShareIntakeContent(
    rawUrl: String,
    db: LinkLedgerDatabase,
    onFinish: () -> Unit,
    onOpenUrl: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var existingLink by remember { mutableStateOf<Link?>(null) }
    var activeLinks by remember { mutableStateOf<List<Link>>(emptyList()) }
    var archivedCount by remember { mutableIntStateOf(0) }
    var budgetState by remember { mutableStateOf(BudgetState()) }
    var isLoading by remember { mutableStateOf(true) }

    // Intake form state
    var titleInput by remember { mutableStateOf("") }
    var selectedPriority by remember { mutableStateOf(Priority.MEDIUM) }
    var selectedDuration by remember { mutableIntStateOf(15) }
    var selectedIntent by remember { mutableStateOf(IntentCategory.READ) }
    var noteInput by remember { mutableStateOf("") }
    var deadlineNotificationsEnabled by remember { mutableStateOf(false) }

    // Dialog & Modal state
    var showDuplicateDialog by remember { mutableStateOf(false) }
    var showOverBudgetModal by remember { mutableStateOf(false) }

    LaunchedEffect(rawUrl) {
        withContext(Dispatchers.IO) {
            val linkDao = db.linkDao()
            val budgetDao = db.budgetDao()

            val existingEntity = linkDao.findLatestByCanonicalUrl(rawUrl)
            val activeEntities = linkDao.getActiveLinks()
            val archCount = linkDao.getArchivedCount()
            val bState = budgetDao.getBudgetState()?.toDomainModel() ?: BudgetState()

            withContext(Dispatchers.Main) {
                existingLink = existingEntity?.toDomainModel()
                activeLinks = activeEntities.map { it.toDomainModel() }
                archivedCount = archCount
                budgetState = bState
                isLoading = false

                if (existingLink != null) {
                    showDuplicateDialog = true
                    titleInput = existingLink?.title ?: ""
                    selectedPriority = existingLink?.priority ?: Priority.MEDIUM
                    selectedDuration = existingLink?.estimatedDurationMinutes ?: 15
                    selectedIntent = existingLink?.intent ?: IntentCategory.READ
                    noteInput = existingLink?.note ?: ""
                }
            }
        }
    }

    if (isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(FluxPalette.Void.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            FluxBadge(text = "Loading budget state...", variant = FluxBadgeVariant.Action)
        }
        return
    }

    val usedPoints = BudgetEngine.calculateUsedCents(activeLinks, archivedCount) / 100.0f
    val effectiveBudget = BudgetEngine.calculateEffectiveBudgetCents(budgetState) / 100.0f
    val proposedActionCost = selectedPriority.value.toFloat()

    val saveLink: () -> Unit = {
        scope.launch(Dispatchers.IO) {
            val linkDao = db.linkDao()
            val eventDao = db.linkEventDao()

            val newLinkId = existingLink?.id ?: UUID.randomUUID().toString()
            val newLink = Link(
                id = newLinkId,
                canonicalUrl = rawUrl,
                displayUrl = rawUrl.removePrefix("https://").removePrefix("http://"),
                title = titleInput.ifBlank { null },
                note = noteInput.ifBlank { null },
                priority = selectedPriority,
                estimatedDurationMinutes = selectedDuration,
                intent = selectedIntent,
                deadlineNotificationsEnabled = deadlineNotificationsEnabled,
                status = LinkStatus.ACTIVE,
                savedAt = Instant.now(),
                createdFrom = CreatedFrom.SHARE,
                updatedAt = Instant.now()
            )

            linkDao.upsertLink(newLink.toEntity())
            eventDao.insertEvent(
                LinkEventEntity(
                    id = UUID.randomUUID().toString(),
                    linkId = newLinkId,
                    eventType = LinkEventType.SAVE.name,
                    timestampEpochMs = Instant.now().toEpochMilli(),
                    details = null
                )
            )

            if (deadlineNotificationsEnabled) {
                NotificationScheduler.scheduleDeadlineNotification(context, newLink)
            }

            withContext(Dispatchers.Main) {
                onFinish()
            }
        }
    }

    // Duplicate Handling Dialog
    if (showDuplicateDialog && existingLink != null) {
        FluxDialog(
            onDismissRequest = onFinish,
            title = "Link Already Exists",
            body = "This URL is already in your ledger. What would you like to do?",
            primaryButtonText = "Update Existing",
            onPrimaryClick = {
                showDuplicateDialog = false
            },
            secondaryButtonText = "Save Copy",
            onSecondaryClick = {
                existingLink = null
                showDuplicateDialog = false
            },
            style = FluxDialogStyle.Paper
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FluxButton(
                    text = "Open Existing Link",
                    onClick = { onOpenUrl(rawUrl) },
                    variant = FluxButtonVariant.Secondary,
                    modifier = Modifier.fillMaxWidth()
                )
                FluxButton(
                    text = "Cancel",
                    onClick = onFinish,
                    variant = FluxButtonVariant.Ghost,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    // Over-Budget Modal
    if (showOverBudgetModal) {
        OverBudgetModal(
            usedPoints = usedPoints,
            effectiveBudget = effectiveBudget,
            actionCostPoints = proposedActionCost,
            activeLinks = activeLinks,
            onDismissRequest = { showOverBudgetModal = false },
            onResolutionSelected = { resolution ->
                showOverBudgetModal = false
                scope.launch(Dispatchers.IO) {
                    val linkDao = db.linkDao()
                    val budgetDao = db.budgetDao()

                    when (resolution) {
                        is OverBudgetResolution.FreeUpLink -> {
                            val linkToModify = linkDao.getLinkById(resolution.linkId)
                            if (linkToModify != null) {
                                when (resolution.action) {
                                    OverBudgetResolution.FreeUpAction.RESOLVE -> {
                                        linkDao.upsertLink(linkToModify.copy(status = LinkStatus.RESOLVED.name, resolvedAtEpochMs = Instant.now().toEpochMilli()))
                                    }
                                    OverBudgetResolution.FreeUpAction.ARCHIVE -> {
                                        linkDao.upsertLink(linkToModify.copy(status = LinkStatus.ARCHIVED.name))
                                    }
                                    OverBudgetResolution.FreeUpAction.DELETE -> {
                                        linkDao.deleteLinkPermanently(resolution.linkId)
                                    }
                                }
                            }
                        }
                        is OverBudgetResolution.LowerPriority -> {
                            val linkToModify = linkDao.getLinkById(resolution.linkId)
                            if (linkToModify != null) {
                                linkDao.upsertLink(linkToModify.copy(priority = resolution.newPriority.value))
                            }
                        }
                        is OverBudgetResolution.ReplaceLink -> {
                            linkDao.deleteLinkPermanently(resolution.linkIdToReplace)
                        }
                        is OverBudgetResolution.TemporaryOverage -> {
                            val updatedState = BudgetEngine.applyOverage(budgetState, resolution.overagePoints, resolution.durationHours)
                            budgetDao.upsertBudgetState(updatedState.toEntity())
                        }
                    }

                    saveLink()
                }
            }
        )
    }

    // Main Share Intake Sheet / Dialog Surface
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FluxPalette.Void.copy(alpha = 0.6f))
            .clickable(onClick = onFinish),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = false) {}, // intercept clicks
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = FluxPalette.Paper
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header & Budget Indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "INTAKE LINK",
                        style = FluxTheme.typography.titleLarge,
                        color = FluxPalette.Void
                    )
                    FluxBadge(
                        text = "Cost: +${selectedPriority.value}.00 pts",
                        variant = FluxBadgeVariant.Action
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // URL Display
                Text(
                    text = rawUrl,
                    style = FluxTheme.typography.bodySmall,
                    color = FluxPalette.CharcoalMuted,
                    maxLines = 2
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Title Input
                Text(text = "Title (Optional)", style = FluxTheme.typography.labelMedium, color = FluxPalette.Void)
                Spacer(modifier = Modifier.height(4.dp))
                FluxInput(
                    value = titleInput,
                    onValueChange = { titleInput = it },
                    placeholder = "Enter descriptive title..."
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Priority Selection (1..5)
                Text(text = "Priority Level (1 to 5 points)", style = FluxTheme.typography.labelMedium, color = FluxPalette.Void)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Priority.entries.forEach { p ->
                        val isSelected = selectedPriority == p
                        FluxButton(
                            text = "P${p.value}",
                            onClick = { selectedPriority = p },
                            variant = if (isSelected) FluxButtonVariant.Primary else FluxButtonVariant.Outlined,
                            modifier = Modifier
                                .weight(1f)
                                .semantics { contentDescription = "Priority level ${p.value}" }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Estimated Duration Selection
                Text(text = "Estimated Duration (minutes)", style = FluxTheme.typography.labelMedium, color = FluxPalette.Void)
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(1, 3, 5, 10, 15, 20, 30, 45, 60, 90, 120).forEach { mins ->
                        val isSelected = selectedDuration == mins
                        FluxButton(
                            text = "${mins}m",
                            onClick = { selectedDuration = mins },
                            variant = if (isSelected) FluxButtonVariant.Data else FluxButtonVariant.Outlined,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Note Input
                Text(text = "Note (Optional)", style = FluxTheme.typography.labelMedium, color = FluxPalette.Void)
                Spacer(modifier = Modifier.height(4.dp))
                FluxInput(
                    value = noteInput,
                    onValueChange = { noteInput = it },
                    placeholder = "Add key takeaways or context...",
                    singleLine = false
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    FluxButton(
                        text = "Cancel",
                        onClick = onFinish,
                        variant = FluxButtonVariant.Ghost,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    FluxButton(
                        text = "Save Link",
                        onClick = {
                            val canPerform = BudgetEngine.canPerformAction(
                                budgetState = budgetState,
                                activeLinks = activeLinks,
                                archivedLinkCount = archivedCount,
                                action = BudgetAction.SaveActiveLink(selectedPriority)
                            )
                            if (canPerform) {
                                saveLink()
                            } else {
                                showOverBudgetModal = true
                            }
                        },
                        variant = FluxButtonVariant.Primary
                    )
                }
            }
        }
    }
}
