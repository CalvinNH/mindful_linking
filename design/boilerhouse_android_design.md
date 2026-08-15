# Bauhaus Android Design Language: Analysis and Recommendation

**Prepared by Manus AI**

## Executive recommendation

The reference image should be treated as a **contemporary Bauhaus-inspired product language**, not as a literal desktop layout to reproduce on a phone. Its identity comes from a disciplined combination of a charcoal structural frame, pale-lime environment, vivid lime action color, lavender data emphasis, white content cards, large geometric radii, sparse line icons, and a tightly controlled dashboard grid.

The BoilerHAUS repository is valuable as a **design-system governance reference** and as a source of web component patterns. It is not a suitable Android runtime dependency. The repository is implemented as a React/TypeScript/Vite/Tailwind/Storybook library with Radix React primitives and CSS tokens [1] [2] [3]. Your native Android app should therefore create a new `:core:ui` Jetpack Compose design system, manually translating the useful token structure and component contracts while preserving the visual palette from the supplied image.

The recommended strategy is to use **Material 3 as an implementation substrate, not as the visual identity**. Android officially supports extending MaterialTheme, replacing selected Material systems, or implementing a fully custom Compose theme [4]. For this project, the middle path is the safest: retain Material 3 behavior, accessibility semantics, and platform integration, but wrap components in Flux/Bauhaus-specific tokens, shapes, typography, and state styling.

## 1. Visual language extracted from the reference

The image uses an editorial dashboard composition. A near-black shell provides the structural frame, while the main workspace is a light-gray field containing white and charcoal cards. The pale-lime outer background is deliberately oversized and visible around the shell, making the product feel like a physical object or poster placed on a colored surface. This is an important brand behavior: the lime is not merely an accent; it establishes the environment.

The interface is geometric but not severe. Cards have generous rounded corners, pills are fully rounded, and icons are thin outlined glyphs. The system combines **hard-edged Bauhaus contrast**—charcoal against lime, white against charcoal—with **soft contemporary product geometry**—capsules, large radii, generous whitespace, and circular data marks. The result is playful and premium rather than industrial or utilitarian.

### Measured palette

The following values were extracted from the supplied 2048 × 1536 raster using deterministic color quantization and regional sampling. Anti-aliasing, shadows, image compression, and local gradients mean that these should be treated as the closest production tokens, not as proof of the original source-file values.

| Semantic role | Measured color | Recommended Android token | Intended use |
|---|---:|---|---|
| Structural charcoal | `#222124` | `FluxPalette.Void` | App shell, dark cards, primary text on light surfaces |
| Paper white | `#FFFFFF` | `FluxPalette.Paper` | Primary cards, selected controls, high-emphasis surfaces |
| Workspace gray | `#E1E1E1` | `FluxPalette.Surface` | Main content background and neutral containers |
| Environmental pale lime | `#DCEA95` | `FluxPalette.CanvasLime` | Root background, onboarding or branded empty states |
| Action lime | `#DAF163` | `FluxPalette.Signal` | Primary CTA, selected state, progress, status emphasis |
| Data lavender | `#BBB2F5` | `FluxPalette.Lavender` | Secondary chart series, informational emphasis, alternate progress |
| Muted lavender | `#B1ADCC` | `FluxPalette.LavenderMuted` | Disabled/secondary chart marks and low-emphasis data |
| Dark supporting gray | `#4C4C49` | `FluxPalette.CharcoalMuted` | Dark-card secondary text and inactive marks |

The semantic names are intentionally different from the repository’s current red/blue vocabulary. BoilerHAUS currently defines `signal` as red, `signal-alt` as blue, `paper` as warm white, and `ash` as dark gray [2]. Reusing those names with their old meanings would create accidental color drift. You may retain the **concept** of semantic aliases, but the Android implementation should define a new palette whose values are explicitly tied to the supplied reference.

The color hierarchy is approximately **structural dark, neutral light, environmental lime, action lime, and data lavender**. Lime and lavender should not compete for the same semantic job: lime means selection, action, progress, or “now”; lavender means comparison, secondary data, or supporting emphasis.

### Contrast decisions

The measured palette supports strong accessibility if the foreground rules are enforced. Deterministic contrast calculations produced approximately 16.02:1 for charcoal on white, 12.39:1 for charcoal on pale lime, 12.77:1 for charcoal on vivid lime, 8.24:1 for charcoal on lavender, and 12.25:1 for charcoal on the gray surface. White on vivid lime is only about 1.25:1, and white on lavender is about 1.94:1; both are unsuitable for normal text.

Therefore, the system should enforce a simple rule: **use charcoal text on all lime and lavender fills; use white text only on charcoal fills**. Do not place white labels on the vivid lime primary button, even if that is visually tempting. Use charcoal text with sufficient weight instead.

## 2. Geometry, typography, and motion

The dominant layout is based on a strong outer frame, a sidebar-to-content split, and a dashboard grid. The reference is approximately 4:3 and visually behaves like a large product board rather than a conventional mobile screen. Its desktop proportions should be translated into a responsive grammar: preserve the hierarchy and contrast, but do not force a sidebar and three-column dashboard onto a narrow phone.

Recommended Compose geometry tokens are shown below. They are starting points derived from the reference’s visual proportions and should be finalized through screenshot comparison on the target Android devices.

| Token family | Recommended values | Design intent |
|---|---|---|
| Screen gutter | 16dp compact; 20dp regular; 24dp expanded | Dense but breathable mobile layout |
| Card radius | 24dp primary; 16dp compact; 32dp hero | Soft geometric surfaces without generic Material appearance |
| Pill radius | `RoundedCornerShape(50)` | Search, filters, selected navigation, CTA affordances |
| Card gap | 12dp compact; 16dp regular | Clear modular rhythm |
| Internal card padding | 16dp compact; 20dp regular; 24dp hero | Large-number dashboards need air around metrics |
| Border | 1dp, usually transparent or low-contrast | Prefer color blocks and elevation over visible outlines |
| Touch target | Minimum 48dp | Required by the implementation plan; validate with Compose accessibility tooling |
| Motion | 100ms fast; 200ms base; 350ms reveal | Direct, calm transitions; no bounce or spring by default |

The typeface in the raster cannot be identified reliably from the image alone. It appears to be a rounded geometric grotesk with a large x-height and relatively open counters. Do not claim an exact font match until screenshot comparisons are complete. Start with a bundled geometric sans such as **Manrope** or **Plus Jakarta Sans**, then lock one font after comparing the title, metric numerals, navigation labels, and helper text. Use a restrained weight range—regular, medium, semibold, and bold—and avoid decorative condensed display faces because the reference relies on broad, friendly geometry.

Use large black page titles, compact supporting copy, and oversized numerical metrics. Numerals should be optically prominent and use tabular or stable-width figures where values update. Avoid all-caps body copy; reserve uppercase or tracking for small metadata only.

## 3. Mapping onto the Link Ledger implementation plan

The existing implementation plan already gives the right architectural location: `:core:ui` owns the Compose theme and shared UI components, while `:feature:now`, `:feature:inbox`, `:feature:archive`, and `:feature:settings` consume them. The design system should be built before the feature screens, because the point budget, recommendation cards, archive badges, and over-budget modal all depend on consistent semantic states.

| Planned area | Recommended Bauhaus treatment |
|---|---|
| `:core:ui` | Own `FluxTheme`, palette, typography, shapes, spacing, elevation, motion, icons, and reusable components. |
| Now screen | Use the pale-gray workspace with a large white hero card. Make the 100-point gauge charcoal/lime first, with lavender reserved for secondary comparison or recommendation data. |
| Recommendation carousel | Use white cards with a charcoal title, lime primary action, lavender secondary metadata, and a small deterministic explanation line. Avoid a visually noisy carousel; show one dominant recommendation at a time on phones. |
| Inbox | Use charcoal priority markers and lime deadline/action states. Preserve the required priority 5→1 and deadline subgroup ordering without exposing recommendation scores. |
| Archive | Use lower-contrast gray surfaces, `0.25 pt` badges, and lavender or muted charcoal for reference status. Keep permanent deletion destructive but visually restrained. |
| Settings | Use white cards on gray, pill-shaped switches and segmented controls, and charcoal text on lime selected states. |
| Over-budget modal | Use a charcoal modal surface with white text, lime primary CTA, and a clear secondary white/gray action. The modal should explain the budget consequence before offering temporary overage. |
| Notifications and permissions | Keep system behavior native, but style in-app permission explanations as the same white-card/charcoal/lime language. |

The implementation plan’s privacy, offline-first, and deterministic-domain requirements should remain independent of visual styling. The design system must not introduce analytics, cloud dependencies, or UI-driven business logic. A component should receive state and callbacks; it should not calculate budget totals, recommendation scores, or notification schedules.

## 4. What to reuse from BoilerHAUS UI

The repository contains enough code to accelerate **design-system thinking**, but not enough code to avoid a native Android implementation. Its strongest reusable assets are the token hierarchy, semantic aliases, component inventory, stories-first workflow, accessibility expectations, and rule that raw values should not appear in component styling [1] [2] [3]. Its current component set also provides a useful coverage checklist: Button, Card, Input, Select, Dialog, Progress, Stat, Tabs, Switch, Toast, Tooltip, Skeleton, Table, and related primitives.

The actual implementations are web-specific. Buttons use React props, Radix Slot, Tailwind classes, CSS variables, browser focus rings, and DOM semantics; Progress uses Radix React Progress; the package publishes JavaScript and CSS rather than Kotlin or Compose artifacts. These parts should be translated, not imported. The repository also has a different canonical brand palette and a web-oriented Storybook workflow, so it cannot be treated as the source of truth for the supplied image.

| Repository asset | Decision for Android |
|---|---|
| CSS token naming and semantic alias pattern | Reuse conceptually and translate to typed Kotlin tokens |
| Component inventory | Reuse as a coverage checklist, then prioritize mobile-relevant components |
| React/Tailwind/Radix implementations | Do not import; rewrite as Compose wrappers/primitives |
| Storybook stories | Replace with Compose previews, screenshot tests, and state matrices |
| `CLAUDE.md` token discipline | Adapt into the Android agent contract |
| Existing red/blue/warm-paper values | Do not reuse for this product; map to the measured lime/lavender/charcoal palette |

## 5. Recommended Android design-system structure

Create the following structure inside the planned `:core:ui` module:

```text
core/ui/
├── theme/
│   ├── FluxPalette.kt
│   ├── FluxTypography.kt
│   ├── FluxShapes.kt
│   ├── FluxSpacing.kt
│   ├── FluxMotion.kt
│   └── FluxTheme.kt
├── component/
│   ├── FluxButton.kt
│   ├── FluxCard.kt
│   ├── FluxBadge.kt
│   ├── FluxProgress.kt
│   ├── FluxStat.kt
│   ├── FluxDialog.kt
│   ├── FluxTabs.kt
│   ├── FluxInput.kt
│   ├── FluxSwitch.kt
│   └── FluxEmptyState.kt
├── icon/
├── chart/
│   ├── BudgetGauge.kt
│   ├── RecommendationMark.kt
│   └── SleepLikeBars.kt
└── test/
    ├── FluxThemeScreenshotTest.kt
    └── FluxAccessibilityTest.kt
```

A minimal typed palette should look conceptually like this:

```kotlin
@Immutable
data object FluxPalette {
    val Void = Color(0xFF222124)
    val Paper = Color(0xFFFFFFFF)
    val Surface = Color(0xFFE1E1E1)
    val CanvasLime = Color(0xFFDCEA95)
    val Signal = Color(0xFFDAF163)
    val Lavender = Color(0xFFBBB2F5)
    val LavenderMuted = Color(0xFFB1ADCC)
    val CharcoalMuted = Color(0xFF4C4C49)
}
```

In production, expose semantic roles through an immutable theme object—for example `background`, `surface`, `surfaceInverted`, `contentPrimary`, `contentOnInverted`, `accentAction`, `accentData`, `statusWarning`, and `statusDanger`—rather than allowing feature modules to reference arbitrary palette fields. This follows Android’s documented custom-theme pattern while preserving a single source of truth [4] [5].

Use Material 3 components only through Flux wrappers. `FluxButton` should decide its container/content colors, shape, minimum size, typography, and pressed/disabled states. `FluxCard` should decide surface color, radius, padding, and elevation. Feature code should not call raw `Button`, `Card`, or `TextField` styling directly. This is the Android equivalent of the repository’s “tokens only” rule.

## 6. AI-driven agentic development plan

The most reliable workflow is a gated sequence in which each agent produces a small artifact and a deterministic verification result. Do not ask a coding agent to “make the whole app look like the image”; that invites inconsistent colors, accidental Material defaults, and business-logic leakage.

| Gate | Agent responsibility | Required output | Acceptance test |
|---|---|---|---|
| 1. Visual contract | Analyze the reference and implementation plan | `visual_contract.md` with palette, geometry, typography, forbidden patterns, and screen mappings | Every token has a semantic role and hex value |
| 2. Token foundation | Implement `:core:ui/theme` | Typed palette, theme, typography, shapes, spacing, motion | No feature module contains raw color, radius, or timing literals |
| 3. Primitive components | Implement Flux wrappers | Buttons, cards, badges, stats, progress, dialogs, tabs, inputs, switches | Compose previews cover light/dark, enabled/disabled, selected/error states |
| 4. Visual reference board | Build a `DesignSystemGallery` screen | One screen showing every token and component state | Screenshot comparison against the reference’s visual grammar |
| 5. Feature composition | Implement Now, Inbox, Archive, Settings | Screens using only `:core:ui` components | Static analysis finds no direct Material styling in features |
| 6. Verification | Run tests and accessibility checks | Screenshot baselines, TalkBack notes, contrast report, UI test results | Compose UI Check, TalkBack, contrast, 48dp touch targets, and state behavior pass |

The agent contract should explicitly forbid raw hex values outside the theme package, arbitrary one-off corner radii, ad hoc animation springs, direct use of Material components in feature modules, and changes to domain behavior while styling UI. It should require a screenshot or Compose preview for every new component state and a short “visual delta” note describing what changed.

The repository’s stories-first idea should be retained as **preview-matrix-first development**. Before implementing a component, the agent writes the expected preview states: primary/secondary/disabled/loading for a button; light/dark/selected/error for a field; neutral/action/warning for a badge; empty/partial/full/over-budget for a gauge. This produces an explicit contract that is easier for an AI agent to satisfy than an informal visual description.

## 7. Validation and next steps

The first implementation milestone should not be a full Now screen. It should be a small design-system gallery containing the palette swatches, typography scale, buttons, cards, badges, progress bars, a budget gauge, a recommendation card, and the over-budget dialog. Once this gallery visually matches the reference language, the feature screens can be composed with much lower risk.

Validate the palette programmatically and visually. The dark-on-color rules are strong, but every final text/background combination still needs a contrast check. Validate TalkBack labels, traversal order, semantics for gauges and charts, and minimum touch targets. Android’s official guidance identifies manual TalkBack and Switch Access testing, Compose UI Check, Accessibility Scanner, automated tests, touch-target size, contrast, and meaningful labels as relevant validation routes [6].

The recommended immediate sequence is therefore: freeze the measured palette; write the Android token contract; implement `FluxTheme`; build the gallery; create screenshot baselines; implement the Now screen; then proceed to Inbox, Archive, and Settings. Keep the BoilerHAUS repository open as a reference for naming, token governance, and component coverage, but keep the Android codebase independent.

## Final decision

**Use BoilerHAUS UI as a reference and process template, not as a dependency.** It has enough code to teach the design-system structure and to provide a useful component checklist, but it does not contain enough native Android or Jetpack Compose code to build this application directly. The correct path is a new, typed Compose design system that preserves BoilerHAUS’s discipline while replacing its web-specific implementation and red/blue palette with the measured charcoal, white, gray, pale-lime, vivid-lime, and lavender language from the supplied image.

## References

[1]: https://github.com/BoilerHAUS/boilerhaus-ui "BoilerHAUS/boilerhaus-ui repository"

[2]: https://raw.githubusercontent.com/BoilerHAUS/boilerhaus-ui/Main/src/tokens/tokens.css "BoilerHAUS canonical CSS tokens"

[3]: https://raw.githubusercontent.com/BoilerHAUS/boilerhaus-ui/Main/CLAUDE.md "BoilerHAUS UI agent and design-system rules"

[4]: https://developer.android.com/develop/ui/compose/designsystems/custom "Custom design systems in Compose"

[5]: https://developer.android.com/develop/ui/compose/designsystems/material3 "Material Design 3 in Compose"

[6]: https://developer.android.com/guide/topics/ui/accessibility/testing "Test your app's accessibility"
