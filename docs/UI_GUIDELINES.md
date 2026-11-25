# UI Guidelines

## Design System

NuKey Auto uses Material 3 with the baseline palette (seed color `#6750A4`). Dynamic colors (Material You) are supported on Android 12+ and enabled by default, but can be toggled off by the user.

## Color Palette

### Baseline (non-dynamic)

Seed color: `#6750A4` (Material 3 default purple). The palette is defined as `md_light_*` / `md_dark_*` colors in `res/values/colors.xml` and `res/values-night/colors.xml`, and `themes.xml` / `values-night/themes.xml` override the M3 color roles with them (primary, secondary, tertiary, error, background, surface and surface containers, outline, scrim). Views still never use those values directly; they reference the theme attributes.

### Dynamic Colors

When enabled (`DYNAMIC_COLORS = true`) and running on Android 12+, the app uses `DynamicColors.applyToActivitiesIfAvailable()` in `NukiApp.kt`. The entire color scheme adapts to the user's wallpaper.

### Usage Rules

- Always reference M3 theme attributes (`?attr/colorPrimary`, `?attr/colorSurface`, etc.), never hardcoded hex values
- Change colors only in `colors.xml` and the theme overrides, never in a layout
- Error states use `?attr/colorError` and `?attr/colorErrorContainer`

## Typography

Standard Material 3 type scale. No custom fonts. Use M3 text appearance styles:

- Headlines: `?attr/textAppearanceHeadlineMedium`
- Body: `?attr/textAppearanceBodyLarge`
- Labels: `?attr/textAppearanceLabelMedium`

## Component Style

### Buttons

- Primary actions (open/lock): `MaterialButton` with M3 styling; `btnAction` is the primary, `btnSecondary` shows Lock door while Unlocked and "Try the demo" on the welcome screen
- Labels are sentence case: "Open door", "Lock door", "Open settings", "Get started", "Try the demo", "Retry", "Cancel"
- Door open (door sensor): Open and Lock are hidden; the primary button shows a disabled "Close the door to lock"
- During the open delay the primary button becomes "Cancel" in the error color
- Do NOT use custom button backgrounds; rely on M3 button styles (the only tint changes are primary vs. error)

### Cards and Containers

- Use `MaterialCardView` for grouped content (every settings section is a card)
- Follow M3 elevation and surface tint guidelines

### ShapeMorphView (Custom Component)

Central UI element on the mobile screen. A custom `FrameLayout` that renders animated M3 Expressive `RoundedPolygon` shapes, with the state icon (`ivIcon`) in the middle.

**Shape mapping by state:**

| Lock State | Shape | Emphasis Animation |
|---|---|---|
| Locked / Unknown | Diamond | Settle |
| Unlocked | Cookie4 | Pulse |
| Unlatched | Sunny | Bounce |
| Locking/Unlocking/Unlatching/BootRun | Cookie6 | None (transition) |
| Countdown | Cookie7 | None (stroke progress) |
| Error / Motor Blocked / Uncalibrated / Offline | Burst | Shake |

**Stroke progress:** During countdown and loading states, the shape's stroke animates as a progress indicator.

**Ambient cycling:** When idle, the shape subtly cycles through minor variations to feel alive. The welcome screen uses a diamond with its own, faster cycle.

**Door open while locked:** a locked lock with an open door shows the warning icon in the error color and "Locked, but the door is open".

### ExpressiveCardAnimator

Manages the animation pipeline for ShapeMorphView:
- Shape transitions between states (morphing from one polygon to another)
- Emphasis animations (shake, bounce, settle, pulse) triggered on state entry
- Ambient cycling for idle states

## Layout Structure

### Mobile (MainActivity)

```
CoordinatorLayout
  ├── AppBarLayout
  │    └── MaterialToolbar (title, settings action)
  └── SwipeRefreshLayout
       └── ConstraintLayout
            ├── ShapeMorphView (center, main visual, icon inside)
            ├── Status text, countdown text, battery text, welcome text
            └── Button row (btnAction, btnSecondary)
```

- `SwipeRefreshLayout` enables pull-to-refresh for manual state fetch
- `ConstraintLayout` for flat, performant layout
- Edge-to-edge: `enableEdgeToEdge()` is called; insets are handled via `ViewCompat.setOnApplyWindowInsetsListener`
- `fitsSystemWindows` is NOT used on any layout

### Welcome screen

Shown while no credentials are saved (`NoCredentials`). The shape is a diamond with the app's own car-and-key mark (the launcher monochrome artwork, scaled up and tinted with the primary color) instead of a lock icon. It shows the welcome title and message, a "Get started" button (opens settings) and a "Try the demo" button (starts demo mode).

### Settings (SettingsActivity)

A custom layout (`activity_settings.xml`), not a Preference screen: `AppBarLayout` + `MaterialToolbar` (with back arrow) above a `ScrollView`. Each section has a small primary-colored label above a `MaterialCardView`:
- Nuki Configuration: token and lock ID in `TextInputLayout`s, plus the demo mode row with a `MaterialSwitch`
- Appearance: theme and language in `TextInputLayout`s with an `AutoCompleteTextView` dropdown (`item_dropdown.xml`), dynamic colors `MaterialSwitch`
- Behavior: confirm actions `MaterialSwitch`, door open delay `Slider` (0-10 s) with a value label
- About: rate, feedback and share buttons, supporter card, donation buttons
- A "Save Settings" button at the bottom

## Spacing and Dimensions

- Follow Material 3 spacing guidelines (multiples of 4dp)
- Standard padding: 16dp horizontal, 8dp between elements
- Touch targets: minimum 48dp x 48dp per Material accessibility guidelines

## Icons

### Style

All icons use **Material Symbols Rounded Outlined**, weight **200**, size **28dp**.

### Naming

Drawable files use `ic_` prefix: `ic_lock`, `ic_unlock`, `ic_battery_3`, etc.

### Battery Icons

8 battery level icons representing charge ranges:

| Icon | Level |
|---|---|
| `ic_battery_0` | Empty / critical |
| `ic_battery_1` | ~15% |
| `ic_battery_2` | ~30% |
| `ic_battery_3` | ~45% |
| `ic_battery_4` | ~60% |
| `ic_battery_5` | ~75% |
| `ic_battery_6` | ~90% |
| `ic_battery_full` | Full |

## UX Principles

1. **Minimal interaction**: Lock/unlock should be achievable in one tap (two if confirmation is enabled)
2. **Immediate feedback**: Shape morphing starts instantly on action, even before API responds
3. **Clear state communication**: Shape, color, and animation all communicate lock state redundantly
4. **Error transparency**: Errors show specific messages (not generic "something went wrong"), localized in EN/NL
5. **No surprises**: Confirmation dialogs are on by default; open delay provides a cancellation window
6. **Privacy-first**: No analytics, no tracking, no data collection beyond what the Nuki API requires

## Loading States

- A running action: ShapeMorphView shows the transition shape (cookie6) with animated stroke, and the buttons are disabled until the lock reports a result
- `SwipeRefreshLayout` shows its built-in spinner on pull-to-refresh
- Android Auto: row text updates to show loading status

## Error States

- Shape changes to burst with shake emphasis animation
- Error message displayed as text below the shape
- Error auto-clears on next successful action or manual refresh
- Errors that arrive while a valid lock state is shown (e.g. an unconfirmed action) appear once as a Snackbar with Retry (CarToast on Android Auto)
- "The lock did not confirm the action" (`ActionNotConfirmed`) stays on screen longer, so it is read before retrying
- Config errors (bad token or ID) turn the primary button into "Open settings"
- Specific error types (unauthorized, rate limited, offline) get distinct messages

## Android Auto Constraints

Android Auto has strict UI limitations imposed by the host (car head unit):

### Layout

- Uses `PaneTemplate` with a maximum of 2 rows + 1 large pane image
- Row 1: lock state (a locked lock with an open door shows a yellow warning icon)
- Row 2: lock battery
- Row 3: door state, only with a Nuki Door Sensor; shows "Close the door to lock" while open
- Pane image: large icon representing current state
- Action buttons: limited to what PaneTemplate allows; Open door / Lock door, Retry, or only Cancel during the open delay. While the door is open, Open and Lock are hidden

### Color

- Tint icons with `CarColor.DEFAULT`, not `CarColor.PRIMARY`. PRIMARY used to render white but shows black on newer hosts; DEFAULT lets the host pick a readable color.
- Do NOT call `setBackgroundColor()` on Android Auto buttons; the host controls button styling.
- Icon tinting is controlled by the host; provide white icons and let the system handle contrast.

### Text

- Keep text short; head units may truncate long strings
- State descriptions should be 1-3 words maximum

### Interaction

- No swipe gestures, no custom touch handling
- Actions happen via button taps only
- Host may impose rate limiting on screen updates
- The screen refreshes every time it becomes visible, and has a refresh action in the header

## Form Patterns (Settings)

- Token and Lock ID: `TextInputLayout` + `TextInputEditText`, validated on save
- Theme and language: `TextInputLayout` (exposed dropdown) + `AutoCompleteTextView`
- Dynamic colors, confirm actions, demo mode: `MaterialSwitch`, each with a `contentDescription`
- Open delay: `Slider` (0-10 seconds); 0 reads "Off"
- Donation buttons: custom buttons triggering BillingHelper

## Theming

Three theme modes controlled by `THEME` preference:

| Value | Behavior |
|---|---|
| `system` | Follow device dark mode setting |
| `light` | Force light theme |
| `dark` | Force dark theme |

Applied via `AppCompatDelegate.setDefaultNightMode()` in `NukiApp.kt`.

## Localization

- English (default): `values/strings.xml`
- Dutch: `values-nl/strings.xml`
- String resource naming: `category_name` pattern (e.g., `status_locked`, `btn_open_door`, `error_generic`)
- All user-facing text must be in string resources, never hardcoded
- Add both EN and NL translations when adding new strings
