@echo off
setlocal

set "ROOT=%~dp0"
if "%ROOT:~-1%"=="\" set "ROOT=%ROOT:~0,-1%"
set "JAVA_HOME=D:\Download\zulu21.44.17-ca-jdk21.0.8-win_x64\zulu21.44.17-ca-jdk21.0.8-win_x64"
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo [1/4] Generating SQLDelight interfaces...
call "%ROOT%\gradlew.bat" -p "%ROOT%" :shared:generateCommonMainWaterValveDbInterface --no-daemon
if errorlevel 1 exit /b 1

echo [2/4] Compiling shared JVM target...
call "%ROOT%\gradlew.bat" -p "%ROOT%" :shared:compileKotlinJvm --no-daemon
if errorlevel 1 exit /b 1

echo [3/4] Compiling shared JVM tests...
call "%ROOT%\gradlew.bat" -p "%ROOT%" :shared:compileTestKotlinJvm --no-daemon
if errorlevel 1 exit /b 1

echo [4/4] Running shared JVM tests...
call "%ROOT%\gradlew.bat" -p "%ROOT%" :shared:jvmTest --no-daemon
if errorlevel 1 exit /b 1

echo.
echo Shared module verification completed successfully.
endlocal
