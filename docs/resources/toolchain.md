# Local toolchain (Windows)

The tools needed to build tvshields from the command line, without Android Studio. Everything
lives on `D:\dev` (the `C:` drive has little free space). Versions recorded on 2026-11-07
(roadmap 0.4).

## Versions

| Tool | Version | Location |
|---|---|---|
| JDK (Microsoft OpenJDK) | 21.0.11 | `C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot` (pre-installed) |
| Android SDK Command-line Tools | 23.0 | `D:\dev\android-sdk\cmdline-tools\latest` |
| Android SDK Platform-Tools (`adb`) | 37.0.1 | `D:\dev\android-sdk\platform-tools` |
| Android SDK Platform | `android-37.0` | `D:\dev\android-sdk\platforms` |
| Android SDK Build-Tools | 37.0.0, plus 36.0.0 (installed by AGP on first build) | `D:\dev\android-sdk\build-tools` |
| Android Gradle Plugin / Kotlin | 9.5.1 / 2.4.21 | `gradle/libs.versions.toml` |
| Android NDK | r30 (30.0.16248370) | `D:\dev\android-sdk\ndk\30.0.16248370` |
| Rust (`stable-x86_64-pc-windows-gnu`, profile `minimal`) | 1.99.1 | `D:\dev\rust` |
| Rust targets | `aarch64-linux-android`, `armv7-linux-androideabi` | — |
| rustup | 1.29.3 | `D:\dev\rust\cargo\bin` |
| `cargo-ndk` | 4.1.2 | `D:\dev\rust\cargo\bin` |
| Gradle (wrapper) | 9.9.0 | Distribution in `D:\dev\gradle\wrapper\dists` |
| GitHub CLI (`gh`) | 2.103.3 | `D:\dev\gh\bin` |

The Rust toolchain is `windows-gnu` so that no Visual Studio Build Tools are needed. The Android
targets are built by the NDK's own clang through `cargo-ndk`.

## User environment variables

| Variable | Value |
|---|---|
| `ANDROID_HOME` | `D:\dev\android-sdk` |
| `ANDROID_NDK_HOME` | `D:\dev\android-sdk\ndk\30.0.16248370` |
| `RUSTUP_HOME` | `D:\dev\rust\rustup` |
| `CARGO_HOME` | `D:\dev\rust\cargo` |
| `GRADLE_USER_HOME` | `D:\dev\gradle` |

Added to the user `Path`: `D:\dev\gh\bin`, `D:\dev\rust\cargo\bin`,
`D:\dev\android-sdk\platform-tools`, `D:\dev\android-sdk\cmdline-tools\latest\bin`.
`JAVA_HOME` is set at machine level by the JDK installer.

## Installing from scratch

1. **Android SDK.** Download the command-line tools zip from
   https://developer.android.com/studio#command-tools, check its SHA-1 and extract it so that
   `bin\` ends up at `D:\dev\android-sdk\cmdline-tools\latest\bin`. Then:
   ```bash
   sdkmanager "platform-tools" "platforms/android-37.0" "build-tools/37.0.0" "ndk/30.0.16248370"
   ```
   Since cmdline-tools 23, `sdkmanager` delegates to the new `android sdk` CLI: package paths use
   `/` instead of `;`, and licenses no longer need `--licenses`.
2. **Rust.** Download `rustup-init.exe` for `x86_64-pc-windows-gnu` from
   https://static.rust-lang.org/rustup/dist/x86_64-pc-windows-gnu/rustup-init.exe, check it
   against its `.sha256`, set `RUSTUP_HOME` and `CARGO_HOME`, then:
   ```bash
   rustup-init.exe -y --no-modify-path --default-host x86_64-pc-windows-gnu --default-toolchain stable --profile minimal -t aarch64-linux-android -t armv7-linux-androideabi
   ```
3. **cargo-ndk.**
   ```bash
   cargo install cargo-ndk --locked
   ```
4. **Gradle.** Nothing to install: `gradlew` downloads Gradle 9.9.0 into `GRADLE_USER_HOME` and
   checks it against `distributionSha256Sum` in `gradle/wrapper/gradle-wrapper.properties`.
5. **gh.** Download `gh_<version>_windows_amd64.zip` from https://github.com/cli/cli/releases,
   check it against the release `checksums.txt` and extract it to `D:\dev\gh`. Then
   `gh auth login --web`.

## Checks

```bash
cargo ndk --version
```

```bash
./gradlew --version
```

```bash
adb version
```

## Building and installing the app

`local.properties` (not versioned) points Gradle to the SDK. Use forward slashes:

```properties
sdk.dir=D:/dev/android-sdk
```

Build the engine first (`engine/build-android.sh`), then the APK, then install it on the TV:

```bash
./gradlew assembleDebug
```

```bash
adb -s 192.168.1.13:5555 install -r app/build/outputs/apk/debug/app-debug.apk
```

On the reference TV, `adb exec-out screencap -p` prints vendor log lines before the PNG data;
strip everything before the `\x89PNG` header to get a valid image.
