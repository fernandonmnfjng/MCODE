# MCODE 2.2.0

## Fase 5: primitivas 2D iniciales

- Añade `mc.circle(x, y, z, radius, plane="xz", segments=32, ...)`.
- Añade `mc.rect(x, y, z, width, height, plane="xz", ...)`.
- Añade `mc.polygon(points, plane="xz", level=0, ...)` con triangulación por ear clipping.
- Añade `mc.polyline(points, plane="xz", level=0, ...)`.
- Soporta los planos `xz`, `xy` y `yz`; puntos 2D pueden mapearse al plano mediante `level`, o pueden darse puntos XYZ completos.
- Los handles mantienen los métodos existentes (`remove`, `hide`, `show`, `color`, `alpha`, `width`, `fill`, `move`).
- Añade tres ejemplos, incluido `games/beacon_hunt.mcode`, un minijuego visual sin modificar bloques ni entidades.

## Estado

No se ha añadido aún un plano orientado arbitrario, texto 3D, esfera/cilindro/cone, entrada de teclado/ratón desde scripts, importación de modelos ni caché avanzada. La compilación completa debe verificarse con `./gradlew clean build` en un entorno con JDK 21 y acceso a los repositorios de Gradle/Fabric.
