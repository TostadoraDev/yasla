# Signal Motion System

## Objetivo

El movimiento explica continuidad, prioridad y cambio de estado. No decora cada elemento. Compose controla el frame clock, por lo que el sistema funciona en pantallas de 60, 90 o 120 Hz sin asumir intervalos de 16 ms.

## Niveles

| Preferencia | Comportamiento |
| --- | --- |
| Off | Cambios inmediatos; sólo la salida nativa del splash puede completar su retiro |
| Reduced | Opacidad y cambios de color breves; sin desplazamientos relevantes |
| Normal | Fade, desplazamiento corto y escala casi imperceptible |
| Enhanced | Misma coreografía con más profundidad y duración controlada |

Si la escala de animación del sistema es cero, el nivel efectivo es Off. El movimiento continuo se entrega al frame clock, sin temporizadores de 16 ms.

## Categorías

### Micro

- Tap/selección: 110–170 ms.
- Presión de transporte: escala `1.0 → 0.92 → 1` y opacidad coordinada.
- Toggle/chip: spring rígido y sin rebote visible.

### Content

- Filas y estados: 220–320 ms.
- Artwork: 360–680 ms según nivel de movimiento; la paleta ambiental usa 900–1100 ms.
- Now Playing: opacidad y escala dependen de la distancia física al foco; escala entre 0.985 y 1, sin traslación independiente por fila.
- Auto-scroll normal: seguidor analítico críticamente amortiguado, frecuencia de respuesta de 8/s, conserva velocidad y posición entre cues. Alcanza aproximadamente el 90% del recorrido en 500 ms. El centro permanece alrededor del 27% del viewport.
- Seek grande o cambio de canción: atenuación parcial, reposicionamiento y entrada; no atraviesa líneas intermedias.
- Reduced elimina transforms de línea y palabra, conserva opacidad y usa un seguidor de respuesta más rápida. Off recoloca por eventos y no mantiene un ciclo de frames.
- AMLL en Now Playing anima una sola palabra semántica según `wordStart → wordEnd`: desplazamiento máximo de 0.8 dp en Normal o 1 dp en Enhanced, escala 0.995–1 y opacidad 0.94–1. Los segmentos controlan sólo el reveal mediante interpolación Hermite monótona, preservando cada timestamp, progreso de frontera y pausa interna.
- La llegada a una nueva línea se anticipa como máximo un 2% del viewport durante los últimos 120–900 ms del intervalo. Pausar deja intacto el reloj de reproducción y lleva el micro movimiento de palabras a reposo mediante un único estado compartido.

### Navigation

- Tabs principales: fade y desplazamiento mínimo de 300–430 ms.
- El tab activo usa un underline fino; icono, label y color cambian como una sola respuesta.
- Subpáginas: shared axis horizontal corto.
- Dialog: fade + escala `0.98 → 1`.
- Sheet: spring amortiguado del componente Material.

### Immersive

- Cambio de canción coordina metadata, portada, paleta y letras.
- Paleta y geometría ambiental: 700 ms en Off, 800 ms en Reduced, 900 ms en Normal y 1100 ms en Enhanced.
- Background global: un único campo de niebla luminosa, con colores suavizados del artwork. Ondas de 56, 72, 84 y 126 segundos deforman lentamente su densidad; comparten un reloj continuo de 504 s. No hay círculos independientes ni vetas de mármol. Reduced funciona a mitad de velocidad.
- Off desactiva el movimiento continuo y conserva únicamente la evolución entre canciones. Reduced usa deriva de 0.3%; Normal mueve sus centros hasta 9% durante ciclos largos y Enhanced hasta 12%. Extensión e intensidad también respiran con amplitudes de 5.5%/6.5% en Normal y 7.5%/9% en Enhanced. El campo siempre es opaco.
- Toda la atmósfera pertenece al root de Now Playing. El backend principal es un `RuntimeShader` AGSL fullscreen con ruido de valor y domain warping de baja frecuencia; API 29–32 evalúa el mismo campo en una malla reutilizable. No hay input shader, bitmap, `Image` ni textura fullscreen. El reloj ambiental no tiene key de canción y modifica advección, evolución, extensión, intensidad y mezcla cromática después de que termine la transición de track. Ver `AMBIENT_FOG_BACKGROUND.md` para evidencia y límites de validación.

## Curvas y springs

- `fast`: alias semántico para respuesta táctil y microinteracciones.
- `standard`: estados y contenido pequeño.
- `content`: entrada/salida de contenido estructural.
- `emphasized`: artwork, paleta y cambios de canción.
- `natural`: curva cúbica `0.2, 0, 0, 1` para contenido y navegación.
- `editorial`: curva cúbica `0.4, 0, 0.2, 1` para letras y paleta ambiental.
- `micro`: `FastOutSlowInEasing` para interacción directa.
- `exit`: `FastOutLinearInEasing` para retirar contenido.
- `settleSpring`: damping sin rebote y stiffness baja.
- `microSpring`: damping sin rebote y stiffness media.

## Coreografías

### Cambio de pantalla

1. La pantalla anterior baja opacidad.
2. La siguiente entra 8–12 dp desde abajo.
3. El indicador de navegación cambia simultáneamente.

### Cambio de canción

1. Portada y metadata salen juntas.
2. La paleta comienza a interpolar.
3. Entran portada y metadata nuevas.
4. La letra nueva toma el foco cuando el contenido ya es legible.

El centro visual de la línea activa se coloca al 27% del viewport de letras. El cálculo resta la mitad de la altura real del item y convierte los offsets de LazyList a coordenadas del viewport restando `viewportStartOffset`. El padding permanece fijo al cambiar la altura de las líneas. Si una línea ocupa más que la zona disponible sobre el foco, su borde superior se mantiene visible.

La progresión visual de palabra usa `smootherstep` en una sola curva de llegada y liberación. Los límites de `TimedSegment` no reinician esa curva. Al terminar, la palabra retorna al baseline y conserva opacidad completa mientras la línea sigue activa. El refinamiento es opt-in en Now Playing: Floating Overlay y widgets mantienen sus contratos existentes.

### Splash

La splash nativa no espera. En la salida, el icono escala ligeramente y desaparece en menos de 220 ms. El contenido Compose aparece con un fade corto.

## Accesibilidad y pruebas

- Verificar Off, Reduced, Normal y Enhanced.
- Verificar escala del sistema en 0x y 1x.
- Comprobar que TalkBack no recibe elementos duplicados durante `AnimatedContent`.
- Comprobar que el foco no se pierde al cambiar de destino.
- Comprobar que listas y letras no generan jank durante cambios de paleta.

