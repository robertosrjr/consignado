@echo off
setlocal
set "JAVA_HOME=C:\Developer\Programas\jdk\openjdk\openjdk-21.0.2_windows-x64_bin\jdk-21.0.2"
set "JRE_HOME=%JAVA_HOME%"
set "JM_LAUNCH=%JAVA_HOME%\bin\java.exe"
set "PATH=%JAVA_HOME%\bin;%PATH%"
if not exist "%JM_LAUNCH%" (
  echo Java 21 nao encontrado em "%JAVA_HOME%".
  exit /b 1
)
if not exist "%~dp0resultados" mkdir "%~dp0resultados"
cd /d "%~dp0"
call "C:\Developer\Programas\apache-jmeter-5.6.3\bin\jmeter.bat" -t "%~dp0Thread Group.jmx" -j "%~dp0resultados\jmeter-gui.log"
