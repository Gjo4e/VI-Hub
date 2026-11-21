# VI Hub

Your world. Your guide.

Unofficial Android companion for GTA VI with a Vice City-inspired neon interface.

## Features
- Live news headlines and web search
- Personal mission checklist and progress tracking
- Location directory, saved places and illustrative interactive map
- Game information and official source links
- GitHub release checks, signed APK updates and preferences

## Release pipeline

GitHub Actions builds the application when source changes are pushed to main. The repository's `VIHUB_SIGNING` secret contains a private signing bundle. Keep this key private and persistent across releases. Do not commit the secret, passwords, or keystore. Each new build uses an increasing version code. The app verifies the package, version and signing certificate before presenting Android's installation confirmation.

- Package: `com.vihub.companion`
- Minimum Android: 8.0 (API 26)
- [Build status](https://github.com/Gjo4e/VI-Hub/actions)
- [APK releases](https://github.com/Gjo4e/VI-Hub/releases)

Initial build and device update tests are pending. Map geometry and artwork are illustrative. Not affiliated with Rockstar Games or Take-Two.
