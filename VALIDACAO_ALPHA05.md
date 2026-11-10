# Validação e auditoria do Projeto IA — alpha05

Data — 7 de novembro de 2026  
Versão — 0.1.0-alpha05 / código 5  
Código verificado — `274bfd6bb57efb30dcf10928c0c642ddab11ec4f`  
Pacote — `br.com.projetoia.app.debug`

## Parecer

O modo Programação foi implementado como agente próprio do Projeto IA, vinculado a um projeto por conversa. A compilação Android, a formatação e os testes JVM passaram. Continua sendo uma alpha experimental, com análise estática reprovada e homologação no aparelho pendente. Claude Code, HyperFrames e chamada de voz contínua gratuita em português permanecem como etapas futuras.

Este relatório atualiza o escopo da auditoria `AUDITORIA_PROJETO_IA_ALPHA04.md`, incluída no código-fonte. Não transforma pendências daquela auditoria em recursos concluídos. As 42 configurações de conexão continuam sendo presets, sem homologação de todas as contas e serviços.

## O que mudou

| Área | Implementação | Evidência e limite |
|---|---|---|
| Entrada | Programação no menu, início e acima do campo de mensagem | Código compilado; visual e toque ainda sem teste Android |
| Projeto | Criar/escolher, preparar e usar na própria conversa | Configuração serializada e teste de persistência de conversa nomeada antes da primeira mensagem |
| Execução | Reutiliza ferramentas de arquivos e shell da base | Escrever, editar e executar exigem aprovação mesmo com overrides antigos permissivos |
| Preparação | Ubuntu Base ARM64 fixado, Git, Python, Node e npm | SHA-256 verificado antes da extração em downloads guiados; instalação real no telefone ainda pendente |
| Diagnóstico | Criar/corrigir arquivo temporário, executar unittest, Git e Node e limpar sua pasta | Receita executada em Linux; teste inclui arquivo `tempfile.py` no projeto para impedir importação acidental |
| Retomada | Vínculo salvo ao ativar; saída apenas desvincula; estado de instalação interrompida recuperado na abertura | Testes de sessão e de transição de estado; morte real do processo ainda precisa ser validada |
| Custo | Inferência de Programação limitada a rotas autorizadas gratuitas do OpenRouter com ferramentas | Testes bloqueiam modelos pagos, outros hosts, caminhos e parâmetros avançados; nenhuma conta real foi chamada |
| Chamadas auxiliares | Título local, sugestões/consolidação automática fora do fluxo e compactação pelo filtro gratuito | TTS automático bloqueado e entrada de chamada contínua oculta em Programação nesta etapa |

O instalador impede duas instalações de Rootfs pelo mesmo repositório em execução. A preparação tenta concluir configurações de pacotes interrompidas antes de instalar as ferramentas. Uma instalação personalizada, uma dependência ausente ou uma falha de rede ainda pode exigir diagnóstico. Isso não constitui um gerenciador universal de pacotes ou um serviço de execução persistente.

## Alcance da proteção de custo

O modo aceita somente o endpoint HTTPS padrão do OpenRouter, com `openrouter/free` ou identificadores terminados em `:free`, suporte a ferramentas e seleção autorizada. Rejeita endpoints, cabeçalhos, corpos personalizados, sobrescritas de provedor e modelos desconhecidos ou pagos. A checagem também ocorre antes do envio pelo roteador. O modo não herda memória, MCP, busca externa ou ferramentas extras do assistente. O limite automático é de 24 chamadas de modelo por execução do agente.

Groq e outros provedores permanecem nas conversas comuns. Eles ficam fora desta primeira versão de Programação porque a gratuidade da conta não é dedutível apenas do catálogo. Autorização indica permissão do usuário e classificação compatível, não autenticação, quota ou qualidade comprovadas.

Essa política é um bloqueio de inferência dentro do fluxo implementado. Não controla cobranças em todos os outros recursos do aplicativo, ações manuais de voz/mídia ou serviços externos chamados por scripts de terceiros. As cotas dos provedores continuam valendo. Instalar o aplicativo não concede acesso ilimitado ou assinatura Claude.

O roteamento ainda usa até seis candidatos, esperas fixas e a janela de contexto declarada nos modelos. Não implementa interpretação completa de Retry-After, orçamento de tokens real, classificação adaptativa de qualidade ou retomada agendada. A interrupção após início de streaming não é repetida automaticamente pelo fallback; isso não garante idempotência de todas as ações externas.

## Testes e verificações

| Módulo | Registrados | Falhas | Erros | Ignorados |
|---|---:|---:|---:|---:|
| app | 520 | 0 | 0 | 0 |
| ai | 284 | 0 | 0 | 0 |
| workspace | 52 | 0 | 0 | 0 |
| common | 7 | 0 | 0 | 0 |
| speech | 43 | 0 | 0 | 0 |
| search | 16 | 0 | 0 | 0 |
| document | 14 | 0 | 0 | 1 |
| highlight | 53 | 0 | 0 | 0 |
| material3 | 1 | 0 | 0 | 0 |
| ui | 27 | 0 | 0 | 0 |
| web | 1 | 0 | 0 | 0 |
| terminal | 144 | 0 | 0 | 0 |
| mediagen | 43 | 0 | 0 | 0 |
| oauth | 9 | 0 | 0 | 0 |
| **Total** | **1214** | **0** | **0** | **1** |

**1213 testes aprovados, um ignorado.** O ignorado é `PdfCorpusTest.extractCorpus`, que exige um corpus externo não fornecido. Os 14 módulos de execução foram verificados. Após os últimos ajustes, os testes dos módulos app e workspace foram repetidos; os demais não tiveram código alterado. Não foram feitos testes instrumentados Android, da variante Play ou de baseline profile.

A validação abrange, entre outros pontos, a seleção sem modelo pago, endpoint padrão, autorização explícita, funcionamento com o roteador global desligado, isolamento da configuração da conversa, persistência antes da primeira mensagem, remoção do vínculo, limpeza do diretório de trabalho ao trocar de projeto, aprovações obrigatórias e recuperação do estado de instalação.

A receita inicial do diagnóstico podia importar um módulo Python com nome de biblioteca padrão presente no projeto. A regressão foi reproduzida por um arquivo `tempfile.py` controlado, e a receita foi corrigida com execução isolada (`-I -S`), ignorando configurações de inicialização. A execução final passa mantendo o arquivo original intacto. O teste também isola a configuração do Git e remove preloads Node na verificação. Isso reduz a superfície do diagnóstico; não torna o Rootfs ou um repositório de terceiros confiável por si só.

- `assembleGithubDebug` — aprovado, incluindo compilação dos componentes nativos de terminal e empacotamento ARM64.
- `spotlessCheck` — aprovado, sem esconder erros de Lint por baseline.
- Assinatura — APK v2 verificado, mesmo certificado das alphas anteriores.
- Manifesto — versão 5, mínimo API 26, alvo 37, pacote próprio e build ainda depurável.
- Android Lint — **reprovado**, com **1901 erros**, **353 avisos** e **1 sugestões**, executado sem rede após preparar as dependências.

### Resultado estático

| Severidade | Categoria | Quantidade |
|---|---|---:|
| Error | `MissingTranslation` | 1852 |
| Warning | `TypographyEllipsis` | 160 |
| Warning | `UnusedResources` | 76 |
| Warning | `TypographyDashes` | 57 |
| Error | `LocalContextGetResourceValueCall` | 49 |
| Warning | `PluralsCandidate` | 35 |
| Warning | `NewerVersionAvailable` | 9 |
| Warning | `IconLocation` | 5 |
| Warning | `Typos` | 4 |
| Warning | `ModifierFactoryExtensionFunction` | 2 |
| Warning | `EmptySuperCall` | 1 |
| Warning | `DiscouragedPrivateApi` | 1 |
| Warning | `InlinedApi` | 1 |
| Warning | `ChromeOsAbiSupport` | 1 |
| Warning | `ObsoleteSdkInt` | 1 |
| Hint | `AutoboxingStateCreation` | 1 |

As ocorrências são achados estáticos, não uma contagem de vulnerabilidades ou falhas de execução confirmadas. Os problemas de tradução e recursos herdados continuam reprovando o gate. As telas novas receberam textos em português brasileiro e inglês; as traduções dos demais idiomas continuam incompletas. A lista com arquivo e linha fica em `AUDIT_EVIDENCE/alpha05/lint-issues.csv`. Nenhuma supressão ou baseline novo foi criado para obter aprovação.

## O que ainda exige Android e conta real

1. Atualizar a alpha anterior preservando chaves e conversas.
2. Preparar um projeto no Poco F7, conferir o diagnóstico local e os downloads com falhas de rede ou pouco espaço.
3. Conversar com OpenRouter gratuito, criar `hello.py`, aprovar escrita/comando e conferir a saída real.
4. Reabrir o app e continuar na mesma conversa, com arquivos e histórico preservados.
5. Cancelar uma execução e conferir seu estado antes da retomada; testar permissões negadas e interrupção pelo Android.
6. Validar filtros de modelos, quotas e troca em conta real, sem acessar rotas pagas.
7. Conferir rolagem, teclado, fontes grandes, acessibilidade e comportamento em segundo plano.

Não foram utilizados segredos do usuário em testes de geração. Não houve instalação desta alpha no telefone, captura de tráfego, teste de Keystore físico, benchmark, renderização de vídeo ou execução de Claude Code. A receita aprovada em Linux não comprova o runtime PRoot/ARM64 no Android.

## Pendências prioritárias

- Política de custo global, inclusive funções legadas, voz e mídia. O avanço desta alpha está limitado a Programação.
- Backup/restauração dos arquivos dos projetos e estratégia para o Rootfs. O backup geral atual continua sem esses diretórios.
- Homologação do runtime, limites de espaço/processos e isolamento. PRoot não é uma VM de segurança forte; o instalador avançado ainda permite downloads sem hash esperado por padrão.
- Roteamento por quota de conta, Retry-After, orçamento de contexto e recuperação confiável de ações de resultado incerto.
- Recuperação de falhas de preferências/cofre, revisão residual de logs, transporte HTTP/CORS e autenticação de serviços herdados, conforme a auditoria anterior.
- Voz contínua em português, com reconhecimento, conversa, síntese, interrupção, Bluetooth e custo confirmado.
- Adaptador opcional Claude Code com autenticação, eventos e aprovações; homologação de HyperFrames e dependências. Compilar qualquer APK inteiramente pelo telefone ainda não foi comprovado.
- Traduções, análise estática aprovada, inventário de dependências/licenças e distribuição release com plano de assinatura e recuperação.

## Rastreabilidade

- `app/.../data/ai/ProgrammingMode.kt` — elegibilidade, escopo da conversa e prompt de trabalho.
- `ChatRequestFactory.kt`, `FreeModelRouting.kt`, `ChatService.kt` e `MemoryConsolidator.kt` — contexto, envio, auxiliares e persistência.
- `ProgrammingModePanel.kt`, `ProgrammingVM.kt`, `ChatPage.kt` e `TTSAutoPlay.kt` — preparação, entrada no modo e controle de voz automática.
- `WorkspaceRepository.kt`, `RootfsInstaller.kt` e `ProgrammingEnvironment.kt` — instalação, integridade, recuperação e diagnóstico.
- `ProgrammingModeTest.kt`, `WorkspaceInstallRecoveryTest.kt`, `ConversationSessionTest.kt` e `ProgrammingEnvironmentTest.kt` — regressões novas.

SHA-256 do APK

```text
e32e822083f902f2efb683abb4d810a46cf3dc308c0a6f5ace89615c86a742cb
```

Certificado SHA-256

```text
8b52983953d2452c8813b7d39e872248c705053c4469e52f884097959b69251f
```

Fontes de referência

- [Ubuntu Base — imagem ARM64 e hashes oficiais](https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/SHA256SUMS).
- [OpenRouter — roteador gratuito](https://openrouter.ai/pt-BR/openrouter/free) e [coleção de modelos gratuitos](https://openrouter.ai/collections/free-models/).
- [Claude Code — integração programática](https://code.claude.com/docs/en/headless) e [limites de suporte a gateways/modelos](https://code.claude.com/docs/en/llm-gateway).
