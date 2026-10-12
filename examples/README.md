# Ejemplos de MCODE

Ejecuta un archivo desde una pestaña del editor. Para detener un script, pulsa **Detener**. Al detenerse o terminar, el runner borra las formas que pertenecen a esa ejecución.

1. `01_2d_primitives.mcode` — primitivas 2D colocadas en un plano del mundo.
2. `02_animation_and_handles.mcode` — animación de una forma y cambio de color con handles.
3. `03_beacon_hunt_game.mcode` — juego de recoger balizas en el mundo.
4. `04_3d_primitives.mcode` — punto, esfera, cilindro, cono y malla 3D.
5. `05_animated_3d_frame.mcode` — animación 3D con intercambio atómico de frames.
6. `06_frame_and_cleanup.mcode` — demuestra que al detener el script no quedan residuos dibujados.
7. `07_3d_beacon_hunt.mcode` — mini juego de recolección con esfera 3D y récord local.

## Animaciones sin residuos

Usa `mc.begin_frame()` al principio de cada frame y `mc.end_frame()` al final. Las nuevas formas permanecen ocultas hasta que `end_frame()` las publica y reemplaza las formas del frame anterior de esa ejecución. Usa `time.sleep()` para controlar la frecuencia de actualización. Si el script termina o se detiene, se limpia toda la geometría que le pertenece.

## Qué significa 2D en esta fase

`circle`, `rect`, `polygon` y `polyline` son geometría 2D definida en un plano `xz`, `xy` o `yz`, pero se dibuja en el mundo 3D de Minecraft. No es todavía una interfaz HUD/GUI pegada a la pantalla; ese sería un renderer de pantalla independiente.

8. `08_model_import_obj.mcode` — importar un modelo estático OBJ exportado de Blender (primero copia el OBJ con Recursos).

## Modelos OBJ y proyectos

Consulta `SOLUCION_OBJ_Y_PROYECTOS.md` para exportar un modelo OBJ desde Blender, importarlo al workspace y distribuir scripts/recursos como paquetes `.mcp`.

10. `MCODE_demo_project.mcp` — paquete de proyecto importable con dos scripts, un OBJ de prueba y una guía breve. En el juego: M > Recursos > Importar proyecto MCODE (.mcp).
