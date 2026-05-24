#!/usr/bin/env bash
# =====================================================================
# FASAL Setup & Start Script (Mac / Linux)
# =====================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo ""
echo "================================"
echo "        FASAL Setup"
echo "================================"
echo ""

if ! command -v mysql > /dev/null 2>&1; then
    echo "ERROR: MySQL not found. Please install MySQL and try again."
    exit 1
fi
echo "[OK] MySQL found."

if ! command -v java > /dev/null 2>&1; then
    echo "ERROR: Java not found. Please install Java 11 or newer."
    exit 1
fi
echo "[OK] Java found."

if ! command -v mvn > /dev/null 2>&1; then
    echo "ERROR: Maven not found. Please install Maven 3.6 or newer."
    exit 1
fi
echo "[OK] Maven found."

echo ""
read -p "Enter your MySQL username (default: root): " DB_USER
DB_USER="${DB_USER:-root}"

read -s -p "Enter your MySQL password (leave blank if none): " DB_PASS
echo ""
echo ""

export MYSQL_PWD="$DB_PASS"

echo "Creating database fasal_db..."
if ! mysql -u "$DB_USER" -e "CREATE DATABASE IF NOT EXISTS fasal_db CHARACTER SET utf8mb4;" 2>/dev/null; then
    echo "ERROR: Failed to create database. Check your MySQL credentials."
    unset MYSQL_PWD
    exit 1
fi
echo "[OK] Database ready."

echo "Loading schema.sql..."
mysql -u "$DB_USER" fasal_db < database/schema.sql
echo "[OK] Schema applied."

echo "Loading seed.sql..."
mysql -u "$DB_USER" fasal_db < database/seed.sql
echo "[OK] Seed data loaded."

unset MYSQL_PWD

DB_CONN_FILE="backend/src/main/java/com/fasal/db/DatabaseConnection.java"
echo "Updating credentials in DatabaseConnection.java..."

if sed --version > /dev/null 2>&1; then
    sed -i -E "s|private static final String DB_USER = \".*\";|private static final String DB_USER = \"${DB_USER}\";|" "$DB_CONN_FILE"
    sed -i -E "s|private static final String DB_PASSWORD = \".*\";|private static final String DB_PASSWORD = \"${DB_PASS}\";|" "$DB_CONN_FILE"
else
    sed -i '' -E "s|private static final String DB_USER = \".*\";|private static final String DB_USER = \"${DB_USER}\";|" "$DB_CONN_FILE"
    sed -i '' -E "s|private static final String DB_PASSWORD = \".*\";|private static final String DB_PASSWORD = \"${DB_PASS}\";|" "$DB_CONN_FILE"
fi
echo "[OK] Database credentials updated."

echo "Compiling backend..."
cd backend
mvn -q compile
echo "[OK] Backend compiled."

echo ""
echo "================================"
echo "       FASAL is ready"
echo "================================"
echo ""
echo "Backend starting on http://localhost:4567"
echo ""
echo "Open these files in your browser:"
echo "  http://localhost:4567/farmer.html      -> Farmer Portal"
echo "  http://localhost:4567/hub-admin.html   -> Hub Admin Panel"
echo "  http://localhost:4567/super-admin.html -> Super Admin / Demo"
echo ""
echo "Demo login accounts:"
echo "  Super Admin:  phone 9000000000  password admin123"
echo "  Hub Admin:    phone 9000000001  password hub123  (Delhi hub)"
echo "  Farmer:       phone 9100000001  password farmer123"
echo ""
echo "Press Ctrl+C to stop the server."
echo ""

mvn -q exec:java -Dexec.mainClass="com.fasal.Main"
