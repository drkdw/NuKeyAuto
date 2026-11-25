# Architecture

## High-Level Architecture

NuKey Auto uses MVVM with a Repository pattern. A single `NukiRepository` instance owns all state and API logic. The phone goes through a Hilt `MainViewModel` (survives rotation); the Auto screen calls the repository directly from its own `lifecycleScope`. Both platforms observe the same `StateFlow<UiState>`, ensuring consistent state across phone and car displays.

```
┌──────────────────┐      ┌──────────────────┐
│ MainActivity     │      │ DoorControlScreen│
│ + ShapeMorphView │      │ (Android Auto)   │
└────────┬─────────┘      └────────┬─────────┘
         ▼                         │ lifecycleScope
┌──────────────────┐               │
│ MainViewModel    │               │
│ (@HiltViewModel) │               │
└────────┬─────────┘               │
         └────────────┬────────────┘
                      ▼
             ┌────────────────┐
             │ NukiRepository │  ← @Singleton, StateFlow<UiState>
             └────────┬───────┘
                      ▼
             ┌────────────────┐
             │ NukiApiService │  ← Retrofit → api.nuki.io
             └────────────────┘
```

## Package Structure

```
com.ddw.nukeyauto/
├── NukiApp.kt                          Application class, theme init, dynamic colors
├── MainActivity.kt                     Mobile UI entry point, state display, ShapeMorphView
├── SettingsActivity.kt                 Credentials, demo mode, appearance, behavior, billing settings
├── di/
│   ├── AppModule.kt                    Hilt module: EncryptedSharedPreferences + Retrofit client
│   └── InMemoryPrefs.kt                Memory-only SharedPreferences fallback
├── data/
│   ├── PrefKeys.kt                     Preference key constants + safe* readers
│   ├── model/
│   │   ├── ApiModels.kt                SmartLockResponse, LockState, DoorState, doorStateFrom(), ActionRequest
│   │   ├── UiState.kt                  UiState data class (incl. lockedButDoorOpen)
│   │   └── NukiError.kt                Sealed error class with toLocalizedString()
│   ├── api/
│   │   └── NukiApiService.kt           Retrofit interface (GET /smartlock/{id}, POST action)
│   ├── demo/
│   │   └── DemoLock.kt                 Simulated lock + door sensor (NukiApiService) for demo mode
│   └── repo/
│       └── NukiRepository.kt           Singleton, StateFlow<UiState>, API calls, polling logic
├── ui/
│   ├── ShapeMorphView.kt               Custom FrameLayout, M3 shape morphing, stroke progress
│   ├── ExpressiveCardAnimator.kt       Shape transitions, emphasis animations, ambient cycling
│   ├── auto/
│   │   ├── DoorControlService.kt       CarAppService entry point for Android Auto
│   │   └── DoorControlScreen.kt        PaneTemplate, status/battery/door rows
│   └── MainViewModel.kt                @HiltViewModel for MainActivity, delegates to NukiRepository
└── billing/
    └── BillingHelper.kt                Google Play Billing 9, consumable donations
```

## State Management

### UiState

`NukiRepository` exposes a `StateFlow<UiState>` containing:

| Field | Type | Description |
|---|---|---|
| `lockState` | `LockState` (enum) | Current lock state (locked, unlocked, unlatched, etc.) |
| `battery` | `Int?` | Lock battery charge in percent; null when the lock only reports critical/OK |
| `batteryCritical` | `Boolean` | Lock battery critical flag |
| `isLoading` | `Boolean` | Whether an API call or action is in progress |
| `error` | `NukiError?` | Current error, if any |
| `countdownSeconds` | `Int` | Remaining seconds in open delay countdown |
| `doorState` | `DoorState` | Door sensor state: `None` (no sensor), `Closed`, `Open`, `Unknown` |
| `doorSensorBatteryCritical` | `Boolean` | Door sensor battery critical flag |
| `lockedButDoorOpen` | `Boolean` (derived) | Locked while the door sensor reports the door open |

### Flow of State Changes

1. User taps the Open door / Lock door button
2. ViewModel calls repository method
3. Repository sets `isLoading = true`, emits new UiState
4. Repository sends API request via Retrofit
5. On success: starts polling sequence (see below)
6. On failure: sets error in UiState, clears loading

Only one action runs at a time: `performLockAction` holds a `Mutex` (`tryLock`) from either surface, and a second call while it is held is ignored. A plain refresh never ends the loading state of a running action or countdown.
7. UI layer reacts to StateFlow emissions automatically

### Polling After Action

After sending a lock or unlatch action:

1. Show Locking / Opening right away
2. Wait 3.5 seconds (initial delay for hardware to respond), then fetch
3. While the lock has not reported the expected result, poll `GET /smartlock/{id}` every 2 seconds, maximum 5 polls
   - Expected result: `Locked` for lock; `Unlatched` or `Unlocked` for open (the lock settles on Unlocked after an unlatch)
   - Polling also stops on an error or on a stable problem state (`MotorBlocked`, `Uncalibrated`, `Undefined`)
4. If the expected result is not reached (and no config error or problem state), set `NukiError.ActionNotConfirmed`; a leftover transition state becomes `Unknown`
5. A timeout or dropped connection after the request was sent refetches once and also ends in `ActionNotConfirmed` (the door may have moved, so never a plain failure). Only `ConnectException` / `UnknownHostException` (never arrived) give `ConnectionFailed`
6. Cancelling the scope (car session ended, activity finished) after sending also marks the action unconfirmed

### Door Watch

While the door sensor reports `Open`, a visible screen calls `refreshWhileDoorOpen()`: refetch every 5 seconds, at most 24 times (2 minutes), skipped while an action is running. This turns "Close the door to lock" into a Lock button as soon as the door closes.

### Open Delay

When `OPEN_DELAY_SECONDS > 0` and the user requests unlatch:

1. Start countdown from configured seconds (`cancelOpen()` is checked after every tick, from either surface)
2. Emit `countdownSeconds` in UiState each second
3. UI shows countdown animation (cookie7 shape)
4. When countdown reaches 0, send the actual unlatch API call
5. User can cancel during countdown (the primary button turns into Cancel)

## Data Models

### LockState Enum

Mapped in `NukiRepository.fetchState()` from the Nuki state code:

| Code | Name | Description |
|---|---|---|
| 0 | Uncalibrated | Lock needs calibration |
| 1 | Locked | Door is locked |
| 2 | Unlocking | Lock is transitioning to unlocked |
| 3 | Unlocked | Door is unlocked |
| 4 | Locking | Lock is transitioning to locked |
| 5 | Unlatched | Door is unlatched (opened) |
| 6 | Unlocked | Lock 'n' Go; leaves the lock unlocked |
| 7 | Unlatching | Lock is transitioning to unlatched |
| 253 | BootRun | Lock is booting |
| 254 | MotorBlocked | Lock motor is physically blocked |
| 255 | Undefined | Lock offline / unavailable |
| other | Unknown | Unrecognized code (also the initial state); raises `UnknownLockState` |

`isTransition` (Locking, Unlocking, Unlatching, BootRun) offers no action; `isFault` (MotorBlocked, Uncalibrated, Undefined, Unknown) is drawn in the error color.

### DoorState

`doorStateFrom()` in `ApiModels.kt` maps the Nuki Door Sensor `doorState`: 2 = `Closed`, 3 = `Open`, 4/5/16/255 = `Unknown` (sensor present but unsure), anything else (missing, 0, 1, 240) = `None`. `None` hides everything door-related, so no setting is needed for people without a sensor. When the door is `Open`, the Open and Lock actions are hidden (phone shows "Close the door to lock"), and `Locked` + `Open` is shown as a warning (`lockedButDoorOpen`).

### SmartLockResponse

Returned by `GET /smartlock/{id}`. Only the fields the app reads are modeled: lock state, battery charge (nullable) and critical flag, door state, door sensor battery critical flag.

### ActionRequest

Sent to `POST /smartlock/{id}/action`. Contains an `action` field:
- `2` = lock
- `3` = unlatch (physically opens the door; never `1` unlock)

### NukiError (sealed class)

Error types with `toLocalizedString()` for user-facing messages (`data/model/NukiError.kt`):
- `NoCredentials`
- `InvalidSmartLockId`
- `UnknownLockState(stateCode)`
- `ConnectionFailed` (any `IOException`)
- `HttpError(code)` (401/403, 404, 429, 5xx and others get distinct strings)
- `ApiException` (fallback for anything else)
- `ActionNotConfirmed` (an action may or may not have reached the lock)

`isConfigError()` is true for `NoCredentials`, `InvalidSmartLockId` and HTTP 401/403/404. A failed fetch with a config error resets the lock state to `Unknown` (and clears battery and door data), so both screens route to the "fix your settings" flow.

## Demo Mode

`PrefKeys.DEMO_MODE` switches `NukiRepository` from the real `NukiApiService` to `DemoLock` (`data/demo/DemoLock.kt`), a simulated lock with a door sensor that never touches the network. It steps through the same states as a real lock, one per poll, and the door stands open for a few refreshes after an open. Stored credentials are untouched. `setDemoMode()` cancels any countdown and resets `UiState`, so real and demo state never mix. Offered from the welcome screen ("Try the demo") and the settings switch.

## Authentication

- User enters Nuki API token and Smart Lock ID in SettingsActivity
- Credentials stored in EncryptedSharedPreferences (AES256-SIV keys, AES256-GCM values); reads go through the `safe*` helpers in `PrefKeys.kt`
- Token injected as `Authorization: Bearer {token}` header via OkHttp interceptor
- No OAuth flow; the token is obtained by the user from the Nuki Web dashboard

## Dependency Injection (Hilt)

`AppModule.kt` provides:

1. **EncryptedSharedPreferences** instance (`@Singleton`). If creation fails it retries once, then wipes the file and key and recreates it; if that fails too it falls back to `InMemoryPrefs` (memory-only, so the app never crashes on launch; the user re-enters credentials)
2. **OkHttpClient** with auth interceptor (`@Singleton`); body logging with the Authorization header redacted in debug builds only
3. **Retrofit** instance pointed at `https://api.nuki.io/` (`@Singleton`)
4. **NukiApiService** from Retrofit (`@Singleton`)

The phone `MainViewModel` uses `@HiltViewModel`. `DoorControlService` is an `@AndroidEntryPoint`; it injects the repository and hands it to `DoorControlScreen`, which launches work in the Screen's `lifecycleScope` (cancelled when the car session ends).

## Navigation

### Mobile

- `MainActivity` - main lock control screen
- `SettingsActivity` - settings and preferences

Navigation between them uses standard `startActivity()`. There is no Navigation Component or nav graph; the app has only two screens.

### Android Auto

- `DoorControlService` extends `CarAppService`, returns `DoorControlScreen`
- `DoorControlScreen` is the only screen; it uses `PaneTemplate`
- It refreshes on every `onStart` (the lock can be operated elsewhere while the car session lives on) and watches an open door via `refreshWhileDoorOpen()` while visible
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
| `DONATION_COUNT` | Int | 0 | Total donation count; supporter = count > 0 |
| `DEMO_MODE` | Boolean | false | Use the simulated lock instead of the Nuki API |

## Error Handling

1. Exceptions are caught in `NukiRepository` and mapped to the `NukiError` sealed class (`IOException` to `ConnectionFailed`, `HttpException` to `HttpError`, rest to `ApiException`)
2. `NukiError.toLocalizedString()` returns user-facing strings (supports EN and NL)
3. Errors are set in `UiState.error` and observed by UI
4. Mobile UI shows error shapes (burst) with shake animation
5. Android Auto shows error text in the pane row
6. Config errors (401/403/404, missing or invalid credentials) lead to "Open settings" on the phone; other errors lead to Retry

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

## Build Variants and Release

- Debug builds use `applicationIdSuffix = ".debug"`, so they install next to the release app.
- Releases use release-please; the full flow (versioning, internal testing builds, changelog) is in [CLAUDE.md](../CLAUDE.md). Do not duplicate it here.
