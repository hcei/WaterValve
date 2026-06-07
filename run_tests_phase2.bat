@echo off
set "JAVA_HOME=C:\Users\ceiii\AppData\Roaming\.minecraft\runtime\java-runtime-delta"
cd /d D:\Reasonix\Reasonix_project\WaterValve
call gradlew.bat testDebugUnitTest --no-daemon
