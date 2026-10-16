# Refinamiento de motion — 3 de octubre de 2026

Cambios aplicados al fondo global de Now Playing y al movimiento de su lista de letras. Se conserva la composición, tipografía, colores de artwork, controles y navegación. No se modificaron el parser AMLL/TTML, la reconstrucción de palabras, el reloj de reproducción, Floating Overlay ni widgets.

## Mármol suave en movimiento

Cuatro campos de luz siguen curvas S asimétricas, con cinco lóbulos difusos por curva. Su curvatura cambia además de desplazarse: ondas combinadas de 18, 24, 28 y 21 segundos, distintas fases y profundidad. Un único reloj Compose comparte un período continuo de 504 segundos; no tiene key de canción. Cambiar artwork interpola color, radio y energía sin reiniciar las fases.

Los lóbulos comparten cuatro brushes cacheados. El dibujo usa 20 círculos suaves y una vignette; no necesita blur de bitmap, nuevas dependencias ni shaders exclusivos de Android 33. La opacidad compuesta se limita explícitamente. Off permanece estático y Reduced reduce amplitud y funciona a mitad de velocidad. La reproducción pausada conserva el movimiento lento del fondo.

Primera revisión del modelo: las luces seguían pareciendo manchas difusas. Se estrechó la veta, se acentuó su curvatura y se recuperó radiancia local dentro de los límites de contraste. Se mantuvieron bordes suaves y paleta oscura.

## Continuidad de las letras

- El tween iniciado por cada cambio de línea se sustituyó por un seguidor analítico críticamente amortiguado. Un único consumidor del frame clock conserva posición y velocidad al cambiar el destino; no usa timers de 16 ms ni coroutines por palabra.
- El padding de la lista permanece fijo. Los offsets de LazyList se convierten a coordenadas del viewport mediante `viewportStartOffset`, evitando contar mal el padding. El foco se mantiene alrededor del 27%; la anticipación máxima es el 2% del viewport.
- La opacidad depende de la distancia física al foco. La escala queda entre 0.985 y 1; se eliminó el desplazamiento independiente de las filas. Un duet que avanza varias filas con reproducción continua no se considera un seek sólo por su índice.
- Los seeks y cambios de canción atenúan el contenido antes de recolocarlo. El scroll manual conserva el control y permite volver a seguir la línea actual.
- `LyricsWord` sigue siendo la unidad visual. Su intervalo completo controla una llegada suave: opacidad 0.94–1, escala 0.995–1, desplazamiento máximo 0.8 dp en Normal y 1 dp en Enhanced. Pausar lleva este micro movimiento a reposo con un único estado compartido.
- Los segmentos controlan el reveal con Hermite monótono. Cada timestamp y progreso de frontera permanece exacto; se suaviza la velocidad entre segmentos contiguos y se respetan los huecos temporales. Los bounds, máscaras y dirección de texto del renderer permanecen intactos.
- Una línea pasada conserva sus glyphs completados y pierde énfasis por distancia, evitando apagar abruptamente el highlight AMLL. Usa una copia de presentación y progreso congelado; no cambia texto/timestamps fuente ni se suscribe al reloj cada frame.
- El refinamiento de `KaraokeText` es opt-in en Now Playing. Sus otros consumidores mantienen el comportamiento anterior.

## Archivos

Producción:

- `app/src/main/java/xyz/tosdev/yetasimplelyicsapp/ui/designsystem/AmbientScene.kt`
- `app/src/main/java/xyz/tosdev/yetasimplelyicsapp/ui/designsystem/DynamicAmbientBackground.kt`
- `app/src/main/java/xyz/tosdev/yetasimplelyicsapp/ui/components/LyricsMotion.kt` — nuevo
- `app/src/main/java/xyz/tosdev/yetasimplelyicsapp/ui/components/Karaoke.kt`
- `app/src/main/java/xyz/tosdev/yetasimplelyicsapp/ui/components/KaraokeText.kt`
- `app/src/main/java/xyz/tosdev/yetasimplelyicsapp/ui/screens/NowPlayingScreen.kt`

Pruebas y soporte:

- `app/src/test/java/xyz/tosdev/yetasimplelyicsapp/ui/LyricsContinuousMotionTest.kt` — nuevo
- `app/src/test/java/xyz/tosdev/yetasimplelyicsapp/ui/AmbientBackgroundTest.kt`
- `app/src/test/java/xyz/tosdev/yetasimplelyicsapp/ui/MotionInteractionTest.kt`
- `.artifacts/run-app-tests.ps1`
- `docs/MOTION_SYSTEM.md`, `docs/AMBIENT_BACKGROUND.md` y este informe
- `.rcs/state/motion-refinement/` — evidencia y límites de aceptación visual

## Validación ejecutada

| Comprobación | Resultado |
| --- | --- |
| Compilación aislada de todas las fuentes Kotlin/Compose y KSP presentes | Exit 0; cuatro advertencias existentes en URL/scaledDensity |
| Suite local del módulo app | **127 tests, 0 fallos**; JUnit 4.13.2 |
| Modelo de scroll | 5, 10 y 20 cues consecutivos; pasos de 60/120 Hz; posición, velocidad, pausa, reset de seek, padding y foco |
| Modelo de palabras | Límites de movimiento, palabra completa, conservación del modelo fuente, reveal monótono, timestamps exactos, segmentos rápidos/desiguales, gaps y fallback sin segmentos |
| Modelo ambiental | Seis paletas, continuidad de posición/velocidad, curvas deformables, límites de brillo, Off/Reduced y fase independiente del artwork |
| Contraste mínimo con blanco del muestreo ambiental | **14.468349:1**; luminancia máxima **0.022572203** |
| Gradle: testDebugUnitTest + lintDebug + assembleDebug | Bloqueado: acceso denegado a `Android/Sdk/platforms/android-37.0/package.xml`; no encuentra target `android-37.0` |
| Android Studio MCP / captura de la app | Bloqueado por requerir aprobación con política `never` |

La prueba espacial del fondo ahora mide variación a lo largo de ambos ejes del campo completo: los dos puntos fijos originales correspondían a círculos que dejaron de existir. Conserva los mismos umbrales de variación y la comprobación de bordes suaves.

## Evidencia y límites

- `.artifacts/marble-motion-compile.log` y `.artifacts/marble-motion-tests.log` contienen los resultados finales.
- `.artifacts/motion/twenty-cues-motion.png` y los CSV de 60/120 Hz muestran el modelo del seguidor usado en producción. Son simulaciones deterministas, no medidas de FPS.
- `.artifacts/ambient/six-palettes-model.png` y `warm-0s-model.png` a `warm-30s-model.png` muestran el modelo geométrico/color de producción.
- `.artifacts/ambient/marble-motion-model.gif` es un extracto de 10 segundos a 20 fps, etiquetado MODEL / NOT ANDROID. No repite artificialmente un ciclo de 10 segundos; el reloj real dura 504 segundos.

No se generó ni instaló un APK nuevo. Queda pendiente observar Now Playing en Android durante 20 transiciones reales, especialmente duet/RTL, wraps largos, seek, pausa/reanudación y cambio de canción, y medir jank/overdraw a 60/120 Hz. Los 20 dibujos ambientales aumentan el trabajo de GPU respecto de las cuatro luces anteriores; las pruebas del modelo no acreditan rendimiento en dispositivo.
