# Link Ledger (MindfulLinking) — Technical Implementation Plan

**Author:** Lead Android Architect & Systems Designer  
**Target Platform:** Android 10+ (API Level 29+), Target SDK 34/35  
**Language/Stack:** Kotlin 2.0+, Jetpack Compose, Room SQLite, DataStore, WorkManager, AlarmManager, Hilt  
**Document Status:** Complete Technical Architecture & Phased Implementation Plan  

---

## Executive Summary & System Architectural Blueprint

Link Ledger ("MindfulLinking") is an Android-first, local-first link manager engineered to enforce deliberate prioritization of leisure attention through a strict **100-point attention budget**, deterministic recommendation scoring, and offline-first data retention.

Unlike conventional bookmarking tools that encourage endless collection, Link Ledger treats saved links as finite commitments against future cognitive bandwidth. The architecture guarantees zero cloud dependencies, complete data locality, total log sanitization, and deterministic UI state management.

### Architectural Blueprint (Data & Control Flow)

```mermaid
flowchart TD
    subgraph Capture Layer [Intake & Parsing]
        AS[Android Sharesheet / ACTION_SEND] --> SRActivity[ShareReceiverActivity]
        MI[Manual Entry UI] --> SRActivity
        SRActivity --> UN[UrlNormalizer Engine]
        UN --> DD[Duplicate Detection Engine]
    end

    subgraph Domain & State Layer [Pure Business Logic]
        DD --> BE[Point Budget Engine]
        BE --> RE[Deterministic Recommendation Engine]
        RE --> DE[Deadline & Urgency Engine]
    end

    subgraph Data & Storage Layer [Local Sandbox]
        BE <--> RDB[(Room SQLite Database)]
        BE <--> DS[DataStore Preferences]
        RDB <--> KS[Android Keystore Encryption]
    end

    subgraph Scheduling Layer [Background Lifecycle]
        DE --> NS[Notification Scheduler]
        NS --> WM[WorkManager Periodic/OneTime]
        NS --> AM[AlarmManager Exact Reminders]
        BR[BootReceiver / TimeZoneChangeReceiver] --> NS
    end

    subgraph UI & Presentation Layer [Jetpack Compose UI]
        RDB --> UINow[Now Screen / Recommendation Carousel]
        RDB --> UIInbox[Inbox Screen / Priority & Deadline Grouping]
        RDB --> UIArchive[Archive Screen / 0.25pt Storage]
        RDB --> UISettings[Settings Screen / Local Export & Analytics]
    end
```

---

## 1. Modular Codebase Architecture

The project is structured as a multi-module Gradle project to enforce strict boundary separation, fast incremental builds, and total decoupling of domain logic from Android SDK components.

```
MindfulLinking/
├── app/                        # Application entry point, Hilt dependency graph container
├── core/
│   ├── model/                  # Pure Kotlin domain models, value objects, and Enums
│   ├── domain/                 # Business logic: URL normalization, Budget engine, Recommendation engine
│   ├── data/                   # Room entities, DAOs, Room DB, Repositories, DataStore, Keystore
│   ├── notifications/          # WorkManager workers, AlarmManager schedulers, BroadcastReceivers
│   ├── share/                  # Share-sheet receiver activity, multi-URL picker, intent handlers
│   ├── ui/                     # Common Compose design system, components, typography, accessibility
│   └── privacy/                # Security rules, log sanitizers, backup exclusion configurations
└── feature/
    ├── now/                    # Now tab: "Use a link" session, recommendation cards & modal
    ├── inbox/                  # Inbox tab: 2-level priority/deadline hierarchy, edit/resolve flows
    ├── archive/                # Archive tab: 0.25pt retained links, search, restore & delete flows
    └── settings/               # Settings tab: local analytics view, data export, privacy controls
```

### Module Dependency Matrix

| Module | Dependencies | Responsibilities |
| :--- | :--- | :--- |
| `:core:model` | None (Pure Kotlin) | Core entities (`Link`, `BudgetState`, `RecommendationSession`, `LinkEvent`), Enums |
| `:core:domain` | `:core:model` | Pure business rules: Budget calculation, UrlNormalizer, Recommendation algorithm |
| `:core:data` | `:core:model`, `:core:domain` | Room Database, DAOs, DataStore Repository, Keystore encryption helper |
| `:core:share` | `:core:model`, `:core:domain`, `:core:data`, `:core:ui` | `ACTION_SEND` intent receiver, URL extractor, duplicate link resolver activity |
| `:core:notifications`| `:core:model`, `:core:domain`, `:core:data` | AlarmManager, WorkManager workers, `BootReceiver`, `TimeZoneChangeReceiver` |
| `:core:ui` | `:core:model` | Jetpack Compose theme, tokens, standard dialogs, accessibility extensions |
| `:core:privacy` | `:core:model` | Crash log interceptors, backup rules, local export generators |
| `:feature:now` | `:core:model`, `:core:domain`, `:core:data`, `:core:ui` | UI & ViewModel for recommendation sessions & active budget status |
| `:feature:inbox` | `:core:model`, `:core:domain`, `:core:data`, `:core:ui` | UI & ViewModel for priority/deadline multi-tier inbox list |
| `:feature:archive` | `:core:model`, `:core:domain`, `:core:data`, `:core:ui` | UI & ViewModel for archive list, search, restore, and 0.25pt management |
| `:feature:settings`| `:core:model`, `:core:domain`, `:core:data`, `:core:ui`, `:core:privacy` | UI & ViewModel for privacy toggles, local aggregate analytics, local data reset |
| `:app` | All `:feature:*` & `:core:*` modules | Application class, Hilt Singleton Component initialization, Jetpack Navigation setup |

---

## 2. Complete Room Database Schemas, DAOs, Enums & Migrations

### 2.1 Domain Enums

```kotlin
package com.mindfullinking.core.model

enum class LinkIntent {
    READ, WATCH, ACT, REFERENCE, OTHER
}

enum class LinkStatus {
    ACTIVE, ARCHIVED, RESOLVED
}

enum class ResolutionReason {
    COMPLETED, APPLIED, NO_LONGER_RELEVANT
}

enum class CreatedFrom {
    SHARE, MANUAL
}

enum class MetadataFetchStatus {
    DISABLED, PENDING, SUCCESS, FAILED
}

enum class EventType {
    SAVED, OPENED, SKIPPED, SNOOZED, RESOLVED, ARCHIVED, RESTORED, DELETED, RECOMMENDED
}
```

### 2.2 Room Entities

```kotlin
package com.mindfullinking.core.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "links",
    indices = [
        Index(value = ["canonical_url"], unique = false),
        Index(value = ["status"]),
        Index(value = ["priority"]),
        Index(value = ["deadline_at"])
    ]
)
data class LinkEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "canonical_url")
    val canonicalUrl: String,

    @ColumnInfo(name = "display_url")
    val displayUrl: String,

    @ColumnInfo(name = "title")
    val title: String? = null,

    @ColumnInfo(name = "note")
    val note: String? = null,

    @ColumnInfo(name = "priority")
    val priority: Int, // 1..5

    @ColumnInfo(name = "estimated_duration_minutes")
    val estimatedDurationMinutes: Int,

    @ColumnInfo(name = "intent")
    val intent: String, // Enum name

    @ColumnInfo(name = "deadline_at")
    val deadlineAtEpochMs: Long? = null, // UTC Epoch Milliseconds

    @ColumnInfo(name = "deadline_has_time")
    val deadlineHasTime: Boolean = false,

    @ColumnInfo(name = "deadline_notifications_enabled")
    val deadlineNotificationsEnabled: Boolean = true,

    @ColumnInfo(name = "status")
    val status: String, // ACTIVE, ARCHIVED, RESOLVED

    @ColumnInfo(name = "resolution_reason")
    val resolutionReason: String? = null,

    @ColumnInfo(name = "saved_at")
    val savedAtEpochMs: Long, // UTC Epoch Milliseconds

    @ColumnInfo(name = "resolved_at")
    val resolvedAtEpochMs: Long? = null,

    @ColumnInfo(name = "last_opened_at")
    val lastOpenedAtEpochMs: Long? = null,

    @ColumnInfo(name = "snoozed_until")
    val snoozedUntilEpochMs: Long? = null,

    @ColumnInfo(name = "skip_count")
    val skipCount: Int = 0,

    @ColumnInfo(name = "last_skipped_at")
    val lastSkippedAtEpochMs: Long? = null,

    @ColumnInfo(name = "recommendation_count")
    val recommendationCount: Int = 0,

    @ColumnInfo(name = "last_recommended_at")
    val lastRecommendedAtEpochMs: Long? = null,

    @ColumnInfo(name = "created_from")
    val createdFrom: String, // SHARE, MANUAL

    @ColumnInfo(name = "metadata_fetch_status")
    val metadataFetchStatus: String = "DISABLED",

    @ColumnInfo(name = "updated_at")
    val updatedAtEpochMs: Long
)

@Entity(tableName = "budget_state")
data class BudgetStateEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int = 1, // Singleton row constraint

    @ColumnInfo(name = "standard_budget")
    val standardBudget: Double = 100.00,

    @ColumnInfo(name = "temporary_overage_points")
    val temporaryOveragePoints: Double = 0.00,

    @ColumnInfo(name = "temporary_overage_expires_at")
    val temporaryOverageExpiresAtEpochMs: Long? = null,

    @ColumnInfo(name = "last_overage_reminder_at")
    val lastOverageReminderAtEpochMs: Long? = null
)

@Entity(tableName = "recommendation_sessions")
data class RecommendationSessionEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "available_minutes")
    val availableMinutes: Int,

    @ColumnInfo(name = "intent_filter")
    val intentFilter: String? = null,

    @ColumnInfo(name = "started_at")
    val startedAtEpochMs: Long,

    @ColumnInfo(name = "recommended_link_ids_json")
    val recommendedLinkIdsJson: String // JSON Array of UUID strings
)

@Entity(
    tableName = "link_events",
    foreignKeys = [
        ForeignKey(
            entity = LinkEntity::class,
            parentColumns = ["id"],
            childColumns = ["link_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["link_id"]), Index(value = ["occurred_at"])]
)
data class LinkEventEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "link_id")
    val linkId: String,

    @ColumnInfo(name = "event_type")
    val eventType: String,

    @ColumnInfo(name = "occurred_at")
    val occurredAtEpochMs: Long
)
```

### 2.3 Data Access Objects (DAOs)

```kotlin
package com.mindfullinking.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.mindfullinking.core.data.entity.BudgetStateEntity
import com.mindfullinking.core.data.entity.LinkEntity
import com.mindfullinking.core.data.entity.LinkEventEntity
import com.mindfullinking.core.data.entity.RecommendationSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LinkDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLink(link: LinkEntity)

    @Update
    suspend fun updateLink(link: LinkEntity)

    @Query("SELECT * FROM links WHERE id = :id")
    suspend fun getLinkById(id: String): LinkEntity?

    @Query("SELECT * FROM links WHERE canonical_url = :canonicalUrl LIMIT 1")
    suspend fun getLinkByCanonicalUrl(canonicalUrl: String): LinkEntity?

    @Query("SELECT * FROM links WHERE canonical_url = :canonicalUrl AND status = :status LIMIT 1")
    suspend fun getLinkByCanonicalUrlAndStatus(canonicalUrl: String, status: String): LinkEntity?

    @Query("SELECT * FROM links WHERE status = 'ACTIVE' ORDER BY priority DESC, saved_at ASC")
    fun getActiveLinksFlow(): Flow<List<LinkEntity>>

    @Query("SELECT * FROM links WHERE status = 'ACTIVE'")
    suspend fun getActiveLinksList(): List<LinkEntity>

    @Query("SELECT * FROM links WHERE status = 'ARCHIVED' ORDER BY updated_at DESC")
    fun getArchivedLinksFlow(): Flow<List<LinkEntity>>

    @Query("SELECT COUNT(*) FROM links WHERE status = 'ARCHIVED'")
    fun getArchivedCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM links WHERE status = 'ARCHIVED'")
    suspend fun getArchivedCount(): Int

    @Query("SELECT SUM(priority) FROM links WHERE status = 'ACTIVE'")
    fun getActivePrioritySumFlow(): Flow<Double?>

    @Query("SELECT SUM(priority) FROM links WHERE status = 'ACTIVE'")
    suspend fun getActivePrioritySum(): Double?

    @Query("SELECT * FROM links WHERE status = 'ACTIVE' AND (snoozed_until IS NULL OR snoozed_until <= :nowEpochMs)")
    suspend fun getEligibleLinksForRecommendation(nowEpochMs: Long): List<LinkEntity>

    @Query("DELETE FROM links WHERE id = :id")
    suspend fun deleteLinkById(id: String)

    @Query("DELETE FROM links")
    suspend fun deleteAllLinks()
}

@Dao
interface BudgetDao {

    @Query("SELECT * FROM budget_state WHERE id = 1")
    fun getBudgetStateFlow(): Flow<BudgetStateEntity?>

    @Query("SELECT * FROM budget_state WHERE id = 1")
    suspend fun getBudgetState(): BudgetStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBudgetState(budgetState: BudgetStateEntity)
}

@Dao
interface RecommendationSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: RecommendationSessionEntity)

    @Query("SELECT * FROM recommendation_sessions ORDER BY started_at DESC LIMIT 1")
    suspend fun getLastSession(): RecommendationSessionEntity?

    @Query("SELECT * FROM recommendation_sessions ORDER BY started_at DESC LIMIT 2")
    suspend fun getLastTwoSessions(): List<RecommendationSessionEntity>
}

@Dao
interface LinkEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: LinkEventEntity)

    @Query("SELECT * FROM link_events WHERE link_id = :linkId ORDER BY occurred_at DESC")
    fun getEventsForLinkFlow(linkId: String): Flow<List<LinkEventEntity>>
}
```

### 2.4 Database Migration & Backup Recovery Strategy

```kotlin
package com.mindfullinking.core.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mindfullinking.core.data.dao.BudgetDao
import com.mindfullinking.core.data.dao.LinkDao
import com.mindfullinking.core.data.dao.LinkEventDao
import com.mindfullinking.core.data.dao.RecommendationSessionDao
import com.mindfullinking.core.data.entity.BudgetStateEntity
import com.mindfullinking.core.data.entity.LinkEntity
import com.mindfullinking.core.data.entity.LinkEventEntity
import com.mindfullinking.core.data.entity.RecommendationSessionEntity
import java.io.File

@Database(
    entities = [
        LinkEntity::class,
        BudgetStateEntity::class,
        RecommendationSessionEntity::class,
        LinkEventEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class LinkLedgerDatabase : RoomDatabase() {

    abstract fun linkDao(): LinkDao
    abstract fun budgetDao(): BudgetDao
    abstract fun recommendationSessionDao(): RecommendationSessionDao
    abstract fun linkEventDao(): LinkEventDao

    companion object {
        private const val DB_NAME = "link_ledger_db.db"

        fun build(context: Context): LinkLedgerDatabase {
            val dbFile = context.getDatabasePath(DB_NAME)
            
            // Edge Case 13: Create local recovery copy before performing migrations
            if (dbFile.exists()) {
                val backupFile = File(context.filesDir, "link_ledger_backup_recovery.db")
                try {
                    dbFile.copyTo(backupFile, overwrite = true)
                } catch (e: Exception) {
                    // Safe logging without user payload
                    android.util.Log.e("LinkLedgerDB", "Recovery backup copy creation failed", e)
                }
            }

            return Room.databaseBuilder(context, LinkLedgerDatabase::class.java, DB_NAME)
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Future database migration SQL statements executed transactionally
            }
        }
    }
}
```

---

## 3. Comprehensive URL Normalization and Duplicate Detection Pipeline

The duplicate detection engine relies on exact equality of deterministic canonical URLs.

### 3.1 Tracking Parameter Allowlist Stripping

Only the following exact tracking parameters are stripped case-insensitively:
`utm_source`, `utm_medium`, `utm_campaign`, `utm_term`, `utm_content`, `utm_id`, `utm_source_platform`, `utm_creative_format`, `utm_marketing_tactic`, `gclid`, `dclid`, `gbraid`, `wbraid`, `msclkid`, `fbclid`, `igshid`, `_ga`, `_gl`.

Parameters such as `ref`, `source`, `id`, `page`, `q`, `si`, `v` are strictly **retained** in their exact original position.

### 3.2 Canonical URL Engine (`UrlNormalizer.kt`)

```kotlin
package com.mindfullinking.core.domain.url

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.net.IDN
import java.util.Locale

object UrlNormalizer {

    private val TRACKING_PARAMS = setOf(
        "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
        "utm_id", "utm_source_platform", "utm_creative_format", "utm_marketing_tactic",
        "gclid", "dclid", "gbraid", "wbraid", "msclkid", "fbclid", "igshid", "_ga", "_gl"
    )

    fun normalize(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        
        // 1. Validation: scheme must be http or https, credentials forbidden
        val uri = URI.create(trimmed)
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: throw IllegalArgumentException("Missing URL scheme")
        require(scheme == "http" || scheme == "https") { "Only HTTP and HTTPS URLs are supported" }
        require(uri.userInfo == null) { "URLs with embedded user credentials are forbidden" }

        // 2. Lowercase Host & Convert to Punycode/ASCII
        val host = uri.host?.lowercase(Locale.ROOT) ?: throw IllegalArgumentException("Missing URL host")
        val asciiHost = IDN.toASCII(host)

        // 3. Handle Port
        val port = uri.port
        val isDefaultPort = (scheme == "http" && port == 80) || (scheme == "https" && port == 443)
        val portString = if (port != -1 && !isDefaultPort) ":$port" else ""

        // 4. Handle Path (Preserve segment casing, normalize empty path to empty or original)
        val rawPath = uri.rawPath ?: ""
        val path = if (rawPath.isEmpty()) "" else rawPath

        // 5. Query Parameter Normalization
        val rawQuery = uri.rawQuery
        val normalizedQuery = if (!rawQuery.isNullOrEmpty()) {
            val queryPairs = rawQuery.split("&").mapNotNull { pair ->
                if (pair.isEmpty()) return@mapNotNull null
                val parts = pair.split("=", limit = 2)
                val key = parts[0]
                val value = if (parts.size > 1) parts[1] else ""
                Pair(key, value)
            }

            val filteredPairs = queryPairs.filter { (key, _) ->
                val decodedKey = try { URLDecoder.decode(key, "UTF-8") } catch (e: Exception) { key }
                !TRACKING_PARAMS.contains(decodedKey.lowercase(Locale.ROOT))
            }

            if (filteredPairs.isNotEmpty()) {
                filteredPairs.joinToString("&") { (k, v) ->
                    if (v.isEmpty()) k else "$k=$v"
                }
            } else null
        } else null

        // 6. Fragment preservation
        val rawFragment = uri.rawFragment
        val fragmentString = if (!rawFragment.isNullOrEmpty()) "#$rawFragment" else ""

        // 7. Assemble Canonical String
        val queryString = if (normalizedQuery != null) "?$normalizedQuery" else ""
        return "$scheme://$asciiHost$portString$path$queryString$fragmentString"
    }

    fun extractUrlsFromText(text: String): List<String> {
        val urlRegex = Regex("""https?://[^\s<>"'{}|\^\[\]`]+""", RegexOption.IGNORE_CASE)
        return urlRegex.findAll(text).map { it.value }.toList()
    }
}
```

### 3.3 Test Case Verification Matrix

| Input URL | Expected Normalized Canonical URL | Verification Rationale |
| :--- | :--- | :--- |
| `https://example.com/a?utm_source=x&x=1` | `https://example.com/a?x=1` | Strips `utm_source`, retains `x=1` |
| `HTTPS://EXAMPLE.COM:443/a` | `https://example.com/a` | Lowercases scheme/host, drops default port 443 |
| `https://example.com/a?ref=homepage` | `https://example.com/a?ref=homepage` | `ref` is not on allowlist; strictly preserved |
| `https://example.com/a#section-2` | `https://example.com/a#section-2` | Retains fragment |
| `https://example.com/a?x=1&utm_campaign=test&y=2` | `https://example.com/a?x=1&y=2` | Strips middle tracking param, preserves parameter order |
| `https://München.de/path` | `https://xn--mnchen-3ya.de/path` | Host converted to IDN Punycode |
| `https://user:pass@example.com` | *Throws IllegalArgumentException* | Rejects user credentials |

---

## 4. Finite Point Budget Engine & Temporary Overage State Machine

### 4.1 Budget Calculation Logic

$$\text{used\_points} = \sum_{\text{link} \in \text{Active}} \text{priority}_{\text{link}} + (0.25 \times N_{\text{archived}})$$

$$\text{effective\_budget} = \begin{cases} 100.00 + \text{overage\_points}, & \text{if overage active and not expired} \\ 100.00, & \text{otherwise} \end{cases}$$

$$\text{available\_points} = \text{effective\_budget} - \text{used\_points}$$

### 4.2 Incremental Cost Table

| Action | Incremental Cost ($\Delta \text{Points}$) | Example Transition |
| :--- | :--- | :--- |
| **Save Active Link** | $+\text{priority}$ | Save priority 4 link $\rightarrow +4.00$ pts |
| **Archive Active Link** | $+0.25 - \text{priority}$ | Archive priority 5 link $\rightarrow -4.75$ pts |
| **Restore Archived Link** | $+\text{priority} - 0.25$ | Restore archived link at priority 3 $\rightarrow +2.75$ pts |
| **Delete Archived Link** | $-0.25$ | Delete archived link $\rightarrow -0.25$ pts |
| **Resolve Active Link** | $-\text{priority}$ | Resolve priority 4 link $\rightarrow -4.00$ pts |
| **Edit Active Priority** | $\text{priority}_{\text{new}} - \text{priority}_{\text{old}}$ | Edit priority from 2 to 5 $\rightarrow +3.00$ pts |

### 4.3 Budget Engine Implementation (`BudgetEngine.kt`)

```kotlin
package com.mindfullinking.core.domain.budget

import com.mindfullinking.core.model.BudgetState
import java.time.Instant

data class BudgetCalculationResult(
    val activePoints: Double,
    val archivedCount: Int,
    val archivedPoints: Double,
    val totalUsedPoints: Double,
    val effectiveBudget: Double,
    val availablePoints: Double,
    val isOverageActive: Boolean,
    val isOverageExpired: Boolean
)

object BudgetEngine {

    fun calculate(
        activePrioritySum: Double,
        archivedCount: Int,
        budgetState: BudgetState,
        now: Instant = Instant.now()
    ): BudgetCalculationResult {
        val archivedPoints = archivedCount * 0.25
        val totalUsedPoints = activePrioritySum + archivedPoints

        val isOverageExpired = budgetState.temporaryOverageExpiresAt != null &&
                now.isAfter(budgetState.temporaryOverageExpiresAt)

        val isOverageActive = budgetState.temporaryOveragePoints > 0.0 && !isOverageExpired

        val effectiveBudget = if (isOverageActive) {
            budgetState.standardBudget + budgetState.temporaryOveragePoints
        } else {
            budgetState.standardBudget
        }

        val availablePoints = effectiveBudget - totalUsedPoints

        return BudgetCalculationResult(
            activePoints = activePrioritySum,
            archivedCount = archivedCount,
            archivedPoints = archivedPoints,
            totalUsedPoints = totalUsedPoints,
            effectiveBudget = effectiveBudget,
            availablePoints = availablePoints,
            isOverageActive = isOverageActive,
            isOverageExpired = isOverageExpired
        )
    }

    fun canPerformAction(
        incrementalCost: Double,
        currentResult: BudgetCalculationResult
    ): Boolean {
        // Post-expiry locked state: user cannot perform actions that increase budget usage
        if (currentResult.isOverageExpired && currentResult.totalUsedPoints > 100.00) {
            return incrementalCost <= 0.0
        }
        return (currentResult.totalUsedPoints + incrementalCost) <= currentResult.effectiveBudget
    }
}
```

---

## 5. Deterministic Recommendation Engine

### 5.1 Eligibility Filtering Rules

A candidate link $i$ is eligible for a session if and only if:
1. `link.status == ACTIVE`
2. `link.snoozed_until == null` OR `link.snoozed_until <= now`
3. `link.estimated_duration_minutes <= available_minutes`  
   *(Exception: If `deadline_at` is within 24 hours or overdue, the link IS eligible despite exceeding available time, but marked with an "Exceeds Time" tag)*
4. If an intent filter is selected, `link.intent == intent_filter`.

### 5.2 Numerical Scoring Formula

$$\text{base\_score}_i = 0.40 \cdot S_{\text{priority}} + 0.22 \cdot S_{\text{deadline}} + 0.18 \cdot S_{\text{duration}} + 0.12 \cdot S_{\text{age}} + 0.08 \cdot S_{\text{skip}} - 0.20 \cdot P_{\text{exposure}}$$

#### Signal Scoring Components:
* **Priority Score ($S_{\text{priority}}$):** $\frac{\text{priority}_i}{5.0}$
* **Deadline Urgency Score ($S_{\text{deadline}}$):**
  * No deadline: $0.00$
  * $> 14$ days remaining: $0.10$
  * $8 - 14$ days remaining: $0.25$
  * $4 - 7$ days remaining: $0.50$
  * $1 - 3$ days remaining: $0.80$
  * $0 - 24$ hours remaining or overdue: $1.00$
* **Duration Fit Score ($S_{\text{duration}}$):**
  $$\max\left(0.0, 1.0 - \frac{|\text{available\_minutes} - \text{duration}_i|}{\max(\text{available\_minutes}, 1)}\right)$$
* **Age Score ($S_{\text{age}}$):** $\min\left(\frac{\text{days\_since\_saved}_i}{30.0}, 1.0\right)$
* **Skip Adjustment ($S_{\text{skip}}$):**
  * 0 skips: $0.00$
  * 1–2 skips: $+0.05$
  * 3–4 skips: $+0.10$
  * 5+ skips: $-0.20$ (and flags link as `RECONSIDER`)
* **Recent Exposure Penalty ($P_{\text{exposure}}$):**
  * Shown in immediately preceding session: $1.00$
  * Shown two sessions ago: $0.50$
  * Otherwise: $0.00$

### 5.3 Subgroup Diversity & Selection Algorithm

1. Rank candidate links by `base_score` in descending order.
2. Select Candidate 1 ($C_1$). Record its subgroup $G(C_1) = (\text{priority}, \text{deadline\_subgroup})$.
3. Re-evaluate remaining candidates. If $G(C_k) == G(C_1)$, apply a **$-0.15$ diversity penalty** to $C_k$'s score.
   *(Do NOT apply penalty if total eligible links span $< 3$ distinct subgroups or if $C_k$ is due within 24h).*
4. Select Candidate 2 ($C_2$).
5. Apply penalty to candidates matching $G(C_1)$ or $G(C_2)$. Select Candidate 3 ($C_3$).

### 5.4 Deterministic Explanation Engine

| Priority Tie-Breaker | Condition Trigger | Template String Output |
| :--- | :--- | :--- |
| **1. Deadline Urgency** | $S_{\text{deadline}} \ge 0.80$ | `"Deadline is approaching soon."` |
| **2. Priority** | $\text{priority} == 5$ | `"Priority 5 and matches your time."` |
| **3. Duration Fit** | $S_{\text{duration}} \ge 0.90$ | `"Fits your available time well."` |
| **4. Age** | $\text{days\_saved} \ge 20$ | `"Saved ${days} days ago."` |
| **5. Neglect / Review** | `skip_count >= 5` | `"Skipped multiple times; decide to keep or archive."` |
| **6. Group Diversity** | Penalty applied | `"Selected to diversify your current queue."` |

---

## 6. Deadline & Notification Lifecycle Engine

### 6.1 Notification Schedule Matrix

| Time Window | Trigger Calculation | Engine Mechanism | Notification Intent |
| :--- | :--- | :--- | :--- |
| **7 Days Before** | `deadline_at - 7 days` | WorkManager `OneTimeWorkRequest` | Periodic Reminder |
| **3 Days Before** | `deadline_at - 3 days` | WorkManager `OneTimeWorkRequest` | Warning Reminder |
| **1 Day Before** | `deadline_at - 24 hours` | WorkManager `OneTimeWorkRequest` | Urgent Reminder |
| **2 Hours Before** | `deadline_at - 2 hours` | AlarmManager (Exact) | Imminent Deadline |
| **Deadline Moment**| Exact `deadline_at` | AlarmManager (`setExactAndAllowWhileIdle`) | Deadline Passed |
| **Overage Expiry** | `overage_expires_at - 24h`| WorkManager `OneTimeWorkRequest` | Budget Expiry Warning |

### 6.2 Alarm & Receiver Logic (`NotificationScheduler.kt`)

```kotlin
package com.mindfullinking.core.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.mindfullinking.core.model.Link
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

class NotificationScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val workManager = WorkManager.getInstance(context)

    fun scheduleLinkDeadlineNotifications(link: Link) {
        cancelNotificationsForLink(link.id)

        val deadline = link.deadlineAt ?: return
        if (!link.deadlineNotificationsEnabled || link.status.name != "ACTIVE") return

        val now = Instant.now()
        val durationUntilDeadline = Duration.between(now, deadline)
        if (durationUntilDeadline.isNegative) return

        // 1. Schedule 24h WorkManager reminder
        if (durationUntilDeadline.toHours() > 24) {
            val delayMillis = durationUntilDeadline.minusHours(24).toMillis()
            val workData = Data.Builder()
                .putString("LINK_ID", link.id)
                .putString("TITLE", link.title ?: link.displayUrl)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<DeadlineWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .setInputData(workData)
                .addTag("deadline_${link.id}")
                .build()

            workManager.enqueue(workRequest)
        }

        // 2. Schedule Exact Alarm for Deadline Moment (AlarmManager)
        if (alarmManager.canScheduleExactAlarms()) {
            val intent = Intent(context, DeadlineAlarmReceiver::class.java).apply {
                putExtra("LINK_ID", link.id)
                putExtra("TITLE", link.title ?: link.displayUrl)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                link.id.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                deadline.toEpochMilli(),
                pendingIntent
            )
        }
    }

    fun cancelNotificationsForLink(linkId: String) {
        workManager.cancelAllWorkByTag("deadline_$linkId")
        val intent = Intent(context, DeadlineAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            linkId.hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pendingIntent?.let { alarmManager.cancel(it) }
    }
}
```

### 6.3 System Receivers (`BootReceiver.kt` & `TimeZoneReceiver.kt`)

```kotlin
package com.mindfullinking.core.notifications.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.mindfullinking.core.notifications.worker.RescheduleNotificationsWorker

class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_SET -> {
                // Reschedule all active notifications safely via background Worker
                val workRequest = OneTimeWorkRequestBuilder<RescheduleNotificationsWorker>().build()
                WorkManager.getInstance(context).enqueue(workRequest)
            }
        }
    }
}
```

---

## 7. Jetpack Compose UI/UX Architecture & Navigation Flow

The app consists of four main top-level navigation destinations (`Now`, `Inbox`, `Archive`, `Settings`) plus modal sheets (`ShareIntakeDialog`, `OverBudgetModal`, `ReconsiderationModal`).

```mermaid
graph TD
    Root[Link Ledger Navigation Root]
    Root --> NowScreen[Now Tab]
    Root --> InboxScreen[Inbox Tab]
    Root --> ArchiveScreen[Archive Tab]
    Root --> SettingsScreen[Settings Tab]

    ShareSheetIntent[Android Share Sheet] --> ShareIntakeDialog[Share Intake Modal]
    ShareIntakeDialog -->|Over Budget| OverBudgetModal[Over Budget Dialog]
    ShareIntakeDialog -->|Duplicate Detected| DuplicateDialog[Duplicate Handling Dialog]

    NowScreen --> TimePickerSheet[Time Available Sheet]
    TimePickerSheet --> RecCarousel[Recommendation Session Cards]
    RecCarousel -->|5 Skips| ReconsiderationModal[Reconsideration Action Sheet]
```

### 7.1 Key Screen Components

1. **`NowScreen.kt`**:
   - Continuous visual point budget gauge (100.00 max).
   - Overage banner (shows remaining time / persistent expired lock banner).
   - Large primary CTA: **"Use a Link"**.
   - Bottom sheet for time selection (5, 10, 15, 20, 30, 45, 60, 90, 120+ minutes) & optional intent filter chips.
   - Up to 3 recommended link cards displaying deterministic explanation badges.
2. **`InboxScreen.kt`**:
   - Grouped priority sections (Priority 5 down to Priority 1).
   - Multi-tier deadline headers within priority (Overdue, Due Today, Due 3 Days, Due 7 Days, Due Later, No Deadline).
   - Link Item Cards with quick action triggers (Resolve, Archive, Snooze, Priority Edit).
3. **`ArchiveScreen.kt`**:
   - Fast local search and intent filters.
   - Clear indicators showing `0.25 pts` cost per archived link.
   - One-tap Restore (verifies budget) and Permanent Delete (releases 0.25 pts).
4. **`SettingsScreen.kt`**:
   - Privacy-by-default toggles (Metadata fetching disabled by default).
   - Aggregate local analytics dashboard (opt-in).
   - Local JSON database export with Keystore encryption option.

---

## 8. Privacy, Security, & Backup Policies

### 8.1 Android Auto Backup & Device Transfer Exclusion

To preserve total privacy, automatic cloud and device-to-device data backups are explicitly disabled via Android Manifest and XML rules.

**`res/xml/backup_rules.xml`**:
```xml
<?xml version="1.0" encoding="utf-8"?>
<full-backup-content>
    <exclude domain="sharedpref" path="." />
    <exclude domain="database" path="." />
    <exclude domain="file" path="." />
</full-backup-content>
```

**`res/xml/data_extraction_rules.xml`**:
```xml
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="sharedpref" path="." />
        <exclude domain="database" path="." />
        <exclude domain="file" path="." />
    </cloud-backup>
    <device-to-device>
        <exclude domain="sharedpref" path="." />
        <exclude domain="database" path="." />
        <exclude domain="file" path="." />
    </device-to-device>
</data-extraction-rules>
```

**`AndroidManifest.xml` snippet**:
```xml
<application
    android:allowBackup="false"
    android:fullBackupOnly="false"
    android:fullBackupContent="@xml/backup_rules"
    android:dataExtractionRules="@xml/data_extraction_rules">
</application>
```

### 8.2 Log Sanitization Interceptor (`SanitizingLogTree.kt`)

```kotlin
package com.mindfullinking.core.privacy

import android.util.Log
import timber.log.Timber

class SanitizingLogTree : Timber.DebugTree() {

    private val urlRegex = Regex("""https?://[^\s]+""", RegexOption.IGNORE_CASE)

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        // Redact any accidentally passed URLs or user content from Logcat
        val sanitizedMessage = message.replace(urlRegex, "[REDACTED_URL]")
        super.log(priority, tag, sanitizedMessage, t)
    }
}
```

---

## 9. Comprehensive End-to-End Testing Strategy

### 9.1 Unit Test Coverage Matrix (`:core:domain`, `:core:data`)

| Test Suite | Target Component | Verifications |
| :--- | :--- | :--- |
| `UrlNormalizerTest` | `UrlNormalizer.kt` | Tracking param stripping (`utm_*`, `gclid`, `fbclid`), host lowercasing, IDN Punycode, non-allowlisted param retention, port stripping. |
| `BudgetEngineTest` | `BudgetEngine.kt` | Arithmetic validation of 0.25pt archive cost, overage state transitions, expiration lock, incremental action costs. |
| `RecommendationEngineTest` | `RecommendationEngine.kt` | Numerical scoring formula accuracy, subgroup diversity -0.15 penalty, 24h deadline override, skip threshold reconsideration flag. |
| `NotificationSchedulerTest` | `NotificationScheduler.kt` | WorkManager vs AlarmManager scheduling logic, cancellation on resolve/archive. |
| `RoomMigrationTest` | `LinkLedgerDatabase` | Migration test helper executing version upgrades cleanly. |

### 9.2 Verification Commands

```bash
# Run all domain & unit tests
./gradlew testDebugUnitTest

# Run specific URL Normalizer & Budget tests
./gradlew :core:domain:test --tests "com.mindfullinking.core.domain.url.UrlNormalizerTest"
./gradlew :core:domain:test --tests "com.mindfullinking.core.domain.budget.BudgetEngineTest"
./gradlew :core:domain:test --tests "com.mindfullinking.core.domain.recommendation.RecommendationEngineTest"

# Run Android instrumentation & Room DB tests
./gradlew :core:data:connectedAndroidTest

# Execute Android Lint & Static Security Audit
./gradlew lintRelease
```

---

## 10. Step-by-Step Phased Implementation Roadmap

```mermaid
gantt
    title Phased Delivery Roadmap
    dateFormat  YYYY-MM-DD
    section Phase 1: Core Engine
    Data Entities & DAOs       :p1_1, 2026-08-10, 3d
    Budget & Normalizer Engine :p1_2, after p1_1, 4d
    section Phase 2: Intake & Rules
    Sharesheet Receiver        :p2_1, after p1_2, 3d
    Duplicate Resolution UI    :p2_2, after p2_1, 3d
    section Phase 3: Recommendation
    Scoring & Diversity Math   :p3_1, after p2_2, 4d
    Now Screen & Carousel      :p3_2, after p3_1, 4d
    section Phase 4: Lifecycle
    WorkManager & AlarmManager :p4_1, after p3_2, 4d
    Boot & TimeZone Receivers  :p4_2, after p4_1, 2d
    section Phase 5: UI & Polish
    Inbox Hierarchy & Archive  :p5_1, after p4_2, 5d
    Settings, Export & Security:p5_2, after p5_1, 4d
```

### Phase 1: Core Domain Engine & Data Architecture
- **Deliverables:** `:core:model`, `:core:domain`, `:core:data` modules set up. Room database, DAOs, `UrlNormalizer`, `BudgetEngine`.
- **Verification Command:** `./gradlew :core:domain:test :core:data:test`

### Phase 2: URL Normalization, Duplicate Detection & Sharesheet Intake
- **Deliverables:** `:core:share` module. Android Sharesheet `ACTION_SEND` intent receiver, duplicate resolution dialogs, manual entry sheet.
- **Verification Command:** `./gradlew :core:share:connectedCheck`

### Phase 3: Point Budget & Recommendation Engine
- **Deliverables:** `:core:domain` recommendation scoring math, subgroup diversity penalty, explanation generator. `:feature:now` UI tab with time picker and recommendation cards.
- **Verification Command:** `./gradlew :feature:now:test`

### Phase 4: Deadline & Notification Lifecycle System
- **Deliverables:** `:core:notifications` module. `DeadlineWorker`, `DeadlineAlarmReceiver`, `SystemEventReceiver`, exact alarm permission handling.
- **Verification Command:** `./gradlew :core:notifications:test`

### Phase 5: Inbox, Archive, Settings & Accessibility Polish
- **Deliverables:** `:feature:inbox`, `:feature:archive`, `:feature:settings`. Priority/deadline grouping list, search, 0.25pt archive controls, export/purge features, TalkBack accessibility audit.
- **Verification Command:** `./gradlew connectedAndroidTest`
