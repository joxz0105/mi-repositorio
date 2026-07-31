# Port de Gish 1.2 a Android moderno

El ZIP original (`Gish1.2.zip`) ya era un port a Android — el de EXL, de 2018.
El problema es que ese proyecto ya no compila ni se instala hoy: apunta a
AGP 3.0.1, `compileSdk 23`, `targetSdk 14` y jcenter, que está apagado desde 2021.
Este directorio es ese proyecto llevado al Android actual.

**No se ha compilado.** El contenedor donde se hizo el port no tiene acceso de
red a `dl.google.com`, así que no se pudieron descargar el SDK ni el NDK. Los
cambios están revisados y las fuentes Java verificadas sintácticamente, pero
la primera compilación real es la de CI
(`.github/workflows/gish-android.yml`) o la tuya en local.

## Qué se cambió

### Build

| | Antes | Ahora |
|---|---|---|
| Gradle | 4.1 | 8.7 |
| Android Gradle Plugin | 3.0.1 | 8.5.2 |
| Repositorios | jcenter (apagado) | google + mavenCentral |
| `compileSdk` | 23 | 34 |
| `minSdk` / `targetSdk` | 9 / 14 | 21 / 34 |
| Java | 6/7 implícito | 17 |
| ABIs | armeabi-v7a, x86 | + arm64-v8a, x86_64 |

- `package="…"` salió del manifiesto y pasó a ser `namespace` en
  `gish/build.gradle`; `versionCode`/`versionName`/`uses-sdk` pasaron a
  `defaultConfig`. Bajo AGP 8 declararlos en el manifiesto es un error.
- Se añadió `src/main/cpp/Application.mk` (antes no existía).
- `proguard-rules.txt` se referenciaba en `build.gradle` pero **no existía** en
  el repo. Ahora existe, con las reglas `-keep` de los símbolos que el motor
  busca por nombre desde JNI (`GishActivity.doVibrate`, `openUrl`, los campos
  de `GishSettings`).

### 64 bits

Google exige arm64 desde 2019 y hay dispositivos sin soporte de 32 bits, así
que añadir `arm64-v8a`/`x86_64` era obligatorio. Al revisar el código nativo
para eso:

- `SDL2/include/SDL_config_android.h` fijaba `SIZEOF_VOIDP 4`, y
  `OpenAL/config.h` fijaba `SIZEOF_LONG 4`, ambos incondicionalmente pese a que
  el mismo header se usa para todas las ABIs. Ahora son condicionales a
  `__LP64__`, como ya lo eran el resto de los `SDL_config_*.h`.

  Para ser exacto: **ninguno de los dos rompía la compilación hoy**.
  `SIZEOF_VOIDP` no se usa en ningún sitio de SDL 2.0.5, y en OpenAL gana antes
  la rama `HAVE_STDINT_H`. Se arreglan porque son incorrectos y son justo el
  tipo de cosa que muerde al tocar cualquiera de las dos librerías.

- El motor de Gish en sí sí resultó estar limpio para 64 bits: no hay casts
  entre puntero e `int`, ni serialización de structs a disco.
- El ASM ARM de Tremor ya estaba correctamente limitado a `armeabi`/`armeabi-v7a`,
  así que arm64 usa las rutas en C.
- `GL4ES/Android.mk` metía `-DBCMHOST` dentro de `LOCAL_EXPORT_C_INCLUDES`, donde
  ndk-build lo trataba como un directorio de includes; y forzaba un `-include`
  con ruta relativa. Corregidos ambos.

### Instalación y permisos

- **`android:exported` explícito.** Sin esto la app **no se instala** en
  Android 12+. Era el bloqueo más inmediato.
- `GishActivity` tenía un `intent-filter` con `MAIN` que la hacía lanzable desde
  fuera, saltándose el launcher que inicializa sus ajustes. Eliminado; ahora es
  `exported="false"`.
- `configChanges` ampliado con `screenSize|screenLayout|smallestScreenSize|uiMode|density`.
  Con el valor antiguo, en cualquier dispositivo moderno la activity se
  recreaba durante el juego.
- Permisos de almacenamiento acotados con `maxSdkVersion` en lugar de pedirse
  siempre.

### Almacenamiento (el cambio de diseño de verdad)

El port original hacía que el usuario navegara hasta una carpeta cualquiera de
`/sdcard` y le pasaba esa ruta al motor, que hace `chdir()` ahí. **Eso dejó de
funcionar en Android 11**: la File API ya no puede leer archivos no-media en
almacenamiento compartido, con permiso o sin él.

Ahora los datos viven en almacenamiento específico de la app
(`/sdcard/Android/data/ru.exlmoto.gish/files/gishdata`), que no necesita ningún
permiso en ninguna versión. Se llena de tres formas, desde el botón
"Browse files…":

1. **Importar desde un ZIP** — extrae el archivo. Si todas las entradas cuelgan
   de una única carpeta raíz (lo normal al comprimir la carpeta `Gish`), esa
   carpeta se descarta para que `texture/` quede en la raíz.
2. **Importar desde una carpeta** — `ACTION_OPEN_DOCUMENT_TREE` + copia
   recursiva. Si eliges la carpeta contenedora en vez de la de datos, la
   detecta.
3. **Navegar el dispositivo** — el picker antiguo, que ahora **solo se ofrece
   en Android 10 e inferior**, donde todavía puede funcionar.

El extractor de ZIP valida cada entrada contra el directorio destino
(*Zip Slip*): un archivo con entradas `../` podría si no escribir fuera del
destino.

Las partidas y la config no se ven afectadas: el motor ya las escribía en
almacenamiento interno vía `SDL_AndroidGetInternalStoragePath()`
(`cpp/Gish/game/config.c`), así que la carpeta de datos solo necesita lectura.

### Java

- `Vibrator`: `VibratorManager` en API 31+, `VibrationEffect` en API 26+.
- Pantalla completa: `WindowInsetsController` en API 30+, flags de
  `setSystemUiVisibility` por debajo, reaplicado en `onWindowFocusChanged`.
  SDL 2.0.5 es anterior a ambas APIs y no gestiona nada de esto.
- `FLAG_KEEP_SCREEN_ON` durante el juego.
- Soporte de *notch* (`layoutInDisplayCutoutMode: shortEdges`, API 27+).
- `setBackgroundDrawable` y `Resources.getDrawable(int)` → `ContextCompat.getDrawable`.
- Temas: se eliminó la escalera `values-v11`/`v14`/`v21`, ya redundante con
  `minSdk 21`. Se añadió `GishGameTheme` para el juego.

### Bugs corregidos de paso

- **Cuelgue en el explorador de archivos.** `FillListTask` hacía
  `while (listFiles == null)` y, al llegar a la raíz del sistema de archivos,
  volvía a empezar desde el almacenamiento externo. Con un volumen ilegible eso
  es un bucle infinito en un hilo de fondo. Ahora sube un número acotado de
  niveles y se rinde con una lista vacía.
- **Crash al salir.** `writeSettings()` se llama desde `onDestroy()` y hacía
  `Integer.parseInt` / `Float.parseFloat` directos sobre los campos de texto.
  Vaciar el campo de vibración o de zoom y salir tiraba la app con
  `NumberFormatException`. Ahora el parseo cae a los valores por defecto.
- `openUrl` desreferenciaba una activity estática sin comprobar null y no
  capturaba `ActivityNotFoundException` (sin navegador instalado, crash).
- La activity estática y el `Vibrator` se liberan en `onDestroy`.
- `showDialog`/`onCreateDialog`, deprecados y con problemas conocidos al rotar,
  sustituidos por construcción directa de `AlertDialog`.

## Qué queda pendiente

- **Compilar.** Es el punto grande. Es un árbol nativo de 2018 (SDL 2.0.5,
  OpenAL Soft antiguo, GL4ES) contra el NDK r26; espera avisos, y es posible
  que algún `#include` o símbolo de libc haya que ajustar. Los avisos más
  ruidosos ya están silenciados en `Application.mk`.
- **Probar en arm64.** Nada del código sugiere problemas, pero eso no sustituye
  a ejecutarlo.
- SDL sigue siendo 2.0.5. Actualizarlo a 2.28+ resolvería por sí solo bastante
  de la gestión de ciclo de vida y audio, pero es un port aparte: hay parches
  locales en `SDLActivity.java` (`getLibraries()` lee `GishSettings.openGles`)
  y en `SDL_android_main.c`.
- `GishFilePickerActivity` sigue usando `AsyncTask` (deprecado, funcional).
- El juego necesita los archivos de datos de Gish, que **no** se distribuyen
  aquí: hay que comprarlos. Ver `assets_full/ReadMe-GishData.md`.

## Compilar

```sh
cd gish-android
ANDROID_HOME=/ruta/al/android-sdk ./gradlew assembleDebug
```

Necesitas JDK 17, el SDK de Android con la plataforma 34 y el NDK
`26.3.11579264` (o cambia `ndkVersion` en `gish/build.gradle`).

El APK sale en `gish/build/outputs/apk/debug/`.

## Datos del juego

Tras instalar: **Browse files… → Import from a ZIP archive…** y elige un ZIP con
los archivos de Gish. También puedes copiarlos por USB a
`Android/data/ru.exlmoto.gish/files/gishdata` (con `.debug` añadido al nombre
del paquete si es una build de debug).

La carpeta es correcta cuando contiene `texture/face.tga`.
