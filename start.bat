@echo off
REM =====================================================================
REM FASAL Setup ^& Start Script (Windows)
REM =====================================================================

setlocal EnableDelayedExpansion

cd /d "%~dp0"

echo.
echo ================================
echo         FASAL Setup
echo ================================
echo.

where mysql >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: MySQL not found. Please install MySQL and try again.
    pause
    exit /b 1
)
echo [OK] MySQL found.

where java >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Java not found. Please install Java 11 or newer.
    pause
    exit /b 1
)
echo [OK] Java found.

where mvn >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Maven not found. Please install Maven 3.6 or newer.
    pause
    exit /b 1
)
echo [OK] Maven found.

echo.

set "DB_USER=root"
set /p DB_USER="Enter your MySQL username (default: root): "

set "DB_PASS="
set /p DB_PASS="Enter your MySQL password (leave blank if none): "

echo.

set "MYSQL_PWD=%DB_PASS%"

echo Creating database fasal_db...
mysql -u %DB_USER% -e "CREATE DATABASE IF NOT EXISTS fasal_db CHARACTER SET utf8mb4;"
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Failed to create database. Check your MySQL credentials.
    set "MYSQL_PWD="
    pause
    exit /b 1
)
echo [OK] Database ready.

echo Loading schema.sql...
mysql -u %DB_USER% fasal_db < database\schema.sql
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Failed to load schema.
    set "MYSQL_PWD="
    pause
    exit /b 1
)
echo [OK] Schema applied.

echo Loading seed.sql...
mysql -u %DB_USER% fasal_db < database\seed.sql
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Failed to load seed data.
    set "MYSQL_PWD="
    pause
    exit /b 1
)
echo [OK] Seed data loaded.

set "MYSQL_PWD="

set "DB_CONN_FILE=backend\src\main\java\com\fasal\db\DatabaseConnection.java"
echo Updating credentials in DatabaseConnection.java...

powershell -NoProfile -Command "(Get-Content '%DB_CONN_FILE%') -replace 'private static final String DB_USER = \".*\";', 'private static final String DB_USER = \"%DB_USER%\";' | Set-Content '%DB_CONN_FILE%'"
powershell -NoProfile -Command "(Get-Content '%DB_CONN_FILE%') -replace 'private static final String DB_PASSWORD = \".*\";', 'private static final String DB_PASSWORD = \"%DB_PASS%\";' | Set-Content '%DB_CONN_FILE%'"
echo [OK] Database credentials updated.

echo Compiling backend...
cd backend
call mvn -q compile
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Maven compile failed.
    pause
    exit /b 1
)
echo [OK] Backend compiled.

echo.
echo ================================
echo       FASAL is ready
echo ================================
echo.
echo Backend starting on http://localhost:4567
echo.
echo Open these files in your browser:
echo   http://localhost:4567/farmer.html      -^> Farmer Portal
echo   http://localhost:4567/hub-admin.html   -^> Hub Admin Panel
echo   http://localhost:4567/super-admin.html -^> Super Admin / Demo
echo.
echo Demo login accounts:
echo   Super Admin:  phone 9000000000  password admin123
echo   Hub Admin:    phone 9000000001  password hub123  ^(Delhi hub^)
echo   Farmer:       phone 9100000001  password farmer123
echo.
echo Press Ctrl+C to stop the server.
echo.

call mvn -q exec:java -Dexec.mainClass="com.fasal.Main"

endlocal
