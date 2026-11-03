package com.mindfullinking.feature.share

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
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
import com.mindfullinking.core.database.BudgetMutationResult
import com.mindfullinking.core.database.BudgetMutationStore
import com.mindfullinking.core.database.LinkLedgerDatabase
import com.mindfullinking.core.database.toDomainModel
import com.mindfullinking.core.domain.BudgetEngine
import com.mindfullinking.core.domain.BudgetResolution
import com.mindfullinking.core.domain.UrlNormalizer
import com.mindfullinking.core.model.BudgetState
import com.mindfullinking.core.model.CreatedFrom
import com.mindfullinking.core.model.Link
import com.mindfullinking.core.model.LinkStatus
import com.mindfullinking.core.model.Priority
import com.mindfullinking.core.notifications.applyBudgetNotificationEffects
import com.mindfullinking.core.notifications.NotificationScheduler
import com.mindfullinking.core.ui.component.FluxBadge
import com.mindfullinking.core.ui.component.FluxBadgeVariant
import com.mindfullinking.core.ui.component.FluxButton
import com.mindfullinking.core.ui.component.FluxButtonVariant
import com.mindfullinking.core.ui.component.FluxDialog
import com.mindfullinking.core.ui.component.FluxDialogStyle
import com.mindfullinking.core.ui.component.FluxInput
import com.mindfullinking.core.ui.component.FluxCard
import com.mindfullinking.core.ui.component.FluxCardVariant
import com.mindfullinking.core.ui.component.FluxDateTimePickerModal
import com.mindfullinking.core.ui.component.FluxSwitch
import com.mindfullinking.core.ui.component.OverBudgetModal
import com.mindfullinking.core.ui.component.OverBudgetResolution
import com.mindfullinking.core.ui.theme.FluxPalette
import com.mindfullinking.core.ui.theme.FluxTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
    val budgetMutationStore = remember(db) { BudgetMutationStore(db) }

    var existingLink by remember { mutableStateOf<Link?>(null) }
    var activeLinks by remember { mutableStateOf<List<Link>>(emptyList()) }
    var archivedCount by remember { mutableIntStateOf(0) }
    var budgetState by remember { mutableStateOf(BudgetState()) }
    var isLoading by remember { mutableStateOf(true) }

    // Intake form state
    var titleInput by remember { mutableStateOf("") }
    var selectedPriority by remember { mutableStateOf(Priority.MEDIUM) }
    var selectedDuration by remember { mutableIntStateOf(15) }
    var noteInput by remember { mutableStateOf("") }
    var deadlineDate by remember { mutableStateOf<LocalDate?>(null) }
    var deadlineTime by remember { mutableStateOf<LocalTime?>(null) }
    var deadlineNotificationsEnabled by remember { mutableStateOf(false) }
    var reminderExplanation by remember { mutableStateOf<String?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        deadlineNotificationsEnabled = granted
        reminderExplanation = if (granted) {
            reminderEnablementPlan(
                notificationPermissionGranted = true,
                notificationPermissionRequestSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
                exactAlarmsAvailable = NotificationScheduler.canScheduleExactAlarms(context)
            ).explanation
        } else {
            "Notifications were not allowed. You can enable them later in Android settings."
        }
    }

    // Dialog & Modal state
    var showDuplicateDialog by remember { mutableStateOf(false) }
    var showOverBudgetModal by remember { mutableStateOf(false) }
    var showDateTimePickerModal by remember { mutableStateOf(false) }

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
                    noteInput = existingLink?.note ?: ""
                    deadlineDate = existingLink?.deadlineDateOnly
                        ?: existingLink?.deadlineAt?.atZone(ZoneId.systemDefault())?.toLocalDate()
                    deadlineTime = existingLink
                        ?.takeIf { it.deadlineHasTime }
                        ?.deadlineAt
                        ?.atZone(ZoneId.systemDefault())
                        ?.toLocalTime()
                    deadlineNotificationsEnabled = existingLink?.deadlineNotificationsEnabled ?: false
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
    val selectedDeadline = deadlineSelection(deadlineDate, deadlineTime, ZoneId.systemDefault())

    fun proposedLink(): Link {
        val now = Instant.now()
        val existing = existingLink
        return existing?.copy(
            canonicalUrl = rawUrl,
            displayUrl = rawUrl.removePrefix("https://").removePrefix("http://"),
            title = titleInput.ifBlank { null },
            note = noteInput.ifBlank { null },
            priority = selectedPriority,
            estimatedDurationMinutes = selectedDuration,
            deadlineAt = selectedDeadline?.deadlineAt,
            deadlineDateOnly = selectedDeadline?.deadlineDateOnly,
            deadlineHasTime = selectedDeadline?.deadlineHasTime ?: false,
            deadlineNotificationsEnabled = deadlineNotificationsEnabled && selectedDeadline != null,
            status = LinkStatus.ACTIVE,
            updatedAt = now
        ) ?: Link(
            id = UUID.randomUUID().toString(),
            canonicalUrl = rawUrl,
            displayUrl = rawUrl.removePrefix("https://").removePrefix("http://"),
            title = titleInput.ifBlank { null },
            note = noteInput.ifBlank { null },
            priority = selectedPriority,
            estimatedDurationMinutes = selectedDuration,
            deadlineAt = selectedDeadline?.deadlineAt,
            deadlineDateOnly = selectedDeadline?.deadlineDateOnly,
            deadlineHasTime = selectedDeadline?.deadlineHasTime ?: false,
            deadlineNotificationsEnabled = deadlineNotificationsEnabled && selectedDeadline != null,
            status = LinkStatus.ACTIVE,
            savedAt = now,
            createdFrom = CreatedFrom.SHARE,
            updatedAt = now
        )
    }

    fun saveLink(resolution: BudgetResolution? = null) {
        scope.launch(Dispatchers.IO) {
            when (val result = budgetMutationStore.saveActiveLink(proposedLink(), resolution)) {
                is BudgetMutationResult.Success -> {
                    applyBudgetNotificationEffects(context, result.notificationEffects)
                    withContext(Dispatchers.Main) { onFinish() }
                }
                BudgetMutationResult.BudgetExceeded -> withContext(Dispatchers.Main) {
                    showOverBudgetModal = true
                }
                else -> withContext(Dispatchers.Main) {
                    Toast.makeText(context, result.userMessage(), Toast.LENGTH_SHORT).show()
                }
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
                saveLink(resolution.toBudgetResolutionFor(proposedLink().id))
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Priority.entries.forEach { p ->
                        val isSelected = selectedPriority == p
                        FluxButton(
                            text = "P${p.value}",
                            onClick = { selectedPriority = p },
                            variant = if (isSelected) FluxButtonVariant.Primary else FluxButtonVariant.Outlined,
                            modifier = Modifier
                                .weight(1f)
                                .semantics { contentDescription = "Priority level ${p.value}" },
                            minWidth = 0.dp,
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp)
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

                // Modern Unified Deadline & Reminder Card
                FluxCard(
                    modifier = Modifier.fillMaxWidth(),
                    variant = FluxCardVariant.Surface
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(FluxTheme.spacing.md)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "DEADLINE & REMINDER",
                                style = FluxTheme.typography.labelMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                                color = FluxTheme.colors.contentPrimary
                            )
                            if (deadlineDate != null) {
                                FluxBadge(
                                    text = if (deadlineTime != null) "Timed" else "All Day",
                                    variant = FluxBadgeVariant.Action
                                )
                            }
                        }

                        if (deadlineDate == null) {
                            Text(
                                text = "Optional. Set an intentional completion deadline with automated reminders.",
                                style = FluxTheme.typography.bodySmall,
                                color = FluxTheme.colors.contentSecondary
                            )
                            FluxButton(
                                text = "Set Deadline & Reminder 📅",
                                onClick = { showDateTimePickerModal = true },
                                variant = FluxButtonVariant.Outlined,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .semantics { contentDescription = "Choose deadline date and time" }
                            )
                        } else {
                            val dateStr = deadlineDate?.format(DateTimeFormatter.ofPattern("d MMM uuuu")) ?: ""
                            val timeStr = deadlineTime?.format(DateTimeFormatter.ofPattern("h:mm a")) ?: "End of day (23:59)"

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                                    .background(FluxPalette.Lavender.copy(alpha = 0.25f))
                                    .border(1.dp, FluxPalette.Void, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Column {
                                    Text(
                                        text = dateStr,
                                        style = FluxTheme.typography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Black),
                                        color = FluxPalette.Void
                                    )
                                    Text(
                                        text = "Due: $timeStr",
                                        style = FluxTheme.typography.bodySmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                                        color = FluxPalette.CharcoalMuted
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FluxButton(
                                    text = "Change Date / Time",
                                    onClick = { showDateTimePickerModal = true },
                                    variant = FluxButtonVariant.Secondary,
                                    modifier = Modifier.weight(1f),
                                    minWidth = 0.dp,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                                )
                                FluxButton(
                                    text = "Remove",
                                    onClick = {
                                        deadlineDate = null
                                        deadlineTime = null
                                        deadlineNotificationsEnabled = false
                                        reminderExplanation = null
                                    },
                                    variant = FluxButtonVariant.Ghost,
                                    modifier = Modifier.weight(0.6f),
                                    minWidth = 0.dp,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Deadline Reminders",
                                        style = FluxTheme.typography.labelMedium,
                                        color = FluxTheme.colors.contentPrimary
                                    )
                                    Text(
                                        text = "Get notified before deadline reaches.",
                                        style = FluxTheme.typography.bodySmall,
                                        color = FluxTheme.colors.contentSecondary
                                    )
                                }
                                FluxSwitch(
                                    checked = deadlineNotificationsEnabled,
                                    onCheckedChange = { enabled ->
                                        if (!enabled) {
                                            deadlineNotificationsEnabled = false
                                            reminderExplanation = null
                                            return@FluxSwitch
                                        }
                                        val plan = reminderEnablementPlan(
                                            notificationPermissionGranted = NotificationScheduler.hasNotificationPermission(context),
                                            notificationPermissionRequestSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
                                            exactAlarmsAvailable = NotificationScheduler.canScheduleExactAlarms(context)
                                        )
                                        reminderExplanation = plan.explanation
                                        if (plan.requestNotificationPermission) {
                                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        } else if (NotificationScheduler.hasNotificationPermission(context)) {
                                            deadlineNotificationsEnabled = true
                                        }
                                    },
                                    modifier = Modifier.semantics {
                                        contentDescription = "Deadline reminders. ${if (deadlineNotificationsEnabled) "On" else "Off"}"
                                    }
                                )
                            }

                            reminderExplanation?.let { explanation ->
                                Text(
                                    text = explanation,
                                    style = FluxTheme.typography.bodySmall,
                                    color = FluxTheme.colors.contentSecondary
                                )
                            }
                        }
                    }
                }

                if (showDateTimePickerModal) {
                    FluxDateTimePickerModal(
                        initialDate = deadlineDate,
                        initialTime = deadlineTime,
                        onDismissRequest = { showDateTimePickerModal = false },
                        onConfirm = { newDate, newTime ->
                            deadlineDate = newDate
                            deadlineTime = newTime
                            reminderExplanation = null
                        },
                        onClear = if (deadlineDate != null) {
                            {
                                deadlineDate = null
                                deadlineTime = null
                                deadlineNotificationsEnabled = false
                                reminderExplanation = null
                            }
                        } else null
                    )
                }

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
                        onClick = { saveLink() },
                        variant = FluxButtonVariant.Primary
                    )
                }
            }
        }
    }
}

internal fun OverBudgetResolution.toBudgetResolutionFor(proposedLinkId: String): BudgetResolution = when (this) {
    is OverBudgetResolution.FreeUpLink -> BudgetResolution.FreeUpLink(
        linkId = linkId,
        action = when (action) {
            OverBudgetResolution.FreeUpAction.RESOLVE -> BudgetResolution.FreeUpAction.RESOLVE
            OverBudgetResolution.FreeUpAction.ARCHIVE -> BudgetResolution.FreeUpAction.ARCHIVE
            OverBudgetResolution.FreeUpAction.DELETE -> BudgetResolution.FreeUpAction.DELETE
        }
    )
    is OverBudgetResolution.LowerPriority -> if (linkId == proposedLinkId) {
        BudgetResolution.LowerProposedPriority(newPriority)
    } else {
        BudgetResolution.LowerPriority(linkId, newPriority)
    }
    is OverBudgetResolution.ReplaceLink -> BudgetResolution.ReplaceLink(linkIdToReplace)
    is OverBudgetResolution.TemporaryOverage -> BudgetResolution.TemporaryOverage(overagePoints, durationHours)
}

private fun BudgetMutationResult.userMessage(): String = when (this) {
    BudgetMutationResult.LinkNotFound -> "The selected link no longer exists."
    BudgetMutationResult.LinkIsNotActive,
    BudgetMutationResult.LinkIsNotArchived -> "That link changed before the update could be saved."
    BudgetMutationResult.InvalidDuration -> "Choose one of the supported duration options."
    BudgetMutationResult.InvalidResolution -> "That budget resolution is no longer valid."
    BudgetMutationResult.BudgetExceeded -> "Your attention budget is full."
    is BudgetMutationResult.Success -> error("Success has no error message")
}
