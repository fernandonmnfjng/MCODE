# MCODE 2.4.1 — Corrección de importación OBJ y diagnóstico de consola

## Corregido
- `mc.import_model` aceptaba solo un argumento posicional aunque la ayuda y los ejemplos enviaban `path, x, y, z`. Ahora acepta `path[, x[, y[, z]]]` y también coordenadas por nombre.
- Si se pasa una coordenada de forma posicional y por nombre a la vez, se devuelve un `TypeError` que indica qué argumento está duplicado.

## Añadido
- Botón **Copiar log**: copia al portapapeles del sistema el registro completo conservado en la consola, incluida la línea parcial actual.
- Atajo `Ctrl+Shift+C` para copiar el log desde el editor.
- Ayuda, metadatos del paquete y versión del proyecto actualizados a 2.4.1.
- Ejemplo `examples/09_console_copy_test.mcode` para probar el copiado de registros.

## Estado de verificación
- El importador OBJ se compiló y se probó de forma aislada con la malla de ejemplo (8 vértices, 12 triángulos, 12 aristas).
- Las comprobaciones estáticas de cambios, metadatos y ZIP pasaron.
- La compilación completa de Fabric queda pendiente: este entorno no puede resolver `services.gradle.org` para descargar Gradle.
