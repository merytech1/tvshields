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

Run the instrumented tests (JNI smoke test) on the TV:

```bash
ANDROID_SERIAL=192.168.1.13:5555 ./gradlew :app:connectedDebugAndroidTest
```

On the reference TV, `adb exec-out screencap -p` prints vendor log lines before the PNG data;
strip everything before the `\x89PNG` header to get a valid image.

## CI (GitHub Actions)

`.github/workflows/ci.yml` runs on every pull request and on pushes to `develop` and `main`
(roadmap 9.1). It uses the same versions as the table above (NDK, `cargo-ndk`, JDK 21, stable Rust,
the Gradle wrapper) on `ubuntu-latest`:

| Job | Steps |
|---|---|
| Rust engine tests | `cargo test --locked` in `engine/` |
| APK and Kotlin tests | `engine/build-android.sh`, then `./gradlew :app:testDebugUnitTest`, then the debug, release (unsigned) and instrumented-test APKs. The debug and release APKs are kept as a build artifact for 14 days. |

The instrumented tests (`app/src/androidTest`) are built but not run: the engine only exists for
ARM, and they run on the TV with `connectedDebugAndroidTest`. `.github/workflows/resources.yml`
checks the resource generator separately, when `tools/resources/` or `engine/` change.

The toolchain setup and the engine build are shared by both APK workflows through
`.github/actions/android-build`.

## Release signing

`.github/workflows/release.yml` builds `app-release.apk` signed with the release key (roadmap
9.2). It runs when a `v*` tag is pushed, or by hand from the Actions tab ("release", "Run
workflow"). It checks the signature with `apksigner`, prints the certificate and the APK's SHA-256,
and keeps the APK as the `release-apk` artifact.

The key never enters the repository. It lives in two GitHub secrets:

| Secret | Value |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | The PKCS12 keystore, in base64 |
| `RELEASE_KEYSTORE_PASSWORD` | Its password (PKCS12 uses it for the key too) |

`app/build.gradle.kts` signs the release build only when `TVSHIELDS_KEYSTORE` (path to the
keystore) and `TVSHIELDS_KEYSTORE_PASSWORD` are set; the key alias is `tvshields` unless
`TVSHIELDS_KEY_ALIAS` says otherwise. Without them, local and CI builds stay unsigned.

**Creating the key (once, by the maintainer).** In PowerShell, outside the repository. `keytool`
asks for the password; use a long one and keep it in a password manager:

```powershell
New-Item -ItemType Directory -Force D:\dev\keys
```

```powershell
& "$env:JAVA_HOME\bin\keytool.exe" -genkeypair -storetype PKCS12 -keystore D:\dev\keys\tvshields-release.p12 -alias tvshields -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=tvshields"
```

Then store it in the repository secrets. Pass the base64 with `--body`, not through a pipe:
Windows PowerShell 5.1 adds a byte order mark and a line break to piped text, and the workflow
then fails with `base64: error: invalid input`. `gh` asks for the password without echoing it:

```powershell
gh secret set RELEASE_KEYSTORE_BASE64 --repo merytech1/tvshields --body ([Convert]::ToBase64String([IO.File]::ReadAllBytes("D:\dev\keys\tvshields-release.p12")))
```

```powershell
gh secret set RELEASE_KEYSTORE_PASSWORD --repo merytech1/tvshields
```

**Keep a backup** of `tvshields-release.p12` and its password outside this PC. Android only
installs an update signed with the same key: if the key is lost, users have to uninstall the app
to install a new version.

**Release certificate.** Created on 2026-11-19. Every published tvshields APK is signed with it;
check a downloaded APK with `apksigner verify --print-certs`:

| Field | Value |
|---|---|
| Subject | `CN=tvshields` (RSA 4096, valid 10,000 days) |
| SHA-256 | `a5:06:fa:b3:36:7c:d9:71:bb:55:93:f7:74:27:2c:0e:51:1b:3c:04:f9:7d:7f:c0:17:7c:45:81:60:d9:4e:67` |

First signed build: release run on `develop` at `21864c1`, `app-release.apk` (4.1 MB, APK
signature scheme v2).

APKs from this key do not install over a build signed with the debug key (the one used on the TV
so far): uninstall the debug build once before installing a release.
