# Instrucciones para Antigravity: desarrollo por fases de MCODE

MCODE pretende convertirse en una API rápida para crear experiencias, herramientas y juegos dentro de Minecraft. Trabaja de forma incremental; no intentes implementar todas las fases en un único cambio.

## Regla obligatoria de trabajo

1. Trabaja en una sola fase/milestone por vez. Antes de cambiar código, resume el objetivo y enumera los archivos que esperas tocar.
2. Termina solo la fase activa. No adelantes primitivas ni importadores de fases posteriores salvo que la fase activa los necesite como tipos vacíos/interfaz de extensión.
3. Después de cada fase, ejecuta `./gradlew build`. Si falla, arregla el error antes de continuar. No declares una fase compilada si Gradle no terminó correctamente.
4. Cuando compile, inicia Minecraft 1.21.1 con Fabric y prueba el comportamiento real dentro del mundo. Revisa un mundo de un jugador y un servidor multijugador cuando corresponda.
5. Escribe en el cierre qué se compiló, qué pruebas dentro del juego se hicieron, qué falló y qué queda pendiente. No afirmes haber probado el juego si no lo abriste.
6. Mantén el modo multijugador LOCAL como límite de seguridad: ninguna función local debe enviar mensajes, comandos ni modificaciones al servidor. Las acciones de red deben tener un control explícito y probado.
7. Evita emojis en el código fuente, comentarios, identificadores y mensajes del programa.

## Estado base y secuencia

- Fase 2 — Renderizador: líneas y quads de color con `WorldRenderEvents.AFTER_TRANSLUCENT`, coordenadas relativas a la cámara, agrupación por capas. Mantén el anclaje/transformación de formas fuera del código del intérprete.
- Fase 3 — Módulo `mc`: `line`, `box`, `clear`, `remove`, `color`; handles de forma con `remove`, `hide`, `color` y `move`. Comprueba autocompletado y ejemplos básicos.
- Fase 4 — Espacios LOCAL/GLOBAL: GLOBAL usa coordenadas absolutas; LOCAL fija un ancla por defecto y permite `mc.mode("local", sigue=True)`. Cada forma retiene su espacio y ancla. Prueba yaw/pitch y movimiento del jugador.
- Fase 5 — 2D en el mundo: estado en MCODE 2.2.0 parcialmente implementado con `circle`, `rect`, `polygon`, `polyline`, planos `xz`/`xy`/`yz` y ear clipping. Antes de ampliar, compila y prueba estas primitivas dentro de Minecraft; no declares terminada la fase hasta validar el render y formas cóncavas.
- Fase 6 — 3D: solo después de validar Fase 5. Añade geometría, transformaciones, culling y caché medible.
- Fase 7 — Importaciones: PNG/texturas y OBJ; libéralas al eliminar la forma. glTF después.
- Fase 8 — Persistencia y ayuda: serialización, autocompletado y ayuda coherentes con las funciones realmente disponibles.

## Regresión de archivos multiplataforma

- Probar Abrir y Guardar en Linux/KDE con Dolphin instalado, Linux/GNOME, Windows y macOS.
- En Linux, comprobar la elección del diálogo disponible (`kdialog`/`zenity`) y el respaldo del selector nativo Java.
- Abrir un `.mcode`, modificarlo y guardarlo; confirmar que el texto exacto se conserva en UTF-8.
- Cancelar Abrir y Guardar: no debe borrar ni modificar el editor.
- Intentar abrir un archivo con otra extensión: debe mostrarse un mensaje claro, no fallar silenciosamente.
- Si ambos selectores fallan, la consola debe mostrar el error, no ocultarlo.

## Pruebas mínimas por fase

- Fase 2: línea y caja visibles al mover la cámara, sin temblor ni coordenadas desplazadas.
- Fase 3: crear, ocultar, recolorear, mover y quitar una forma; limpiar escena; probar argumentos inválidos.
- Fase 4: forma GLOBAL quieta en coordenadas de mundo; forma LOCAL anclada que no siga al jugador; forma LOCAL que siga; cambiar yaw/pitch; verificar que en un servidor `mc.say` y GLOBAL no pueden enviar nada.
- Consola: ejecutar un bucle con `time.sleep(1)` y verificar que cada salida queda en el historial, con desplazamiento vertical y sin borrar líneas anteriores.


## MCODE 2.2.0 — estado actual y siguiente trabajo
Antes de ampliar primitivas 2D/3D, validar en el juego: persistencia del borrador tras ESC/cierre/reapertura; abrir/guardar con SetPATH; confirmación de sobrescritura; dos pestañas ejecutándose a la vez; `import path.module`; lectura/escritura de archivos dentro del workspace; rechazo de rutas que intenten escapar del workspace. Las ejecuciones simultáneas comparten consola y escena visual, por lo que cada script debe usar identificadores/nombres distintos para sus recursos visuales.


## Ejemplos y pruebas 2.2.0

- `examples/01_2d_primitives.mcode`: validación visual de círculos, rectángulos, polígonos y polilíneas en LOCAL.
- `examples/02_animation_and_handles.mcode`: prueba movimiento y cambio de color de un handle.
- `examples/03_beacon_hunt_game.mcode`: minijuego client-side que requiere GLOBAL en un mundo de un jugador; solo dibuja, consulta posición y escribe un récord local. No edita bloques ni entidades.
- Los scripts de ejemplo se parsearon con el lexer/parser MCODE y las primitivas nuevas pasaron una prueba Java aislada con stubs de las APIs Minecraft: círculo, rectángulo, polígono cóncavo y polilínea. La compilación completa y la validación in-game siguen pendientes hasta disponer de acceso a los repositorios Gradle/Fabric.


## MCODE 2.3.0 — siguientes verificaciones

- Fase 6 inicia con `point`, `sphere`, `cylinder`, `cone`, `mesh` y transformaciones `move`, `rotate`, `scale`, `reset_transform`.
- Cada ejecución de pestaña posee sus formas; detener/finalizar limpia solo esa ejecución.
- Para animaciones, utiliza `mc.begin_frame()` y `mc.end_frame()` alrededor de cada frame dentro del bucle. El frame nuevo se publica de forma atómica.
- Cada pestaña tiene botón `×`, confirmación de guardar cambios y se protege la última pestaña.
- Las primitivas 2D de fase 5 son geometría plana dentro del mundo 3D, no dibujos de GUI/HUD.
- Prueba los ejemplos `04_3d_primitives.mcode`, `05_animated_3d_frame.mcode` y `06_frame_and_cleanup.mcode`. Antes de declarar fase 6 terminada, compila con JDK 21 y prueba dentro de Minecraft 1.21.1 con Fabric.
