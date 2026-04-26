#!/bin/bash
# ──────────────────────────────────────────────────────────────────────────────
# build-installer.sh – Gera o instalador RPM do FControl (Para Fedora/Nobara)
# ──────────────────────────────────────────────────────────────────────────────

set -e

export JAVA_HOME=/usr/lib/jvm/temurin-17-jdk

echo "📦 1. Compilando o FControl com Maven..."
$JAVA_HOME/bin/java -version
./mvnw clean package -DskipTests

rm -rf release
mkdir -p release
mkdir -p target/jpackage-input

cp target/fcontrol-*.jar target/jpackage-input/

echo "🛠️ 2. Criando o JRE Customizado com jlink..."
$JAVA_HOME/bin/jlink --add-modules java.se,jdk.unsupported,java.management,java.desktop,java.naming,java.sql,java.net.http,java.instrument,java.rmi,java.security.jgss,jdk.crypto.ec,jdk.charsets,jdk.localedata,java.xml,java.management.rmi,jdk.jsobject,jdk.xml.dom,jdk.zipfs,jdk.crypto.cryptoki,jdk.naming.dns --output target/custom-jre --no-header-files --no-man-pages

echo "🛠️ 3. Empacotando Instalador RPM..."
$JAVA_HOME/bin/jpackage \
  --input target/jpackage-input \
  --name "FControl" \
  --main-jar "fcontrol-0.0.1-SNAPSHOT.jar" \
  --type rpm \
  --dest release \
  --app-version 1.0.0 \
  --description "FControl - Gerenciamento Financeiro Pessoal" \
  --vendor "Leandro Jose Lesnik" \
  --icon "src/main/resources/static/icon.png" \
  --linux-shortcut \
  --linux-package-name "fcontrol" \
  --linux-app-category "Office" \
  --linux-menu-group "Office;Finance;" \
  --java-options "-XX:+UseParallelGC" \
  --runtime-image target/custom-jre

echo ""
echo "✅ Construção Completa!"
echo "Na sua pasta 'release' agora há um arquivo FControl.rpm!"
echo "Você pode dar dois cliques nele pelo explorador de arquivos e instalar de forma nativa no seu Nobara!"
