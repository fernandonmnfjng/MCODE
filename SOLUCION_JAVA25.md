# Solución para `Unsupported class file major version 69`

## Qué está pasando

`major version 69` corresponde a bytecode de Java 25. El log contiene dos mensajes diferentes:

- `WARNING: java.lang.System::load ... native access`: es una advertencia de acceso nativo; no es la causa del fallo de compilación.
- `Unsupported class file major version 69`: es la causa real. Gradle 8.14.3 no declara soporte para ejecutarse sobre Java 25. La compatibilidad oficial de Gradle para ejecutar sobre Java 25 empieza en Gradle 9.1.0. El proyecto usa Fabric Loom 1.9.2 para Minecraft 1.21.1; por tanto, esta reparación conserva Gradle 8.14.3 y selecciona JDK 21, en lugar de actualizar Gradle a ciegas.

Referencia oficial: <https://docs.gradle.org/current/userguide/compatibility.html>

## Uso recomendado

El lanzador `gradlew` (Linux/macOS) y `gradlew.bat` (Windows) ahora buscan una instalación de JDK 21 y la seleccionan automáticamente, incluso si la variable `JAVA_HOME` apunta a Java 25. Así, el comando habitual también pasa por la comprobación:

### Linux

```bash
./gradlew --version
./gradlew clean build
```

Si no tienes JDK 21 en Ubuntu/Debian:

```bash
sudo apt update
sudo apt install openjdk-21-jdk
./gradlew clean build
```

Si JDK 21 está en una ruta personalizada:

```bash
export MCODE_JAVA21_HOME="/ruta/a/la/carpeta-raiz-del-jdk-21"
./gradlew clean build
```

La carpeta indicada debe contener `bin/java`, no debe ser la carpeta `bin` en sí.

### macOS

```bash
./gradlew --version
./gradlew clean build
```

El lanzador consulta `/usr/libexec/java_home -v 21` y también busca ubicaciones comunes. Si el JDK está en una ruta no estándar, define `MCODE_JAVA21_HOME` como arriba.

### Windows (CMD)

```bat
gradlew.bat --version
gradlew.bat clean build
```

Si el JDK 21 está en una ruta personalizada:

```bat
set MCODE_JAVA21_HOME=C:\ruta\al\jdk-21
gradlew.bat clean build
```

## Si compilas desde un IDE

Algunos IDE (por ejemplo, IntelliJ IDEA) inician Gradle directamente y no necesariamente usan el script `gradlew`; en ese caso configura la JVM de Gradle como JDK 21:

- IntelliJ IDEA: Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JVM → JDK 21.
- VS Code: asegúrate de que la extensión de Gradle use JDK 21 o ejecuta `./gradlew clean build` desde una terminal donde el wrapper indique JDK 21.

Cierra los daemons antiguos antes de volver a compilar:

```bash
./gradlew --stop
./gradlew --version
./gradlew clean build
```

## Dónde queda el mod

Cuando termine con `BUILD SUCCESSFUL`, busca el JAR en `build/libs/`. No se deben crear `build` ni `libs` manualmente: Gradle las genera durante la compilación.

Esta reparación fuerza la selección del JDK correcto en el wrapper y da un error claro si no encuentra JDK 21. La compilación completa aún debe ejecutarse en tu equipo con acceso a los repositorios de Gradle/Fabric.
