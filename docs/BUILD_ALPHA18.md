# Build e diagnóstico da Alpha 18

## Origem do código

Em 16/11/2026, a cópia local estava limpa em `main`, no commit `30457b5`
(`Importar codigo-fonte Ren IA Alpha 15`). Não havia alterações locais da
Alpha 18. Após buscar todas as branches remotas, o código mais recente estava
em `origin/feature/alpha17-claude`, commit `d2ee258` — apesar do nome da branch,
`app/build.gradle.kts` já declarava versão `0.1.0-alpha18-ren`, código 18.
A branch `feature/alpha18` foi criada a partir desse commit, preservando o
histórico e as alterações de produto existentes.

O diff herdado em relação a `main` inclui integração Claude/OpenRouter e
Anthropic, orquestração de agentes do aplicativo, seleção e verificação de
modelos, continuidade de sessão, terminal Linux compartilhado, ajustes de
interface, traduções e testes. Essas mudanças pertencem à base importada;
esta revisão altera a esteira de build e sua documentação, sem redesenhar a
interface ou substituir a arquitetura.

## Ferramentas declaradas

| Componente | Versão/configuração | Fonte |
| --- | --- | --- |
| Gradle | 9.6.0 | `gradle/wrapper/gradle-wrapper.properties` |
| Android Gradle Plugin | 9.4.0 | `gradle/libs.versions.toml` |
| Kotlin / Compose compiler | 2.4.21 | `gradle/libs.versions.toml` |
| SDK de compilação do app | 37, minor API 2 (37.2) | `app/build.gradle.kts` |
| SDK alvo / mínimo do app | 37 / 26 | `app/build.gradle.kts` |
| JDK de execução no CI | Temurin 21, preservando o major usado no CI anterior | workflow |
| Bytecode Java e Kotlin | 17 | app e convenções de `build-logic` |
| Node / pnpm | 22 / 11 | workflow; build de `web-ui` chamado por `:web:preBuild` |
| CMake | 3.22.1 | módulos `workspace` e `terminal` |

JDK de execução e alvo de bytecode são configurações distintas. Não foi
declarado um `jvmToolchain(21)` no produto. O SDK e os componentes nativos
necessários são resolvidos pelo AGP no SDK preparado pelo setup-android.

## Falhas anteriores comprovadas pelos logs

- [Alpha 16, execução 42905817580](https://github.com/jrenansoares/ren-ia/actions/runs/42905817580):
  referência inexistente `VIDEO` e parâmetros incompatíveis no construtor do
  gateway Claude, durante `compileGithubDebugKotlin`.
- [Alpha 17, execução 42964170460](https://github.com/jrenansoares/ren-ia/actions/runs/42964170460):
  imports ausentes de `ProviderSetting`/`Model`, import duplicado de
  `SettingsStore` e argumento `task` ausente em `automaticRoutes`.
- [Alpha 17, execução 42965313652](https://github.com/jrenansoares/ren-ia/actions/runs/42965313652):
  erros de compilação nos testes `ModelProbeTest` e `WorkspaceToolsTest`.
- [Alpha 17, execução 42968041783](https://github.com/jrenansoares/ren-ia/actions/runs/42968041783):
  599 testes do app executados, uma falha de asserção no teste de streaming
  `OpenRouterClaudeGatewayTest` e três testes ignorados.

Essas falhas foram corrigidas na base `d2ee258`. Sua
[execução 42970030565](https://github.com/jrenansoares/ren-ia/actions/runs/42970030565)
concluiu testes, montagem e upload com sucesso. Isso comprova aquela revisão,
não a nova configuração de CI. Os logs examinados não comprovam OOM como causa
dessas falhas; os limites de memória abaixo são preventivos.

## Workflow único de build

`.github/workflows/alpha18-build.yml` substitui os workflows Alpha 16, Alpha 17
e diário de release. O workflow de issues permanece independente. Não há
publicação automática de releases nem dependência de segredos de release.

- Gatilhos: push em `main` e `feature/alpha*`, PR para `main` e disparo manual.
- Checkout v4, Temurin 21, setup-android v3 e setup-gradle v4.
- Permissão de repositório somente para leitura; cache Gradle somente para
  leitura em PRs; cancelamento de execuções anteriores da mesma referência.
- Wrapper executável e `local.properties` criado somente se ausente.
- `GRADLE_OPTS` conforme solicitado; argumentos explícitos na CLI garantem
  heap de 4 GiB, metaspace de 1 GiB e dump de OOM no processo de build,
  sobrepondo o heap de 8 GiB do `gradle.properties`. Máximo de dois workers,
  sem paralelismo entre projetos e sem daemon persistente.
- Checagem POSIX do bootstrap preservada em `scripts/validate-bootstrap.py`.
- Cache/chave debug opcional preservados. A continuidade da assinatura com um
  APK já instalado depende do mesmo certificado, não apenas do versionCode.
- Dependências de `web-ui` instaladas com lockfile congelado, para manter a
  interface web no APK em vez de excluir `:web:buildWebUi`.
- `testDebugUnitTest` testa módulos sem flavors. O app tem flavors `github` e
  `play`, por isso `:app:testGithubDebugUnitTest` é solicitado explicitamente.
- `assembleDebug` monta os sabores debug sem remover os canais existentes.
- O APK GitHub é verificado e copiado de `apk/github/debug/` para `apk/debug/`.
  A ausência de APK falha no shell e no upload (`if-no-files-found: error`).
- Artefato: `Ren-IA-Alpha18-debug`; relatórios JVM são enviados mesmo em falha.

## Validação nesta estação

- JDK local: OpenJDK 21.0.13.
- Não há Android SDK configurado, `sdkmanager` no PATH ou `local.properties`;
  `assembleDebug` não foi validado localmente.
- A tentativa de `./gradlew --version` não conseguiu baixar a distribuição
  por conexão recusada no Java deste ambiente; isso não é erro de compilação
  do aplicativo.
- Checagem POSIX do bootstrap: `POSIX_BOOTSTRAP_OK`.
- YAML do workflow analisado, actionlint 1.7.11 e `git diff --check` sem erros;
  todos os blocos shell passam em `bash -n`.
- Verificação de artefatos exercitada com arquivos temporários: copia um
  arquivo presente, rejeita ausência de APK e rejeita arquivo vazio. Essa
  checagem valida o script de staging, não o conteúdo de um APK compilado.

O resultado da nova compilação deve ser consultado na execução do workflow
associada ao SHA publicado em `feature/alpha18`. Instalação, provedores reais e
comportamento em aparelho não foram homologados nesta tarefa.
