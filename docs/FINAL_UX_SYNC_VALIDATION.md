# UX, sincronización y localización

Validación local: 14–15 de octubre de 2026. Proyecto `yetasimplelyicsapp`.

## Las seis mejoras

1. **AMLL +250 ms.** El parser añade `AMLL_TIMING` únicamente al reconocer el atributo `timing` del namespace Apple, no por la extensión TTML. `LyricsOffsetPolicy` elige primero el override nullable de la canción, incluido `0`; en su ausencia aplica 250 a AMLL y 0 al resto. Se conserva la clave existente de preferencias. El contenedor carga la preferencia antes de publicar el documento y actualiza el único `LyricsSyncEngine`: positivo retrasa, sin sumar sobre los timestamps. Now Playing, widgets y Floating Lyrics consumen esa misma posición. El marcador se almacena en el campo de capacidades existente, sin migrar el esquema de Room. Los documentos antiguos que no conservaron metadatos de origen no se reclasifican por conjetura; reimportarlos permite reconocer su formato.
2. **Fullscreen Lyrics.** Estado de presentación guardable, misma pantalla y mismo `movableContentOf` para el renderer. Desaparecen header, controles, tiempos, offset y navegación; el panel y la separación también colapsan en pantallas anchas. Botón accesible y Back recuperan los controles. El destino sigue siendo Now Playing, por lo que conserva la preferencia de pantalla encendida. No se modificó el background.
3. **Marquee.** `TextMeasurer` compara el layout real de una/dos líneas con el ancho disponible. Sólo el overflow anima título y artista, cada uno independientemente, con pausa en extremos y reinicio por identidad visual. El texto interior se mide sin límite horizontal; el recorte pertenece al viewport. Reduced/Off mantienen el texto quieto y su descripción accesible completa.
4. **Tamaño de letras.** Factor 0.75–1.5, normalizado y guardado en la nueva clave `lyrics_text_scale`. Slider de cuatro posiciones y preview. Escala font size, line height y tracking de primarias, secundarias y traducciones; también Current Word. No cambia la densidad de la pantalla ni el tamaño de widgets/overlay. Cambian las medidas reales y el wrapping; se mantienen lista, timeline, trayectoria y seguimiento de la región de foco existente (0.27).
5. **Ajustes.** Filas planas, separadores, secciones, switches y diálogos de radio. Grupos: acceso, apariencia, letras, reproducción, idioma y acerca de. Se mantienen permisos multimedia/overlay/notificaciones; tema/color dinámico/contraste/motion; word sync/vista/fallback/alineación/traducción; colores custom y opacidad condicionales; mantener pantalla; selección de idioma. Apariencia de karaoke se expande con motion compartido. Acerca de muestra versión real, repositorio, GPL-3.0 y créditos. El texto largo de créditos se presenta como descripción debajo de su título, evitando que lo comprima hasta una columna de caracteres. No se renombraron claves anteriores.
6. **Nombre.** Sólo `app_name` en inglés/español cambia a **Yet a Simple Lyrics App**. Acerca de usa ese recurso. Package, application ID, namespaces, base de datos, preferencias y repositorio permanecen iguales; YASLA sigue siendo abreviatura.

## Palabras rápidas: causa y solución

El scheduler monotónico ya despertaba aproximadamente 1–5 ms tarde en la prueba de 100 ms. El cuello estaba después: un mutex serializaba instancias y cada palabra consultaba geometría, leía preferencias, escribía snapshot, medía/rasterizaba y escribía la firma. La segunda instancia podía encontrar su palabra obsoleta antes de Binder. La reconstrucción semántica no era la causa.

Ahora hay cache por instancia de configuración/geometría/firma; rasterización fuera de Main; cache de glyphs de hasta 4 MiB y prewarm de la siguiente `LyricsWord` completa. Una cola conflada por instancia persiste sólo el snapshot más reciente fuera del mutex de entrega; valida generación dentro de la transacción. Configuración, resize, idioma y eliminación invalidan la cache. El guard inmediatamente anterior a Binder sigue descartando contenido obsoleto. No se reproducen palabras atrasadas.

Los deadlines se calculan desde el reporte absoluto de MediaSession y su reloj monotónico, incluyendo velocidad y offset. No dependen de cuándo terminó el render anterior. El índice semántico se prepara al cambiar documento; los segmentos siguen siendo sólo timing. DEBUG registra palabra esperada saneada, timestamp original, deadline, petición, preparación, envío y descartes. Los datos transitorios de lookahead no alteran firmas ni persistencia.

No se añadieron actualizaciones parciales de RemoteViews ni un fallback automático basado en una constante. La preparación/disco era el cuello medido, y la API no confirma cuándo dibuja un launcher. El modo configurable **Current Line** existente permite priorizar continuidad en canciones extremas; AUTO sigue usando palabras cuando el word sync es fiable, nunca decide por cantidad de snaps.

### Medición antes/después

Emulador API 36.1, render SwiftShader, dos instancias; host de prueba real visible, misma fixture de 30 palabras por intervalo. La app principal no dirige el reloj visual durante la medición. No es una certificación de Spotify, de un launcher de terceros ni de batería.

| Intervalo | Dibujadas antes | Dibujadas después | P50 interna antes/después | Máxima antes/después |
|---|---:|---:|---:|---:|
| 100 ms | 1/30 | 22/30 | 287 / 37 ms | 287 / 131 ms |
| 150 ms | 1/30 | 30/30 | 126 / 27 ms | 126 / 79 ms |
| 200 ms | 30/30 | 30/30 | 101 / 29 ms | 195 / 89 ms |
| 300 ms | 24/30 | 30/30 | 113 / 26 ms | 157 / 49 ms |
| 500 ms | 28/30 | 30/30 | 77 / 25 ms | 249 / 34 ms |

P50 y máxima miden sample→Binder de las muestras entregadas, no palabras descartadas. El contador del host recoge dibujos observados durante cada fase; la palabra ya visible en el warm-up no necesita reenviarse. A 100 ms siguen existiendo pérdidas internas: esta ejecución solicitó 22 envíos y el host dibujó los 22, por lo que no se atribuyen esos descartes al launcher. No se afirma precisión visual de milisegundos ni que absolutamente todas las palabras puedan mostrarse. Otros launchers pueden además retrasar/agrupar actualizaciones. Fuentes oficiales: [actualizaciones de App Widgets](https://developer.android.com/develop/ui/views/appwidgets/advanced), [AppWidgetManager](https://developer.android.com/reference/android/appwidget/AppWidgetManager).

## Localización

Recursos nativos: `values` inglés y `values-es` español. Primera selección sin preferencias: inglés, incluso antes del handoff en `Application.attachBaseContext`. Se preservan elecciones existentes y la selección System explícita. API 33+ usa `LocaleManager.applicationLocales` como fuente de verdad tras el handoff; versiones anteriores usan `Configuration`/contexto localizado y la preferencia guardada. La acción de Ajustes refresca superficies y recrea la Activity; System busca un idioma soportado y usa inglés como fallback.

Auditoría final: **392 recursos base, 390 españoles, 0 claves traducibles faltantes, 0 incompatibilidades de argumentos**. Dos ausencias intencionales con `translatable=false`: `ui_home_brand` y `system_overlay_service_subtype`. Incluye navegación, Now Playing, biblioteca, búsqueda, estudio/editor, ajustes, configuración/previews/estados de widgets, Floating Lyrics, permisos, diálogos, notificaciones y accesibilidad. No se traducen letras, metadatos musicales ni provenance persistida de proveedores. Se corrigió el test para permitir las [categorías de plurales propias de cada idioma](https://developer.android.com/guide/topics/resources/string-resource#Plurals).

## Evidencia y límites

- Unit tests: **217 app + 92 dominio**, sin fallos.
- Build Debug y AndroidTest: correctos. Build adicional mediante Android Studio MCP: correcto.
- Lint Debug: **0 errores, 54 advertencias**; no se ocultaron ni cambiaron severidades.
- Diagnósticos frescos IDE de archivos modificados: sin errores.
- Pruebas nativas de offset y localización: correctas. Comprueban ausencia de override, 0/negativo/personalizado, recargas, handoff, español persistente, inglés, System y recursos UI/widget/notificación/diálogo.
- Fullscreen y escala en Activity real: correctos. La línea pasó de 253 px a 847 px al cambiar 0.75→1.5; cambió el wrapping, sin modificar la posición reportada del reproductor. Back restaura navegación y se conserva `FLAG_KEEP_SCREEN_ON`.
- La primera captura tenía un Floating Lyrics previamente habilitado sobre la Activity; la prueba se aisló del servicio sin modificar su renderer. Las capturas finales no contienen texto duplicado.
- Selector real de Ajustes: diálogo de tema y cambio English→Español correctos, incluida navegación traducida. La Activity conserva el scroll durante la recreación.
- Repetición final sobre el APK corregido: **1 prueba nativa correcta en 39.285 s**, incluyendo fullscreen, escala, idioma y ancho legible del título Créditos. Captura `credits-green-proof/settings-about-spanish.png` inspeccionada; también muestra el nombre visible completo. Build final adicional con unit tests y Lint: correcto (`final-credits-verified-build.log`).
- Idioma real del emulador: **6 casos correctos**. Sin selección guardada y dispositivo es-ES, la app muestra inglés; español persiste tras force-stop/reapertura; System muestra español en es-ES, inglés en fr-FR y vuelve a español al cambiar el dispositivo en tiempo real. Se usan XML de accesibilidad nuevos en cada lectura, sin reutilizar un dump anterior (`native-locale-runtime-results.json`).
- Para simular una instalación nueva se aisló temporalmente el listener de notificaciones: un listener ya habilitado podía arrancar el proceso y conservar preferencias en memoria antes de editar la fixture. Se restauraron idioma, overlay y permiso del listener; tamaño original 1080×2400, sistema en-US. Se cerró únicamente el emulador propio (`final-restored-environment.json`).
- Fullscreen ancho: texto medido 1150→2331 px; al volver, 1150 px y los mismos bounds originales. Capturas normal/fullscreen/restored verificadas.
- Regresión de widgets: dos pruebas adicionales correctas, incluyendo segundo plano, palabras semánticas con seis segmentos, pausa, resize dentro de la misma clase de tamaño, reconexión y cambios de permiso sin duplicar callbacks.
- No se ensayó Spotify real, teléfono físico, 120 Hz ni Doze de fabricante. La instrumentación y los cambios globales de idioma se ejecutaron en API 36.1; el camino anterior a API 33 tiene comprobaciones de lógica, no una ejecución en un dispositivo de esa versión.

Los hashes verifican que `DynamicProceduralBackground`, `AmbientScene`, `ArtworkAmbientColors`, `AmllKaraokeText`, `AmllLyricsMotion` y `KaraokeText` no cambiaron.

Artefactos locales: `.artifacts/rapid-localization-20261013/` contiene logs, auditoría de recursos, comparación de latencias, capturas, hashes y manifiesto de 39 archivos de código/recursos/tests. Ver `final-credits-verified-build.log`, `final-checks-summary.json`, `resource-audit.json`, `latency-comparison.json`, `credits-green-device.log`, `wide-bounds-verified.log`, `widget-regression-device.log`, `native-locale-runtime-results.json` y `modified-files.json`. El veredicto visual se guarda en `.rcs/state/final-ux/forge-progress.json`. Se conservan evidencias de intentos fallidos: aislamiento del overlay/listener, timeout durante el arranque bajo presión del emulador y correcciones del harness de accesibilidad; no se alteró el renderer para hacer pasar pruebas.

APK final: `app/build/outputs/apk/debug/app-debug.apk`. SHA-256: `FABE3C14041B08352B1E0552C62427AC18CC51501073DD8F759B896AD16758E0`.

## Archivos de implementación

- Sincronización: `LyricsAppContainer.kt`, `WidgetCueTiming.kt`, `data/preferences/AppPreferences.kt`, dominio `LyricsModels.kt`, `TtmlLyricsParser.kt`, `LyricsOffsetPolicy.kt`.
- Widgets: `widget/LyricsWidgetUpdater.kt`, `LyricsWidgetState.kt`, `CurrentWordRemoteViews.kt`, `WidgetModels.kt`, `WidgetDeliveryDiagnostics.kt`.
- UX: `ui/AdvancedLyricsApp.kt`, `LyricsUiModels.kt`, `screens/NowPlayingScreen.kt`, `screens/SettingsScreen.kt`, `components/Karaoke.kt`, `components/AmllLyricsViewport.kt`, `data/preferences/VisualPreferences.kt`.
- Localización: `localization/AppLanguageManager.kt`; `values` y `values-es` de `strings.xml`, `ui_strings.xml`, `lyrics_size_strings.xml`, `settings_refinement_strings.xml`; además `values-es/studio_strings.xml` y `values-es/system_strings.xml`.
- Tests: `LyricsOffsetPolicyTest.kt`, `WidgetCueTimingTest.kt`, `ImmersiveMarqueePolicyTest.kt`, `LyricsTextScalePolicyTest.kt`, `LanguageSelectionTest.kt`, `LocalizationResourcesTest.kt`; instrumentación `RapidWidgetInstrumentedTest.kt`, `OffsetPersistenceInstrumentedTest.kt`, `FinalUxInstrumentedTest.kt`, `LanguageSelectionInstrumentedTest.kt`.
