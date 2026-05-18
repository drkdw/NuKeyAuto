# Roadmap

## Shipped (v1.2.8) - MVP Complete

Everything listed here is implemented and published on Google Play:

- [x] Lock/unlock/unlatch via Nuki Web API
- [x] Android Auto support (Car App Library, PaneTemplate)
- [x] Real-time lock state display with M3 Expressive shape morphing
- [x] Battery level indicators (lock + keypad, 8 icon levels)
- [x] Configurable open delay (0-10 second countdown)
- [x] Confirmation dialogs before actions (toggleable)
- [x] Light/dark/system theme
- [x] Material You dynamic colors (Android 12+)
- [x] Encrypted credential storage
- [x] English and Dutch localization
- [x] Edge-to-edge display with WindowInsets handling
- [x] Pull-to-refresh on mobile
- [x] Google Play Billing donations (3 tiers, consumable)
- [x] Supporter badge in toolbar and settings

## Planned / Under Consideration

These are ideas that have been discussed but are NOT committed. Priority and feasibility may change.

### High Interest

#### App Logo / Branding
- Current state: attempted generation with Pillow, result was not professional quality
- Need: professionally designed app icon and feature graphic for Play Store
- Blocker: requires either design skills or a suitable generation tool
- Note: Pillow is too limited for this; consider vector-based approaches or commissioning

#### Lock State Change Notifications
- Push notification when lock state changes (e.g., someone unlocks the door)
- Requires background polling or Nuki webhook integration
- Considerations: battery impact, Nuki API rate limits, notification permissions on Android 13+
- Would need a lightweight background service or WorkManager job

### Medium Interest

#### Wear OS Companion App
- Quick lock/unlock from smartwatch
- Tile for instant access
- Complication showing lock state
- Considerations: separate module, Wear OS specific UI (Horologist/Compose for Wear), Bluetooth/Wi-Fi dependency

#### Multiple Lock Support
- Support controlling more than one Nuki smart lock
- Requires: lock selection UI, per-lock state management, updated data model
- Current architecture assumes single lock; repository and preferences would need refactoring
- API already supports it (just use different lock IDs)

#### Lock Activity History / Log
- Show recent lock/unlock events with timestamps
- Nuki API may provide activity logs (needs investigation)
- Would need a new screen or bottom sheet
- Consider local caching of recent events

### Low Priority / Exploratory

#### Widget
- Home screen widget showing lock state with quick action buttons
- Glance (Compose-based) or RemoteViews
- Needs periodic refresh strategy

#### Tasker / Intent Integration
- Allow external automation apps to trigger lock/unlock
- Expose actions via broadcast intents
- Security consideration: how to authenticate incoming intents

#### Additional Languages
- Currently: English, Dutch
- Potential: German, French (common Nuki markets)
- Low effort per language but requires native speaker review

## Ruled Out

These have been investigated and will not be pursued:

| Idea | Reason |
|---|---|
| Android Auto dashboard cards | Not available for IOT app category (tested and confirmed) |
| Jetpack Compose migration | View-based UI is a deliberate architectural choice |
| Analytics / tracking | Privacy-first principle, non-negotiable |
| Ads | Non-negotiable; monetization is donation-only |
| Nuki Bluetooth direct control | Out of scope; app uses Web API only |

## Technical Debt

Items that are not user-facing features but should be addressed:

- **Hilt 2.59.2 warnings**: Legacy variant API warnings from Hilt plugin; not fixable in app code, will resolve when Hilt updates
- **Google Play edge-to-edge warnings**: Originate from Material/AppCompat libraries, not app code; will resolve with library updates
- **Unit tests**: No test suite currently; adding tests for NukiRepository (state transitions, error mapping, polling logic) would improve maintainability
- **UI tests**: No instrumented tests; ShapeMorphView animations are complex and could benefit from screenshot tests
