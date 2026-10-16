# Campo procedural de Now Playing

Esta implementación reemplaza el renderer anterior de halo central. El artwork se usa únicamente para extraer una paleta; nunca se dibuja, amplía, difumina ni se entrega al shader como imagen.

## Arquitectura

`AdvancedLyricsApp` coloca `DynamicProceduralBackground` como primer hijo de su `Box(fillMaxSize)`, antes de `AppFrame`. Por ello ocupa la pantalla completa detrás del status bar, header, artwork, metadata, lyrics, controles y navegación. No vive dentro de `LyricsViewport` y no añade cards detrás del texto.

`ProceduralAtmosphereCanvas` elige el backend:

- API 33+: un `RuntimeShader` AGSL fullscreen, sin input shader, textura ni bitmap.
- API 29–32 y Canvas software: el mismo campo matemático se evalúa sobre una malla pequeña y reutilizable; `Canvas` interpola los vértices.
- API 24–28 hardware: washes enormes y solapados mantienen compatibilidad sin mostrar contornos circulares ni una portada de fondo.

## Campo de luz

`PROCEDURAL_ATMOSPHERE_SHADER` evalúa ruido de valor quintic y aplica domain warping de frecuencia baja. Tres muestras correlacionadas controlan tono, iluminación y undertone. El resultado mezcla dominante, secondary, muted y accent sobre una base derivada del artwork. No existe centro, radio, máscara de halo ni caída radial en el backend principal; el color cubre cada píxel del viewport.

La paleta conserva cuatro familias visuales comprobadas: naranja/ámbar/rojo, violeta/navy, oliva/beige y gris/charcoal. `proceduralAtmospherePalette` limita la luminancia de sus tonos antes de dibujar, pero mantiene una luz neutra visible cuando la portada es casi negra. Las mezclas del campo no inventan colores que no aparezcan en la paleta.

## Movimiento continuo

El reloj ambiental está fuera de la key del artwork y de cualquier estado de reproducción. Las fases de 56, 72, 84 y 126 segundos siguen avanzando mientras Now Playing está visible, incluso después de terminar la transición de canción o con playback pausado. El ciclo conjunto tarda 504 segundos en cerrar y no usa ping-pong, por lo que no hay rebotes ni reinicios perceptibles.

En cada frame sólo cambian uniforms: cuatro offsets de advección, dos offsets de evolución, extensión, intensidad, alpha y distribución cromática. Esto redistribuye regiones amplias de luz en lugar de desplazar círculos. `Off` queda inmóvil; `Reduced` conserva una variación mínima; `Normal` y `Enhanced` comparten el mismo lenguaje con distinta amplitud.

Cuando cambia la canción, `animateColorAsState` y los parámetros de artwork retargetean el campo durante 900 ms en Normal o 1100 ms en Enhanced. El clock no se crea de nuevo, así que la nueva atmósfera continúa desde la fase visual ya presente.

## Rendimiento

El renderer está aislado en `drawWithCache`. La composición de Now Playing no lee la fase y no se recompone cada frame. En API 33+ se dibuja un único rect fullscreen con un shader compilado una vez; los frames posteriores sólo actualizan arrays de uniforms y colores. No existe blur por frame, bitmap fullscreen, snapshot ni loop manual de coroutines. La ruta CPU reutiliza posiciones, índices y el buffer de colores.

## Archivos principales

- `ui/AdvancedLyricsApp.kt`: ubicación fullscreen en el root.
- `ui/designsystem/DynamicProceduralBackground.kt`: reloj y transición continua.
- `ui/designsystem/ProceduralAtmosphereShader.kt`: fuente AGSL analítica.
- `ui/designsystem/ProceduralAtmosphereField.kt`: paleta y evaluador de compatibilidad.
- `ui/designsystem/ProceduralAtmosphereCanvas.kt`: backends de Canvas.
- `ui/designsystem/AmbientScene.kt`: fases, perfiles y malla reutilizable.
- `ui/theme/ArtworkAmbientColors.kt`: extracción de swatches reales.

## Verificación del 4 de octubre de 2026

- Fuentes principales Kotlin/Compose: código 0, con cuatro warnings deprecados preexistentes fuera de este renderer.
- JVM: 136 pruebas, todas pasan. Cubren diez paletas, movimiento en toda ventana de 20 s, continuidad de 8/17 ms, `Off`/`Reduced`, cambio de artwork sin reset, campo opaco, reutilización de buffers, ausencia de bordes duros, monocromo y transiciones de paleta. Contraste mínimo calculado contra blanco: 4.67:1; durante interpolaciones: 5.10:1.
- Debug/androidTest: compila con código 0. Las pruebas preparadas capturan el renderer real a 0/10/20 s y comprueban que el padre no recomponga, pero no pudieron ejecutarse sin acceso al SDK/dispositivo.
- El shader de producción se tradujo mecánicamente a WebGL y compiló sin errores. Se renderizaron las diez paletas a 0/5/10/20 s; es evidencia del programa matemático, no una captura Android.
- `visual-verdict`: 92/100, pass. Clasificó el resultado como campo procedural atmosférico, sin artwork reconocible, blobs discretos ni degradado lineal/radial obvio.

Evidencia local: `.artifacts/procedural-field/tests.log`, `shader-validation.json`, `shader-warm-0-20s.png`, `shader-monochrome-0-20s.png`, `reference-analysis-brass.jpg` y `.rcs/state/procedural-atmosphere/forge-progress.json`.

El build Gradle completo continúa dependiendo de que el proceso pueda leer el SDK Android 37. La validación Android real de AGSL, banding, 60/120 Hz y jank debe ejecutarse cuando ese acceso esté disponible; las capturas de escritorio se etiquetan expresamente como no Android.
