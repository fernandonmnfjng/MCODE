@echo off
setlocal EnableExtensions EnableDelayedExpansion
set "APP_HOME=%~dp0"
set "APP_BASE_NAME=%~n0"
set "CLASSPATH=%APP_HOME%gradle\wrapper\gradle-wrapper.jar"
set "MCODE_JAVA21="

rem MCODE / Minecraft 1.21.1 uses JDK 21 with the Gradle 8.14.3 wrapper.
rem Gradle 8.14.3 does not officially support running on Java 25.

if defined MCODE_JAVA21_HOME call :TryJavaHome "%MCODE_JAVA21_HOME%"
if not defined MCODE_JAVA21 if defined JAVA_HOME_21 call :TryJavaHome "%JAVA_HOME_21%"
if not defined MCODE_JAVA21 if defined JAVA_HOME_21_X64 call :TryJavaHome "%JAVA_HOME_21_X64%"
if not defined MCODE_JAVA21 if defined JAVA_HOME call :TryJavaHome "%JAVA_HOME%"

if not defined MCODE_JAVA21 if defined ProgramFiles (
  for /d %%D in ("%ProgramFiles%\Eclipse Adoptium\jdk-21*" "%ProgramFiles%\Java\jdk-21*" "%ProgramFiles%\Microsoft\jdk-21*" "%ProgramFiles%\Amazon Corretto\jdk21*") do (
    if not defined MCODE_JAVA21 call :TryJavaHome "%%~fD"
  )
)
if not defined MCODE_JAVA21 if defined LOCALAPPDATA (
  for /d %%D in ("%LOCALAPPDATA%\Programs\Eclipse Adoptium\jdk-21*" "%LOCALAPPDATA%\Programs\Microsoft\jdk-21*" "%LOCALAPPDATA%\Programs\Eclipse Foundation\jdk-21*") do (
    if not defined MCODE_JAVA21 call :TryJavaHome "%%~fD"
  )
)
if not defined MCODE_JAVA21 if defined USERPROFILE (
  for /d %%D in ("%USERPROFILE%\.jdks\*21*" "%USERPROFILE%\scoop\apps\temurin21-jdk\current") do (
    if not defined MCODE_JAVA21 call :TryJavaHome "%%~fD"
  )
)

if defined MCODE_JAVA21 (
  set "JAVA_HOME=!MCODE_JAVA21!"
  set "PATH=!JAVA_HOME!\bin;!PATH!"
  set "JAVA_EXE=!JAVA_HOME!\bin\java.exe"
) else (
  rem Last resort: use Java from PATH only if it is exactly version 21.
  java.exe -version 2>&1 | findstr /C:"21." >nul
  if not errorlevel 1 (
    set "JAVA_HOME="
    set "JAVA_EXE=java.exe"
  ) else (
    echo ERROR: MCODE requiere JDK 21. No se encontro una instalacion compatible.
    echo Instala JDK 21 o define MCODE_JAVA21_HOME con la carpeta raiz del JDK.
    echo No uses Java 25 con Gradle 8.14.3: produce Unsupported class file major version 69.
    echo Consulta SOLUCION_JAVA25.md.
    exit /b 2
  )
)

echo MCODE: ejecutando Gradle con JDK 21:
"%JAVA_EXE%" -version
"%JAVA_EXE%" %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
set "EXIT_CODE=%ERRORLEVEL%"
endlocal & exit /b %EXIT_CODE%

:TryJavaHome
if not exist "%~1\bin\java.exe" exit /b 0
"%~1\bin\java.exe" -version 2>&1 | findstr /C:"21." >nul
if not errorlevel 1 set "MCODE_JAVA21=%~1"
exit /b 0
