# Android TV app requirements

Official guide: https://developer.android.com/training/tv/get-started/create

## Manifest

For the app to **show up on the TV home screen** and be installable on TVs without a touchscreen:

```xml
<uses-feature android:name="android.software.leanback" android:required="false" />
<uses-feature android:name="android.hardware.touchscreen" android:required="false" />

<application android:banner="@drawable/banner" …>
  <activity android:name=".ui.MainActivity" android:exported="true">
    <intent-filter>
      <action android:name="android.intent.action.MAIN" />
      <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
    </intent-filter>
  </activity>
</application>
```

- `banner`: a **320×180 px** image shown on the TV home screen.
- `leanback` with `required="false"` also allows installation on "uncertified" Android boxes.

## Remote control

| Key | `KeyEvent` |
|---|---|
| Arrows | `KEYCODE_DPAD_UP/DOWN/LEFT/RIGHT` |
| OK | `KEYCODE_DPAD_CENTER`, `KEYCODE_ENTER` |
| Back | `KEYCODE_BACK` |
| Menu (not always present) | `KEYCODE_MENU` |
| Play/pause | `KEYCODE_MEDIA_PLAY_PAUSE` |

Virtual-cursor clicks are simulated by dispatching `MotionEvent.ACTION_DOWN` and `ACTION_UP` to the
WebView at the cursor coordinates.

## UI

- Jetpack Compose for TV: https://developer.android.com/training/tv/playback/compose
- Viewing distance ~3 m: text ≥ 18 sp and an always-visible focus indicator.
- Overscan: keep a ~5 % margin at the edges for our own UI.

## Installing without the Play Store (sideload)

```bash
adb connect <TV-IP>:5555
```

```bash
adb install -r tvshields.apk
```

On the TV: Settings → Device Preferences → About → press "Build" 7 times to enable Developer
options, then enable "Network/USB debugging". Menus vary by manufacturer.
