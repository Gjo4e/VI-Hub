# VI Hub

**Your world. Your guide.**

An unofficial Android companion for GTA VI, with a Vice City-inspired neon interface.

## Initial app scope

- News feed with source links and cached headlines
- Mission checklist and progress tracking
- Location directory, saved places and an illustrative interactive map
- Web search and official game information
- Settings, release alerts and GitHub update checking

## Android project

Package: `com.vihub.companion` · Minimum Android 8.0 · Initial version 1.0.0

The first source package is prepared separately and needs to be extracted into the repository root (including `app/`, `.github/`, `build.gradle`, `settings.gradle` and `gradle.properties`). Once committed, the included GitHub Actions workflow can build a test APK. Versioned releases require a persistent release signing key stored securely in GitHub Actions secrets. Do not commit the key or passwords.

See the README included in the source package for detailed build and release instructions. The updater checks this repository's releases, verifies the package and signing certificate, and uses Android's installation confirmation.

## Links

- [Repository](https://github.com/Gjo4e/VI-Hub)
- [Releases](https://github.com/Gjo4e/VI-Hub/releases)
- [Actions](https://github.com/Gjo4e/VI-Hub/actions)

Fan-made, not affiliated with Rockstar Games or Take-Two. Map geometry and concept artwork are illustrative, not official game assets.
