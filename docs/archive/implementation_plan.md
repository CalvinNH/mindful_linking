# Implementation Plan: Link Ledger UX Refinements & ML-Auction Recommendation Flow

This implementation plan outlines the structural, UX, and domain refinements requested for **Link Ledger** ("MindfulLinking"). The plan consolidates the main application screens, introduces an animated ML-Auction recommendation popup, eliminates unused fields (Intent categories), removes non-user-facing routes (Design System Gallery), cleans up Settings, and adds new duration choices (1-minute and 3-minute).

---

## User Review Required

> [!IMPORTANT]
> **Key Refinements Covered in this Plan**:
> 1. **Unified Main Screen (Now + Inbox)**: Replaces separate Now and Inbox tabs with a single unified screen. A hero top card displays the 100-point budget gauge and the "I have time available now" action card, while a scrollable lower section displays the 2-level Priority/Deadline Inbox hierarchy.
> 2. **Animated ML-Auction Recommendation Dialog**: Clicking "I have time available now" opens an interactive modal dialog allowing the user to select time duration (including new **1-minute** and **3-minute** options). Tapping **"Run Auction"** triggers a dynamic ~2-second link-shuffling animation simulating a background ML candidate analysis before revealing the top 3 recommendations.
> 3. **Complete Removal of Intent Categories**: Intent selection (READ, WATCH, ACT, REFERENCE) is removed from link intake, recommendation filters, domain models, and UI.
> 4. **Settings & Navigation Cleanup**: Removes the Design System Gallery route entirely from user navigation and removes the Automatic Metadata Fetching card from Settings.
> 5. **Bottom Navigation**: Streamlined to 3 tabs: **Ledger** (Unified Now + Inbox), **Archive**, and **Settings**.

---

## Proposed Changes

### Component & Module Updates

#### 1. Data Models & Domain Logic (`:core:model`, `:core:domain`)

##### [MODIFY] `Link.kt` & `Enums.kt`
- Remove `intent` parameters from link creation and recommendation request signatures.
- Add `1` and `3` to valid estimated duration options list (`1, 3, 5, 10, 15, 20, 30, 45, 60, 90, 120`).

##### [MODIFY] `RecommendationEngine.kt`
- Remove intent category filter from eligibility evaluation.
- Support 1-minute and 3-minute duration values in `duration_fit_score` calculations ($S_{\text{duration}}$).

---

#### 2. Design System & Feature UI (`:core:ui`, `:feature:now`, `:feature:inbox`, `:feature:share`)

##### [NEW] `RecommendationAuctionDialog.kt`
A popup modal card triggered by the "I have time available now" button:
- **Phase 1 (Time Selection)**: Header *"Choose the amount of time you currently have available"* with single-select duration chips: **1 min**, **3 min**, 5 min, 10 min, 15 min, 20 min, 30 min, 45 min, 60 min, 90 min, 120+ min.
- **Phase 2 (ML Auction Action)**: **"Run Auction"** CTA button.
- **Phase 3 (ML Simulation Animation)**: Upon tapping "Run Auction", renders a 2-second vertical link-shuffling ticker animation displaying candidate titles flowing up/down with a charcoal/lime progress beam, giving an impression of real-time background ML analysis.
- **Phase 4 (Results Presentation)**: Displays the top 3 recommended link cards with PRD 9.6 explanation badges, Open CTA, Resolve, Snooze, and Archive actions. Disables "Skip" on 5-skip reconsidered cards. Includes "No links fit" shortest link fallback state.

##### [NEW] `MainLedgerScreen.kt`
Unified main view combining Now hero card and Inbox list:
- **Top Sticky / Hero Card**: 100-Point Budget Gauge (`BudgetGauge.kt`), overage status banner, and the *"I have time available now"* button card.
- **Scrollable Lower Section**: 2-Level Priority ($5 \rightarrow 1$) $\rightarrow$ 6 Deadline Subgroups Inbox hierarchy with quick action CTAs.

##### [MODIFY] `ShareIntakeActivity.kt`
- Remove Intent selector dropdown from intake form.
- Add 1-min and 3-min options to estimated duration selector.

##### [MODIFY] `SettingsScreen.kt`
- Remove the Automatic Metadata Fetching settings card.
- Retain Privacy Analytics Opt-In summary, Local Encrypted Data Export, and Permanent Data Wipe controls.

---

#### 3. Navigation & App Shell (`:app`)

##### [MODIFY] `MainActivity.kt`
- Remove `DesignSystemGallery` route and bottom bar tab.
- Update bottom navigation bar to 3 items:
  1. **Ledger** (`MainLedgerScreen` - Unified Now + Inbox)
  2. **Archive** (`ArchiveScreen`)
  3. **Settings** (`SettingsScreen`)

---

## Verification Plan

### Automated Unit Tests
Run domain unit tests to verify 1-min and 3-min recommendation calculations and intent-free candidate ranking:
```bash
./gradlew :core:domain:test
```

### Manual & UI Verification
1. **Unified Screen**: Launch the app and verify that the Now budget card is at the top of the main screen with the scrollable Inbox list directly underneath.
2. **ML Auction Popup**: Tap "I have time available now", select **1 min** or **3 min**, tap **Run Auction**, and verify the ~2-second link-shuffling animation before the top 3 recommendations appear.
3. **No Intent Field**: Share a URL to the app and confirm no Intent dropdown is displayed. Confirm no Intent filters exist in the recommendation pop-up.
4. **Settings Cleanup**: Open Settings and confirm Automatic Metadata Fetching card is removed.
5. **No DS Gallery**: Confirm Design System Gallery is removed from navigation.
6. **Full Build**: Execute `./gradlew assembleDebug` to verify clean compilation across all modules.

---
*End of Implementation Plan.*
