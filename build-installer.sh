#!/bin/bash
# ──────────────────────────────────────────────────────────────────────────────
# build-installer.sh – Gera o instalador RPM do MyTwoCents (Para Fedora/Nobara)
# ──────────────────────────────────────────────────────────────────────────────

set -e

export JAVA_HOME=/usr/lib/jvm/temurin-17-jdk

echo "📦 1. Compilando o MyTwoCents com Maven..."
$JAVA_HOME/bin/java -version
./mvnw clean package -DskipTests

rm -rf release
mkdir -p release
mkdir -p target/jpackage-input

cp target/mytwocents-*.jar target/jpackage-input/mytwocents.jar

echo "🛠️ 2. Criando o JRE Customizado com jlink..."
$JAVA_HOME/bin/jlink --add-modules java.base,java.desktop,java.sql,java.net.http,java.naming,java.instrument,java.management,java.transaction.xa,jdk.unsupported,jdk.crypto.ec,jdk.jsobject,jdk.charsets,jdk.localedata,jdk.zipfs --output target/custom-jre --no-header-files --no-man-pages --strip-debug --compress=2

echo "🛠️ 3. Empacotando Instalador RPM..."
$JAVA_HOME/bin/jpackage \
  --input target/jpackage-input \
  --name "MyTwoCents" \
  --main-jar "mytwocents.jar" \
  --type rpm \
  --dest release \
  --app-version 1.0.0 \
  --description "MyTwoCents - Gerenciamento Financeiro Pessoal" \
  --vendor "Leandro Jose Lesnik" \
  --icon "src/main/resources/static/icon.png" \
  --linux-shortcut \
  --linux-package-name "mytwocents" \
  --linux-app-category "Office" \
  --linux-menu-group Office \
  --java-options "-XX:+UseParallelGC" \
  --runtime-image target/custom-jre --resource-dir src/main/resources/package/linux

echo ""
echo "✅ Construção Completa!"
echo "Na sua pasta 'release' agora há um arquivo MyTwoCents.rpm!"
echo "Você pode dar dois cliques nele pelo explorador de arquivos e instalar de forma nativa no seu Nobara!"
