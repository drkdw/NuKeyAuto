# Architecture

## High-Level Architecture

NuKey Auto uses MVVM with a Repository pattern. A single `NukiRepository` instance owns all state and API logic. Two separate ViewModels (mobile and Auto) delegate to that repository. Both platforms observe the same `StateFlow<UiState>`, ensuring consistent state across phone and car displays.

```
┌──────────────┐    ┌──────────────┐
│  Mobile UI   │    │ Android Auto │
│ MainActivity │    │ DoorControl  │
│ ShapeMorphView│   │   Screen     │
└──────┬───────┘    └──────┬───────┘
       │                   │
       ▼                   ▼
┌──────────────┐    ┌──────────────┐
│   Mobile     │    │    Auto      │
│ MainViewModel│    │ MainViewModel│
│ (@HiltVM)    │    │ (manual)     │
└──────┬───────┘    └──────┬───────┘
       │                   │
       └────────┬──────────┘
                ▼
       ┌────────────────┐
       │ NukiRepository │  ← Singleton, holds StateFlow<UiState>
       │  (Hilt @Singleton)│
       └────────┬───────┘
                ▼
       ┌────────────────┐
       │ NukiApiService │  ← Retrofit interface
       └────────┬───────┘
                ▼
       ┌────────────────┐
       │  Nuki Web API  │
       │ api.nuki.io    │
       └────────────────┘
```

## Package Structure

```
com.ddw.nukeyauto/
├── NukiApp.kt                          Application class, theme init, dynamic colors
├── MainActivity.kt                     Mobile UI entry point, state display, ShapeMorphView
├── SettingsActivity.kt                 Credentials, appearance, behavior, billing settings
├── di/
│   └── AppModule.kt                    Hilt module: EncryptedSharedPreferences + Retrofit client
├── data/
│   ├── PrefKeys.kt                     Preference key constants (object with const vals)
│   ├── model/
│   │   ├── ApiModels.kt                SmartLockResponse, LockState enum, ActionRequest
│   │   └── NukiError.kt                Sealed error class with toLocalizedString()
│   ├── api/
│   │   └── NukiApiService.kt           Retrofit interface (GET /smartlock/{id}, POST action)
│   └── repo/
│       └── NukiRepository.kt           Singleton, StateFlow<UiState>, API calls, polling logic
├── ui/
│   ├── ShapeMorphView.kt               Custom FrameLayout, M3 shape morphing, stroke progress
│   ├── ExpressiveCardAnimator.kt       Shape transitions, emphasis animations, ambient cycling
│   ├── auto/
│   │   ├── DoorControlService.kt       CarAppService entry point for Android Auto
│   │   ├── DoorControlScreen.kt        PaneTemplate, two-row layout, battery levels
│   │   └── MainViewModel.kt            Manual instantiation, delegates to NukiRepository
│   └── mobile/
│       └── MainViewModel.kt            @HiltViewModel, delegates to NukiRepository
└── billing/
    └── BillingHelper.kt                Google Play Billing 8, consumable donations
```

## State Management

### UiState

`NukiRepository` exposes a `StateFlow<UiState>` containing:

| Field | Type | Description |
|---|---|---|
| `lockState` | `LockState` (enum) | Current lock state (locked, unlocked, unlatched, etc.) |
| `battery` | Battery info | Lock and keypad battery levels |
| `isLoading` | `Boolean` | Whether an API call is in progress |
| `error` | `NukiError?` | Current error, if any |
| `countdownSeconds` | `Int` | Remaining seconds in open delay countdown |

### Flow of State Changes

1. User taps lock/unlock/unlatch button
2. ViewModel calls repository method
3. Repository sets `isLoading = true`, emits new UiState
4. Repository sends API request via Retrofit
5. On success: starts polling sequence (see below)
6. On failure: sets error in UiState, clears loading
7. UI layer reacts to StateFlow emissions automatically

### Polling After Action

After sending a lock/unlock/unlatch action:

1. Wait 3.5 seconds (initial delay for hardware to respond)
2. Poll `GET /smartlock/{id}` every 2 seconds
3. Maximum 5 poll attempts
4. Stop polling when lock state reflects the expected result
5. Update UiState with each poll response

### Open Delay

When `OPEN_DELAY_SECONDS > 0` and the user requests unlatch:

1. Start countdown from configured seconds
2. Emit `countdownSeconds` in UiState each second
3. UI shows countdown animation (cookie7 shape)
4. When countdown reaches 0, send the actual unlatch API call
5. User can cancel during countdown

## Data Models

### LockState Enum

| Value | Name | Description |
|---|---|---|
| 0 | UNCALIBRATED | Lock needs calibration |
| 1 | LOCKED | Door is locked |
| 2 | UNLOCKING | Lock is transitioning to unlocked |
| 3 | UNLOCKED | Door is unlocked |
| 4 | LOCKING | Lock is transitioning to locked |
| 5 | UNLATCHED | Door is unlatched (opened) |
| 7 | UNLATCHING | Lock is transitioning to unlatched |
| 254 | MOTOR_BLOCKED | Lock motor is physically blocked |
| 255 | UNDEFINED | Unknown state / lock offline |

### SmartLockResponse

Returned by `GET /smartlock/{id}`. Contains lock state, battery charge/critical status, and device metadata.

### ActionRequest

Sent to `POST /smartlock/{id}/action`. Contains an `action` field:
- `2` = lock
- `3` = unlatch

### NukiError (sealed class)

Error types with `toLocalizedString()` for user-facing messages:
- Unauthorized (401/403)
- NotFound (404)
- RateLimited (429)
- ServerError (5xx)
- NetworkError (connectivity issues)
- Generic (fallback)

## Authentication

- User enters Nuki API token and Smart Lock ID in SettingsActivity
- Credentials stored in EncryptedSharedPreferences (AES256-SIV keys, AES256-GCM values)
- Token injected as `Authorization: Bearer {token}` header via OkHttp interceptor
- No OAuth flow; the token is obtained by the user from the Nuki Web dashboard

## Dependency Injection (Hilt)

`AppModule.kt` provides:

1. **EncryptedSharedPreferences** instance (`@Singleton`)
2. **OkHttpClient** with auth interceptor (`@Singleton`)
3. **Retrofit** instance pointed at `https://api.nuki.io/` (`@Singleton`)
4. **NukiApiService** from Retrofit (`@Singleton`)

The mobile `MainViewModel` uses `@HiltViewModel` with `@Inject constructor`. The Android Auto `MainViewModel` is manually instantiated because Car App Library does not support Hilt injection directly.

## Navigation

### Mobile

- `MainActivity` - main lock control screen
- `SettingsActivity` - settings and preferences

Navigation between them uses standard `startActivity()`. There is no Navigation Component or nav graph; the app has only two screens.

### Android Auto

- `DoorControlService` extends `CarAppService`, returns `DoorControlScreen`
- `DoorControlScreen` is the only screen; it uses `PaneTemplate`
- No multi-screen navigation on Auto

## Local Storage (Preferences)

All preferences use EncryptedSharedPreferences. Key constants are in `PrefKeys.kt`:

| Key | Type | Default | Description |
|---|---|---|---|
| `NUKI_TOKEN` | String | "" | Nuki API Bearer token |
| `NUKI_ID` | String | "" | Smart Lock ID |
| `CONFIRM_ACTION` | Boolean | true | Show confirmation before actions |
| `OPEN_DELAY_SECONDS` | Int | 0 | Countdown before unlatch (0-10) |
| `THEME` | String | "system" | Theme mode: system/light/dark |
| `DYNAMIC_COLORS` | Boolean | true | Enable Material You colors |
| `IS_SUPPORTER` | Boolean | false | Has donated at least once |
| `DONATION_COUNT` | Int | 0 | Total donation count |

## Error Handling

1. API errors are caught in `NukiRepository` and mapped to `NukiError` sealed class
2. `NukiError.toLocalizedString()` returns user-facing strings (supports EN and NL)
3. Errors are set in `UiState.error` and observed by UI
4. Mobile UI shows error shapes (burst) with shake animation
5. Android Auto shows error text in the pane row
6. Network errors (no connectivity) are handled separately from HTTP errors

## Naming Conventions

| Element | Convention | Example |
|---|---|---|
| Functions | camelCase | `fetchLockState()` |
| Classes | PascalCase | `NukiRepository` |
| XML view IDs | camelCase | `btnAction`, `tvStatus`, `etToken` |
| Drawables | ic_ prefix | `ic_lock`, `ic_battery_3` |
| String resources | category_name | `status_locked`, `btn_open_door`, `error_generic` |
| Preference keys | UPPER_SNAKE_CASE | `NUKI_TOKEN`, `OPEN_DELAY_SECONDS` |

## Patterns to Follow

- MVVM with Repository: all business logic in repository, ViewModels are thin delegates
- StateFlow for reactive state: single source of truth in repository
- Sealed classes for error types: exhaustive when-expressions
- EncryptedSharedPreferences for any sensitive data
- KDoc comments on every public function
- English-only code comments

## Patterns to Avoid

- Do NOT use Jetpack Compose (project uses View-based UI deliberately)
- Do NOT use the `flux_` prefix (this is a personal project, not agency work)
- Do NOT use Yoda conditions (`if (null == x)`) - this is Kotlin, not PHP
- Do NOT add analytics, tracking, or ad SDKs
- Do NOT use `fitsSystemWindows` in layouts (edge-to-edge is handled via WindowInsets)
- Do NOT call `setBackgroundColor` on Android Auto buttons (host-controlled)
