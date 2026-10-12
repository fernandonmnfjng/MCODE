# Modelos de Blender y paquetes de proyecto

## Importar un modelo de Blender

1. En Blender, selecciona la malla que quieras exportar.
2. Usa `File > Export > Wavefront (.obj)` y exporta la geometría. Esta primera versión importa mallas estáticas; no transfiere materiales PBR, rigging ni animaciones.
3. Dentro de Minecraft abre MCODE con `M`, pulsa `Recursos` y después `Importar modelo Blender (OBJ)`.
4. El modelo se copia a `.minecraft/mcode/models/`.
5. Usa este código en una pestaña:

```python
SetPATH tests/my_blender_model
import mc
import time
mc.mode("local", sigue=True)
model = mc.import_model("models/my_model.obj", 0, 1, 4, scale=1, axis="blender", color="cyan", fill=True)
while True:
    model.rotate(0, 1, 0)
    time.sleep(0.05)
```

`axis="blender"` convierte el eje Z vertical de Blender al eje Y vertical de Minecraft. Si tu exportación ya aplica esa conversión, usa `axis="xyz"`.

## Paquetes `.mcp`

Pulsa `Recursos > Exportar proyecto MCODE (.mcp)` para crear un ZIP con extensión `.mcp`. Incluye scripts y recursos compatibles que están en el workspace `mcode`, excepto la sesión del editor. Pulsa `Importar proyecto MCODE (.mcp)` para instalarlos en el workspace. Si hay conflictos, puedes sobrescribir los archivos o conservar los existentes.

Por seguridad, el importador bloquea rutas absolutas y `..`, limita tamaño y cantidad de archivos, y no instala JARs, ejecutables ni bibliotecas nativas. El paquete `.mcp` distribuye proyectos de scripts y assets MCODE; no compila ni instala por sí mismo un mod Fabric `.jar`.

## Navegar pestañas y booleanos

- Usa `‹` y `›` para desplazar la barra de pestañas cuando hay muchas.
- `Ctrl+Tab` / `Ctrl+Shift+Tab` y `Ctrl+PageDown` / `Ctrl+PageUp` cambian entre pestañas.
- Las pestañas enseñan el nombre del archivo o la última parte de `SetPATH`.
- El editor normaliza los literales escritos o pegados como `true` / `false` a `True` / `False`; cadenas y comentarios no se modifican.

## Proyecto de prueba descargable

La carpeta `examples/MCODE_demo_project.mcp` contiene dos scripts y `models/demo-cube.obj`. Impórtalo desde **M > Recursos > Importar proyecto MCODE (.mcp)**; después abre `games/model_spin.mcode` desde el workspace o la pestaña Abrir y ejecútalo. Para probar tu propia malla, expórtala a OBJ desde Blender y utiliza **Recursos > Importar modelo Blender (OBJ)**.
