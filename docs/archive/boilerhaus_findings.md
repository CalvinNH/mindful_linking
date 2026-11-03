# BoilerHAUS UI repository findings

Source repository: https://github.com/BoilerHAUS/boilerhaus-ui

## Verified repository characteristics

- Public package `@boilerhaus-ui/boilerhaus-ui`, current repository version shown as 0.1.5.
- Web-only implementation: React 19, TypeScript, Vite, Tailwind CSS v4, Storybook 10, Radix UI primitives.
- Token source is `src/tokens/tokens.css`; package exports React components and `tokens.css`.
- Current component inventory includes Accordion, Alert, AspectRatio, Avatar, Badge, Breadcrumb, Button, Card, Checkbox, Combobox, DataTable, DatePicker, Dialog, DropdownMenu, FileUpload, Input, Label, PageShell, Popover, Progress, RadioGroup, Select, Skeleton, Spinner, Stat, Stepper, Switch, Table, Tabs, Textarea, Toast, and Tooltip.
- README design rules: use tokens only; no raw hex, arbitrary spacing, or ad hoc durations; favor token utilities/CSS custom properties; isolate components; use accessible Radix primitives; Storybook is the component workshop.
- CLAUDE.md adds: no raw values; canonical token source; semantic aliases preferred; each component has implementation, stories, and index; `bh-` class naming convention; stories first; accessibility and contrast verification are mandatory; prefer editing existing helpers.
- Canonical CSS tokens observed from `tokens.css`: `void #0C0C0C`, `paper #F7F5F0`, `signal #D92B2B`, `signal-alt #1A52CC`, `rule #E2DED8`, `smoke #6B6660`, `ash #2E2C2A`, `caution #9A6F00`, `growth #1B6B3A`; display font Barlow Condensed; body DM Sans; mono JetBrains Mono; type scale from xs through 4xl; radii none/sm/md/lg/full; motion 100/200/350/500ms; shadows sm through xl.
- Android applicability: token concepts, naming discipline, component inventory, accessibility emphasis, stories-first workflow, and AI-agent rules are reusable. React/Tailwind/Radix code is not directly reusable for native Android/Jetpack Compose; the repository’s current canonical palette also does not match the supplied lime/lavender/charcoal reference.

## Reference-image measurements already extracted

- Image size: 2048 x 1536.
- Dominant measured colors include charcoal `#222124`, white `#FFFFFF`, surface gray `#E1E1E1`, pale lime background `#DCEA95`, vivid lime accent `#DAF163`, and lavender `#BBB2F5`.
- Regional measurements confirm: sidebar primarily `#222124`; main surface mixes `#FFFFFF`/`#E1E1E1`/`#222124`; white card primarily `#FFFFFF`; dark analytics card primarily `#222124`; lavender chart uses `#BBB2F5`; lime accent uses `#DAF163`.
- Image-level dominant-color shares are affected by anti-aliasing, shadows, and gradients; these are measurements from the supplied raster rather than declared design tokens.

## Additional verified evidence

The complete raw token file confirms an 8px-base spacing system (`4, 8, 12, 16, 24, 32, 48, 64, 96, 128`), 12-column grid, 280px sidebar, 48px topbar, 1px borders, four shadow levels, standard/enter/exit easing, semantic aliases, and a dark-mode remapping. It also explicitly says “no bounce, no spring.”

The raw package metadata confirms that the published package exposes only compiled JavaScript/CJS/TypeScript and `tokens.css`, with React and ReactDOM as peer dependencies; the runtime component dependencies are Radix React packages. No Android SDK, Kotlin, Jetpack Compose, Material, XML resources, or native platform module is indicated.

The repository tree is a web component library with source, Storybook, token, and configuration files. The available evidence supports treating it as a reference implementation for token governance and component documentation—not as an Android dependency to import directly.

## Android guidance relevant to the recommendation

Android’s official Compose documentation presents three viable custom-design-system approaches: extend `MaterialTheme`, replace selected Material systems such as colors/typography/shapes, or implement a fully custom design system. It also documents using custom theme objects and CompositionLocals for additional values. For this project, the best fit is a controlled custom theme layered over Material 3 primitives, with app-specific semantic tokens and wrapper components rather than importing the web library.

The official Material 3 documentation confirms that Compose Material 3 supplies type-safe color, typography, and shape systems and supports custom color schemes and component theming. The official accessibility-testing guide lists manual TalkBack testing, Switch Access, Compose UI Check, Accessibility Scanner, automated testing, and checks for touch-target size, color contrast, meaningful labels, and traversal order.
