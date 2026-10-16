# Rediseño UX/UI

## Dirección

La nueva identidad se llama **Signal Noir**: una interfaz musical editorial, silenciosa y precisa. Combina fondos de grafito con luz tomada de la portada, títulos amplios, microetiquetas monoespaciadas y superficies translúcidas sólo en elementos flotantes. La portada y la letra crean la atmósfera; los controles mantienen una presencia secundaria.

La interfaz evita replicar Apple Music, Material You o Nothing OS. Conserva de esas referencias sólo principios generales: continuidad de movimiento, adaptación cromática, jerarquía limpia y personalidad tipográfica.

## Inventario actual

| Área | Entrada | Estado actual | Alcance del rediseño |
| --- | --- | --- | --- |
| Navegación | `AdvancedLyricsApp.kt` | Cinco destinos principales y tres subpáginas con estado local | Barra ligera animada, rail coherente y transiciones compartidas |
| Sonando | `NowPlayingScreen.kt` | Layout adaptable, portada, metadata, letras y controles | Fondo ambiental derivado del artwork, header sin cards, letras a 27% del viewport y controles compactos |
| Biblioteca | `LibrarySearchScreens.kt` | Recientes/favoritos y filas en superficies | Lista editorial, badges y estados vacíos propios |
| Buscar | `LibrarySearchScreens.kt` | Campo, resultados y mensajes | Barra inmediata, estados discretos y badges de calidad |
| Estudio | `StudioScreens.kt` | Hub, editor, overlay y widget | Jerarquía por tareas, previews dominantes y controles agrupados |
| Ajustes | `SettingsScreen.kt` | Lista larga de secciones | Aplicación del sistema visual sin modificar opciones |
| Overlay real | `LyricsOverlayService.kt` | Renderer funcional | Fuera del alcance del renderer; sólo se rediseña su configuración |
| Widget real | `LyricsWidget.kt` | Renderer Glance funcional | Fuera del alcance del renderer; sólo se rediseña su configuración |
| Configuración de widget del sistema | `WidgetConfigurationActivity.kt` | Actividad independiente | Adopta los mismos encabezados, superficies y jerarquía del editor principal |
| Tema y paleta | `LyricsTheme.kt`, `PaletteEngine.kt` | Tema neutro y motor de paleta sin integrar al árbol principal | Tokens centralizados e interpolación de paleta de artwork |

`LyricsApp.kt` es una pantalla de demostración antigua; el runtime usa `AdvancedLyricsApp`.

## Problemas encontrados

- Espaciados, radios, opacidades y duraciones aparecen como valores locales en cada pantalla.
- La paleta de artwork está implementada, pero `AdvancedLyricsApp` no la entrega al tema.
- El tema neutraliza el fondo incluso cuando recibe una paleta, por lo que el color dinámico pierde presencia.
- Biblioteca y Buscar usan una tarjeta por fila y muestran metadata con la misma prioridad.
- Studio concentra más de mil líneas y repite títulos, sliders, chips, superficies y divisores.
- Ajustes usa grandes contenedores redondeados para cada grupo y produce una sucesión de cards.
- `ScreenHeading` recibe `eyebrow` pero no lo muestra.
- La navegación usa duraciones locales y la selección depende del indicador Material estándar.
- Faltan previews dedicadas para pantallas principales, tema claro/oscuro y tamaños amplios.
- `onOpenSearchResult` y `onOpenWidgetPicker` no están conectados desde `MainActivity`; el rediseño no inventará comportamiento para esos callbacks.

## Componentes compartidos

Se centralizan los patrones visibles con una API pequeña:

- `SignalSectionHeader` y el patrón de toolbar de subpágina para jerarquía editorial.
- `SignalSurface` para superficies elevadas o glass con fallback opaco.
- `SignalBadge` para proveedor, calidad y estado.
- `SignalEmptyState` para estados sin contenido.
- `SignalNavigationBar` y `SignalNavigationRail` para navegación principal.
- `DynamicProceduralBackground` para la única atmósfera global de Now Playing; el viewport de letras no dibuja iluminación adicional.
- `SignalIconButton` para controles compactos con touch target accesible.

`KaraokeText` añade profundidad draw-only por palabra semántica sin tocar el parser ni convertir sílabas en composables. Overlay y Glance permanecen aislados.

La navegación inferior usa iconos sin contenedor, labels discretos y un underline fino animado. El header compacto de Now Playing usa una portada de 96 dp, título bold, artista con cuerpo más legible, álbum y el package real de la sesión; la fuente de lyrics y el tipo de sync permanecen fuera de la superficie principal. El offset ocupa los extremos de la fila de transporte, usa color secundario y su label sólo aparece cuando es distinto de cero.

El shell aplica el mismo padding horizontal base antes de `AnimatedContent` para todos los destinos. Now Playing ya no añade un segundo padding local ni cambia las constraints externas durante la transición, por lo que el ancho útil permanece estable al cambiar de pestaña. Los insets superior, inferior y de navegación siguen perteneciendo al `AppFrame` compartido.

`DynamicProceduralBackground` es el primer hijo del `Box(fillMaxSize)` de `AdvancedLyricsApp`, hermano de `AppFrame`, antes de sus insets. El fondo ocupa también status area y navegación. El backend principal usa un shader procedural fullscreen con ruido de baja frecuencia y domain warping; no existe portada ampliada, halo central ni iluminación local dentro de lyrics. `ArtworkAmbientColors` conserva muestras reales de portada antes del aclarado de acentos para controles. Now Playing mantiene superficie oscura y foreground claro también cuando las otras pestañas usan el tema claro; la preferencia guardada no cambia.

Los colores se adaptan antes de entrar al shader. Advección, evolución, extensión, intensidad y mezcla cromática se leen sólo en `onDrawBehind`, de modo que no recomponen el contenido por el movimiento ambiental. Ver [AMBIENT_FOG_BACKGROUND.md](AMBIENT_FOG_BACKGROUND.md) para arquitectura, transiciones, pruebas y límites de validación.

## Estructura propuesta de Now Playing

### Teléfono

1. Identidad de canción compacta: portada, título, artista, álbum y package de la sesión, sin chips ni cards.
2. Letra como región flexible principal, con tipografía editorial de 38 sp y la línea activa al 27% del viewport para anticipar más contexto por debajo.
3. Progreso discreto y controles Previous / Play-Pause / Next según capacidades reales de MediaSession.
4. Fondo atmosférico derivado de portada con vignette estable.

Current Word prioriza siempre el texto semántico completo: una línea, reducción moderada, cortes lingüísticos, cortes por grafema y reducción de emergencia. La última ruta conserva todos los grafemas y nunca sustituye contenido por puntos suspensivos.

### Pantalla amplia

1. Columna izquierda con portada protagonista, metadata y controles.
2. Columna derecha dedicada a letras.
3. La paleta y el cambio de canción se animan como una sola secuencia perceptual.

## Splash

Se usa `androidx.core:core-splashscreen` y un tema de inicio nativo. Android muestra el icono y el color base mientras crea la actividad. Al salir, el icono hace una transición breve de escala/opacidad y se retira inmediatamente; no existe temporizador artificial. El contenido Compose entra con una opacidad breve que respeta el nivel de movimiento.

## Fases

1. Design System, tema, tipografía, motion, paleta y splash.
2. Navegación y Now Playing.
3. Biblioteca y Buscar.
4. Studio, editor de widget y configuración de overlay.
5. Ajustes y pulido transversal.

Después de cada fase se ejecutan diagnósticos y pruebas disponibles. La verificación visual se apoya en Compose Previews y captura real cuando Android Studio o un dispositivo sean accesibles.
