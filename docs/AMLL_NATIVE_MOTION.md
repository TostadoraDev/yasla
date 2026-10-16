# Movimiento nativo de letras: comparación con AMLL

> Este documento describe el pase original de motion. El refinamiento posterior conserva sus timestamps y resortes, añade glow limitado a los glyphs iluminados y reduce el alpha del texto pendiente. La procedencia y decisión de licencia siguen pendientes en [LICENSE_STATUS.md](LICENSE_STATUS.md).

## Referencia y alcance

Referencia investigada: `@applemusic-like-lyrics/core` **0.5.2**, ya instalada en
`<local AMLL demo checkout>`.
Los originales se inspeccionaron mediante `sourcesContent` de `amll-core.mjs.map`.
El demo existente se ejecutó en `http://127.0.0.1:5173`; no se instalaron paquetes.

Archivos originales estudiados:

- `src/lyric-player/dom/lyric-line.ts`: float de palabras, énfasis de caracteres, máscara y alpha.
- `src/lyric-player/dom/lyric-group.ts`: composición y actualización de grupos.
- `src/lyric-player/base/index.ts`, `layout.ts`, `group.ts`, `timeline.ts`, `line.ts`: foco, geometría, filas calientes y buffer.
- `src/utils/spring.ts`, `derivative.ts`: solución analítica y conservación de velocidad.
- `src/utils/optimize-lyric.ts`: ventanas visuales adelantadas y superposiciones.
- `src/styles/lyric-player.module.css`: transiciones, opacidad y profundidad.

El upstream y el paquete local declaran **AGPL-3.0-only**. No se incorporaron archivos,
dependencias ni recursos de AMLL al APK. El código Kotlin se escribió para YASLA a
partir de la investigación de comportamiento y parámetros; no debe describirse como
una implementación clean-room, ni suponerse que cambiar de lenguaje elimina posibles
obligaciones de licencia. Esta documentación conserva la procedencia para evaluar esas
obligaciones antes de distribuir trabajo derivado.

Upstream: <https://github.com/amll-dev/applemusic-like-lyrics>.

La integración de producción sólo afecta `AutoScrollingLyrics`, utilizado por el modo
`FULL_LYRICS` de Now Playing. `NowPlayingScreen` pasa ahora `isPlaying` a ese viewport.
Los renderers de Floating Lyrics, widgets, Current Word y editor no se sustituyeron.
Background, header, controles, seek bar, navegación, parser, búsqueda y MediaSession
conservan su implementación.

## Diferencias del renderer anterior

El desplazamiento anterior usaba una trayectoria genérica de scroll y poses de entrada
de poca amplitud. El reveal suavizaba cada intervalo con una curva de orden seis.
AMLL avanza su máscara **linealmente entre los timestamps**: su movimiento orgánico
proviene del float, énfasis, resortes, alpha y desenfoque, no de modificar esos timestamps.

## Algoritmos reproducidos

| Mecanismo | Implementación nativa |
| --- | --- |
| Posición de filas | Resorte analítico; masa 0.9, rigidez 90 y damping 15 al iniciar/seek. En transición normal, rigidez 170–220 según intervalo visual y damping `2.2 * sqrt(k)`. |
| Escala de filas | Resorte de masa 2, rigidez 100, damping 25; 0.97 inactiva / 1 activa. Pausada: todas vuelven a 1. |
| Cascada | Retarget de filas con retrasos de 50 ms, decrecientes por factor 1.05 después del foco. Conserva posición y velocidad actuales. |
| Foco | Ventanas visuales adelantadas hasta 600 ms en gaps y 400 ms en overlaps, con límites de seguridad; timestamps de palabras intactos. Foco YASLA conservado en 27% del viewport. |
| Superposiciones | Filas hot separadas del buffer; una fila corta ya terminada conserva su estado hasta expirar el buffer completo o entrar una fila nueva. Seek limpia el buffer. |
| Opacidad de wrapper | Activa 0.85; word-synced inactiva 1 bajo máscara; line-synced inactiva 0.2. Transición de 400 ms, CSS `ease`. |
| Máscara | Alphas ligadas al resorte de escala: dark 0.2→0.4, bright 0.2→1. Ataque exponencial 50/s y salida 7/s. |
| Float de palabra | Desplazamiento `−0.05em`, duración mínima 1000 ms, CSS `ease-out`; background `−0.1em`. Mantiene pose tras cantar. |
| Énfasis | Palabras de al menos 1000 ms, 2–7 unidades UTF-16 o CJK; onda de caracteres con 32 muestras, escalado, expansión, float sinusoidal y sombra. Refuerzo de última palabra. |
| Curvas de énfasis | Ascenso cubic-bezier(0.2,0.4,0.58,1), descenso complementario cubic-bezier(0.3,0,0.58,1). Interpolación de las muestras como WAAPI. |
| Desenfoque | Distancia al foco/buffer, factor compacto 0.8 y máximo 5 dp; transición CSS `ease` de 400 ms. RenderEffect nativo desde Android 12. |

La solución analítica reproduce también las ramas crítica/subamortiguada del original.
Retarget interrumpido parte de la posición y velocidad visual actuales. El delta real
de cada frame no se recorta a 50 ms: las caídas de FPS no ralentizan artificialmente
los resortes respecto al reloj de reproducción.

## Palabras, máscara y wrapping

`LyricsWord` es la unidad visual; sus `TimedSegments` son puntos temporales del reveal.
El movimiento base no se reinicia en cada snap. Los caracteres se separan para la onda
de énfasis únicamente en palabras elegibles; no se reconstruyen palabras desde snaps
en esta capa.

En runs RTL se conserva el movimiento de palabra completa y el reveal, pero se omite
la separación de caracteres para mantener el shaping contextual (por ejemplo, las
uniones árabes). Esa onda de caracteres es una diferencia deliberada respecto al
renderer DOM; no se presenta como igualdad absoluta en todas las escrituras.

`TextLayoutResult` aporta bounds y avances por línea visual y dirección bidi. La máscara
recorre esas medidas reales, con feather equivalente a media altura de la línea.
Envolventes de distintas líneas visuales no se convierten en un rectángulo gigante.

Los fragmentos se miden y dibujan aislados, manteniendo los anclajes y baselines medidos
del párrafo completo. Los caracteres se aíslan sólo durante énfasis; el resto utiliza
palabras/runs completos. Esto permite pintar el glow sin clip rectangular que corte
la sombra o duplique líneas vecinas. La captura GPU demostró que spans transparentes
sobre un párrafo completo no eran suficientes al sobreescribir su brush; ese enfoque
se eliminó.
No hay un fondo o rectángulo de gradiente: el brush se entrega a `drawText`.

Al salir de current, sólo el float base invierte su reloj. La onda de énfasis y glow
continúan hacia delante, evitando que una palabra terminada vuelva a expandirse.
El float terminado invierte desde su duración efectiva; no espera a descontar el resto
de la línea. Un reloj de salida acotado mantiene el dibujo mientras exista movimiento
pendiente incluso con playback pausado; se detiene al completar la cola visual.
Seek resetea estos relojes de palabra sin hacer snap de la posición de la línea.

## Rendimiento y accesibilidad

- Un coordinador `withFrameNanos` por viewport; ninguna coroutine/Animatable por palabra.
- Contexto `AndroidUiDispatcher.Main`: frame clock de Choreographer independiente de que
  una composición estática produzca repintados; no depende de la animación del background.
- Trabajo de Vsync limitado a lifecycle STARTED; conserva la trayectoria al volver y
  no mantiene un loop de render de Now Playing con la actividad en segundo plano.
- Tiempo musical obtenido del `LyricsTimeline` de la app; ningún reloj de playback adicional.
- Frames de filas consumidos en `graphicsLayer` y drawing, no en composición del árbol.
- Texto, fragmentos y mediciones cacheados; sólo filas visibles actualizadas.
  Callbacks con el mismo layout no reconstruyen las mediciones de fragmentos; se
  invalidan al cambiar constraints, geometría, estilo o fuentes resueltas.
- Filas inactivas no se suscriben al reloj musical de alta frecuencia; congelan su máscara.
- RenderEffect de blur cacheado en pasos de medio píxel; sin bitmaps de letras por frame.
- OFF: cambios inmediatos. REDUCED: sin desplazamiento de palabras, scale o blur.
- NORMAL/ENHANCED: valores originales, sin atenuarlos por criterio estético.

## Validación y diferencias pendientes

Pruebas unitarias verifican resortes, curvas CSS, cadence 60/120 Hz, alpha, blur,
énfasis, salida/seek, máscara medida, snaps/gaps, buffer, preroll y claves estructurales.

La escena debug usa el mismo TTML que el demo: palabras cortas/largas, palabras con
2 y 6 snaps, líneas con wrap, pausas, seeks y una reproducción continua de 20 segundos.
Las capturas y datos del demo están en `.artifacts/amll-parity`; la actividad de prueba
y su reloj determinista no se incluyen en builds release.

Las fuentes, rasterización y blur difieren entre Chrome y Android. Se conserva además
el foco y tipografía de YASLA; no se igualó el layout completo del demo. Android anterior
a API 31 omite el blur de RenderEffect. No se afirma igualdad pixel-perfect ni rendimiento
físico a 120 Hz basándose solamente en las pruebas numéricas.

Resultado final del pase:

- `:app:testDebugUnitTest`: **177 tests, 0 failures, 0 errors**.
- `:core:domain:test`: 72 tests del reporte existente, tarea `UP-TO-DATE`; dominio sin cambios.
- `:app:assembleDebug` y `:app:assembleDebugAndroidTest`: **BUILD SUCCESSFUL**.
- `:app:lintDebug`: completado, 0 errores; conserva 38 warnings (incluidos atributos debug para API 27).
- Android Studio MCP: análisis fresco, 0 errores en los archivos nuevos.
- Prueba GPU en AVD Pixel_10_Pro_XL/API 36.1 con ANGLE: **OK (1 test)**,
  incluyendo reproducción continua de 20 segundos, 12 muestras de timing, pausa, seek y
  comprobación de brillo accidental en futuras filas y palabra RTL completa.
  Último pase: **57.588 segundos**.
- Capturas finales: `.artifacts/amll-parity/final-pass/`.
- Video nativo: `.artifacts/amll-parity/native-motion.mp4`; cuadros comparativos y video del
  demo en esa misma carpeta. La comparación de videos utiliza muestras de tiempo aproximadas;
  las capturas fijas y las pruebas matemáticas usan timestamps exactos.
- La primera captura real encontró overdraw que no era detectable en las pruebas de math.
  Se corrigió y repitió el pase antes de aceptar el resultado; verdict de motion: 92/100.
- Hashes conservados: `KaraokeText`, `AdvancedLyricsApp`, `LyricsAppContainer`,
  `LyricsOverlayService` y `LyricsWidget` son idénticos al inicio del pase.

La traza del emulador mostró pausas de render durante el calentamiento, por lo que
este pase no demuestra rendimiento estable a 60/120 Hz ni igualdad temporal absoluta.
Se redujo la invalidación de layouts y la variedad de radios de blur, pero la
medición de rendimiento físico continúa pendiente.

El teléfono físico se desconectó durante las pruebas. La versión final quedó validada
en el emulador y disponible como APK; no se afirma que se haya instalado esa última
versión en el teléfono ni que se haya validado allí la frecuencia de 120 Hz.

APK final: SHA-256 `3BDB02AB18547711258876C8D14289DF01AA300CF240B0023BD04896244826D9`.

Referencia del frame clock usado: [AndroidX AndroidUiDispatcher](https://raw.githubusercontent.com/androidx/androidx/androidx-main/compose/ui/ui/src/androidMain/kotlin/androidx/compose/ui/platform/AndroidUiDispatcher.android.kt).

## Archivos de producción

- `ui/components/AmllLyricsMotion.kt`
- `ui/components/AmllVisualTimeline.kt`
- `ui/components/AmllRowMotion.kt`
- `ui/components/AmllKaraokeText.kt`
- `ui/components/AmllLyricsViewport.kt`
- `ui/components/Karaoke.kt`
- `ui/screens/NowPlayingScreen.kt` (paso de `isPlaying`)

Archivos de validación/documentación:

- `app/src/test/java/xyz/tosdev/yetasimplelyicsapp/ui/AmllLyricsMotionTest.kt`
- `app/src/test/java/xyz/tosdev/yetasimplelyicsapp/ui/AmllViewportContractTest.kt`
- `app/src/debug/java/xyz/tosdev/yetasimplelyicsapp/AmllMotionTestActivity.kt`
- `app/src/debug/assets/amll-motion-fixture.ttml`
- `app/src/debug/AndroidManifest.xml`
- `app/src/androidTest/java/xyz/tosdev/yetasimplelyicsapp/AmllMotionInstrumentedTest.kt`
- `docs/AMLL_NATIVE_MOTION.md`
- `.rcs/state/amll-motion/forge-progress.json`
- `.artifacts/amll-parity/` (capturas, videos y scripts de investigación local).
