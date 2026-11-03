# Comprehensive Audit & Critique Report: Link Ledger Implementation Plan

**Target Application:** Link Ledger ("MindfulLinking")  
**Evaluated Document:** `draft_implementation_plan.md`  
**Reference Specification:** `prd_content.txt` (Product Requirements Document)  
**Evaluator:** Principal Software Quality, Security, and Android Architecture Critic  
**Date:** August 7, 2026  

---

## 1. Executive Summary & Audit Overview

This critique provides a rigorous architectural and requirements audit of `draft_implementation_plan.md` against the 17 sections of `prd_content.txt`.

While the draft implementation plan provides a strong foundational multi-module architecture, clean Room entities, and clear domain separation, **critical gaps, algorithmic bugs, state transition oversights, floating-point precision risks, and Android background lifecycle flaws** must be resolved before proceeding to production implementation.

### Severity Summary Table

| Severity Level | Count | Primary Impact Areas |
| :--- | :---: | :--- |
| **HIGH** | **6** | Algorithmic Hard Constraints, Permission & Exact Alarm Crashes, Overage Expiry Worker, URL Parameter Corruption, Budget Arithmetic Rounding, Inbox Hierarchy Spec |
| **MEDIUM** | **6** | Shortest Link Recommendation Fallback, Duplicate Equivalence Branching, Date-Only Timezone Anchoring, Explanation Template Logic, Room Schema Converters & Indexes, DataStore Preferences Schema |
| **LOW** | **4** | Reconsideration Card Interaction Disabling, 120+ Duration Mapping, Log Sanitization Scope, UI Accessibility & Security Integration Tests |

---

## 2. Full PRD Requirement Coverage Matrix

The following matrix evaluates the draft implementation plan against all 17 sections of `prd_content.txt`:

| PRD Section | Status | Audit Findings & Gaps |
| :--- | :---: | :--- |
| **1. Product Summary** | **Pass** | Core concepts (100-point budget, 3 recommendations, local-first) accurately reflected. |
| **2. Goals** | **Pass** | Offline-first, share sheet intake, and zero-cloud goals captured. |
| **3. Non-goals for v1** | **Pass** | No cloud sync, analytics SDKs, or accounts present in architecture. |
| **4. Core Terminology** | **Pass** | Active, Archived (0.25pt), Resolved, Budget, Priority, Snooze defined. |
| **5. Assumptions** | **Partial** | Duration option `120+` is not explicitly mapped to `120` in scoring engine; `POST_NOTIFICATIONS` permission flow for Android 13+ is missing. |
| **6. Point-budget Economics** | **Deficient** | Budget engine uses `Double` (IEEE 754 precision issues); temporary overage choices (24h, 7d, 30d) and 24h pre-expiry notification worker are missing. |
| **7. User Flows (7.1 - 7.6)** | **Deficient** | Duplicate resolution action branching is incomplete; Inbox 2-level grouping/sorting is missing ViewModel code; Recommendation fallback for "No links fit" is omitted. |
| **8. Deadline & Notification**| **Deficient** | Date-only default to 23:59 local time is unhandled; exact alarm fallback on permission denial missing; notification scheduling lacks `POST_NOTIFICATIONS` guard. |
| **9. Recommendation Algorithm**| **Deficient** | Hard constraint for consecutive session exclusion (PRD 9.5) is degraded to a soft penalty; explanation tie-breakers & template strings deviate from PRD 9.6. |
| **10. Privacy & Architecture** | **Pass** | Local-first sandbox, backup exclusion XMLs, and logging tree specified. |
| **11. Data Model** | **Partial** | Missing Room `@TypeConverter` for JSON string arrays; missing index on `(status, snoozed_until)`. |
| **12. System Architecture** | **Pass** | 6 multi-module boundaries (`:core:*` and `:feature:*`) strictly decoupled. |
| **13. Edge Cases** | **Partial** | Multiple URL picker mentioned in diagram but missing implementation details; missing destination app fallback chooser unspecified. |
| **14. Privacy Analytics** | **Partial** | Aggregate local analytics mentioned, but DataStore schema and storage rules are absent. |
| **15. Acceptance Criteria** | **Pass** | Operational criteria covered across architecture modules. |
| **16. Testing Requirements** | **Deficient** | Unit tests lack timezone/DST coverage; UI accessibility (TalkBack) and security tests lack concrete Compose test code. |
| **17. Launch Scope** | **Pass** | Launch scope alignment verified. |

---

## 3. High-Severity Issues & Architectural Risks (HIGH)

### [H-1] Recommendation Exposure Filter Bug vs PRD 9.5 Hard Constraint
* **PRD Requirement (Section 9.5):**  
  > "Do not show the same link in consecutive sessions unless:  
  > 1. It is due within 24 hours; or  
  > 2. Fewer than three eligible links exist."
* **Draft Plan Flaw (Section 5.2 & 5.3):**  
  The draft plan treats recent exposure solely as a soft numerical penalty in the scoring formula:  
  $$\text{exposure\_penalty} = \begin{cases} 1.00 & \text{if shown in immediately previous session} \\ 0.50 & \text{if shown two sessions ago} \\ 0.00 & \text{otherwise} \end{cases}$$  
  $$\text{base\_score} = \dots - 0.20 \times \text{exposure\_penalty}$$
* **Impact:**  
  A high-priority link (Priority 5, urgent deadline) that was shown in the immediately preceding session can easily overcome a `-0.20` penalty ($0.20 \times 1.00 = 0.20$ deduction) and be selected AGAIN in the next consecutive session, even when 10 other eligible links exist! This directly violates the hard constraint in PRD 9.5.
* **Concrete Recommendation:**  
  Update `RecommendationEngine.kt` to enforce a **hard eligibility filter** BEFORE scoring:
  ```kotlin
  fun filterEligibleLinks(
      candidates: List<Link>,
      lastSessionLinkIds: Set<String>,
      now: Instant
  ): List<Link> {
      val isEligibleByDefault = candidates.filter { link ->
          val isDueWithin24h = link.deadlineAt != null && 
              Duration.between(now, link.deadlineAt).toHours() <= 24
          !lastSessionLinkIds.contains(link.id) || isDueWithin24h
      }
      
      // Fallback: If filtered list has fewer than 3 links, bypass exposure filter
      return if (isEligibleByDefault.size < 3) candidates else isEligibleByDefault
  }
  ```

---

### [H-2] Android 13+ `POST_NOTIFICATIONS` & Android 12+ Exact Alarm Fallback Missing
* **PRD Requirement (Section 5 & 8):**  
  Request `POST_NOTIFICATIONS` only when deadline reminders are enabled. Notification permission denial must NOT delete deadline data. System exact alarms must handle OS permission state safely.
* **Draft Plan Flaw (Section 6.2):**  
  `NotificationScheduler.kt` directly calls `alarmManager.setExactAndAllowWhileIdle()` and enqueues WorkManager tasks without checking:
  1. `ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)` on API 33+.
  2. Fallback when `alarmManager.canScheduleExactAlarms()` returns `false` on API 31+.
* **Impact:**  
  - If a user enables notifications on Android 13+ and permission is not granted, posting a notification from `DeadlineWorker` or `DeadlineAlarmReceiver` will throw a runtime `SecurityException` or fail silently.
  - If `canScheduleExactAlarms()` is `false`, the draft code skips scheduling the exact alarm entirely, leaving the user with **no notification at the actual deadline moment**.
* **Concrete Recommendation:**  
  1. Add runtime permission verification prior to scheduling notifications.
  2. Implement a fallback for exact alarms using WorkManager with `setInitialDelay`:
  ```kotlin
  if (alarmManager.canScheduleExactAlarms()) {
      alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, deadline.toEpochMilli(), pendingIntent)
  } else {
      // Fallback: Schedule non-exact WorkManager task anchored to deadline moment
      val delayMs = Duration.between(Instant.now(), deadline).toMillis().coerceAtLeast(0)
      val fallbackWork = OneTimeWorkRequestBuilder<DeadlineWorker>()
          .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
          .setInputData(workData)
          .addTag("deadline_exact_fallback_${link.id}")
          .build()
      workManager.enqueue(fallbackWork)
  }
  ```

---

### [H-3] Overage Expiry 24-Hour Pre-Expiry Notification Worker Missing
* **PRD Requirement (Section 6.3 & Section 8):**  
  > "The app sends one reminder before overage expiry." (specifically 24 hours prior to `temporary_overage_expires_at`).
* **Draft Plan Flaw (Section 6.2):**  
  The draft plan includes `BudgetStateEntity` fields (`temporary_overage_expires_at`, `last_overage_reminder_at`), but `NotificationScheduler.kt` contains **zero code** to schedule, cancel, or handle the 24-hour overage expiry reminder worker!
* **Impact:**  
  Users who activate a temporary point budget overage will never receive the mandatory 24-hour warning before their budget expires and locks active link creation.
* **Concrete Recommendation:**  
  Add `scheduleOverageExpiryReminder(budgetState: BudgetState)` to `NotificationScheduler.kt`:
  ```kotlin
  fun scheduleOverageExpiryReminder(budgetState: BudgetState) {
      workManager.cancelAllWorkByTag("overage_expiry_reminder")
      val expiresAt = budgetState.temporaryOverageExpiresAt ?: return
      val reminderTime = expiresAt.minus(Duration.ofHours(24))
      val delayMs = Duration.between(Instant.now(), reminderTime).toMillis()

      if (delayMs > 0) {
          val workRequest = OneTimeWorkRequestBuilder<OverageExpiryWorker>()
              .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
              .addTag("overage_expiry_reminder")
              .build()
          workManager.enqueueUniqueWork("overage_expiry", ExistingWorkPolicy.REPLACE, workRequest)
      }
  }
  ```

---

### [H-4] `UrlNormalizer.kt` Query Parameter Mutation & Unhandled Exception Crashing
* **PRD Requirement (Section 7.2):**  
  > "Preserve... All query parameters not in the removal allowlist... Query parameters not on the exact allowlist remain untouched... Reject URLs containing user credentials."
* **Draft Plan Flaw (Section 3.2):**  
  In `UrlNormalizer.kt`:
  1. `URI.create(trimmed)` is called directly on raw input strings. If `trimmed` contains unencoded non-ASCII characters in the path (e.g. `https://example.com/münchen/page`), `URI.create()` throws an unchecked `IllegalArgumentException` **before** reaching `IDN.toASCII()`.
  2. Query string parsing:
     ```kotlin
     val parts = pair.split("=", limit = 2)
     val key = parts[0]
     val value = if (parts.size > 1) parts[1] else ""
     // ...
     if (v.isEmpty()) k else "$k=$v"
     ```
     If a raw query parameter has no equals sign (e.g. `?ref`), or has an empty value (`?ref=`), this logic converts `?ref=` into `?ref`. This alters non-allowlisted query parameters, breaking destination site behavior.
* **Impact:**  
  Malformed or non-ASCII URLs shared from Android apps crash the Share Intake flow, and valid destination parameters (such as OAuth states or web flags) are mutated.
* **Concrete Recommendation:**  
  Refactor `UrlNormalizer.kt` to use Android's `Uri.parse()` or Java's `URLDecoder` safely with regex-based query pair preservation:
  ```kotlin
  fun normalize(rawUrl: String): String {
      val trimmed = rawUrl.trim()
      // Use Android Uri parsing or encode invalid characters before URI creation
      val androidUri = android.net.Uri.parse(trimmed)
      require(androidUri.scheme?.lowercase(Locale.ROOT) in setOf("http", "https")) { "Invalid scheme" }
      require(androidUri.userInfo == null) { "Embedded credentials forbidden" }

      val host = androidUri.host?.lowercase(Locale.ROOT) ?: throw IllegalArgumentException("Missing host")
      val asciiHost = IDN.toASCII(host)
      val port = androidUri.port
      val defaultPort = (androidUri.scheme == "http" && port == 80) || (androidUri.scheme == "https" && port == 443)
      val portStr = if (port != -1 && !defaultPort) ":$port" else ""

      // Preserve raw query parameters strictly unless key matches allowlist
      val queryStr = androidUri.queryParameterNames.let { names ->
          val preserved = names.filter { !TRACKING_PARAMS.contains(it.lowercase(Locale.ROOT)) }
          if (preserved.isEmpty()) ""
          else "?" + androidUri.encodedQuery?.split("&")?.filter { pair ->
              val key = pair.split("=")[0]
              !TRACKING_PARAMS.contains(URLDecoder.decode(key, "UTF-8").lowercase(Locale.ROOT))
          }?.joinToString("&")
      } ?: ""

      val path = androidUri.encodedPath ?: ""
      val fragment = androidUri.encodedFragment?.let { "#$it" } ?: ""

      return "${androidUri.scheme?.lowercase(Locale.ROOT)}://$asciiHost$portStr$path$queryStr$fragment"
  }
  ```

---

### [H-5] Floating-Point Precision Inaccuracies in Point Budget Engine
* **PRD Requirement (Section 6.1):**  
  `used_points = sum(priority active) + (0.25 * archived_link_count)`  
  Standard budget = `100.00`.
* **Draft Plan Flaw (Section 2.2 & 4.3):**  
  The draft plan uses primitive IEEE 754 floating-point `Double` in `BudgetStateEntity` (`standardBudget: Double = 100.00`) and throughout `BudgetEngine.kt`.
* **Impact:**  
  Binary floating-point calculations accumulate representation errors. For example:
  - $3 \times 0.25 = 0.75$, but $99.50 - 0.25$ can yield `99.25000000000001`.
  - Evaluating `totalUsedPoints > effectiveBudget` when `totalUsedPoints` evaluates to `100.00000000000001` causes the app to falsely block saving a link and trigger an invalid over-budget modal!
* **Concrete Recommendation:**  
  Use scaled integer arithmetic (e.g. `Int` storing point values multiplied by 100, where $1 \text{ point} = 100$, $0.25 \text{ points} = 25$) inside `BudgetEngine` and database queries, or apply explicit rounding to 2 decimal places using `BigDecimal`:
  ```kotlin
  fun Double.roundToTwoDecimals(): Double = 
      BigDecimal(this).setScale(2, RoundingMode.HALF_UP).toDouble()
  ```

---

### [H-6] Inbox 2-Level Grouping & 3-Tier Sub-Sorting Missing Implementation
* **PRD Requirement (Section 7.4):**  
  - Level 1: Priority (5 down to 1).
  - Level 2: Deadline subgroup (Overdue, Due today, Due within 3d, Due within 7d, Due later, No deadline).
  - Sub-sorting within subgroup:  
    1. Earliest deadline (`deadline_at ASC`)  
    2. Oldest saved date (`saved_at ASC`)  
    3. Most recent edit date (`updated_at DESC`)
* **Draft Plan Flaw (Section 2.3 & 7.1):**  
  `LinkDao.kt` provides only a basic flat query:  
  `SELECT * FROM links WHERE status = 'ACTIVE' ORDER BY priority DESC, saved_at ASC`  
  The plan completely omits the domain/ViewModel grouping algorithm required to construct the nested 2-level hierarchy and apply the 3-tier tie-breaking sorting.
* **Concrete Recommendation:**  
  Add explicit domain transformation logic in `:core:domain`:
  ```kotlin
  enum class DeadlineSubgroup { OVERDUE, DUE_TODAY, DUE_WITHIN_3D, DUE_WITHIN_7D, DUE_LATER, NO_DEADLINE }

  data class InboxPriorityGroup(
      val priority: Int,
      val subgroups: Map<DeadlineSubgroup, List<Link>>
  )

  fun List<Link>.toInboxHierarchy(now: Instant): List<InboxPriorityGroup> {
      return this.groupBy { it.priority }
          .toSortedMap(Comparator.reverseOrder())
          .map { (priority, linksInPriority) ->
              val groupedSubgroups = linksInPriority.groupBy { it.determineDeadlineSubgroup(now) }
                  .mapValues { (_, subgroupLinks) ->
                      subgroupLinks.sortedWith(
                          compareBy<Link> { it.deadlineAt ?: Instant.MAX }
                              .thenBy { it.savedAt }
                              .thenByDescending { it.updatedAt }
                      )
                  }
              InboxPriorityGroup(priority, groupedSubgroups)
          }
  }
  ```

---

## 4. Medium-Severity Issues & Technical Flaws (MEDIUM)

### [M-1] Shortest Link Recommendation Fallback Missing
* **PRD Requirement (Section 7.5 line 255):**  
  If no active links fit the user's selected time window, the app must:
  1. Surface the shortest active links.
  2. Label them as "Exceeding available time".
  3. Provide a direct navigation path to the Inbox.
* **Draft Plan Gap (Section 7.1):**  
  The plan describes the standard recommendation carousel but omits the fallback state when all active links exceed `available_minutes`.
* **Recommendation:**  
  Update `RecommendationEngine.kt` to include fallback candidate retrieval when eligible list is empty:
  ```kotlin
  if (eligibleLinks.isEmpty()) {
      val fallbackLinks = activeLinks.sortedBy { it.estimatedDurationMinutes }.take(3)
      return RecommendationResult.NoFitFound(shortestLinks = fallbackLinks)
  }
  ```

---

### [M-2] Duplicate Link Equivalence Action Branching Missing in DAO & ViewModel
* **PRD Requirement (Section 7.3):**  
  - Active duplicate: Offer Open, Update existing (DEFAULT), Save another copy, Cancel.
  - Archived duplicate: Offer Open, Restore to active, Save another copy, Cancel.
* **Draft Plan Gap (Section 2.3 & 3.2):**  
  `LinkDao.kt` uses `@Query("SELECT * FROM links WHERE canonical_url = :canonicalUrl LIMIT 1")`. If multiple copies already exist (via "Save another copy"), `LIMIT 1` returns an arbitrary record.
* **Recommendation:**  
  Update DAO query to `ORDER BY saved_at DESC LIMIT 1` and implement explicit state handling for active vs archived duplicate UI dialogs.

---

### [M-3] Date-Only Deadline 23:59 Local Timezone Default Conversion Unhandled
* **PRD Requirement (Section 8 line 288 & 311):**  
  Date-only deadlines default to 23:59 in the device's current timezone. Store deadline instants in UTC; render in current local timezone.
* **Draft Plan Gap (Section 2.2):**  
  `LinkEntity` stores `deadlineAtEpochMs: Long?` and `deadlineHasTime: Boolean`. The plan does not specify how date-only inputs (e.g. `2026-08-15`) are converted to epoch milliseconds using the user's local `ZoneId`.
* **Recommendation:**  
  Provide an explicit Instant converter utility:
  ```kotlin
  fun LocalDate.toDateOnlyDeadlineInstant(zoneId: ZoneId = ZoneId.systemDefault()): Instant {
      return this.atTime(LocalTime.of(23, 59, 59)).atZone(zoneId).toInstant()
  }
  ```

---

### [M-4] Explanation Template Tie-Breakers & String Wording Discrepancies
* **PRD Requirement (Section 9.6):**  
  Deterministic explanation template based on strongest positive factor using tie order:  
  1. Deadline urgency  
  2. Priority  
  3. Duration fit  
  4. Age  
  5. Neglect/review status  
  6. Group diversity  
* **Draft Plan Flaw (Section 5.4):**  
  The draft plan table hardcodes conditions like `priority == 5` and alters PRD template output strings (e.g. `"Deadline is approaching soon"` instead of `"Deadline is tomorrow"`, `"Selected to diversify your current queue"` instead of `"Chosen to vary your current queue"`).
* **Recommendation:**  
  Align template strings and tie-breaker threshold evaluation strictly with PRD Section 9.6.

---

### [M-5] Room Database `@TypeConverter` & Composite Index Missing
* **PRD Requirement (Section 11):**  
  Room entities represent enums, timestamps, and JSON collections safely.
* **Draft Plan Flaw (Section 2.2 & 2.3):**  
  1. `RecommendationSessionEntity.recommendedLinkIdsJson` stores a JSON string without defining a Room `@TypeConverter`.
  2. `LinkEntity` lacks a composite index on `(status, snoozed_until)`, causing full table scans when `getEligibleLinksForRecommendation` is executed.
* **Recommendation:**  
  1. Add `@Index(value = ["status", "snoozed_until"])` to `LinkEntity`.
  2. Provide `Converters.kt` for `List<String>` / JSON conversions using `kotlinx.serialization`.

---

### [M-6] DataStore Preferences Schema for Opt-In Settings Unspecified
* **PRD Requirement (Section 10 & 14):**  
  Metadata fetching (disabled by default) and privacy-preserving aggregate analytics (opt-in) require local preferences storage.
* **Draft Plan Gap (Section 2):**  
  The draft plan mentions DataStore Preferences in architectural diagrams but provides **no DataStore schema**, key definitions, or repository code.
* **Recommendation:**  
  Define `UserPreferencesRepository` with `PreferencesKeys` (`METADATA_FETCH_ENABLED`, `ANALYTICS_OPT_IN`, `DEFAULT_OVERAGE_DURATION`).

---

## 5. Low-Severity Issues & Technical Polish (LOW)

### [L-1] Reconsideration Link Card "Skip" Action Disabling
* **PRD Requirement (Section 9.5):** At 5 skips, mark link `Reconsider`. When surfaced, require one action: Keep, Lower priority, Snooze, Archive, Delete.
* **Draft Gap:** Draft UI does not explicitly state that the standard "Skip" CTA button must be **hidden or disabled** on reconsidered cards to force user decision.

### [L-2] `120+` Minute Duration Representation
* **PRD Requirement (Section 5):** `120+` is treated as 120 minutes for calculations until edited.
* **Draft Gap:** `LinkEntity.estimatedDurationMinutes` stores `Int`. The domain model needs a helper to format `120` as `"120+ min"` in UI components.

### [L-3] Log Sanitizer Regex Scope
* **Draft Plan (Section 8.2):** `SanitizingLogTree.kt` replaces HTTP/HTTPS URLs with `[REDACTED_URL]`.
* **Improvement:** Extend regex to scrub shared titles, user notes, and query parameters from raw log strings.

### [L-4] UI Accessibility & Security Test Code
* **PRD Requirement (Section 16):** Test TalkBack labels, minimum touch targets (48dp), screen rotation, dark mode, and backup exclusion rules.
* **Draft Gap:** Section 9 provides build command line triggers but omits concrete `ComposeTestRule` snippets for accessibility and security verification.

---

## 6. Mathematical & Algorithmic Rigor Analysis

### Recommendation Base Score Formula Verification

The PRD base score equation is:
$$\text{base\_score} = 0.40 \cdot S_{\text{priority}} + 0.22 \cdot S_{\text{deadline}} + 0.18 \cdot S_{\text{duration}} + 0.12 \cdot S_{\text{age}} + 0.08 \cdot S_{\text{skip}} - 0.20 \cdot P_{\text{exposure}}$$

#### Boundary Value & Discretization Specification:

1. **Priority Score ($S_{\text{priority}}$):**
   $$S_{\text{priority}} = \frac{\text{priority}}{5.0}, \quad \text{where } \text{priority} \in \{1, 2, 3, 4, 5\}$$

2. **Deadline Urgency Score ($S_{\text{deadline}}$):**
   Let $\Delta t_{\text{hours}}$ be the hours remaining until `deadline_at`:
   $$S_{\text{deadline}} = \begin{cases} 
   1.00 & \text{if } \Delta t_{\text{hours}} \le 24 \text{ or overdue} \\
   0.80 & \text{if } 24 < \Delta t_{\text{hours}} \le 72 \quad (1 \text{--} 3 \text{ days}) \\
   0.50 & \text{if } 72 < \Delta t_{\text{hours}} \le 168 \quad (4 \text{--} 7 \text{ days}) \\
   0.25 & \text{if } 168 < \Delta t_{\text{hours}} \le 336 \quad (8 \text{--} 14 \text{ days}) \\
   0.10 & \text{if } \Delta t_{\text{hours}} > 336 \quad (> 14 \text{ days}) \\
   0.00 & \text{if } \text{deadline\_at} = \text{null}
   \end{cases}$$

3. **Duration Fit Score ($S_{\text{duration}}$):**
   $$S_{\text{duration}} = \max\left(0.0, 1.0 - \frac{|\text{available\_minutes} - \text{duration}|}{\max(\text{available\_minutes}, 1)}\right)$$

4. **Age Score ($S_{\text{age}}$):**
   Let $t_{\text{days}} = \frac{\text{Duration.between(saved\_at, now).toSeconds()}}{86400.0}$:
   $$S_{\text{age}} = \min\left(\frac{t_{\text{days}}}{30.0}, 1.0\right)$$

5. **Skip Adjustment ($S_{\text{skip}}$):**
   $$S_{\text{skip}} = \begin{cases}
   0.00 & \text{if } \text{skip\_count} = 0 \\
   +0.05 & \text{if } \text{skip\_count} \in \{1, 2\} \\
   +0.10 & \text{if } \text{skip\_count} \in \{3, 4\} \\
   -0.20 & \text{if } \text{skip\_count} \ge 5 \quad (\text{and flag as RECONSIDER})
   \end{cases}$$

---

## 7. Action Plan & Revision Guidelines for the Planner

To elevate `draft_implementation_plan.md` to production readiness, the lead planner must execute the following revisions:

1. **Algorithmic Hard Constraints:** Refactor Section 5 to implement hard eligibility filtering for consecutive sessions before calculating numerical base scores (resolving [H-1]).
2. **Notification & Permission Lifecycles:** Update `NotificationScheduler.kt` in Section 6 to check `POST_NOTIFICATIONS` permission, schedule exact alarm fallbacks via WorkManager, and register the 24-hour overage expiry reminder worker (resolving [H-2] and [H-3]).
3. **URL Normalizer Robustness:** Replace raw `URI.create()` calls in `UrlNormalizer.kt` with Android `Uri` parsing, protect non-ASCII hostnames, and preserve raw parameter formatting for non-allowlisted query flags (resolving [H-4]).
4. **Point Budget Precision:** Replace `Double` budget accounting with `BigDecimal` rounded to 2 decimal places or scaled integer arithmetic (resolving [H-5]).
5. **Inbox Hierarchy Code:** Add explicit domain transformation code for 2-level Inbox grouping and 3-tier sub-sorting in Section 7 (resolving [H-6]).
6. **Room Schema & DataStore Completeness:** Add Room `@TypeConverter` for JSON lists, add composite index `(status, snoozed_until)` on `LinkEntity`, and specify the DataStore Preferences schema for privacy settings (resolving [M-5] and [M-6]).
7. **UX Fallbacks & Template Alignment:** Add the "No links fit" shortest link recommendation fallback state and align explanation template wording strictly with PRD 9.6 (resolving [M-1] and [M-4]).
8. **Testing Enhancements:** Add concrete Compose test rules for accessibility (TalkBack), rotation, and security log sanitization (resolving [L-4]).

---
*End of Critique Report.*
