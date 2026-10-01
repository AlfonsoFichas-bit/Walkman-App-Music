# Walkman — Análisis de arquitectura y plan de mejora

> Snapshot: 60 clases Kotlin, Compose + Media3 + Room. Compilado con `compileSdk 37`, `minSdk 27`.
> Todos los hallazgos verificados leyendo el código; las citas `archivo.kt:línea` son exactas.

---

## 1. Frontend

### 1.1 Rendimiento de dibujado — el mayor problema

`ui/player/CassetteDrawing.kt:123-146` — `drawWaffleTexture` ejecuta un doble bucle `while` con
`stepX = w * 0.013f` y `stepY = h * 0.021f`. Eso son **~71 columnas × ~44 filas ≈ 3.100 iteraciones**,
cada una con hasta 2 `drawRect`. Son **~5.000 draw calls por frame**.

El `Canvas` de `ui/player/AnimatedCassette.kt:126` captura `animState`, que cambia 60 veces/seg, así que
Compose invalida el lambda completa en cada frame: **la carcasa estática se vuelve a dibujar 60 veces/seg
para un resultado idéntico**. Son ~300.000 draw calls/segundo.

Además, en cada frame se reconstruyen sin cachear:
- `Brush.linearGradient` con una `listOf` de 4 colores — CassetteDrawing.kt:80-89
- `Path()` para el relieve — CassetteDrawing.kt:160-165
- 5 tornillos con susshine, `TextStyle` para los textos del label

**Causa raíz:** la función mezcla dibujo estático (carcasa, waffle, tornillos, label, reflejos) y animado
(bobinas, cinta,Wndows). Está todo dentro del mismo `DrawScope` invalidado.

**Fix:** extraer la capa estática a `Modifier.drawWithCache` (o a un `ImageBitmap` cacheado por
tamaño/color) y dejar en el draw path solo lo que depende de `animState`.

### 1.2 Recomposición

| Hallazgo | Ubicación |
|---|---|
| **46 usos de `collectAsState`, 0 de `collectAsStateWithLifecycle`** | 10 archivos de `ui/` |
| `lifecycle-runtime-compose` **no está en el catálogo** | `gradle/libs.versions.toml` |
| Polling de 500 ms que reescribe el `StateFlow` completo → recompone toda la UI del player 2×/s | `PlayerControllerViewModel.kt:130-143` |
| `PlayerUiState` **no anotado `@Immutable`/`@Stable`** y contiene `List<Song>` → Compose lo trata inestable y propaga recomposiciones | `PlayerControllerViewModel.kt:46` |
| `albums.sortedByDescending { it.year }.take(10)` **dentro de un `item {}`** → ordenación de la colección completa en cada recomposición | `HomeScreen.kt:124` |
| Las listas usan `key` pero **sin `contentType`** | `LibraryScreen.kt:228,262,343,416`, `AlbumArtCarousel.kt:256` |
| `SongMetadataEditorState` es el único `@Stable` de toda la app; hay **0 `@Immutable`** | `SongMetadataEditor.kt:31` |

### 1.3 I/O en el hilo principal

`model/Models.kt:29-34` — `Song.albumArtUri` es un getter que ejecuta `File.exists()` vía
`CoverStore.resolve` (CoverStore.kt:24-25). Se evalúa en:

- `Models.kt:84` (`toMediaItem()`) → invocado desde `PlayerControllerViewModel.kt:288,378,431,445,578`.
  `playSongs()` sobre 2.000 canciones hace **2.000 `File.exists()` en el hilo principal**.
- La composición de listas: `AlbumArtCarousel.kt:295`, `SongListItem.kt:65`, `HomeScreen.kt:211,292,405`,
  `MiniPlayerBar.kt:124`, `AlbumDetailScreen.kt:66,83`. En un `LazyColumn` es **un syscall de disco por
  ítem, por scroll**.

Este es también el motivo de que los backups de `CoverStore` en la UI sean lentos: la información de
"¿esta canción tiene cover?" debería ser un **flag precalculado en el escaneo de biblioteca**, no una
consulta al sistema de archivos en cada lectura.

### 1.4 Theming — el hueco más grande

`ui/theme/Theme.kt:47-53`:

```kotlin
val colorScheme = when {
    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> { ... }
    else -> WalkmanDarkColorScheme      // ← SIEMPRE oscuro
}
```

- **No existe un `colorScheme` claro.** En API < 31 la app es **siempre oscura**; en API 31+ el "claro" es
  el dynamic del sistema, no un light scheme de marca. La identidad visual (morado/violeta) solo se ve en
  dispositivos Android 12+ cuando el dynamic color está activo.
- `dynamicColor = true` está **fijo y sin opción** en `SettingsScreen` → el usuario no puede forzar la marca.
- `enableEdgeToEdge` está **dentro del composable** con un cast a `ComponentActivity` (Theme.kt:56) que se
  reevalúa en cada recomposición del tema y traga en silencio el caso "no es Activity". Debería estar en
  `MainActivity.onCreate` (que ya lo llama en MainActivity.kt:25 — está duplicado).
- No se pasan `Typography` ni `Shapes` custom (Theme.kt:63-67), así que se usan los de Material por defecto.
- **Tokens sobreescritos por pantalla:** `titleMedium` con `fontSize = 20.sp` hardcodeado
  (PlayerHeader.kt:136), `labelMedium` con color explícito (PlaybackControls.kt:274,279), dimensiones
  bespoke (logo 34×17 dp PlayerHeader.kt:127, iconos de transporte 30/22 dp PlaybackControls.kt:334,205,219).
- **La paleta de marca se sustituye por la portada** (ArtworkColorExtractor.kt:39-44 + gradiente por álbum
  inyectado en `PlayerUiState`). Efecto: la app se ve distinta en cada pantalla y nunca se verifican las
  garantías de marca.
- `Color.White.copy(alpha=…)` en 5 sitios de `PlaybackControls.kt` (262, 275, 280, 304) y
  `WalkmanNavHost.kt:247` (`black@0.3f` sobre el gradiente) → candidatos claros a fallar 4.5:1.
  **No hay tokens semánticos de "texto secundario"**, así que el contraste no es auditable de forma central.
- Falta un sistema de espaciado y de `elevation`.

### 1.5 Accesibilidad — esencialmente cero

**0 usos** de `Modifier.semantics`, `Role.*`, `stateDescription`, `heading()`, `liveRegion` o
`clearAndSetSemantics` en toda la app. Todo depende de los roles implícitos de Material3.

- **El cassette es inaccesible** — `CassetteDrawing.kt:32` es un `Canvas` sin `contentDescription`, sin
  `stateDescription` y sin `progressBarRangeInfo`. No hay forma de saber si la cinta gira, qué pista está
  cargada ni en qué punto va. **Toda la identidad de la app es un bitmap mudo para TalkBack.**
- **Objetivos pulsables sin etiqueta:**
  - `PlayerHeader.kt:115-129` — logo de 40 dp con `.clickable` e `Icon(contentDescription = null)`:
    sin nombre **ni rol**. Y es el **único disparador accesible del rail** (que además se abre con drag).
    Un usuario de TalkBack no tiene camino fiable para abrir el menú de navegación.
  - `WalkmanNavHost.kt:244-252` — scrim del rail sin descripción ni `Role.Button`.
  - `LibraryScreen.kt:413` — celda "crear artista".
  - `QueueScreen.kt:238` — fila de reordenar.
- **`"Menu"` hardcodeado** en `WalkmanScaffold.kt:127`, pese a existir `R.string.cd_open_drawer`.
- **Toggles sin estado anunciado:** shuffle (PlaybackControls.kt:201), repeat (:215) y favorito
  (AlbumArtCarousel.kt:192) solo describen la acción, no si están activos → falta
  `Modifier.toggleable(value, role = Role.Switch)`.
- **9 `IconButton` por debajo de 48 dp** (el mínimo táctil): `PlayerHeader.kt:70,83,117,145,172,198` y
  `AlbumArtCarousel.kt:184,199,210`. Agravante: `PlayerHeader.kt:142` usa `spacedBy(0.dp)`, dejando
  objetivos contiguos sin separación. Bien resuelto: `TransportIconButton` (PlaybackControls.kt:306-308).
- **Ningún `heading()`** en los títulos de pantalla (TrackDetailContent.kt:116, ArtistDetailScreen.kt:144,
  AlbumDetailScreen.kt:84) → se pierde la navegación por encabezados.
- **Selección no anunciada** en el rail (WalkmanNavigationRail.kt:64,84).
- **Títulos duplicados en el foco:** el cover lleva el título como `contentDescription`
  (HomeScreen.kt:212,293,342,406, LibraryScreen.kt:290, AlbumArtCarousel.kt:296) y además hay un `Text`
  con el mismo texto (SongListItem.kt:103) → se lee dos veces.
- **Gestos sin alternativa:** swipe next/prev (MiniPlayerBar.kt:84-90, CassetteFullScreen.kt:76-90) y
  drag para abrir el rail (WalkmanNavHost.kt:172-183). Ninguno tiene `customActions` de `semantics`.
- **No se respeta "quitar animaciones":** no hay lectura de `Settings.Global.ANIMATOR_DURATION_SCALE`, y las
  transiciones del cassette son `infiniteRepeatable` (AnimatedCassette.kt). Con la opción de accesibilidad
  activa el resultado es un bucle infinito a 0× de duración, con impacto en batería y riesgo para usuarios
  con fotosensibilidad.
- El `Slider` de progreso funciona (PlaybackControls.kt:250-264) pero anuncia la fracción `0f..1f`, no el
  tiempo, porque no se pasan valores semánticos.
- El overlay del rail **no captura `back`** (WalkmanNavHost.kt:239-253): solo se cierra tocando el scrim.

### 1.6 Navegación

- **8 rutas string sin type safety** (WalkmanNavHost.kt:299,310,318,323,328,336,345,351). Con
  `kotlinx-serialization` se obtendrían rutas tipadas, argumentos obligatorios y deep links de un solo lugar.
- **Sin deep links** — ningún `composable` declara `deepLinks` y el manifest no tiene intent-filters.
  No se puede compartir ni abrir un álbum/artista desde fuera.
- **Rail inconsistente:** `WalkmanNavHost.kt:268-274` usa el patrón correcto
  (`popUpTo(startDestination){saveState}` + `launchSingleTop` + `restoreState`), pero
  `WalkmanNavigationRail.kt:74` navega a `"home"` solo con `launchSingleTop` → el back stack crece.
- **El player no es una ruta, es un overlay booleano** (`playerExpanded = rememberSaveable`,
  WalkmanNavHost.kt:96; montado en `AnimatedVisibility` por encima del NavHost en :208-212). Cerrarlo con
  `back` funciona (`WalkmanScreen.kt:88`) pero el estado no es restaurable ni compartible. Y desde el
  player se navega a destinos que quedan **debajo** de un overlay ya cerrado (WalkmanNavHost.kt:215-229).
- **3 `BackHandler`** (WalkmanScreen.kt:88, CassetteFullScreen.kt:50, SongEditScreen.kt:50) → el orden de
  captura depende del orden del árbol de composición: frágil ante cualquier refactor.
- `libraryTab` viaja como parámetro (WalkmanNavHost.kt:106,203,265) en vez de por `savedStateHandle` → se
  pierde al reconstruir el grafo desde otra entrada.
- **Sin transiciones de ruta**, en contraste fuerte con el resto de animaciones de la app.

### 1.7 Internacionalización

Paridad de recursos correcta: 98 `string` en `values/strings.xml` vs 97 en `values-es/strings.xml`; la
única diferencia es `app_name`, correctamente no traducido.

- **Falsos plurales:** `format_songs` y `format_albums` son `string` con `%d` en vez de `<plurals>`.
  Con `n = 1` el inglés produce "1 songs".
- **`String.format` sin locale explícito:** `Models.kt:39` y `PlaybackControls.kt:342-348` — este último
  lleva `@SuppressLint("DefaultLocale")` (`:342`), que es una supresión deliberada de un aviso real.
  Usar `DateUtils.formatElapsedTime` o `Locale.getDefault()`.
- **Literales en código:** `"Menu"` (WalkmanScaffold.kt:127) y los defaults del cassette
  (`CassetteModels.kt:16-20`: `"ADICTIVA"`, `"ANUEL AA"`, `"UCX 46"`).
- **Falta `android:localeConfig`** en el manifest → en Android 13+ la app no aparece en los ajustes de
  idioma por aplicación.

---

## 2. Hallazgos críticos fuera del frontend

Ordenados por severidad.

### 🔴 C1 — Corrupción del archivo de audio del usuario
`data/AudioTagWriter.kt:143` — `writeBack` abre el destino con modo `"w"`:
```kotlin
context.contentResolver.openOutputStream(song.uri, "w")?.use { output -> ... }
```
`"w"` **trunca a 0 bytes en el momento de abrir**. Si la copia falla a mitad (cuota llena, EIO, cierre del
proceso), el archivo del usuario queda corrupto o vacío, y no hay backup del audio. Además copia el archivo
**entero dos veces** (`:129-130` + `:144`); para un FLAC de 60 MB son 120 MB de I/O.
**Fix:** escribir a un temporal completo, verificar, y solo entonces abrir el destino.

### 🔴 C2 — Fuga del `Player.Listener`
`viewmodel/PlayerControllerViewModel.kt:599-602`:
```kotlin
override fun onCleared() {
    controllerFuture?.let { MediaController.releaseFuture(it) }
    super.onCleared()
}
```
Se registró el listener en `:177-180` pero **nunca se hace `removeListener`** → el listener sigue apuntando
al ViewModel liberado.

### 🔴 C3 — La Activity retenida en un campo estático
`model/Models.kt:14` es un `var` **top-level** (campo estático) y `MainActivity.kt:23` le asigna una lambda
que captura `this`:
```kotlin
songArtworkResolver = { song -> CoverStore.resolve(this, song.id) }
```
Retiene la **Activity completa** (y con ella su `Window`, decor view y todo el árbol de Compose) durante
toda la vida del proceso, más allá de `onDestroy`. No hay `songArtworkResolver = null` en ningún sitio.
También invierte la dependencia: el modelo de dominio depende de `data.CoverStore`.

### 🔴 C4 — `Palette.generate()` en el hilo principal
`data/media/ArtworkColorExtractor.kt:55` — no hay ningún `withContext`. `Palette.from(bitmap).generate()` es
CPU-intensivo y síncrono (decodifica bloques de píxeles y cuantiza en Lab/HSL); con un bitmap 1024×1024
son ~1M de píxeles.

Se invoca desde `PlayerControllerViewModel.kt:209-221` con `viewModelScope.launch` (Main.immediate), y
`refreshCurrent()` se dispara desde los listeners de `MediaController` en `onPlaybackStateChanged` (`:98`),
`onMediaItemTransition` (`:102`) y `onTimelineChanged` (`:108`) → **corre en Main en cada cambio de estado
de reproducción**, no solo al cambiar de canción.

Agravantes:
- Se lanza una coroutine **por evento sin cancelar la anterior** → al saltar rápido entre pistas, N
  coroutines corren `Palette` en paralelo y **todas escriben en `_state`**: gana la última en terminar, no
  la última en pedir.
- **Caché de 1 entrada y no transaccional** (`:26-35`), con campos mutables sin `@Volatile`.
- **Bug de acierto falso:** si `albumId == 0L` (canciones sin álbum, muy común), `takeIf { 0L == 0L }`
  **acierta siempre** → todas las canciones sin álbum comparten la paleta de la primera.

`CoverStore.kt:30-64` es la pieza mejor escrita del proyecto (suspend + IO, escritura atómica con `.tmp` +
`renameTo`, `recycle` explícitos), pero `decodeStream` (`:36-37`) **no usa `inJustDecodeBounds`/`inSampleSize`**
→ un JPEG de 4000×3000 son 48 MB de bitmap. Y `save` **devuelve `Unit`**, así que el llamador
(`PlayerControllerViewModel.kt:512-513`) no puede saber si falló → asigna `coverFile` igualmente y luego
`AudioTagWriter.apply` intenta embeber un archivo inexistente (fallo tragado por el `catch` vacío de `:527`).

### 🟠 C5 — Dos consultas MediaStore en el hilo principal
`MediaRepository` **no impone ningún dispatcher** (cero `withContext` en el paquete `data/`), y hay dos
llamadores que el eligen mal:
- `LibraryScreen.kt:178-187` — `rememberCoroutineScope()` (Main) → `playerViewModel.playSongsNext(libraryViewModel.songsByAlbum(...))`
  → `songsByAlbum` es `suspend` pero no cambia de dispatcher → `contentResolver.query(...)` en **Main**.
  Idéntico en `SearchScreen.kt:190,195`.
- `LibraryViewModel.kt:80-84` — `viewModelScope.launch` (Main) → `loader(MediaRepository(...))`, una query
  MediaStore completa en Main por cada `LibraryEvents.reload`. Contrasta con `LibraryViewModel.kt:57` que sí
  usa `Dispatchers.IO`: la inconsistencia está dentro del mismo archivo.

### 🟠 C6 — Límite de bind-args de SQLite
`MediaRepository.kt:43` y `:74` — `loadAlbumsForSongs` / `loadArtistsForSongs` hacen batch con
`IN (?,?,…)` sobre **toda** la biblioteca. **No hay chunking**, y `SQLITE_MAX_VARIABLE_NUMBER` es 999 en
muchas builds. Con >999 álbumes (muy normal: ~10k canciones) →
`SQLiteBindOrColumnIndexOutOfRangeException`, y como `loadSongs()` ya habría devuelto las canciones, la app
queda **con canciones pero sin álbumes, sin error visible, para siempre**.

### 🟠 C7 — El filtro de carpetas no funciona en almacenamiento moderno
`MediaRepository.kt:108-115` filtra por `MediaStore.Audio.Media.DATA`, **obsoleto desde API 29**. Con
`targetSdk 37` la columna no está garantizada: si devuelve `null`, el `LIKE` no matchea nada → **biblioteca
vacía sin error**.

`FolderResolver.kt:25-31` además **inventa rutas** para almacenamiento secundario:
```kotlin
absolutePath = "/storage/${documentId.replace(':', '/')}"
```
Para un `documentId` = `1AEF-1234:Music` produce `/storage/1AEF-1234/Music`, una ruta que no coincide con
nada de MediaStore. El usuario selecciona una SD y obtiene 0 canciones sin diagnóstico. La ruta primaria
también está hardcodeada a `/storage/emulated/0/` (`FolderResolver.kt:22`).

Y con N carpetas la `selection` crece a `2N+1` placeholders unidos por `OR`, lo que **impide cualquier uso
de índices** en `DATA` → full scan de la tabla `files` en `MediaProvider` por cada carga.

### 🟠 C8 — `MediaItem.toSong()` pierde `mimeType` y `dataPath`
`model/Models.kt:98-111` no los restaura. `refreshQueue` reconstruye la cola desde
`player.getMediaItemAt(i).toSong()` (PlayerControllerViewModel.kt:227-229), así que **un `Song` que ha
pasado por el player ya no puede reescribir tags**: `AudioTagWriter.extensionFor` (`:186-199`) depende de
`mimeType`/`dataPath` y cae a `"dat"`. Es un bug funcional de datos, no solo de arquitectura.

### 🟠 C9 — Operaciones multi-DAO no transaccionales
- `PlayerPersistence.kt:46-52` — `saveQueue` hace `clear()` + `insertAll()` sin transacción. Si el proceso
  muere entre ambas (muy probable: el usuario puede matar la app), **la cola se pierde entera**.
- `PlayerPersistence.kt:111-116` — `updateSongMetadata` hace 4 llamadas DAO secuenciales sin
  `@Transaction`. Con el mismo `Song` denormalizado en `favorites`/`queue`/`play_history`/`playlist_songs`
  (Entities.kt), el estado parcial es la norma.
- `PlayerPersistence.kt:96-105` — `recordPlay` es un read-modify-write no atómico → dos llamadas
  concurrentes pierden un incremento de `playCount`.

### 🟡 C10 — Cero índices en 6 tablas
Ningún `@Entity` declara `indices = [...]`. Las consultas que lo necesitan:
`favorites(addedAt)`, `play_history(lastPlayedAt)`, `play_history(playCount)`, y sobre todo
**`playlist_songs(songId)`** — la PK compuesta `(playlistId, songId)` no sirve para
`DELETE ... WHERE songId = :songId` (Daos.kt:101-105), así que eso es un **full table scan en cada edición
de canción**.

Tampoco hay `@ForeignKey`/`onDelete = CASCADE`: borrar una canción deja filas huérfanas en
`playlist_songs` (y `PlayerPersistence.kt:43` no limpia los backups de `CoverStore` → **fuga de disco**:
los `.img`/`.mime` de `ORIG_DIR` nunca se limpian).

### 🟡 C11 — Escrituras y recomposiciones 2×/s
- `PlayerControllerViewModel.kt:130-143` — `SharedPreferences.edit().apply()` **2 veces por segundo**
  mientras se reproduce, con el desgaste de flash correspondiente, y `_state.update { copy(positionMs…) }`
  2×/s fuerza recomposición de toda la UI del player.
- `PlayerControllerViewModel.kt:224-240` — `refreshQueue` reconstruye la cola (N × `toSong()` con 5
  `extras.get*` + `Uri` cada uno) y lanza `saveQueue` → **N filas SQLite reescritas dos veces por segundo**
  con una cola de 2.000 pistas.

### 🟡 C12 — Sin `ContentObserver` de MediaStore
La biblioteca solo se refresca por: permiso concedido, `LaunchedEffect(Unit)`, cambio de carpetas o
`LibraryEvents`. **Copiar un MP3 nuevo a `/sdcard/Music` con la app abierta no aparece en la biblioteca.**

### 🟡 C13 — `LibraryEvents` pierde eventos
`data/LibraryEvents.kt:8-13` — `MutableSharedFlow(replay=0, extraBufferCapacity=1)` + `tryEmit`. Sin
colector activo, el valor se **descarta silenciosamente** y la biblioteca queda desincronizada. Debería ser
`Channel(Channel.BUFFERED)` con `send`, o un `StateFlow<Long>` con versión.

### 🟡 C14 — 9 `catch` completamente vacíos, ningún estado de error
Vacíos: `AudioTagWriter.kt:160` (rescan), `PlayerControllerViewModel.kt:188` (**fallo al conectar el
MediaController → el player queda muerto y silencioso**), `:191`, `:481`, `:514`, `:527`, `:564` (**borrado
del cover custom aunque `restoreOriginal` falló → pérdida de datos**), `SettingsViewModel.kt:44` (el folder
se guarda aunque no tendrá acceso), `:58`, `PlaybackWidgetProvider.kt:268`.

**No existe `Result<T>` en ningún punto** (0 usos de `kotlin.Result`). El patrón es "devolver `Boolean`/`null`
y tragar la causa" — y `AudioTagWriter.apply` devuelve `Boolean` que el llamador **descarta**
(PlayerControllerViewModel.kt:519-526), así que el usuario ve el título nuevo en la UI pero el archivo
conserva el tag viejo y el próximo rescan lo revierte.

Ningún ViewModel tiene estado de error (`_state` de LibraryViewModel solo tiene songs/albums/artists/isLoading;
`PlayerUiState` no tiene campo de error; `SettingsViewModel` solo `_folders`/`_folderReady`). Y
`LibraryViewModel.kt:55-65` no tiene `try/finally` → si `loadSongs()` lanza `SecurityException` (permiso
revocado), **el spinner se queda cargando para siempre**.

El único caso bien tratado es `PlayerControllerViewModel.kt:115-123` (`onPlayerError` con `Log.e` completo) —
ese es el estándar que el resto del archivo debería seguir.

---

## 3. Estado del widget (bug resuelto)

**Síntoma:** al cambiar de canción con la app abierta y luego ir a la pantalla de inicio, el widget no
actualiza el cover.

**Causa raíz #1 — el widget es un `BroadcastReceiver` sin ciclo de vida.**
`widget/PlaybackWidgetProvider.kt:417-431` tiene 6 campos estáticos mutables. Android los reinicia a `null`
cuando mata el proceso, y **`connect()` solo se llama desde `onUpdate`, `onReceive` y
`onAppWidgetOptionsChanged`**. Con `updatePeriodMillis="0"` (playback_widget_info.xml:8) el sistema nunca
programa actualizaciones. Resultado: si el proceso se recrea (lo hace el `PlaybackService` en foreground),
**nadie registra `controllerListener`**, así que `onMediaItemTransition` nunca se dispara y el launcher
muestra el último `RemoteViews` renderizado.

**Causa raíz #2 — no hay puente app→widget.** El `PlayerControllerViewModel` sabe cuándo cambia la pista
pero no se lo dice al widget. Solo hay push en el sentido contrario (widget → player).

**Causa raíz #3 — carrera en el artwork.** `loadArtwork` (`:309-329`) captura el `views` del render actual
y hace `updateAppWidget` con ese snapshot cuando Coil termina. Si llega otro render en medio
(`onPlaybackStateChanged` → `renderNow`), el snapshot antiguo **se reaplica encima y revierte el cover**.
Además `:151` pone siempre el placeholder antes de encolar → parpadeo en cada cambio, y `.size(96)` (`:314`)
produce un cover borroso.

**Causa raíz raíz #4 — el ticker corre en el hilo principal.** `scope` es
`Dispatchers.Main.immediate` (`:417`) y el ticker (`:364-371`) hace `updateProgress` → IPC a
`AppWidgetService` **1 vez/segundo en Main**.

**Causa raíz #5 — el cover base usa un provider obsoleto.** `Models.kt:31-33` cae a
`content://media/external/audio/albumart`, **eliminado en API 29**. Además `songArtworkResolver` solo lo
asigna `MainActivity.onCreate`, así que si el widget actualiza antes de que se abra la app, los covers
personalizados no se resuelven.

**Solución aplicada** (`git status`: 9 archivos tocados, `lintDebug` a 0 errores, tests en verde):

1. **La propiedad de los renders pasa al servicio.** `PlaybackService` registra un `Player.Listener` que
   llama a `WidgetRenderer.render` desde `onEvents`, un único hook que cubre transiciones de pista,
   estado de reproducción, `isPlaying`, timeline y discontinuidades. El servicio está vivo exactamente
   mientras hay audio sonando, que es justo cuando el widget importa.
2. **`WidgetRenderer` nuevo** (`widget/WidgetRenderer.kt`): único punto de render, con `LruCache` de
   portadas. Al llegar el artwork **re-renderiza el juego de vistas completo** en vez de reaplicar un
   snapshot capturado, y descarta resultados cuyo `songId` ya no sea el mostrado.
3. **Sin parpadeo a placeholder:** el bitmap cacheado se aplica al instante y el placeholder solo se usa
   cuando no hay nada que mostrar. Antes se ponía siempre antes de encolar, así que parpadeaba en cada
   cambio de pista.
4. **`PlaybackWidgetProvider` reducido a receptor fino:** solo taps y renders bajo demanda. Se conecta en
   `onEnabled`, y **libera controller, listener y `ListenableFuture` en `onDeleted`/`onDisabled`**, lo
   que cierra la fuga que mantenía el servicio de reproducción abierto para siempre.
5. **El cover se resuelve sin `MainActivity`:** nueva `WalkmanApplication` que instala el
   `applicationContext` en `CoverStore`, más `loadThumbnail` (API 29+) con fallback a
   `MediaMetadataRetriever` y al provider antiguo. Antes dependía de un `var` estático que solo
   asignaba `MainActivity.onCreate`.
6. **Ticker: el sampling se queda en Main y solo el push se va a background.** `ExoPlayer` verifica su
   propio hilo de aplicación y lanza `IllegalStateException` ante cualquier acceso desde otro hilo, así
   que el scope del servicio es `Dispatchers.Main.immediate` y el ticker **muestrea** `duration` /
   `currentPosition` en Main. Esos valores viajan a `WidgetRenderer.renderProgress` como primitivos
   (`Long`/`Int`), nunca como `Player`, y el `updateAppWidget` sí corre en `Dispatchers.Default`
   (`pushScope`), así el IPC de 1 Hz sigue sin bloquear Main.
7. **Guardas de API:** `setColorStateList` requiere API 31 y `minSdk` es 27 — **la app crasheaba con
   `NoSuchMethodError` en Android 8-11** al aplicar los tints. Ahora está condicionado por versión.
8. **Conteo de widgets cacheado:** `getAppWidgetIds` es una llamada binder y `manageProgressTicker`
   preguntaba en cada evento, en Main. `WidgetRenderer` cachea el número de instancias y expone
   `hasWidgets()`, que ya no hace IPC.
9. **Extras corregidos en el mismo cambio:** `Palette.generate()` pasa a `Dispatchers.Default` y su caché
   pasa a ser transaccional y con clave `albumId|uri` (el bug del `albumId == 0` hacía que *todas* las
   canciones sin álbum devolvieran la paleta de la primera); `PlayerControllerViewModel` cancela el job
   de paleta anterior en vez de acumularlos; `onCleared` hace `removeListener` + `release`.

### Regresión introducida y corregida: acceso al `Player` desde `Default`

La primera versión del punto 6 dejó el scope del servicio en `Dispatchers.Default` y el ticker llamaba a
`player.isPlaying` desde ahí. ExoPlayer aborta el proceso:

```
FATAL EXCEPTION: DefaultDispatcher-worker-4
java.lang.IllegalStateException: Player is accessed on the wrong thread.
  Current thread: 'DefaultDispatcher-worker-4'   Expected thread: 'main'
    at androidx.media3.exoplayer.ExoPlayerImpl.verifyApplicationThread(ExoPlayerImpl.java:3150)
    at androidx.media3.common.BasePlayer.isPlaying(BasePlayer.java:123)
    at ...PlaybackService.manageProgressTicker(PlaybackService.kt:93)
```

Todo lo demás del widget ya pasaba por `onEvents` del `Player.Listener` (Main), y eso es exactamente la
regla que queda: **el `Player` solo se lee dentro de scopes con `Dispatchers.Main`; los datos salen como
valores inmutables.** Además `render()` movió la lectura de `currentMediaItem` dentro de su
`scope.launch`, para que sea correcta aunque se llame desde otro hilo.

### Verificación en dispositivo (Samsung SM-S918B)

- 6 transiciones de pista seguidas: 0 `FATAL`, 0 `ANR`, 0 `wrong thread`.
- Con el player en `PLAYING`, el `RemoteViews` del widget cambia en cada muestreo de 3 s → el push de
  progreso llega al launcher.
- En `PAUSED` el `RemoteViews` no cambia entre muestras → el ticker se detiene como debe.
- Un `next` estando en pausa sí cambia el `RemoteViews` → el render completo (título, cover, tints)
  ocurre también sin reproducción.


### Archivos tocados

| Archivo | Cambio |
|---|---|
| `widget/WidgetRenderer.kt` | **Nuevo.** Render único, caché de artwork, resolución de cover moderna |
| `widget/PlaybackWidgetProvider.kt` | Receptor fino; libera recursos; `onEnabled` |
| `service/PlaybackService.kt` | Empuja renders en `onEvents`; ticker fuera de Main |
| `WalkmanApplication.kt` | **Nuevo.** Instala el `applicationContext` en `CoverStore` |
| `AndroidManifest.xml` | `android:name=".WalkmanApplication"` |
| `MainActivity.kt` | Eliminado el `var` estático que retenía la Activity |
| `model/Models.kt` | `albumArtUri` sin el resolver estático |
| `data/CoverStore.kt` | `install()` + `uriFor(songId)` sin `Context` |
| `data/media/ArtworkColorExtractor.kt` | `Palette` fuera de Main; caché con `Mutex` y clave por URI |
| `viewmodel/PlayerControllerViewModel.kt` | `paletteJob` cancelable; `removeListener` en `onCleared` |
| `ui/navigation/WalkmanNavHost.kt` | `stringResource` en vez de `context.getString` |
| `res/values/strings.xml` | `app_name` con `translatable="false"` |

---

## 4. Plan de trabajo

### Fase 1 — Rendimiento del frontend (mayor impacto, riesgo bajo)
- [ ] `CassetteDrawing`: extraer la capa estática a `drawWithCache` / `ImageBitmap` cacheado por tamaño;
      dejar solo bobinas y cinta en el draw path animado. Cachear `Brush`, `Path` y `TextLayoutResult`.
- [ ] Separar `positionMs` / `isPlaying` del resto de `PlayerUiState`; anotar con `@Immutable`.
- [ ] Eliminar el `File.exists()` de `Song.albumArtUri` → flag `hasCustomCover` precalculado en el escaneo.
- [ ] Cachear `albums.sortedByDescending { it.year }` con `remember(albums)`.
- [ ] Añadir `contentType` a las listas `LazyColumn`.
- [ ] Escribir a `SharedPreferences` con `debounce` en `Dispatchers.IO`.

### Fase 2 — Theming
- [ ] Crear `WalkmanLightColorScheme` y registrar la variante clara.
- [ ] Mover `enableEdgeToEdge` a `MainActivity.onCreate` (eliminar el cast a `ComponentActivity` del tema).
- [ ] Sustituir `Color.White.copy(alpha=…)` y los `fontSize` por pantalla por tokens del tema.
- [ ] Auditar contraste ≥4.5:1 de los alphas de PlaybackControls.kt:262,275,280,304 y el scrim de
      WalkmanNavHost.kt:247.
- [ ] Añadir `Shapes` custom y un sistema de espaciado.
- [ ] Toggle de color dinámico en `SettingsScreen`.

### Fase 3 — Accesibilidad (requisito de publicación en Play)
- [ ] Identidad semántica del cassette: `contentDescription` con pista/estado + `progressBarRangeInfo`.
- [ ] Etiquetar el logo (PlayerHeader.kt:115-129) y el scrim (WalkmanNavHost.kt:244-252).
- [ ] `Modifier.toggleable(..., role = Role.Switch)` en shuffle/repeat/favorito.
- [ ] Subir los 9 `IconButton` de 40 dp a 48 dp; `spacedBy(8.dp)` en PlayerHeader.kt:142.
- [ ] `heading(true)` en los títulos de pantalla; `semantics { selected = }` en el rail.
- [ ] `customActions` de `semantics` como alternativa a los 3 gestos.
- [ ] Respetar "quitar animaciones" leyendo `ANIMATOR_DURATION_SCALE`.
- [ ] Detener el cassette cuando el player no está visible o está pausado.

### Fase 4 — Corrección de los críticos
- [ ] C1: escritura atómica en `AudioTagWriter.writeBack` (temporal → verificar → renombrar).
- [ ] C2: `removeListener` antes de `releaseFuture` en `onCleared`.
- [ ] C3: eliminar el `var` estático `songArtworkResolver` → inyección desde el contenedor.
- [ ] C4: `withContext(Dispatchers.Default)` en `Palette.generate` + cache transaccional y con clave por URI
      (arreglando el falso acierto con `albumId == 0`) + cancelar el job anterior.
- [ ] C5: imponer `Dispatchers.IO` dentro de `MediaRepository` en vez de delegar en el llamador.
- [ ] C6: chunking de los `IN (?,?,…)` a bloques de 500.
- [ ] C7: migrar el filtro de carpetas de `DATA` a `RELATIVE_PATH` + `VOLUME` (API 29+).
- [ ] C8: conservar `mimeType`/`dataPath` en los extras de `MediaItem`.
- [ ] C9: `@Transaction` en `saveQueue`, `updateSongMetadata` y `recordPlay`.
- [ ] C12: `ContentObserver` sobre `MediaStore`.
- [ ] C13: `LibraryEvents` → `Channel(Channel.BUFFERED)`.
- [ ] C14: `Result<T>` en la capa de datos + estado de error en los tres ViewModels.

### Fase 5 — Estado, navegación e i18n
- [ ] Añadir `androidx.lifecycle:lifecycle-runtime-compose` y migrar los 46 `collectAsState` a
      `collectAsStateWithLifecycle`.
- [ ] Rutas tipadas con `kotlinx-serialization`; deep links `walkman://album/{id}`.
- [ ] Unificar `WalkmanNavigationRail.kt:74` con el patrón `popUpTo`/`saveState` de WalkmanNavHost.kt:268.
- [ ] `BackHandler` para cerrar el overlay del rail.
- [ ] `libraryTab` a `savedStateHandle`.
- [ ] `format_songs`/`format_albums` → `<plurals>`; `DateUtils.formatElapsedTime` o locale explícito.
- [ ] Añadir `android:localeConfig`.

### Fase 6 — Datos y tests
- [ ] Índices: `favorites(addedAt)`, `play_history(lastPlayedAt)`, `play_history(playCount)`,
      `playlist_songs(songId)`. `@ForeignKey` con `onDelete = CASCADE`.
- [ ] `exportSchema = true` + `MigrationTestHelper` (activa `room-testing`).
- [ ] `distinctUntilChanged` + `stateIn` en los `Flow` de `PlayerPersistence`.
- [ ] Paginación/búsqueda con debounce en `SearchScreen` (hoy filtra la colección completa en cada tecla).
- [ ] Dependencias de test: `kotlinx-coroutines-test`, `room-testing`, `MockK`, `Turbine`, `Robolectric`.
- [ ] Tests reales de `SearchLogic.filterSongs`/`filterAlbums` (hoy 0 cobertura: los 5 tests de filtrado
      reimplementan el filtro a mano en vez de llamar al código de producción).
- [ ] Revisar `isReturnDefaultValues = true`: combinado con la ausencia de Robolectric hace que un test que
      invoque `MediaStore` o `Uri` **pase con valores silenciosamente falsos**.

---

## 5. Lo que ya está bien

Para no romper lo que funciona:

- **`service/PlaybackService.kt`** — la pieza más limpia del proyecto (52 líneas, sin estado global, sin DI).
  `NextRenderersFactory` con `EXTENSION_RENDERER_MODE_ON` + fallback a software, `handleAudioFocus = true`,
  `setHandleAudioBecomingNoisy(true)`, y un `onTaskRemoved` correcto (el servicio sobrevive al swipe).
- **`data/CoverStore.kt:30-64`** — `suspend` + IO, escritura atómica con `.tmp` + `renameTo` con fallback a
  `copyTo`, `finally` que limpia el temporal, `recycle` explícitos. Es el patrón a replicar.
- **El ViewModel se pasa explícitamente al NavHost** (WalkmanNavHost.kt:83-85, 299-358) en vez de
  `viewModel()` por pantalla → evita instancias duplicadas por `NavBackStackEntry`.
- **El cassette dibujado a mano** es un standout real de identidad; el problema es de *caching*, no de diseño.
- **Paridad de recursos es/en** correcta.
- `PlayerControllerViewModel.kt:115-123` (`onPlayerError`) es el modelo de logging que debería seguir el
  resto del archivo.
- Sin `GlobalScope`, sin `runBlocking`, `viewModelScope` bien usado en los tres ViewModels.
