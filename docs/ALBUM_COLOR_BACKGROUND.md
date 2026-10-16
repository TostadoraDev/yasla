# Mármol con los colores del álbum

Pasada anterior, sustituida por la petición de [niebla luminosa](AMBIENT_FOG_BACKGROUND.md).

La petición más reciente cambia la intención del fondo: las vetas deben tener presencia y color, manteniendo la composición de Now Playing. Esta pasada sustituye la atenuación casi negra anterior.

## Cambios

- Selección de tonos de portada por separación de matiz, además de población. Dos versiones claras/oscuras del mismo rojo ya no desplazan un azul presente en el álbum.
- Cuarto acento real de la portada. Se conserva un apoyo neutro cuando existe; portadas monocromas permanecen monocromas. Las muestras menores al 1.5% de población no se convierten en acentos dominantes.
- Saturación original y mayor intensidad de luz. Las fuentes tienen luminancia limitada a 0.30; blanco y gris se atenúan y casi negro nunca se aclara.
- Cuatro vetas de cinco lóbulos con más presencia. La opacidad compuesta por veta se limita a 0.72 para controlar los cruces entre colores. Se conserva la forma, el movimiento continuo y los ciclos de 14/18/28/21 segundos.
- La cuarta luz usa el nuevo acento en lugar de repetir el dominante. Los colores siguen interpolándose al cambiar canción y el reloj conserva su fase.
- El tratamiento de colores se recuerda por paleta. El movimiento sigue en la capa de dibujo con cuatro brushes reutilizados y veinte lóbulos; no hay nueva dependencia ni blur por frame.

La primera prueba de intensidad detectó contraste mínimo de 5.21:1 en un cruce multicolor. Se redujo la opacidad de las vetas, conservando su saturación, para cumplir el objetivo de 7:1 con texto blanco en el modelo.

## Archivos

- `app/src/main/java/xyz/tosdev/yetasimplelyicsapp/ui/theme/ArtworkAmbientColors.kt`
- `app/src/main/java/xyz/tosdev/yetasimplelyicsapp/ui/designsystem/AmbientScene.kt`
- `app/src/main/java/xyz/tosdev/yetasimplelyicsapp/ui/designsystem/DynamicAmbientBackground.kt`
- `app/src/test/java/xyz/tosdev/yetasimplelyicsapp/ui/ArtworkAmbientColorsTest.kt`
- `app/src/test/java/xyz/tosdev/yetasimplelyicsapp/ui/AmbientBackgroundTest.kt`
- `.artifacts/run-app-tests.ps1`
- Este informe, los enlaces de documentación ambiental y `.rcs/state/album-color/`.

## Validación

La última compilación aislada principal y debug/androidTest de esta pasada terminó en 0. Pasaron 137 pruebas y el modelo alcanzó contraste mínimo 7.128873:1 (luminancia máxima 0.097288355). Estos resultados corresponden al mármol anterior, no al renderer de niebla que lo sustituye. Los renders matemáticos no sustituyen una captura Android.

El intento actual de Gradle falló al calcular el hash del archivo del SDK `<Android SDK>\.knownPackages`, con acceso denegado. Registro: `.artifacts/album-color-gradle.log`. Android Studio MCP rechaza referencias y diagnósticos porque requieren aprobación y la política activa es `never`. No hay APK nuevo ni verificación visual en dispositivo.
