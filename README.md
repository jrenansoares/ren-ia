## Versão atual

Ren alpha15. Veja [GUIA_REN_ALPHA15.md](GUIA_REN_ALPHA15.md) e [BUILD_VALIDATION.json](BUILD_VALIDATION.json).

# Ren

Aplicativo Android de conversas com provedores de IA, projetos locais, terminal e ferramentas de programação. O código-fonte e o estado conhecido estão descritos em [README_PROJETO_IA.md](README_PROJETO_IA.md), em português.

Versão atual: `0.1.0-alpha13-ren`. Compilação e assinatura verificadas; homologação no aparelho pendente. Consulte [GUIA_REN_ALPHA13.md](GUIA_REN_ALPHA13.md).

## Principais recursos

- Chat com provedores configurados pelo usuário e seleção de modelo por conversa.
- Teste individual de modelos e contingência automática opcional entre modelos aprovados.
- Projetos Android com ambiente Linux compartilhado e menu para instalar e localizar repositórios Git.
- Ferramentas de arquivos e terminal sujeitas às aprovações da conversa.

## Compilar

```bash
./gradlew assembleGithubDebug -x :web:buildWebUi
```

A compilação depende da versão de Gradle declarada no wrapper e de um Android SDK compatível. Execute e valide a instalação em aparelho antes de distribuir qualquer APK.

## Licença

Consulte `LICENSE` e os avisos dos componentes. Código incorporado de terceiros permanece sujeito às respectivas licenças; a marca e as alterações próprias não removem esses termos.
