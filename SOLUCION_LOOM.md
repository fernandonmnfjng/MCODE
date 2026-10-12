# Solución: `fabric-loom` no se encontraba

## Causa

El proyecto solicitaba `id 'fabric-loom' version '1.9'`. Esa versión exacta no tiene un marcador estable publicado en el Maven oficial de Fabric. La línea 1.9 sí tiene versiones estables publicadas, entre ellas `1.9.1` y `1.9.2`.

## Corrección incluida

- `build.gradle`: `fabric-loom` actualizado a `1.9.2`.
- `gradle.properties`: versión del mod actualizada a `2.0.3`.
- `README.md`: nombre esperado del JAR actualizado a `textmenu-2.0.3.jar`.
- Se mantiene Gradle 8.14.3 y JDK 21, sin migrar innecesariamente a Gradle 9.

## Linux y macOS

Desde la carpeta del proyecto: 

```bash
./gradlew --refresh-dependencies clean build
```

## Windows

```bat
gradlew.bat --refresh-dependencies clean build
```

`--refresh-dependencies` hace que Gradle vuelva a consultar los repositorios después del cambio de versión. Si vuelve a aparecer `Could not resolve plugin artifact`, comprueba que la red/firewall pueda acceder a `https://maven.fabricmc.net/` y `https://plugins.gradle.org/`.

## Resultado esperado

Tras `BUILD SUCCESSFUL`, el archivo instalable queda en `build/libs/textmenu-2.0.3.jar`. Copia ese JAR a la carpeta `mods` de una instancia Minecraft 1.21.1 con Fabric.

La modificación se ha comprobado estáticamente y la estructura del ZIP se ha validado. La compilación completa debe confirmarse en un equipo con acceso a Maven/Gradle; este entorno no puede resolver DNS para los repositorios de Fabric.
