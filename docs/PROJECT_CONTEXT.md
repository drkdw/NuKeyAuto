# Project Context

## What is NuKey Auto?

NuKey Auto is an Android application that controls Nuki smart locks from both an Android phone and Android Auto. It communicates with the Nuki Web API to lock, unlock, and unlatch doors. The app is a personal project by Dirk De Wever, published on Google Play under the package name `com.ddw.nukeyauto`.

## Goal

Provide a minimal, privacy-respecting way to operate a Nuki smart lock from a car (Android Auto) and from a phone, without analytics, tracking, or ads.

## Features

- Lock, unlock, and unlatch a Nuki smart lock via the Nuki Web API
- Android Auto integration using Car App Library PaneTemplate
- Real-time lock state display with animated shape morphing (Material 3 Expressive)
- Battery level indicators for lock and keypad
- Configurable open delay (0-10 second countdown before unlatching)
- Confirmation dialogs before destructive actions (toggleable)
- Light/dark/system theme with optional Material You dynamic colors
- Encrypted credential storage (EncryptedSharedPreferences)
- Voluntary donation system via Google Play Billing (consumable products)
- Localized in English and Dutch

## Target Audience

Nuki smart lock owners who want quick lock control from Android Auto while driving, or a lightweight alternative to the official Nuki app on their phone.

## Platforms

| Platform | Min SDK | Target SDK | Library |
|---|---|---|---|
| Android phone | 29 (Android 10) | 36 (compileSdk 37) | Standard Android |
| Android Auto | 29 | 36 | Car App Library 1.7.0 |

Android Auto dashboard cards (IOT category) were investigated and confirmed not possible for this app type. The only supported surface is the full Car App Library screen.

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin 2.4 |
| UI toolkit | View-based (XML layouts), NOT Jetpack Compose |
| DI | Hilt 2.60 |
| Networking | Retrofit 3 + OkHttp 5 |
| Design system | Material 3 (Material Components for Android) |
| Local storage | EncryptedSharedPreferences (Jetpack Security) |
| Billing | Google Play Billing Library 9 |
| Build system | Gradle 9.8, Android Gradle Plugin 9.4 |
| Architecture | MVVM + Repository pattern |

## Architecture Summary

The app follows MVVM with a shared repository singleton:

1. **NukiRepository** (singleton) holds a `MutableStateFlow<UiState>` that represents the current lock state, battery info, loading status, errors, and countdown.
2. The phone uses a `@HiltViewModel` `MainViewModel`; the Auto screen calls the same repository directly from its `lifecycleScope`.
3. The mobile UI observes the StateFlow and renders state via `ShapeMorphView` (custom animated view). Android Auto observes the same flow and renders via `PaneTemplate`.
4. API calls go through a Retrofit interface (`NukiApiService`), authenticated with a Bearer token stored in EncryptedSharedPreferences.

## Current Status

- Version 1.2.10, published on Google Play
- Fully functional single-lock control on phone and Android Auto
- All core features complete; remaining work is polish and new features

## Key Decisions Already Made

These decisions are final and should not be revisited:

| Decision | Rationale |
|---|---|
| View-based UI, not Compose | Deliberate choice for this project; do not migrate |
| No analytics/tracking/ads | Privacy-first, non-negotiable |
| EncryptedSharedPreferences for credentials | Simple, secure, sufficient for token + ID storage |
| Hilt for DI | Already integrated; Hilt legacy variant API warnings are from the Hilt plugin itself, not fixable in app code |
| Single lock support | Current scope; multi-lock is a future consideration |
| `enableEdgeToEdge()` with WindowInsets | Already implemented; `fitsSystemWindows` removed from all layouts |
| Google Play edge-to-edge warnings | Originate from Material/AppCompat libraries, not app code |
| Auto icons tinted CarColor.DEFAULT | PRIMARY rendered black on newer hosts; DEFAULT is host-picked and readable |
