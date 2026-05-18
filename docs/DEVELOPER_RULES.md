# Developer Rules

Rules and conventions for anyone (human or AI) contributing code to NuKey Auto.

## Kotlin Conventions

### Naming

| Element | Style | Example |
|---|---|---|
| Classes, objects, interfaces | PascalCase | `NukiRepository`, `LockState`, `UiState` |
| Functions, properties, local variables | camelCase | `fetchLockState()`, `isLoading`, `countdownSeconds` |
| Constants (`const val` in objects) | UPPER_SNAKE_CASE | `NUKI_TOKEN`, `BASE_URL` |
| XML view IDs | camelCase | `btnAction`, `tvStatus`, `etToken` |
| Drawable resources | ic_ prefix, snake_case | `ic_lock`, `ic_battery_3`, `ic_battery_full` |
| String resources | category_name, snake_case | `status_locked`, `btn_open_door`, `error_generic` |
| Layout files | activity_ / fragment_ / item_ prefix | `activity_main`, `activity_settings` |

### Style

- Use `val` over `var` wherever possible
- Use data classes for plain value holders
- Use sealed classes for closed type hierarchies (errors, states)
- Use `when` exhaustively on sealed classes and enums (no `else` branch when all cases are covered)
- Use expression body (`= expr`) for single-expression functions
- Use scope functions (`let`, `also`, `apply`, `run`) where they improve clarity, not just to be clever
- No Yoda conditions (`if (null == x)`); use standard Kotlin idioms (`if (x == null)`)
- No `!!` operator; use safe calls, `let`, or `checkNotNull`/`requireNotNull` with meaningful messages

### Documentation

- KDoc comments on every public function and class
- Format: standard KDoc (`/** ... */`), not PHP-style docblocks
- `@param`, `@return`, `@throws` tags where applicable
- Code comments in English only
- Comments explain "why", not "what" (the code should be self-explanatory for "what")

## Architecture Rules

### MVVM + Repository

- All API calls and business logic go in `NukiRepository`
- ViewModels are thin delegates: they call repository methods and expose its StateFlow
- ViewModels do NOT contain business logic, API calls, or direct preference access
- UI layer (Activity/Screen) observes ViewModel state and renders it; no logic in UI layer

### State Management

- Single source of truth: `NukiRepository.stateFlow`
- State is immutable (copy-on-write via data class `copy()`)
- Never mutate UiState fields directly; always emit a new instance
- Both mobile and Auto ViewModels share the same repository instance

### Dependency Injection

- Use Hilt for all injectable dependencies on the mobile side
- Android Auto ViewModels are manually instantiated (Car App Library limitation)
- All Hilt modules go in `di/` package
- Scoping: repository, Retrofit, OkHttp, and SharedPreferences are `@Singleton`

## UI Rules

### View-Based UI Only

This project uses View-based UI (XML layouts + View classes). Do NOT introduce Jetpack Compose. This is a deliberate, final decision.

### Layouts

- Use `ConstraintLayout` for complex layouts (flat hierarchy, no nested LinearLayouts)
- Do NOT use `fitsSystemWindows` on any layout; edge-to-edge is handled via `ViewCompat.setOnApplyWindowInsetsListener`
- `enableEdgeToEdge()` is already called in activities
- All dimensions in dp, text sizes in sp
- Minimum touch target: 48dp x 48dp

### Theming

- Always use Material 3 theme attributes (`?attr/colorPrimary`, `?attr/textAppearanceBodyLarge`, etc.)
- Never hardcode colors as hex values in layouts or code
- Dynamic colors are applied at the Application level; individual views should not manage this

### Android Auto

- Use only Car App Library templates (currently PaneTemplate)
- Do NOT call `setBackgroundColor()` on buttons; the host controls button styling
- `CarColor.PRIMARY` renders as white; this is expected host behavior
- Keep text extremely short (1-3 words for state labels)
- Test on Desktop Head Unit (DHU) during development

## File Organization

### Where New Code Goes

| Type | Location |
|---|---|
| Data models | `data/model/` |
| API interfaces | `data/api/` |
| Repository classes | `data/repo/` |
| Hilt modules | `di/` |
| Mobile UI (ViewModels) | `ui/mobile/` |
| Mobile UI (Activities, custom views) | `ui/` or root package |
| Android Auto UI | `ui/auto/` |
| Billing | `billing/` |
| Preference constants | `data/PrefKeys.kt` |

### File Naming

- One class per file (with the exception of closely related small classes like data models in `ApiModels.kt`)
- File name matches the primary class name
- Kotlin files use `.kt` extension (obviously)

## Resource Rules

### Strings

- All user-facing text MUST be in string resources (`strings.xml`), never hardcoded
- Every new string must have both English and Dutch translations
- English: `values/strings.xml` (default)
- Dutch: `values-nl/strings.xml`
- Naming pattern: `category_name` (e.g., `status_locked`, `error_network`, `btn_lock`)

### Drawables

- Icons: Material Symbols Rounded Outlined, weight 200, 28dp
- File naming: `ic_` prefix followed by descriptive name in snake_case
- Provide appropriate density variants or use vector drawables

### No Prefix Pollution

Do NOT use the `flux_` prefix on any resource or identifier. This is a personal project, not an agency project.

## Performance Rules

- Do NOT block the main thread with API calls (Retrofit + coroutines handle this)
- Use `viewModelScope` or `lifecycleScope` for coroutine launches
- Avoid creating objects in `onDraw()` or other frequently-called methods
- ShapeMorphView and ExpressiveCardAnimator handle their own animation performance; do not add redundant invalidation calls
- Polling uses coroutine delays, not Handler/Timer

## Error Handling Rules

- Catch exceptions in the repository layer, not in ViewModels or UI
- Map all exceptions to `NukiError` sealed class
- Always provide user-facing error messages via `toLocalizedString()`
- Never show raw exception messages, stack traces, or HTTP status codes to the user
- Log errors for debugging but do not expose technical details in the UI

## Security Rules

- API token and lock ID stored only in EncryptedSharedPreferences
- Never log the API token or include it in error messages
- Never commit credentials to version control
- `local.properties` is in `.gitignore`

## Testing Rules

- Currently no test suite exists (this is a known gap)
- When adding tests, prioritize:
  1. `NukiRepository`: state transitions, error mapping, polling logic
  2. `NukiError.toLocalizedString()`: correct messages for each error type
  3. `LockState` enum: mapping from API codes to enum values
- Use JUnit 5 for unit tests
- Use MockK for mocking (Kotlin-native, works well with coroutines)
- Use Turbine for testing StateFlow emissions

## Libraries: Allowed and Disallowed

### Currently Used (do not replace)

| Library | Purpose |
|---|---|
| Hilt | Dependency injection |
| Retrofit 3 | HTTP client |
| OkHttp 5 | HTTP transport |
| Material Components | UI components and theming |
| Jetpack Security (EncryptedSharedPreferences) | Credential storage |
| Google Play Billing 8 | Donations |
| Car App Library 1.7.0 | Android Auto |

### Do NOT Add

| Library/Category | Reason |
|---|---|
| Jetpack Compose | View-based UI is a deliberate choice |
| Room / SQLite | No local database needed; preferences suffice |
| Analytics SDKs (Firebase, etc.) | No tracking policy |
| Ad SDKs | No ads policy |
| Kotlin Serialization / Moshi | Retrofit 3 handles JSON; no need for a separate library unless Retrofit changes |
| Navigation Component | Only 2 screens; `startActivity()` is sufficient |

### May Add If Needed

| Library | Use Case |
|---|---|
| WorkManager | Background polling for notifications feature |
| Coil / Glide | If image loading becomes necessary |
| Timber | Structured logging (evaluate if logging needs grow) |
| Turbine | StateFlow testing |
| MockK | Mocking in tests |

## Git Conventions

- Commit messages: imperative mood, concise summary line (e.g., "Add open delay countdown animation")
- Branch naming: `feature/description`, `fix/description`, `chore/description`
- Keep commits focused; one logical change per commit

## How AI Assistants Should Write Code

When generating code for this project:

1. Follow all conventions listed in this document without exception
2. Read existing code before writing new code to match the established patterns
3. Do not introduce new architectural patterns without explicit instruction
4. Do not refactor working code unless asked to
5. Always provide both EN and NL string translations for new user-facing text
6. Test that Android Auto code works within PaneTemplate constraints
7. Use the existing Hilt setup; do not create new DI frameworks
8. Match the existing KDoc comment style found in the codebase
9. When in doubt about a pattern, look at how `NukiRepository.kt` and `MainActivity.kt` do it
