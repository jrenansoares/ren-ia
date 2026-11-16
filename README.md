## Versão atual

Ren alpha18 (`0.1.0-alpha18-ren`, código 18). Veja o [diagnóstico e a esteira de CI da Alpha 18](docs/BUILD_ALPHA18.md).

# Ren

Aplicativo Android de conversas com provedores de IA, projetos locais, terminal e ferramentas de programação. O código-fonte e o estado conhecido estão descritos em [README_PROJETO_IA.md](README_PROJETO_IA.md), em português.

Os guias Alpha 13–15 e `BUILD_VALIDATION.json` registram validações históricas dessas versões; não comprovam a compilação desta revisão. A homologação da Alpha 18 no aparelho exige validação própria.

## Principais recursos

- Chat com provedores configurados pelo usuário e seleção de modelo por conversa.
- Teste individual de modelos e contingência automática opcional entre modelos aprovados.
- Projetos Android com ambiente Linux compartilhado e menu para instalar e localizar repositórios Git.
- Ferramentas de arquivos e terminal sujeitas às aprovações da conversa.

## Compilar

```bash
cd web-ui
pnpm install --frozen-lockfile
cd ..
./gradlew testDebugUnitTest :app:testGithubDebugUnitTest --stacktrace
./gradlew assembleDebug --no-daemon --stacktrace
```

A compilação usa JDK 21, Gradle 9.6.0, AGP 9.4.0 e SDK Android 37.2 (alvo 37). O front-end incorporado exige Node 22 e pnpm 11. O APK do canal GitHub fica em `app/build/outputs/apk/github/debug/`; o workflow o copia para `app/build/outputs/apk/debug/` e publica o artefato `Ren-IA-Alpha18-debug`. Execute e valide a instalação em aparelho antes de distribuir qualquer APK.

## Licença

Consulte `LICENSE` e os avisos dos componentes. Código incorporado de terceiros permanece sujeito às respectivas licenças; a marca e as alterações próprias não removem esses termos.
