# Changelog

## [1.3.0](https://github.com/drkdw/NuKeyAuto/compare/1.2.13...1.3.0) (2026-11-25)


* Release with release-please, like Flux Settings ([f0e0d0c](https://github.com/drkdw/NuKeyAuto/commit/f0e0d0c9b7e72ca2feea40f00ff96207539fd233))


### 🚀 Features

* Brand colors by default and an app header in About ([0205062](https://github.com/drkdw/NuKeyAuto/commit/0205062b6bd5f4dc289e1d3565b99fd6b0bbdadd))
* Low battery stands out in the error color (phone) and yellow (car) ([22c60cb](https://github.com/drkdw/NuKeyAuto/commit/22c60cb7f613adfd7f2fe95f5676ab715f590484))
* One message bar at the bottom for hints and errors ([c260843](https://github.com/drkdw/NuKeyAuto/commit/c2608437c1e8e921f0eef4c5d650ae68040a63f7))
* Rebrand to indigo with an amber accent ([0e751d8](https://github.com/drkdw/NuKeyAuto/commit/0e751d8b262d8c3541f06508934196b94a35583a))
* Rebrand to indigo with an amber accent ([7e374fd](https://github.com/drkdw/NuKeyAuto/commit/7e374fdc6174b7897dfc875256c8490fa6ceff7d))
* Show the Nuki Door Sensor state and adapt the actions to it ([#3](https://github.com/drkdw/NuKeyAuto/issues/3)) ([afd9504](https://github.com/drkdw/NuKeyAuto/commit/afd950469058ef2eff290548f0127ad58b9b1afe))
* Website and Play Store images in the new brand ([e016995](https://github.com/drkdw/NuKeyAuto/commit/e016995ef0a48276c20c8b4c24572a2dbfa1568d))


### 🐛 Bug fixes

* Battery percentage in the car title; lock reads Unlocked when a sensor shows the door ([ddf43dd](https://github.com/drkdw/NuKeyAuto/commit/ddf43dd24fd552bc3d08b869852dff2cb5ec989a))
* Car lock row icon matches 'Unlocked' when the door row shows the open door ([88571d5](https://github.com/drkdw/NuKeyAuto/commit/88571d5388037b4aec410b1d70dc0faaa27019d7))
* Car shows one quiet status row; door before battery on phone and car ([57d4806](https://github.com/drkdw/NuKeyAuto/commit/57d4806dba46ba9f047fead043dd0ae3cda9d24c))
* Car shows the door as its own row, only when the sensor is sure ([86cd6af](https://github.com/drkdw/NuKeyAuto/commit/86cd6af94581fbaf11bd583ca99f407a3ccade74))
* Center the door hint icon and text as one group on the page axis ([1e2926c](https://github.com/drkdw/NuKeyAuto/commit/1e2926ce0318be64c6f8f76ff31265a53341b8c0))
* Door hint as a compact label under the status; action area stays empty ([617422d](https://github.com/drkdw/NuKeyAuto/commit/617422d33a09446929dd0fea376b5c3be3e8eb54))
* Door hint as a start-aligned message with icon, not a button look-alike ([1268d9e](https://github.com/drkdw/NuKeyAuto/commit/1268d9eb8454634a7b0baaa0e2378f7723155033))
* Door hint without icon and with card corners so it is not mistaken for a button ([6d3d2a7](https://github.com/drkdw/NuKeyAuto/commit/6d3d2a7f6ae6145a8067f5fdf51d0e3c2e78b61d))
* Door state as a second line of the car status row ([6e83559](https://github.com/drkdw/NuKeyAuto/commit/6e83559ebf12ffd993559dd681428b0e48b0214d))
* Handle billing errors, HTTP 429 and repeated error messages ([434c656](https://github.com/drkdw/NuKeyAuto/commit/434c65661ca2f451c45a701da74e6c2bf453f7b2))
* Keep the error color on the phone's low battery text ([1539c38](https://github.com/drkdw/NuKeyAuto/commit/1539c3891f7ac6366938eb9289532a8304604256))
* Make lock actions reliable and add a demo mode ([5697815](https://github.com/drkdw/NuKeyAuto/commit/56978154b30350dde9b3e1e4f42c29ebfb002471))
* Make the 'close the door to lock' hint stand out ([b54a919](https://github.com/drkdw/NuKeyAuto/commit/b54a9191b916d22abab5f74be8ba9873c92a6019))
* Never leave an unconfirmed action on screen or redirect it ([3ade684](https://github.com/drkdw/NuKeyAuto/commit/3ade684da702c019d326070e29fa9bd2dd5fe3f0))
* Reliable lock actions, demo mode and release-please ([560d942](https://github.com/drkdw/NuKeyAuto/commit/560d942fc13ad40d59d6f8046cf348828abd9d3c))
* Show 'close the door to lock' as readable text, not a disabled button ([8e527d1](https://github.com/drkdw/NuKeyAuto/commit/8e527d1604c95ccab57f552755e9c7cf9ffd2922))
* Show the lock status in the car during an action ([95a8c14](https://github.com/drkdw/NuKeyAuto/commit/95a8c14b74d620c8ddd5ab56626516c271258b50))
