# Requisitos de una app para Android TV

Guía oficial: https://developer.android.com/training/tv/get-started/create

## Manifiesto

Para que la app **aparezca en la pantalla de inicio de la TV** y se pueda instalar en
TVs sin pantalla táctil:

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

- `banner`: imagen de **320×180 px** que se ve en el inicio de la TV.
- `leanback` con `required="false"` permite instalarla también en cajas Android "no certificadas".

## Control remoto

| Tecla | `KeyEvent` |
|---|---|
| Flechas | `KEYCODE_DPAD_UP/DOWN/LEFT/RIGHT` |
| OK | `KEYCODE_DPAD_CENTER`, `KEYCODE_ENTER` |
| Atrás | `KEYCODE_BACK` |
| Menú (no siempre existe) | `KEYCODE_MENU` |
| Reproducir/pausa | `KEYCODE_MEDIA_PLAY_PAUSE` |

Los clics del cursor virtual se simulan enviando `MotionEvent.ACTION_DOWN` y `ACTION_UP` a la WebView
en las coordenadas del cursor.

## Interfaz

- Jetpack Compose for TV: https://developer.android.com/training/tv/playback/compose
- Distancia de lectura ~3 m: texto ≥ 18 sp y foco siempre visible.
- Overscan: dejar ~5 % de margen en los bordes para la UI propia.

## Instalación sin Play Store (sideload)

```bash
adb connect <IP-de-la-TV>:5555
```

```bash
adb install -r tvshields.apk
```

En la TV: Ajustes → Preferencias del dispositivo → Información → pulsar 7 veces "Compilación" para
activar las Opciones de desarrollador, y luego activar "Depuración por red/USB". Los menús varían
por fabricante.
