#!/bin/bash
# ──────────────────────────────────────────────────────────────────────────────
# setup-postgres.sh – Instala e configura o PostgreSQL para o FControl
# Compatível com: Fedora, Nobara Linux, RHEL, CentOS Stream
# Execute com: bash setup-postgres.sh
# ──────────────────────────────────────────────────────────────────────────────

set -e

echo "📦 Instalando PostgreSQL via dnf..."
sudo dnf install -y postgresql postgresql-server

echo "🗄️ Inicializando banco de dados (primeira vez)..."
# Só inicializa se ainda não foi feito
if [ ! -f /var/lib/pgsql/data/PG_VERSION ]; then
  sudo postgresql-setup --initdb
fi

echo "🚀 Iniciando e habilitando serviço..."
sudo systemctl start postgresql
sudo systemctl enable postgresql

echo "🔧 Criando banco e usuário para o FControl..."
sudo -u postgres psql <<SQL
-- Cria o usuário (ignora se já existir)
DO \$\$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'fcontrol') THEN
    CREATE USER fcontrol WITH PASSWORD 'fcontrol123';
  END IF;
END
\$\$;

-- Cria o banco (ignora se já existir)
SELECT 'CREATE DATABASE fcontrol OWNER fcontrol'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'fcontrol')\gexec

-- Concede privilégios
GRANT ALL PRIVILEGES ON DATABASE fcontrol TO fcontrol;
SQL

echo ""
echo "✅ PostgreSQL configurado com sucesso!"
echo ""
echo "  Banco:    fcontrol"
echo "  Usuário:  fcontrol"
echo "  Senha:    fcontrol123"
echo "  Host:     localhost:5432"
echo ""
echo "Agora rode a aplicação com:"
echo "  cd /home/zantetsu/Documentos/DEV/fcontrol"
echo "  JAVA_HOME=/usr/lib/jvm/temurin-17-jdk ./mvnw spring-boot:run"
