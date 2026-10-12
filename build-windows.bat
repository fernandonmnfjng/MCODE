@echo off
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"

set "MCODE_JAVA21="

if defined JAVA_HOME_21_X64 if exist "%JAVA_HOME_21_X64%\bin\java.exe" set "MCODE_JAVA21=%JAVA_HOME_21_X64%"
if not defined MCODE_JAVA21 if defined JAVA_HOME_21 if exist "%JAVA_HOME_21%\bin\java.exe" set "MCODE_JAVA21=%JAVA_HOME_21%"

if not defined MCODE_JAVA21 if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" (
  "%JAVA_HOME%\bin\java.exe" -version 2>&1 | findstr /C:"21." >nul
  if not errorlevel 1 set "MCODE_JAVA21=%JAVA_HOME%"
)

if not defined MCODE_JAVA21 if defined ProgramFiles (
  for /d %%D in ("%ProgramFiles%\Eclipse Adoptium\jdk-21*") do if exist "%%~fD\bin\java.exe" set "MCODE_JAVA21=%%~fD"
  for /d %%D in ("%ProgramFiles%\Java\jdk-21*") do if exist "%%~fD\bin\java.exe" set "MCODE_JAVA21=%%~fD"
)

if defined MCODE_JAVA21 (
  set "JAVA_HOME=!MCODE_JAVA21!"
  set "PATH=!JAVA_HOME!\bin;!PATH!"
) else (
  java -version 2>&1 | findstr /C:"21." >nul
  if errorlevel 1 (
    echo ERROR: No encontre un JDK 21.
    echo Instala JDK 21 o define JAVA_HOME_21_X64 con la ruta del JDK 21.
    echo No ejecutes Gradle con Java 25 para este proyecto.
    java -version 2>&1
    exit /b 2
  )
)

echo Java seleccionado:
java -version
call gradlew.bat clean build %*
exit /b %ERRORLEVEL%
