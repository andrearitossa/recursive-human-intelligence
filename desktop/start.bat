@echo off
setlocal
rem pushd maps UNC / WSL paths to a temporary drive; cd /d cannot do this.
pushd "%~dp0"
if errorlevel 1 (
 echo Could not access the RHI folder: %~dp0
 pause
 exit /b 1
)
rem An older Java often comes first on PATH, so look for a real JDK 17+ by location too.
set "JDK="
call :try "%JAVA_HOME%"
for /d %%D in ("%ProgramFiles%\Eclipse Adoptium\jdk-*" "%ProgramFiles%\Java\jdk-*" "%ProgramFiles%\Microsoft\jdk-*" "%ProgramFiles%\Zulu\zulu-*" "%ProgramFiles%\Amazon Corretto\jdk*") do call :try "%%~fD"
for /f "delims=" %%P in ('where javac 2^>nul') do call :try "%%~dpP.."
if not defined JDK (
 echo RHI requires a JDK 17 or newer. Install one, or set JAVA_HOME to it.
 popd
 pause
 exit /b 1
)
if not exist build mkdir build
echo Compiling RHI...
"%JDK%\bin\javac" -encoding UTF-8 -d build *.java
if errorlevel 1 goto failed
echo Checking imports, persistence, cancellation, and CSV...
"%JDK%\bin\java" -Djava.awt.headless=true -cp build Checks
if errorlevel 1 goto failed
echo Opening RHI...
"%JDK%\bin\java" -cp build Loop
if errorlevel 1 goto failed
popd
exit /b 0
:failed
echo RHI could not start. Copy the error above so it can be diagnosed.
popd
pause
exit /b 1
:try
if defined JDK exit /b 0
if not exist "%~1\bin\javac.exe" exit /b 0
for /f "tokens=2 delims= " %%V in ('"%~1\bin\javac" -version 2^>^&1') do for /f "delims=." %%M in ("%%V") do if %%M GEQ 17 set "JDK=%~1"
exit /b 0
