# AGENTS.md — MindfulLinking Android Implementation Contract

## Purpose

You are implementing **Link Ledger / MindfulLinking**, an Android-first, local-first link manager. The product must follow the implementation architecture in `implementation_plan.md` and the Bauhaus-inspired visual language defined in `bauhaus_android_design_recommendation.md`.

Treat the files listed in the **Authoritative project documents** section as project requirements, not optional suggestions. Before changing architecture, data behavior, UI structure, or design tokens, read the relevant document and explain any conflict before proceeding.

## Authoritative project documents

The following files should be stored in the repository under `design/` unless the project already has an equivalent documentation directory:

```text
design/
├── implementation_plan.md
├── bauhaus_android_design_recommendation.md
├── reference_palette.json
├── reference_contrast.txt
├── boilerhaus_findings.md
└── reference_image.png                  # Optional but strongly recommended
```

### Source-of-truth hierarchy

Use the following precedence when requirements appear to conflict:

1. `implementation_plan.md` is authoritative for architecture, domain behavior, persistence, notifications, privacy, module boundaries, and feature requirements.
2. `bauhaus_android_design_recommendation.md` is authoritative for visual language, palette, typography direction, geometry, Compose design-system structure, and AI-driven UI workflow.
3. `reference_palette.json` is the machine-readable source for measured colors extracted from the supplied reference image.
4. `reference_contrast.txt` is the source for measured contrast ratios and foreground/background safety rules.
5. `boilerhaus_findings.md` explains what may be learned from the BoilerHAUS repository and what must not be imported directly.
6. `reference_image.png` is a visual reference only. When visual appearance and measured token files disagree, preserve the semantic token contract and record the discrepancy for review.

## Non-negotiable product constraints

The app is local-first and must work without cloud dependencies, analytics tracking, or network access. Persist application data locally using the architecture specified by `implementation_plan.md`. Do not introduce authentication, cloud synchronization, remote analytics, or unrelated services.

Preserve the strict 100.00-point attention budget, scaled-integer arithmetic, URL normalization rules, deterministic recommendation engine, deadline lifecycle, notification permission handling, backup exclusions, log redaction, and privacy requirements. UI code must not reimplement domain calculations or silently alter business rules.

The planned module boundaries are part of the contract:

```text
:app
:core:model
:core:database
:core:datastore
:core:domain
:core:notifications
:core:ui
:feature:share
:feature:now
:feature:inbox
:feature:archive
:feature:settings
```

Keep business logic in the appropriate core module. Keep visual tokens and reusable Compose components in `:core:ui`. Feature modules should compose components and provide state/callbacks; they should not own global colors, typography, shapes, or animation values.

## Android implementation rules

Use native Kotlin and Jetpack Compose. Use Material 3 as a behavioral and accessibility substrate where useful, but never allow default Material styling to define the product identity. Wrap Material components in project-specific `Flux*` components such as `FluxButton`, `FluxCard`, `FluxBadge`, `FluxProgress`, `FluxStat`, `FluxDialog`, `FluxTabs`, `FluxInput`, and `FluxSwitch`.

Implement the design system in `:core:ui` using typed Kotlin tokens. The expected starting structure is:

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
├── icon/
├── chart/
└── test/
```

Use semantic roles rather than raw palette names in feature code. Preferred examples include `background`, `surface`, `surfaceInverted`, `contentPrimary`, `contentSecondary`, `contentOnInverted`, `accentAction`, `accentData`, `statusWarning`, and `statusDanger`.

Do not place raw hex values, arbitrary corner radii, arbitrary spacing values, or ad hoc animation durations in feature modules or component implementations. All such values must originate in the theme/token package. If a new token is necessary, add it centrally, document its semantic role, and update the design documentation before using it.

## Required visual language

The product follows the supplied contemporary Bauhaus-inspired reference:

| Role | Token value | Rule |
|---|---:|---|
| Structural charcoal | `#222124` | App shell, dark cards, primary text on light surfaces |
| Paper white | `#FFFFFF` | Primary cards and high-emphasis surfaces |
| Workspace gray | `#E1E1E1` | Main content background and neutral containers |
| Environmental pale lime | `#DCEA95` | Root canvas, branded empty states, environmental framing |
| Action lime | `#DAF163` | Primary actions, selected states, progress, “now” emphasis |
| Data lavender | `#BBB2F5` | Secondary metrics, comparison data, alternate progress |
| Muted lavender | `#B1ADCC` | Low-emphasis data and inactive chart marks |
| Supporting charcoal gray | `#4C4C49` | Secondary text and inactive marks on dark surfaces |

Use charcoal text on white, gray, pale-lime, vivid-lime, and lavender surfaces. Use white text only on charcoal surfaces. Never use white text on vivid lime or lavender for normal labels because those combinations do not provide sufficient contrast.

The visual system uses large geometric cards, 16–24dp mobile gutters, 12–16dp component gaps, 16–24dp internal card padding, 16–32dp card radii, and fully rounded pills. Preserve the hierarchy of charcoal structure, light workspace, lime action, and lavender secondary data. Do not replace the palette with BoilerHAUS’s existing red/blue CSS palette.

Use a friendly geometric sans-serif with a restrained weight range. Keep page titles and metric numerals visually prominent, use stable-width numerals where values update, and reserve uppercase/tracking for small metadata. Do not introduce a decorative font without visual comparison against the reference image.

Default motion should be direct and calm: approximately 100ms for fast state changes, 200ms for normal transitions, and 350ms for reveals. Avoid bounce and spring motion unless a future design decision explicitly approves it.

## Component and screen workflow

Before implementing a new reusable component, define its public API and preview state matrix. Every component should have previews or screenshot tests covering relevant states such as enabled, disabled, selected, loading, empty, error, warning, dark surface, and light surface.

Build a `DesignSystemGallery` or equivalent internal screen before completing the feature screens. It must show palette swatches, typography, shapes, buttons, cards, badges, progress indicators, statistics, budget gauge, recommendation card, tabs, inputs, switches, and the over-budget dialog.

Implement screens only with shared `:core:ui` components:

| Screen | Required visual treatment |
|---|---|
| Now | White hero card on a gray workspace; charcoal/lime budget gauge; lavender only for secondary comparison data; one dominant recommendation card on phones |
| Inbox | Charcoal priority markers, lime deadline/action states, required priority 5→1 and deadline subgroup ordering, no recommendation-score exposure |
| Archive | Lower-contrast neutral surfaces, visible `0.25 pt` badges, restrained restore/delete states |
| Settings | White cards, pill-shaped controls, charcoal text on lime selected states |
| Over-budget modal | Charcoal modal, white text, lime primary action, explicit explanation of the budget consequence |

Do not expose internal recommendation scores in the Inbox. Do not move recommendation calculations into composables. Do not change notification, permission, backup, or privacy behavior while styling screens.

## Accessibility requirements

All interactive controls must have at least 48dp touch targets. Provide meaningful TalkBack labels and content descriptions for gauges, charts, icons, buttons, recommendation explanations, and state changes. Preserve logical traversal order. Validate light and dark themes, selected and disabled states, contrast, focus/pressed states, and semantics.

Use Compose UI Check, Android accessibility tooling, TalkBack, Switch Access, and automated UI tests where applicable. A visually attractive component is incomplete until its accessibility behavior is verified.

## Verification gates

Work in small, reviewable phases:

1. Read the relevant project documents and inspect existing code before modifying anything.
2. Implement or update centralized tokens.
3. Build or update the component preview/state matrix.
4. Implement the component or screen using only shared tokens and wrappers.
5. Run unit tests and relevant module tests.
6. Run screenshot or preview validation and accessibility checks.
7. Report the files changed, tests run, visual states verified, and any unresolved discrepancy.

At minimum, preserve and run the verification commands defined in `implementation_plan.md`, including domain tests, module tests, connected checks, `lintRelease`, and `testDebugUnitTest` as the project becomes available.

## Agent behavior and change discipline

Read before modifying. Prefer editing existing project files over creating duplicate helpers. Make one coherent change at a time. Do not rewrite unrelated modules. Do not introduce a dependency merely to solve a styling problem without explaining why it is necessary.

If a requirement is ambiguous, inspect the authoritative documents first. If ambiguity remains, choose the smallest implementation consistent with the existing architecture and record the assumption. Never silently replace the palette, remove accessibility semantics, weaken privacy behavior, or change domain formulas.

For every UI change, include a short visual-delta note explaining how the change preserves the charcoal/lime/lavender Bauhaus language. For every new token, include its semantic name, value, intended usage, contrast considerations, and the components that consume it.

## Completion criteria

The implementation is considered design-compliant only when the following are true:

- The app uses a centralized typed Compose theme in `:core:ui`.
- Feature modules do not contain raw design values or direct ad hoc Material styling.
- The reference palette and foreground rules are implemented consistently.
- The DesignSystemGallery and feature screens pass visual review.
- Components have state previews or screenshot coverage.
- TalkBack labels, traversal, touch targets, contrast, and dark/light states are verified.
- Domain, notification, privacy, and backup requirements remain unchanged and tested.
- The final agent report lists changed files, commands run, test results, and known visual differences from the reference.
