# API Reference

## Base Configuration

| Property | Value |
|---|---|
| Base URL | `https://api.nuki.io/` |
| Authentication | Bearer token in `Authorization` header |
| Content-Type | `application/json` |
| Client | Retrofit 3 + OkHttp 5 |

## Authentication

All requests require a valid Nuki Web API token:

```
Authorization: Bearer <NUKI_TOKEN>
```

The token is obtained by the user from the Nuki Web dashboard (https://web.nuki.io). It is stored locally in EncryptedSharedPreferences and injected into every request via an OkHttp interceptor configured in `AppModule.kt`.

There is no OAuth flow, no refresh token, and no token expiration handling. If the token becomes invalid, the user must enter a new one in Settings.

## Endpoints

### GET /smartlock/{id}

Fetches the current state of a smart lock.

**Path parameters:**

| Parameter | Type | Description |
|---|---|---|
| `id` | String | Smart Lock ID (stored as `NUKI_ID` in preferences) |

**Response: `SmartLockResponse`**

Key fields in the response:

| Field | Type | Description |
|---|---|---|
| `state.state` | Int | Lock state code (see Lock States table below) |
| `state.batteryCritical` | Boolean | Whether lock battery is critically low |
| `state.batteryCharge` | Int | Lock battery percentage (0-100) |
| `state.keypadBatteryCritical` | Boolean | Whether keypad battery is critically low |

**Example request:**

```
GET https://api.nuki.io/smartlock/123456789
Authorization: Bearer abc123def456
```

**Example response (abbreviated):**

```json
{
  "smartlockId": 123456789,
  "name": "Front Door",
  "state": {
    "state": 1,
    "stateName": "locked",
    "batteryCritical": false,
    "batteryCharge": 87,
    "batteryCharging": false,
    "keypadBatteryCritical": false
  }
}
```

---

### POST /smartlock/{id}/action

Sends an action command to the smart lock.

**Path parameters:**

| Parameter | Type | Description |
|---|---|---|
| `id` | String | Smart Lock ID |

**Request body: `ActionRequest`**

| Field | Type | Description |
|---|---|---|
| `action` | Int | Action code (see Action Codes below) |

**Action codes:**

| Code | Action | Description |
|---|---|---|
| 2 | Lock | Lock the door |
| 3 | Unlatch | Unlatch (open) the door |

Note: "Unlock" is achieved by unlatching (action 3). The API does not have a separate unlock-without-unlatch action used by this app.

**Example request:**

```
POST https://api.nuki.io/smartlock/123456789/action
Authorization: Bearer abc123def456
Content-Type: application/json

{
  "action": 3
}
```

**Example response:**

```
HTTP 204 No Content
```

A successful action returns 204 with no body. The lock state change is confirmed by subsequent polling.

## Lock States

| Code | Name | Description | UI Shape |
|---|---|---|---|
| 0 | Uncalibrated | Lock needs calibration | Burst |
| 1 | Locked | Door is locked | Diamond |
| 2 | Unlocking | Transitioning to unlocked | Cookie6 |
| 3 | Unlocked | Door is unlocked | Cookie4 |
| 4 | Locking | Transitioning to locked | Cookie6 |
| 5 | Unlatched | Door is unlatched (opened) | Sunny |
| 7 | Unlatching | Transitioning to unlatched | Cookie6 |
| 254 | Motor Blocked | Lock motor is physically jammed | Burst |
| 255 | Undefined | Unknown state or lock offline | Burst |

## Error Handling

### HTTP Error Codes

| Code | NukiError Type | User Message (EN) | Cause |
|---|---|---|---|
| 401 | Unauthorized | Invalid or expired API token | Bad token |
| 403 | Unauthorized | Token lacks permission | Insufficient scope |
| 404 | NotFound | Smart lock not found | Wrong lock ID |
| 429 | RateLimited | Too many requests, try again later | API rate limit hit |
| 500-599 | ServerError | Nuki server error | Backend issue |

### Network Errors

Connectivity failures (no internet, DNS resolution failure, timeout) are caught as `NetworkError` and presented separately from HTTP errors.

### Error Flow

1. Retrofit throws exception or returns error response
2. `NukiRepository` catches and maps to `NukiError` sealed class
3. Error is set in `UiState.error`
4. `NukiError.toLocalizedString()` provides user-facing message in EN or NL
5. UI displays error (burst shape + shake on mobile, text on Auto)
6. Error clears on next successful API call or manual refresh

## Polling Behavior

After sending an action (lock or unlatch), the app polls to confirm the state change:

```
Action sent
   │
   ▼
Wait 3.5 seconds (initial delay for hardware response)
   │
   ▼
Poll GET /smartlock/{id} ──► State matches expected? ──► Done, update UI
   │                                    │
   │                                    No
   │                                    │
   ▼                                    ▼
Wait 2 seconds                   Attempt < 5?
   │                                │        │
   │                               Yes       No
   │                                │        │
   └────────────────────────────────┘        ▼
                                         Stop polling,
                                         show last known state
```

**Polling parameters:**

| Parameter | Value |
|---|---|
| Initial delay | 3.5 seconds |
| Poll interval | 2 seconds |
| Max attempts | 5 |
| Total max wait | ~13.5 seconds |

The initial 3.5-second delay exists because the Nuki lock hardware takes time to physically execute the action. Polling immediately after sending the command would return the old state.

## Rate Limiting

The Nuki Web API enforces rate limits. When hit (HTTP 429), the app:

1. Stops the current operation
2. Shows a "rate limited" error message
3. Does NOT automatically retry (user must manually retry)

To avoid rate limiting:
- Polling uses a conservative 2-second interval
- The app does not make parallel API calls
- Pull-to-refresh is throttled by SwipeRefreshLayout's built-in behavior

## Open Delay (Pre-Action Countdown)

When `OPEN_DELAY_SECONDS > 0` and the user requests unlatch:

1. Countdown starts from configured value (1-10 seconds)
2. `countdownSeconds` is emitted in UiState each second
3. The API call is NOT sent until countdown reaches 0
4. User can cancel during the countdown
5. After countdown completes, the normal action + polling flow begins

This is purely a client-side delay; no API interaction occurs during the countdown.
