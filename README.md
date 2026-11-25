<p align="center">
  <img src="docs/assets/img/icon.png" width="96" height="96" alt="NuKey Auto icon">
</p>

<h1 align="center">NuKey Auto</h1>

<p align="center">
  Control your Nuki smart lock from Android Auto and your phone.<br>
  Free, open source, no ads, no tracking.
</p>

<p align="center">
  <a href="https://play.google.com/store/apps/details?id=com.ddw.nukeyauto"><img src="https://img.shields.io/badge/Google_Play-Download-414141?logo=googleplay&logoColor=white" alt="Get it on Google Play"></a>
  <a href="https://drkdw.github.io/NuKeyAuto/"><img src="https://img.shields.io/badge/Website-drkdw.github.io-3A4AB8" alt="Website"></a>
  <img src="https://img.shields.io/badge/Android-10%2B-3DDC84?logo=android&logoColor=white" alt="Android 10+">
  <img src="https://img.shields.io/badge/Kotlin-View--based_M3-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin">
</p>

<p align="center">
  <img src="docs/assets/img/auto-locked.png" width="600" alt="NuKey Auto in Android Auto: smart lock locked at 82% battery, door closed, with an Open door button">
</p>

<p align="center">
  <img src="docs/assets/img/phone-unlocked.png" width="180" alt="Phone app, unlocked with the door closed">
  <img src="docs/assets/img/phone-door-open.png" width="180" alt="Phone app, door open with the hint to close it before locking">
  <img src="docs/assets/img/phone-confirm.png" width="180" alt="Phone app, confirmation dialog">
</p>

## Features

- **Android Auto first**: lock status, door and battery with one clear action on your car screen, readable at a glance
- **Safe by design**: optional confirmation and a cancellable countdown before the door opens
- **Nuki Door Sensor**: shows whether the door is open and will not offer to lock an open door; nothing changes without a sensor
- **Phone app**: the same controls as a standalone app, in the NuKey Auto colors or your wallpaper colors, light and dark
- **Demo mode**: try every screen with a simulated lock before you set up your Nuki
- **Private**: no analytics, no tracking; credentials are stored encrypted on the device and only sent to the official Nuki Web API
- **English and Dutch**
- Optional support-the-developer donations via Google Play

## Requirements

- Android 10 (API 29) or higher
- A Nuki smart lock connected to Nuki Web (via a Bridge or the built-in Wi-Fi)
- A Nuki Web API token and your Smart Lock ID, see the [setup guide](https://drkdw.github.io/NuKeyAuto/support.html)

## Build

Open the project in Android Studio and build. Signing credentials are read from `local.properties`, which is not in version control.

```bash
./gradlew assembleDebug
```

Developer documentation lives in [`docs/`](docs): [architecture](docs/ARCHITECTURE.md), [API reference](docs/API_REFERENCE.md) and [roadmap](docs/ROADMAP.md).

## Support

- Questions and setup help: [Support and FAQ](https://drkdw.github.io/NuKeyAuto/support.html)
- Found a bug or have an idea: [open an issue](https://github.com/drkdw/NuKeyAuto/issues)
- [Privacy policy](https://drkdw.github.io/NuKeyAuto/privacy-policy.html)

## License

The source code is licensed under the [GNU General Public License v3.0](LICENSE). You may use, study, change and share it; anything you distribute that is based on it must be released under the same license, with its source code.

The name "NuKey Auto", the app icon and the Google Play listing are not covered by this license. A fork that is published must use its own name and icon.

---

NuKey Auto is not affiliated with Nuki Home Solutions GmbH. Nuki is a registered trademark of Nuki Home Solutions GmbH.
