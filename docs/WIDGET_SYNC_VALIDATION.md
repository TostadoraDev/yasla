# Widgets, voces y sincronización

Este pase corrige el modelo semántico, la entrega de widgets, la recuperación de sesiones y el control de pantalla. No rediseña Sonando. Los diez archivos protegidos de background y animación AMLL conservan sus hashes originales.

## 1. Por qué todavía se separaban palabras por snaps

Había dos entradas incorrectas al modelo: el parser de TTML genérico tomaba el final de un span como final de palabra, y documentos ya guardados en Room podían conservar una `LyricsWord` por cada fragmento temporal. Corregir únicamente el dibujo no reparaba esos documentos.

`TimedSegment` conserva el texto temporal y sus timestamps. `LyricsWord` contiene el texto semántico completo y sus segmentos. El widget recibe `LyricsWord.text`; no selecciona una sílaba como texto visible.

## 2. Parser y normalización

`TtmlLyricsParser` reconstruye el texto real antes de determinar palabras, tanto para AMLL como para TTML genérico. Conserva espacios interiores, NBSP, puntuación y Unicode; respeta `xml:space="preserve"`. La indentación de un XML formateado no se convierte en un separador artificial entre sílabas.

`reconstructSemanticLyricsWords` repara el grafo antiguo al cargarlo mediante `LyricsEntityMapper`. Usa el texto original de la línea como referencia; la reparación no cambia el esquema de Room ni reescribe destructivamente la biblioteca.

## 3. Reconstrucción y layout

Los límites semánticos proceden del texto y, en idiomas sin espacios convencionales, de `BreakIterator` con el locale correspondiente. Los offsets del texto se proyectan sobre los segmentos conservando su información temporal. La normalización es idempotente y conserva los IDs de palabras ya correctas.

Ejemplos verificados: `cuan` + `do` → CUANDO; `qui` + `e` + `re` → QUIERE; `mien` + `tras` → MIENTRAS; seis segmentos → EXTRAORDINARIO; `te ` + `quiero` → TE y QUIERO. Puntuación, NBSP, XML formateado y segmentación japonesa también tienen regresiones.

El resolvedor existente mantiene la última palabra durante gaps y recalcula la palabra correspondiente al hacer seek. La unidad de cambio del widget es el ID de `LyricsWord`, no el segmento activo.

Se reutiliza el fitter existente: mide con el mismo `TextPaint` que dibuja, intenta una línea, reduce moderadamente el tamaño y busca particiones equilibradas por grafemas. Los cortes lingüísticos son una preferencia, nunca una restricción. Las dimensiones reales del widget, menos su padding, determinan el ancho y la altura; no se pasan timestamps ni segmentos al algoritmo. Current Word no genera ellipsis. La mayúscula se aplica en presentación con el locale; el modelo conserva su texto fuente.

Límite de datos: si un registro antiguo ya perdió los espacios originales y contiene separadores inventados, no existe información suficiente para reconstruirlo con certeza. No se unen palabras por adivinación; ese documento necesita el TTML original.

## 4. Primera voz

Las definiciones `ttm:agent` se analizan antes de construir las líneas. Su orden determina las voces principales/secundarias; las alineaciones explícitas tienen prioridad. La voz asignada al primer span vocal real se resuelve por timestamp, incluso dentro de wrappers sin timestamp, antes de publicar el documento.

Por ello una primera línea de v2 puede aparecer a la derecha desde el primer estado. También se corrigen documentos guardados con las identidades canónicas v1/v2 pero alineación AUTO: la primera aparición de v2 no redefine v2 como voz principal. No se deduce un dueto del nombre de artistas ni del audio. Metadatos insuficientes conservan el estilo normal.

## 5. Voces de fondo y duetos

`LyricsPresentation` prepara las alineaciones de las voces de primer plano antes de recorrer las voces de fondo. `ttm:role="x-bg"` conserva su rol secundario e hereda la alineación de su grupo vocal; no crea una tercera voz de dueto aunque aparezca antes del foreground o tenga otro agent. Las animaciones y el viewport existentes permanecen intactos.

Tests cubren primera voz principal/secundaria, alternancia, metadata ausente, background primero, agent en span, alineación explícita y resolución al volver al inicio.

## 6. Pantalla encendida

La preferencia existente ahora está activada por defecto y sigue respetando un valor guardado OFF. Sus nombres en español e inglés son los solicitados.

`KeepScreenOnEffect` observa el lifecycle y la pestaña seleccionada. Activa `FLAG_KEEP_SCREEN_ON` sólo si la preferencia está ON, Sonando está seleccionado y la actividad está RESUMED. Lo desactiva al cambiar de pestaña, pasar a segundo plano, disponer el efecto o destruir la actividad. `MainActivity` aplica y limpia el flag oficial. No se introduce WakeLock ni se intercepta el bloqueo manual.

## 7. Causas del retraso

La entrega anterior llamaba a `GlanceAppWidget.update()` y consideraba el contenido renderizado al regresar. Esa llamada solicita una sesión asíncrona; no confirma una entrega a `AppWidgetManager`. El estado podía quedar marcado como entregado aunque la actualización siguiera pendiente del worker/sistema.

En las primeras pruebas del nuevo envío directo apareció además un coste real: componer Glance por cada palabra, con varios widgets instalados, podía tardar cientos de milisegundos y acumular trabajo.

Ahora `WidgetUpdateDispatcher` mantiene una única señal conflada, vuelve a muestrear el reloj compartido y descarta contenido obsoleto antes del Binder. No reproduce palabras atrasadas. Los modos generales usan composición puntual en el proceso vivo y `AppWidgetManager.updateAppWidget`; Current Word utiliza RemoteViews mínimos con el mismo bitmap transparente de glyphs del renderer existente, evitando una composición Glance por palabra.

Sólo se marca entregado después del retorno de Binder. La enumeración, adquisición del mutex y envío tienen un límite de ocho segundos. Fallos temporales no matan el consumidor. Configuración, refresh y resize toman una muestra fresca del reloj compartido, también estando pausado.

La planificación semántica existente usa límites absolutos de la canción y posición estimada monotónica con velocidad, estado y offset. No se añade polling de playback ni WorkManager para sincronización en tiempo real. Los atributos estables de sync se calculan al cargar el documento, no mediante recorridos de todas sus palabras en cada muestra.

La revisión final detectó un caso separado: el refresh de resize recorría todas las instancias y un fallo podía abortar el resto. En pausa no llegaba otra palabra para reintentarlo. Ahora el receiver sólo encola los IDs afectados al mismo dispatcher de Application. Las revisiones por ID preservan un resize recibido durante composición, los lotes de tres rotan los fallos y sólo se eliminan IDs confirmados o borrados. El refresh dirigido fuerza geometría aunque ambos tamaños pertenezcan a COMPACT. Tiene un timeout por instancia de dos segundos dentro del presupuesto de ocho segundos, incluyendo el mutex. Las excepciones se aíslan por ID. No se crean scopes ni retries propios del BroadcastReceiver.

## 8. Causas de pérdida de conexión

Una reconexión podía conservar el mismo token pero dejar inválido el grafo de callbacks anterior. Los cambios de permiso tampoco reconstruían automáticamente toda la suscripción. Un callback antiguo ya encolado podía volver a actuar sobre una sesión reemplazada. Por separado, una excepción o una operación de entrega bloqueada podía detener el consumidor de widgets.

## 9. Recuperación

El monitor compartido pertenece a Application y no depende de que Sonando esté abierto. `MediaPlaybackMonitor` observa cambios del permiso, reconstruye el listener y los callbacks al reconectar, libera registros antiguos y descarta callbacks de una generación anterior. Los tokens destruidos no se vuelven a registrar desde una lista momentáneamente obsoleta.

Hay un único retry pendiente, sólo tras fallar la suscripción: 250 ms, 1 s, 4 s, 15 s y después un minuto. Una recuperación correcta lo cancela. Con una conexión sana no hay sondeo periódico. Se conserva el `requestRebind` oficial de NotificationListenerService y el bootstrap de Application al recrearse el proceso. No se añade otro detector de Spotify, Foreground Service ni servicio permanente.

## 10. Latencia y evidencia

Los logs DEBUG separan muestra MediaSession, posición estimada, ID hash de palabra, objetivo monotónico programado, solicitud, composición y entrega a Binder. No incluyen las letras completas. `launcherAck=unavailable` deja explícito que el retorno de Binder no confirma cuándo el launcher pintó.

La instrumentación utiliza una MediaSession propia sin audio comercial ni comandos a otros reproductores y un AppWidgetHost real. El host registra cada RemoteViews recibido. La prueba larga envía 450 palabras preparadas con diferentes cantidades de segmentos y verifica cambio de texto visible, palabra completa, gaps, pausa, seek hacia atrás, frecuencia de publicaciones y continuidad con la actividad detenida.

La prueba de diez minutos pasó: **600,020 s**, **300 IDs de palabras semánticas**, **seis textos completos visibles** y **303 actualizaciones recibidas por el host**. Latencia muestra→Binder: **mediana 145 ms, máximo 511 ms**. El host siguió recibiendo al final; no hubo cola de palabras atrasadas ni publicaciones por cada sílaba. Pausa y seek hacia atrás también pasaron. Log: `.artifacts/widgets-sync-20261013/device-semantic-600-green.log`; métricas: `device-trace-600.log`.

La comprobación del texto del host lee exclusivamente el ImageView de glyphs, porque AppWidgetHostView añade un nombre accesible del proveedor independiente de la palabra. La primera ejecución larga falló por ese matcher del test; se conserva su log, y la repetición corregida pasó.

CPU del proceso completo de instrumentation DEBUG: 74,720 s CPU / 600,020 s wall, aproximadamente 12,45 % de un núcleo. Incluye las comprobaciones de test cada 100 ms, MediaSession sintética y logs; no mide por separado CPU del widget en release ni consumo de batería. El profiling físico sigue preparado, no certificado.

La prueba de paridad pasó: compara el bitmap nativo con el bitmap del renderer Glance para cinco palabras en tres tamaños, sin aproximaciones de fuente. La captura del host verifica glyphs realmente dibujados y alpha cero en la esquina. En la captura de 252 × 231 px, los bounds visibles fueron (42, 76)–(213, 153): centrados y sin recorte ni ellipsis.

![Palabra completa en un widget pequeño, con transparencia real](screenshots/widget-semantic-word.png)

## 11. Límites y protocolo preparado

El emulador utilizado es API 36.1. Instrumentation y la MediaSession de prueba mantienen un proceso disponible: diez minutos de esta prueba no demuestran comportamiento bajo muerte del proceso, Doze, limitaciones del fabricante o un launcher físico. Android puede aplazar CPU/jobs en segundo plano; no se promete sincronización exacta mientras el proceso esté suspendido. Véase [Doze y App Standby](https://developer.android.com/training/monitoring-device-state/doze-standby).

La recuperación usa las APIs admitidas de [NotificationListenerService](https://developer.android.com/reference/android/service/notification/NotificationListenerService). No se pide exclusión de batería ni se añade FGS para mantener vivo artificialmente el widget.

Protocolo preparado para un teléfono y Spotify real:

1. Importar AMLL con seis segmentos por palabra y otra canción que empiece con v2. Confirmar palabra completa y estilo inicial, incluido seek a cero.
2. Con acceso a notificaciones concedido, colocar Current Word pequeño y grande, redimensionarlos y abrir YASLA una vez. Reproducir, volver al launcher y abrir otra app.
3. Cambiar canción, pausar, reanudar y hacer seek en ambas direcciones. Dejar reproducir diez minutos y comparar objetivo/request/Binder en los logs DEBUG con la pintura del launcher.
4. Cerrar y volver a abrir Spotify; comprobar token nuevo, un callback por sesión, palabra actual recuperada sin abrir Sonando. Revocar y conceder el acceso, repetir con otro reproductor.
5. Bloquear manualmente, desbloquear y repetir tras recreación ordinaria del proceso. Probar Doze por separado, esperando recuperación del estado actual al reactivarse, no reproducción de una cola de palabras.
6. Configurar un timeout corto. Sonando debe superar ese timeout; Ajustes debe permitir el apagado. Probar preferencia OFF y reiniciar la aplicación.
7. Perfetto/Batterystats en hardware para CPU, batería y número de callbacks/broadcasts. La CPU medida dentro de instrumentation DEBUG no es una medición de autonomía ni de 60/120 Hz.

## 12. Tests, diagnostics y build

- App: **203/203 tests unitarios**, cero errores/fallos/skipped.
- Dominio: **89/89 tests unitarios**, cero errores/fallos/skipped.
- Android Studio MCP: **28/28 archivos Kotlin analizados, cero errores**, cobertura completa.
- El build de Android Studio MCP terminó con `success=true`; los contadores de mensajes fueron null, no un supuesto cero.
- El runner MCP no pudo crear la configuración de tests solicitada. Se usaron Gradle y AndroidJUnitRunner como alternativa, con sus logs conservados.
- Gradle: debug, APK de tests, release sin firmar y lint **BUILD SUCCESSFUL** tras la última corrección. Lint: cero errores, 57 warnings en el proyecto.
- La APK final pasó **6/6 tests instrumentados** en 106,994 s: continuidad corta, paridad Native/Glance, recuperación de MediaSession/permiso sin Activity, lifecycle y pestañas del flag, apagado real con timeout de 15 s y preferencia default/persistida. El test de continuidad incluye resize **96 → 120 dp estando pausado**, ambos COMPACT, y comprueba que cambió el tamaño real del raster conservando la misma palabra. Log: `device-final-resize.log`.
- La prueba larga de 600 s pasó antes del último ajuste dirigido de resize; la APK final recibió además la repetición completa de seis casos con continuidad de 20 s y resize. No se presenta esta última ejecución como otra prueba de diez minutos.
- La revisión independiente encontró el caso de resize/retry y terminó sin hallazgos materiales pendientes después de corregirlo. Tres regresiones nuevas del dispatcher pasaron de RED a GREEN: refresh de palabra pausada, reintento sólo de IDs fallidos y resize recibido durante composición.

Las fuentes, capturas y logs de este pase están en `.artifacts/widgets-sync-20261013/`. Los fallos iniciales y sus regresiones RED/GREEN también se conservan; no se presentan como pruebas exitosas.

La prueba de pantalla mantuvo Sonando encendido 18 s con timeout de 15 s y comprobó apagado en Ajustes tras otros 18 s. Timeout, alimentación simulada y preferencia previa se restauraron al terminar. No se desinstaló ni vació la aplicación; sólo se borraron el widget y documento sintéticos creados por la prueba.

## Archivos modificados

La lista exacta de fuentes y tests figura a continuación. `WidgetCurrentWordResolver`, `WordLineBreaker`, `WidgetWordBitmap` y NotificationListenerService se reutilizaron sin modificarlos.

<!-- changed-files:start -->
- app/src/androidTest/java/xyz/tosdev/yetasimplelyicsapp/CurrentWordDeliveryInstrumentedTest.kt
- app/src/androidTest/java/xyz/tosdev/yetasimplelyicsapp/KeepScreenOnInstrumentedTest.kt
- app/src/androidTest/java/xyz/tosdev/yetasimplelyicsapp/WidgetConnectionInstrumentedTest.kt
- app/src/androidTest/java/xyz/tosdev/yetasimplelyicsapp/data/preferences/VisualPreferencesInstrumentedTest.kt
- app/src/debug/AndroidManifest.xml
- app/src/debug/java/xyz/tosdev/yetasimplelyicsapp/KeepScreenOnTestActivity.kt
- app/src/debug/java/xyz/tosdev/yetasimplelyicsapp/Widgets2HostActivity.kt
- app/src/main/java/xyz/tosdev/yetasimplelyicsapp/LyricsAppContainer.kt
- app/src/main/java/xyz/tosdev/yetasimplelyicsapp/MainActivity.kt
- app/src/main/java/xyz/tosdev/yetasimplelyicsapp/data/local/LyricsEntityMapper.kt
- app/src/main/java/xyz/tosdev/yetasimplelyicsapp/media/MediaPlaybackMonitor.kt
- app/src/main/java/xyz/tosdev/yetasimplelyicsapp/ui/AdvancedLyricsApp.kt
- app/src/main/java/xyz/tosdev/yetasimplelyicsapp/ui/KeepScreenOnEffect.kt
- app/src/main/java/xyz/tosdev/yetasimplelyicsapp/ui/LyricsUiModels.kt
- app/src/main/java/xyz/tosdev/yetasimplelyicsapp/widget/CurrentWordRemoteViews.kt
- app/src/main/java/xyz/tosdev/yetasimplelyicsapp/widget/LyricsWidget.kt
- app/src/main/java/xyz/tosdev/yetasimplelyicsapp/widget/LyricsWidgetUpdater.kt
- app/src/main/java/xyz/tosdev/yetasimplelyicsapp/widget/WidgetDeliveryDiagnostics.kt
- app/src/main/java/xyz/tosdev/yetasimplelyicsapp/widget/WidgetUpdateDispatcher.kt
- app/src/main/res/layout/widget_current_word_live.xml
- app/src/main/res/values-es/ui_strings.xml
- app/src/main/res/values/ui_strings.xml
- app/src/test/java/xyz/tosdev/yetasimplelyicsapp/data/local/LyricsEntityMapperTest.kt
- app/src/test/java/xyz/tosdev/yetasimplelyicsapp/ui/KeepScreenOnPolicyTest.kt
- app/src/test/java/xyz/tosdev/yetasimplelyicsapp/widget/SemanticWidgetTimelineTest.kt
- app/src/test/java/xyz/tosdev/yetasimplelyicsapp/widget/WidgetUpdateDispatcherTest.kt
- core/domain/src/main/kotlin/xyz/tosdev/yetasimplelyicsapp/domain/lyrics/LyricsPresentation.kt
- core/domain/src/main/kotlin/xyz/tosdev/yetasimplelyicsapp/domain/lyrics/SemanticLyricsWords.kt
- core/domain/src/main/kotlin/xyz/tosdev/yetasimplelyicsapp/domain/lyrics/parser/TtmlLyricsParser.kt
- core/domain/src/test/kotlin/xyz/tosdev/yetasimplelyicsapp/domain/lyrics/LyricsPresentationTest.kt
- core/domain/src/test/kotlin/xyz/tosdev/yetasimplelyicsapp/domain/lyrics/SemanticLyricsWordsTest.kt
- core/domain/src/test/kotlin/xyz/tosdev/yetasimplelyicsapp/domain/lyrics/parser/TtmlLyricsParserTest.kt
<!-- changed-files:end -->

