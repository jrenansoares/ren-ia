@echo off
setlocal
cd /d "%~dp0"
where git >nul 2>&1
if errorlevel 1 (
 echo Git nao encontrado. Instale Git para Windows: https://git-scm.com/downloads/win
 pause
 exit /b 1
)
if not exist .git (git init -b main || exit /b 1)
git config user.name >nul 2>&1
if errorlevel 1 git config user.name "jrenansoares"
git config user.email >nul 2>&1
if errorlevel 1 git config user.email "160426547+jrenansoares@users.noreply.github.com"
git add .
git diff --cached --quiet
if errorlevel 1 (git commit -m "Importar codigo-fonte Ren IA Alpha 15" || exit /b 1)
git remote get-url origin >nul 2>&1
if errorlevel 1 git remote add origin https://github.com/jrenansoares/ren-ia.git
if errorlevel 1 exit /b 1
git branch -M main
git push -u origin main
if errorlevel 1 (
 echo Falha no envio. Verifique login GitHub e permissao de acesso.
 pause
 exit /b 1
)
echo Projeto enviado com sucesso!
pause
