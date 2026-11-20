# RustCraft Launcher

RustCraft is an Android-first Minecraft Java launcher maintained in place under
the existing project name. It has an original Minecraft Vanilla-inspired
interface, a native Rust core exposed over JNI, offline-profile management,
the 26.3-only version manifest filter, checksum-verified client downloads,
live install progress, and app-private add-on folders.

## Honest compatibility status

This build does **not** claim or provide a playable Android port:

- It does not launch Minecraft Java Edition.
- It does not download or bundle an Android Java runtime.
- It does not download the complete library and asset graph.
- It does not integrate Horizon's desktop game engine or UI. Horizon is a
  LGPL-3.0 Rust/Minecraft project that is not an official Android port; its
  source currently has no supported Android distribution or launcher backend
  package for this app to reuse.
- Microsoft and Ely.by are account choices in the UI, but they are not
  authenticated account providers in this build.
- Touch controls and keyboard/mouse switching are only launcher settings/placeholders;
  they are not connected to a running game.
- Mods and resource packs have private import/folder-management support only.
  The app does not browse, resolve loader compatibility, download, activate,
  or verify them from a marketplace. Forge and Fabric are not falsely enabled.

## Architecture

- `app/`: native Android application (minimum Android 10 / API 29), Compose UI,
  coroutine-based I/O, and app-scoped storage (no broad storage permission).
- `rust/`: small synchronous Rust library; JNI commands handle version metadata,
  verified client downloads and live progress, offline profiles, settings, and
  managed files.
- The game directory lives in the app's external files directory. Users can
  import files through Android's Storage Access Framework.

The design follows the general separation of UI, account/profile management,
version metadata, and game files used by public Android launchers such as
[Amethyst](https://github.com/AngelAuraMC/Amethyst-Android) and
[Fold Craft Launcher](https://github.com/FCL-Team/FoldCraftLauncher), without
copying their implementation. Mojang's public version manifest is the source
of vanilla version metadata. RustCraft is independent and is not affiliated
with Mojang or Microsoft. Its visual design is original; it does not use
Apple logos or imply Apple affiliation.

[Horizon](https://github.com/horizon-rust/horizon) is separately licensed
under LGPL-3.0-or-later and documents a desktop Linux/macOS/Windows Rust game
engine, not an Android launcher library. Its game engine and UI are therefore
not copied or linked into this Android project.

## Build

Prerequisites:

- JDK 17
- Android SDK with platform 35 and NDK 27.2.12479018
- Rust stable and `cargo-ndk` (`cargo install cargo-ndk`)
- Rust targets `aarch64-linux-android`, `armv7-linux-androideabi`,
  `x86_64-linux-android`, and `i686-linux-android`
- Android SDK environment configured (for example, `ANDROID_HOME`)

```sh
rustup target add aarch64-linux-android armv7-linux-androideabi \
  x86_64-linux-android i686-linux-android
./gradlew assembleRelease
```

The Android `preBuild` task compiles the Rust library for the four Android ABIs
and packages it into the APK. The release variant is R8-shrunk and signed with
the local Android debug key for direct testing; use a private release keystore
before publishing through an app store. Install it with
`adb install app/build/outputs/apk/release/app-release.apk`.

Rust core tests can be run on a host with `cargo test --manifest-path rust/Cargo.toml`.

## Controls

There is no playable game window in this build, so no touch or keyboard/mouse
mapping is active. Touch-control size, transparency and enablement preferences
are saved by the launcher for a future Android Horizon runtime. The controls
planned for that integration are movement/virtual joystick, jump, sneak,
sprint, attack, interact and inventory.

## Account behavior

- Offline: creates a local-only profile.
- Microsoft: launches no fake login and stores no fake token. Official browser
  OAuth requires a registered Microsoft identity client and Xbox Live/Minecraft
  entitlement services that are not configured in this project.
- Ely.by: no credentials are transmitted or stored. Ely.by authentication
  requires a registered application/API integration and 2FA flow that is not
  implemented.

## Storage and downloads

The app writes only to its own Android files directory, under `games/`; Android
removes these files if the app is uninstalled. No
`MANAGE_EXTERNAL_STORAGE` permission is requested. Imported files are copied
from the user's chosen document provider into the appropriate managed folder.
Vanilla installation downloads the version JSON and client JAR and verifies
the SHA-1 hashes published by Mojang before moving files into place. Progress
shows the current download stage, percentage, byte counts, and transfer rate.
Up to three offline profiles can be stored. Microsoft and Ely.by account
sign-in are not configured in this build. The launcher does not yet download
libraries/assets or make a version launchable.

## License

Project source is licensed under the MIT License. Minecraft is a trademark of
Mojang Studios. This project is not approved by or associated with Mojang or
Microsoft.
