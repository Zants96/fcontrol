@echo off
setlocal

echo "============================================================"
echo "    FControl - Criador de Instalador Windows (.msi/.exe)"
echo "============================================================"
echo.
echo Requisitos:
echo 1. JAVA_HOME precisa estar apontando para o JDK 17
echo 2. O programa 'WiX Toolset' precisa estar instalado no Windows
echo.

if "%JAVA_HOME%"=="" (
    echo [ERRO] A variavel JAVA_HOME nao esta configurada!
    echo Configure-a apontando para sua instalacao do JDK.
    pause
    exit /b 1
)

echo [1/3] Compilando o FControl com Maven...
call mvnw.cmd clean package -DskipTests

rmdir /s /q release
mkdir release
mkdir target\jpackage-input
copy target\fcontrol-*.jar target\jpackage-input\

echo.
echo [2/3] Criando JRE Customizado com jlink...
"%JAVA_HOME%\bin\jlink" --add-modules java.se,jdk.unsupported,java.management,java.desktop,java.naming,java.sql,java.net.http,java.instrument,java.rmi,java.security.jgss,jdk.crypto.ec --output target\custom-jre --no-header-files --no-man-pages --strip-debug

echo.
echo [3/3] Empacotando Instalador Nativo do Windows...
"%JAVA_HOME%\bin\jpackage" ^
  --input target\jpackage-input ^
  --name "FControl" ^
  --main-jar "fcontrol-0.0.1-SNAPSHOT.jar" ^
  --type exe ^
  --dest release ^
  --app-version 1.0.0 ^
  --description "FControl - Controle Financeiro" ^
  --vendor "Zantetsu" ^
  --icon "src\main\resources\static\icon.ico" ^
  --win-shortcut ^
  --win-menu ^
  --win-dir-chooser ^
  --runtime-image target\custom-jre

echo.
echo [SUCESSO] Construcao finalizada!
echo Va ate a pasta 'release' para encontrar seu instalador .exe
pause
