#!/bin/bash
# install-java.sh — Instala Eclipse Temurin JDK 8 y 21 en el devcontainer
set -e

echo "=== Instalando Eclipse Temurin JDK 8 y 21 ==="

# Agregar repositorio de Adoptium
apt-get update -q
apt-get install -y -q wget apt-transport-https gnupg

wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public | \
  gpg --dearmor | tee /etc/apt/trusted.gpg.d/adoptium.gpg > /dev/null

echo "deb https://packages.adoptium.net/artifactory/deb $(awk -F= '/^VERSION_CODENAME/{print$2}' /etc/os-release) main" | \
  tee /etc/apt/sources.list.d/adoptium.list

apt-get update -q
apt-get install -y -q temurin-8-jdk temurin-21-jdk maven

# Crear symlinks con nombres consistentes
ARCH="$(dpkg --print-architecture)"
ln -sfn "/usr/lib/jvm/temurin-8-jdk-${ARCH}"  /usr/lib/jvm/temurin-8
ln -sfn "/usr/lib/jvm/temurin-21-jdk-${ARCH}" /usr/lib/jvm/temurin-21

echo "=== Temurin 8 ==="
/usr/lib/jvm/temurin-8/bin/java -version

echo "=== Temurin 21 ==="
/usr/lib/jvm/temurin-21/bin/java -version

echo "=== Maven ==="
mvn --version

echo "=== Instalación completa ==="
