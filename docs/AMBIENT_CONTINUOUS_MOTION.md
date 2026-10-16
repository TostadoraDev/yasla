# Movimiento ambiental continuo — 3 de octubre de 2026

Registro de la pasada anterior. El refinamiento posterior de color y su validación están en [ALBUM_COLOR_BACKGROUND.md](ALBUM_COLOR_BACKGROUND.md).

Esta pasada modifica exclusivamente el fondo de Now Playing. Conserva composición, paleta, header, letras, controles, Floating Overlay, widgets, parsing AMLL/TTML y reloj de reproducción.

## Resultado implementado

Se mantienen cuatro campos de luz animados. Cada campo forma una veta suave con cinco lóbulos superpuestos: veinte dibujos en total, reutilizando cuatro brushes. La base oscura y la vignette permanecen estáticas.

| Campo | Color | Fase principal | Fase secundaria | Desplazamiento relativo |
| --- | --- | --- | --- | --- |
| Superior | Dominante | 14 s | 28 s | 1.00 |
| Derecho | Secundario | 18 s | 21 s | 0.82 |
| Inferior izquierdo | Muted | 28 s | 18 s | 0.42 |
| Reflejo inferior | Dominante | 21 s | 14 s | 0.62 |

Cada trayectoria combina sin/cos de dos frecuencias, una fase inicial diferente y sentido alternado. Además del desplazamiento X/Y, cambian lentamente radio, alpha y curvatura. Un reloj común de 252 segundos permite cerrar todas las fases sin discontinuidad; no se crea un reloj por campo.

Normal aumenta el desplazamiento máximo base a 5.5% del ancho y la variación de radio a 4.5%. Enhanced usa 7.5% y 5.5%, respectivamente. Las amplitudes reales de cada campo también dependen de su factor de profundidad. La curvatura tiene un límite independiente para conservar la silueta previa. Reduced conserva movimiento mínimo a mitad de velocidad; Off elimina el movimiento continuo y conserva la transición de canción cuando Android permite animaciones.

## Continuidad y recuperación

El reloj permanece fuera de la key de artwork. Cambiar canción retargetea las animaciones existentes de colores, posición, radios y energía sin recrearlo. El dibujo sigue en la raíz de Sonando, antes de los insets, detrás de toda la pantalla.

Se encontró una ruta que podía mantener el fondo inmóvil: `rememberSignalMotion` conserva la disponibilidad inicial de animaciones del sistema. Ahora el fondo vuelve a consultar esa disponibilidad en `ON_RESUME` y recrea únicamente ese snapshot cuando cambia. Esto corrige la ruta de estado obsoleto; no se ha confirmado que fuera la causa en el dispositivo del usuario. Salir de Sonando o activar/desactivar Off puede recrear el reloj; el contrato de continuidad entre canciones no depende de ello.

## Legibilidad y coste

Se conservan los límites de brillo, alpha y colores extraídos del artwork. En siete paletas y las fases/variaciones muestreadas, el contraste mínimo del modelo con blanco es **14.455664:1**, con luminancia máxima **0.022635892**. No son mediciones de pantalla ni una garantía sobre cada píxel posible.

El reloj se lee en `onDrawBehind`, no en la composición de la pantalla. `drawWithCache` conserva los brushes; una `graphicsLayer` propia aísla el dibujo del fondo. No se añade blur, bitmap animado, dependencia, coroutine por campo ni loop manual de 60 FPS. La prueba Android de recomposición está escrita y compila, pero todavía no se ha ejecutado.

## Validación realizada

- Compilación aislada de fuentes principales Kotlin/Compose y KSP: **exit 0**. Cuatro warnings existentes sobre URL/scaledDensity, fuera del fondo.
- Suite local: **128 pruebas unitarias, 0 fallos**. Incluye continuidad del ciclo, geometría acotada, modos de motion, contraste, siete paletas y diferencias temporales con artwork constante.
- Compilación aislada de todas las fuentes debug y androidTest, con el compilador Compose: **exit 0**. Se corrigió un conflicto JVM entre la propiedad `palette` y el método de prueba, ahora `updatePalette`.
- Ventanas de diez segundos desde 0, 7, 19 y 37 segundos: diferencia media mínima por paleta cromática entre 1.27 y 1.70 pasos de canal de ocho bits. El máximo cambio medio en un segundo de todas las paletas es 0.844. Estas métricas detectan una escena inmóvil o cambios abruptos, pero no demuestran percepción humana ni FPS.
- Inspección de imágenes del modelo en 0 y 12 segundos y galería de siete paletas: fondo oscuro, vetas amplias y bordes difusos. Composición del modelo aceptada; aceptación visual Android pendiente.
- Cuatro pruebas de instrumentación listas: movimiento real en diez segundos sin recomponer el padre, Off estático, continuidad al cambiar identidad de artwork y actualización de animaciones del sistema al reanudar.

Evidencia:

- `.artifacts/ambient-continuous-compile.log`
- `.artifacts/ambient-continuous-tests.log`
- `.artifacts/ambient-instrumentation-compile.log` (sin errores; proceso terminó en 0)
- `.artifacts/ambient/temporal-motion.csv`
- `.artifacts/ambient/marble-motion-model.gif` (modelo de diez segundos; no captura Android)
- `.artifacts/ambient/six-palettes-model.png` (nombre histórico; contiene siete paletas)

## Bloqueos de build y dispositivo

El comando `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug --offline --console=plain` falla al leer `<Android SDK>\platforms\android-37.0\package.xml` con acceso denegado; después Gradle informa que no puede encontrar el target `android-37.0`. Registro: `.artifacts/ambient-continuous-gradle.log`.

Android Studio MCP rechaza referencias/diagnósticos con `MCP tool call requires approval, but approval policy is never`. No fue posible completar preview ni validación mediante el IDE. No se generó ni instaló un APK nuevo, no se ejecutaron las cuatro pruebas Android y no se midieron 60/120 Hz ni jank. Las pruebas matemáticas a intervalos de esos refrescos no sustituyen esa validación.

## Archivos de esta pasada

Producción:

- `app/src/main/java/xyz/tosdev/yetasimplelyicsapp/ui/designsystem/AmbientScene.kt`
- `app/src/main/java/xyz/tosdev/yetasimplelyicsapp/ui/designsystem/DynamicAmbientBackground.kt`

Pruebas:

- `app/src/test/java/xyz/tosdev/yetasimplelyicsapp/ui/AmbientBackgroundTest.kt`
- `app/src/test/java/xyz/tosdev/yetasimplelyicsapp/ui/MotionInteractionTest.kt`
- `app/src/debug/java/xyz/tosdev/yetasimplelyicsapp/AmbientBackgroundTestActivity.kt`
- `app/src/debug/AndroidManifest.xml`
- `app/src/androidTest/java/xyz/tosdev/yetasimplelyicsapp/AmbientBackgroundInstrumentedTest.kt`
- `.artifacts/compile-ambient-instrumentation.ps1`

Documentación y valoración:

- `docs/AMBIENT_BACKGROUND.md`
- `docs/MOTION_SYSTEM.md`
- `docs/AMBIENT_CONTINUOUS_MOTION.md`
- `.rcs/state/ambient-continuous/model-verdict.json`
- `.rcs/state/ambient-continuous/forge-progress.json`
