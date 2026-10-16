# Fondo ambiental de Now Playing

El renderer actual está documentado en [AMBIENT_FOG_BACKGROUND.md](AMBIENT_FOG_BACKGROUND.md). Lo que sigue es el registro histórico del mármol, sustituido por la nueva petición de niebla luminosa. Las pasadas anteriores de color y movimiento están en [ALBUM_COLOR_BACKGROUND.md](ALBUM_COLOR_BACKGROUND.md) y [AMBIENT_CONTINUOUS_MOTION.md](AMBIENT_CONTINUOUS_MOTION.md).

Referencia de esta pasada: `codex-clipboard-3e26c837-8851-469f-a3c3-4c339c433e66.png`. La primera pasada se centró en el fondo y contraste. La continuación de motion se documenta en MOTION_REFINEMENT.md; conserva composición, parsing AMLL/TTML, reproducción, Floating Overlay y widgets.

## Construcción

1. `AdvancedLyricsApp` coloca `DynamicAmbientBackground` como primer hijo de su `Box(fillMaxSize)`, hermano de `AppFrame`. Se dibuja antes de los insets y ocupa status area, header, letras, controles y navegación. Sólo existe en Sonando.
2. Una base charcoal sólida y cuatro campos de luz forman el ambiente. Cada campo se distribuye sobre una curva S de cinco lóbulos difusos, formando vetas de mármol suave. Una vignette radial muy tenue oscurece bordes. Se retiraron tanto el campo local de letras como la sombra vertical inferior.
3. Luz principal alrededor del 32% del ancho / 21% del alto; secundaria a la derecha, 96% / 52%; luz muted al 18% / 78%; reflejo tenue del dominante al 28% / 90%. No son círculos pequeños: radios horizontales entre 90% y 110% del ancho y verticales entre 26% y 43% del alto. Todas se mezclan con bordes suaves.
4. `PaletteEngine` obtiene las muestras del mismo análisis de portada ya existente. `ArtworkAmbientColors` conserva dominante, secundario, muted, oscuro y un acento adicional antes del aclarado de controles. Selecciona matices distintos reales de la portada, no simples variantes de brillo. Negro/blanco dominante no oculta las muestras cromáticas; una portada monocroma permanece monocroma. Se conserva saturación con luminancia de fuente limitada a 0.30 y un límite adicional para blanco/gris. El oscuro sólo matiza la base. La cuarta luz usa el acento adicional de la portada.
5. Cuatro fases de 14/18/28/21 segundos producen drift, curvatura, expansión y opacidad desfasados mediante ondas combinadas. Off detiene el movimiento continuo; Reduced reduce su amplitud y funciona a mitad de velocidad. No hay un reloj por luz ni bucle manual. Los transforms se aplican exclusivamente al dibujo del fondo.
6. Al cambiar canción, los estados existentes interpolan colores, posición, radio y energía usando los motion tokens ambientales. El reloj no tiene key de canción. La identidad sólo cambia al salir de Now Playing o cambiar el nivel de movimiento a/de Off. La disponibilidad de animaciones del sistema se consulta otra vez al recibir ON_RESUME; esa actualización no incluye el reloj en su key.
7. No hay gradiente lineal ni fondo dentro del viewport de lyrics. Las regiones tienen centros y radios independientes y dejan zonas oscuras entre ellas; el reflejo inferior evita un simple descenso de color arriba a negro abajo.
8. `drawWithCache` conserva cuatro brushes radiales unitarios y una vignette. Las fases se leen dentro de `onDrawBehind`: se transforma el canvas y varía el alpha sin reconstruir shaders por el movimiento continuo. Las cinco muestras de cada curva se precalculan: 20 dibujos por frame con los mismos cuatro brushes; la opacidad compuesta de cada veta está acotada. Sólo colores o geometría del viewport invalidan esa caché. Una graphicsLayer propia limita la invalidación continua al fondo. No hay blur de bitmap ni nueva dependencia. Este uso sigue la [separación de caché y dibujo de Compose](https://developer.android.com/develop/ui/compose/graphics/draw/modifiers).

La luminancia de las fuentes y el alpha máximo se acotan antes de dibujar. Las pruebas muestrean el resultado compuesto para siete paletas, distintas fases y las variaciones de layout más intensas, de forma que una portada clara no vuelva blanca la pantalla. Sonando mantiene foreground y barras del sistema claros sobre su fondo oscuro; la preferencia de tema de las otras pestañas no se modifica. Una key de tema evita arrastrar texto oscuro del tema claro durante la entrada a esta superficie.

## Archivos de implementación

- `app/src/main/java/xyz/tosdev/yetasimplelyicsapp/ui/designsystem/DynamicAmbientBackground.kt`: renderer y estados de animación.
- `app/src/main/java/xyz/tosdev/yetasimplelyicsapp/ui/designsystem/AmbientScene.kt`: geometría, falloff y cálculo de movimiento comprobable.
- `app/src/main/java/xyz/tosdev/yetasimplelyicsapp/ui/theme/ArtworkAmbientColors.kt`: selección y amortiguación de muestras.
- `app/src/main/java/xyz/tosdev/yetasimplelyicsapp/ui/theme/PaletteEngine.kt`: conserva muestras ambientales junto a la paleta de interfaz.
- `app/src/main/java/xyz/tosdev/yetasimplelyicsapp/ui/designsystem/SignalComponents.kt`: elimina los dos fondos anteriores.
- `app/src/main/java/xyz/tosdev/yetasimplelyicsapp/ui/AdvancedLyricsApp.kt`: única capa global, composición estable y contraste.
- `app/src/main/java/xyz/tosdev/yetasimplelyicsapp/ui/screens/NowPlayingScreen.kt`: retira el campo ambiental local sin alterar el layout interior.
- `app/src/main/java/xyz/tosdev/yetasimplelyicsapp/MainActivity.kt`: sincroniza iconos del sistema con la superficie activa.

## Validación y límites

`AmbientBackgroundTest` comprueba siete familias de muestras: naranja/marrón, azul, rojo, verde, muy clara, casi negra y multicolor. Cubre conservación del matiz, ausencia de color inventado en monocromos, amortiguación de blanco/neón, contraste, variación en ambos ejes, continuidad de las fases en intervalos de 60/120 Hz y modos Off/Reduced/Normal/Enhanced. Los intervalos de prueba no son mediciones de FPS ni de jank.

Con la propiedad JVM `ambient.validation.dir`, el test exporta renders del modelo usando los mismos centros, radios, colores y stops que producción. `.artifacts/ambient/six-palettes-model.png` conserva su nombre histórico y muestra las siete paletas; `warm-0s-model.png` hasta `warm-30s-model.png` muestran distintas fases. Estos son renders matemáticos de fondo, **no capturas de Compose ni prueba de Skia en un dispositivo**. No sustituyen una inspección de Now Playing ejecutándose ni validan toda la extracción desde un bitmap Android real.

La compilación local usa `.artifacts/compile-ui.ps1` con Kotlin/Compose y dependencias ya presentes. `.artifacts/run-app-tests.ps1` compila todas las pruebas unitarias del módulo app y ejecuta las clases enumeradas, incluida la nueva suite. No equivale a empaquetar un APK.

El intento de Gradle `:app:testDebugUnitTest :app:lintDebug :app:assembleDebug --offline` quedó bloqueado al leer `platforms/android-37.0/package.xml` por acceso denegado. Android Studio MCP rechazó diagnósticos por requerir aprobación mientras la política activa es `never`. La verificación visual definitiva en Android, el APK y lint quedan pendientes de acceso al entorno; no se declaran aprobados.


### Resultado de la pasada anterior (antes del refinamiento de motion)

- Compilación aislada de todas las fuentes Kotlin/Compose y fuentes KSP presentes: exit code 0. Cuatro warnings existentes en URL/scaledDensity, fuera de los archivos de fondo.
- Suite local del módulo app: **110 tests, 0 fallos**, JUnit 4.13.2.
- Muestreo de seis paletas, 21 fases y cinco layouts: máximo de luminancia **0.034070227**; mínimo contraste con blanco **12.489557:1**. Son métricas del modelo, no mediciones de pantalla.
- Inspección de `six-palettes-model.png` y fases del fondo cálido: regiones amplias, fondo oscuro y bordes suaves. El verdict del modelo queda separado del gate Android, que permanece pendiente.
- Pruebas modificadas: `ui/AmbientBackgroundTest.kt` (comportamiento y renders opcionales) y `ui/MotionInteractionTest.kt` (retira el contrato obsoleto del campo local eliminado).
- Soporte actualizado: `.artifacts/run-app-tests.ps1`, `docs/UI_REDESIGN.md`, `docs/MOTION_SYSTEM.md` y los verdicts de `.rcs/state/ui-redesign/`.


