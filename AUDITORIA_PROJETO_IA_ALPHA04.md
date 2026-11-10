# Auditoria do Projeto IA — alpha04

Data — 7 de novembro de 2026  
Versão — 0.1.0-alpha04 / código 4  
Código auditado — `db47f5dd63b5c55206d3093a506250febf8b454b`  
Pacote — `br.com.projetoia.app.debug`  
Escopo — produto, interface, conversas, APIs, roteamento, credenciais, arquivos, execução, voz, distribuição e testes.

## 1. Parecer

O Projeto IA já é um protótipo Android compilável com conversas, ferramentas e uma interface própria. A alpha04 reorganiza o menu conforme a referência do ChatGPT e corrige duas exposições de credenciais/conteúdo que permaneciam em caminhos herdados. A experiência de chaves recebeu uma organização própria, mas a qualidade das integrações ainda varia por serviço.

A prioridade agora é tornar confiável o ciclo **conectar → conversar → executar → salvar → retomar**, com bloqueio efetivo de custo. A quantidade de serviços configuráveis não mede essa confiabilidade. As 42 opções são configurações de conexão, não 42 integrações homologadas em contas reais.

**Classificação de maturidade — alpha experimental.** Ainda não considero atendidos os requisitos de garantia de uso exclusivamente gratuito, chamada de voz contínua em português, backup completo de projetos e controle dedicado de Claude Code/HyperFrames pelo chat. O menu pode ser avaliado instalando esta atualização; não foi feito teste visual ou de toque em aparelho nesta preparação.

## 2. Método e limites

Foram lidos o código atual, os requisitos registrados, a auditoria anterior dos dois aplicativos de referência e as capturas enviadas. Foram examinados os pontos de entrada do chat, importação de modelos, cofre, exportação, logs, servidor web, voz, runtime Linux, aprovação de ferramentas, persistência e manifesto. Compilação, testes JVM, formatação e assinatura do APK foram verificados. O relatório identifica os caminhos concretos que sustentam os achados.

Evidências usadas

- **Código** — fluxo encontrado na implementação. Não equivale a sucesso em um servidor real.
- **JVM** — teste automatizado de lógica/parsing/estado, executado sem conta real.
- **APK** — propriedade verificada no artefato compilado.
- **Usuário** — capturas e relatos desta conversa. Houve resposta à primeira mensagem e rejeição do histórico na segunda em uma alpha inicial, além do erro 401 do TTS. A versão exata de cada captura não foi inferida.
- **Pendente** — exige aparelho, credencial, captura de tráfego, benchmark ou teste de recuperação.

Não foram usados segredos do usuário em chamadas de teste. Não houve instalação da alpha04 no Poco F7, execução de Claude Code/HyperFrames, renderização de vídeo, teste de rede LAN real, teste de Keystore em aparelho, medição de bateria ou verificação exaustiva de CVEs de dependências. Esta é uma auditoria técnica de escopo amplo; não é uma prova de ausência de vulnerabilidades nem uma certificação de segurança.

### Testes e verificações

| Módulo | Testes registrados | Falhas / erros / ignorados |
|---|---:|---|
| Aplicativo | 507 | 0 / 0 / 0 |
| IA | 284 | 0 / 0 / 0 |
| Voz | 43 | 0 / 0 / 0 |
| Workspace/SSH | 49 | 0 / 0 / 0 |
| Núcleo comum | 7 | 0 / 0 / 0 |
| Busca | 16 | 0 / 0 / 0 |
| Documentos | 14 | 0 / 0 / 1 |
| Realce de código | 53 | 0 / 0 / 0 |
| Cores/tema | 1 | 0 / 0 / 0 |
| UI/Markdown | 27 | 0 / 0 / 0 |
| Web | 1 | 0 / 0 / 0 |
| Terminal | 144 | 0 / 0 / 0 |
| Mídia | 43 | 0 / 0 / 0 |
| OAuth | 9 | 0 / 0 / 0 |
| **Total** | **1.198** | **0 / 0 / 1** |

**1.197 testes aprovados; um ignorado.** O ignorado é `PdfCorpusTest.extractCorpus`, que exige um corpus local e um diretório de saída. Esse ensaio de extração sobre corpus não foi validado nesta execução. Foram verificados os 14 módulos de execução do APK; testes instrumentados, baseline profile e a variante Play não foram executados.

- `assembleGithubDebug` — aprovado.
- `spotlessCheck` — aprovado.
- Assinatura APK v2 — aprovada; certificado igual ao das alphas anteriores.
- Manifesto — pacote próprio, versão 4, Android mínimo API 26, alvo 37, somente ARM64, backup automático desativado, build ainda depurável.
- Android Lint — **reprovado**, com 1.864 erros, 357 avisos e uma sugestão. Oito ocorrências foram corrigidas em relação à primeira análise; não foi criado baseline para ocultar o restante.

A primeira execução ampliada encontrou cinco expectativas de teste da base incompatíveis com as decisões já implementadas no fork: valores iniciais, campo `free_router`, provedor padrão e exportação de cabeçalhos. Os testes foram atualizados para verificar os novos padrões e preservar as verificações de formato, leitura/escrita e remoção de credenciais. Não foram restaurados padrões inseguros para obter aprovação. Um teste adicional confere o conteúdo real decodificado do QR.

Os 56 testes selecionados anteriormente não representavam a suíte inteira. A ampliação melhora a evidência de regressão, mas a contagem não substitui teste de uso, medição de cobertura ou avaliação da qualidade das respostas de IA.

### Leitura do resultado estático

Os erros restantes são 1.815 ocorrências de `MissingTranslation` e 49 de `LocalContextGetResourceValueCall`. São categorias de completude de idioma e atualização de recursos nas telas Compose. Não equivalem a 1.864 vulnerabilidades ou falhas de execução confirmadas, mas reprovam o gate estático e precisam de correção antes de produção. A compilação e os testes JVM aprovados não tornam esse gate aprovado. A lista completa, com arquivo e linha, está em `AUDIT_EVIDENCE/lint-issues.csv` no código-fonte.

## 3. Histórico consolidado das entregas

| Versão | Entrega principal | Limite material |
|---|---|---|
| Auditoria de origem e licença | Código incorporado e interfaces locais | Análise estática; componentes mantêm suas licenças próprias |
| Alpha01 | Pacote próprio, tema, início, rotas de projeto, conexões, roteamento opt-in, ajustes de privacidade, ditado Android | Primeira resposta real confirmada; falha de histórico apareceu em seguida |
| Alpha02 | Compatibilidade de histórico OpenRouter/Groq e atualizador inativo sem aviso falso | Correção testada na serialização; sem confirmação de ponta a ponta no aparelho nesta preparação |
| Alpha03 | Filtro de modelos, nova tela de chaves, 42 configurações de serviços, busca, chave oculta, cabeçalho automático | Importar catálogo não prova acesso ou gratuidade; chamada contínua permaneceu pendente |
| Alpha04 | Menu lateral inspirado no print, conversas com ícones, perfil no rodapé, logs de provedores protegidos, QR sem credenciais estruturadas e auditoria ampliada | Continuidade dos requisitos e pendências descritos abaixo |

As versões mantêm pacote e assinatura. Instalar por cima preserva os dados do aplicativo no fluxo normal de atualização. Desinstalar remove o armazenamento privado e pode inutilizar segredos cifrados com a chave do Keystore do aparelho. Não recomendar desinstalação como procedimento rotineiro de atualização.

## 4. Matriz dos requisitos

| Requisito | Estado na alpha04 | O que falta para aceitar |
|---|---|---|
| Uso pelo Android sem computador obrigatório | Parcial | Validar instalação do runtime, retomada e tarefas reais pelo app |
| Menu semelhante ao print do ChatGPT | Implementado no código | Conferir toque, rolagem, fontes grandes, paisagem e TalkBack em aparelho |
| Simplicidade dos fluxos de chaves | Parcial | Telas avançadas e fluxos antigos ainda precisam de unificação e tradução |
| Cadastro intuitivo de chave | Parcial | Carregamento está guiado; falta teste real e estado de acesso/quota com validade |
| Somente opções viáveis | Parcial | Filtro conservador implementado; capacidades, acesso e preço ainda exigem homologação |
| Troca automática entre IAs | Parcial | Há failover limitado antes de emitir chunks; faltam cotas por conta, Retry-After e distribuição de tentativas |
| Escolher a melhor IA para a tarefa | Inicial | Pontuação por palavras/capacidades; não há avaliação medida de qualidade |
| Garantia de uso totalmente gratuito | **Não atendida como garantia técnica global** | Política de custo com bloqueio de preço desconhecido, incluindo funções auxiliares e mídia |
| Conversas, arquivos e ferramentas | Herdado e parcialmente testado | Fluxos completos com anexos, permissões negadas e interrupção real |
| Linux e terminal | Herdado | Integridade de downloads, limites e homologação no aparelho |
| Chamada de voz em português | **Integração pendente** | Fluxo contínuo gratuito, chave reutilizada, microfone/áudio/retomada e teste real |
| Claude Code/Codex CLI pelo chat | **Não homologado** | Instalação ARM64, autenticação, ponte de eventos, aprovações, cancelamento e retomada |
| HyperFrames e renderização | **Não homologado** | Repositório/versão exatos, dependências e renderização de teste no ambiente escolhido |
| Backup completo dos projetos | **Não atendido pelo backup atual** | Incluir arquivos de projeto e validar restauração; não confundir registro no banco com arquivos |
| Servidor Linux gratuito externo | **Não integrado** | Backend de execução, limites, autenticação e capacidade verificáveis |
| Distribuição pública de produção | **Pendente** | Resolver bloqueadores, build release, cadeia de assinatura, testes Android e inventário de dependências |

## 5. Menu solicitado

A organização implementada usa a marca Projeto IA, sem se apresentar como aplicativo oficial do ChatGPT. A referência foi a hierarquia da captura enviada, não uma cópia do serviço ou de suas funções.

- Topo com título e busca de conversas.
- Atalhos em lista para Início, Mídia, Projetos, Salvos, Chaves de IA e Roteamento.
- Conversas com ícone, título de uma linha, seleção e agrupamentos de fixados/datas.
- Novo chat e avatar/perfil no rodapé.
- Perfil reúne assistente, configurações, estatísticas, tradução, nova pasta e backup.
- Cabeçalho acompanha a rolagem em janelas baixas. A lista mantém paginação e as operações de mover, fixar e excluir da base.

Os rótulos Sites, Codex e Biblioteca não foram acrescentados como atalhos fictícios para recursos sem implementação equivalente. Salvos abre os favoritos existentes; Projetos abre o workspace real.

**Atenções de UX ainda abertas:** a lista do drawer continua vinculada ao assistente e à pasta da base. É necessário deixar esse escopo mais claro para evitar a impressão de conversa perdida. O seletor manual de modelo no campo de mensagem também precisa explicar sua relação com o modo automático. O novo visual não foi fotografado em execução nesta auditoria.

## 6. Controles existentes e correções confirmadas

| Controle | Evidência | Avaliação |
|---|---|---|
| Credenciais em campos sensíveis cifradas | `SettingsFields.kt`, `SecretCipher.kt` | AES/GCM com chave Android Keystore no caminho de produção; falta teste instrumentado de recuperação |
| Backup automático Android desligado | Manifesto e XMLs de extração | Confirmado na configuração do APK |
| Exportação comum de configurações sem campos de credencial | `BackupSanitizer.kt`, `BackupManager.kt` | Testes de sanitização aprovados; não equivale a cifrar conversas/anexos |
| Diagnóstico HTTP sem corpo e com cabeçalhos sensíveis mascarados | `RequestLoggingInterceptor.kt` | Implementado; revisão residual de URLs e mensagens de exceção ainda recomendada |
| Logs de adaptadores de IA e voz | `ProviderLog.kt` e 22 arquivos atualizados | **Corrigido nesta auditoria.** Debug/info não emitem conteúdo; avisos/erros são constantes e não incluem mensagens, tags recebidas ou exceções |
| `println` com payloads dos provedores | Adaptadores Chat Completions, Responses, Google e Claude | **Removido nesta auditoria** nos caminhos examinados |
| QR/texto de compartilhamento de conexão | `ShareSheet.kt`, `ShareSheetTest.kt` | **Corrigido nesta auditoria.** API keys, credenciais estruturadas e cabeçalhos personalizados não acompanham o template |
| Servidor web desligado/local por padrão | `Settings.kt`, `WebServerManager.kt` | LAN exige autenticação e senha não vazia; não houve ensaio de acesso pela rede |
| Aprovação de escrita, edição e shell | `WorkspaceTools.kt` | Padrão ativado; leitura permanece sem aprovação e há overrides por workspace |
| Limites de ferramentas | `WorkspaceTools.kt`, `WorkspaceManager.kt` | Leitura de arquivo até 8 MiB; shell padrão 30 s, máximo solicitado 600 s |
| Falha parcial de stream e cancelamento | `FreeModelRoutingTest.kt` | Testes confirmam que o wrapper não troca depois de emitir um chunk e propaga cancelamento |
| Assinatura e dados da instalação | APK / certificado | Mesmo certificado das versões anteriores; não houve instalação real da alpha04 nesta preparação |
| Firebase do módulo principal | Remoções da alpha01; inspeção estática | Não tratar declarações `apply false` do build raiz como telemetria executando; falta captura de tráfego para uma afirmação de ponta a ponta |
| Métricas de uso | `UsageRecorder.kt`, `UsageRecordEntity.kt` | Registro local de IDs e tokens, sem texto de prompt nesse modelo de dados; faltam métricas completas de falhas/cotas/latência |

O filtro de modelos elimina os casos apresentados nos prints: Orpheus, Whisper, Prompt Guard e tarefas similares não entram no chat automático. A identificação por metadados e registro de nomes conhecidos é uma proteção de compatibilidade, não um teste funcional universal.

## 7. Achados abertos e prioridades

P0 significa bloqueador do objetivo declarado ou da liberação pública. P1 exige correção antes de depender do recurso para trabalho importante. P2 melhora consistência, manutenção e experiência. A prioridade não implica que tenha ocorrido cobrança, vazamento ou perda de dados.

| ID | Prioridade | Achado | Próxima ação / critério de aceite |
|---|---|---|---|
| A01 | P0 | O modo gratuito não verifica preço/plano dos provedores diretos | Política fail-closed: rota só é elegível se gratuidade e escopo estiverem identificados; preço desconhecido deve bloquear no modo estrito |
| A02 | P1 | Cotas e cooldowns são tratados por modelo com esperas fixas | Aplicar limites por chave/conta/provedor e respeitar Retry-After; simular limite compartilhado |
| A03 | P1 | As seis primeiras tentativas podem ser todas do mesmo provedor | Distribuir tentativas entre provedores elegíveis; testar seis modelos de um serviço bloqueado e outro serviço disponível |
| A04 | P1 | Contexto é comparado com limites declarados, sem orçamento real por pedido | Importar limites confiáveis, estimar tokens incluindo ferramentas/imagens e compactar com política de custo |
| A05 | P1 | Importar /models não valida credencial, acesso e quota em todos os serviços | Separar chave cadastrada, catálogo carregado, acesso testado, quota e preço; registrar data e expiração do resultado |
| A06 | P1 | Backups não incluem as pastas locais dos workspaces | Criar exportação de projeto com manifesto e teste de restauração em instalação limpa |
| A07 | P1 | Falha de decifração de configurações pode impedir a inicialização | Recuperação por campo/credencial, preservando conversas; testar chave invalidada, restauração e dados corrompidos |
| A08 | P1 | Downloads de rootfs não têm digest/assinatura verificados no caminho lido | Manifesto de runtime versionado, hash esperado e rejeição de arquivo alterado antes da extração |
| A09 | P1 | PRoot não é isolamento forte para código de repositório ou agente | Definir fronteira de confiança, restringir montagens/segredos e validar saída de escopo e permissões |
| A10 | P1 | HTTP é permitido globalmente, inclusive em ajustes avançados | HTTPS para endpoints externos; exceções explícitas e limitadas para ambiente local |
| A11 | P1 | Servidor web tem CORS amplo e permite modo sem JWT no localhost | Exigir autenticação mesmo local ou restringir origem; rever transporte e senha; teste de navegador e rede real |
| A12 | P1 | A proteção recente de logs não cobre todo caminho de sincronização/diagnóstico | Auditar URLs, corpos de erro, exportações e logs dos demais módulos com segredos fictícios |
| A13 | P1 | Não repetir stream não garante idempotência de ações externas | Registrar intenção/resultado de ferramentas e impedir repetição automática quando o resultado for incerto |
| A14 | P1 | Chamada de voz não tem integração gratuita em português concluída | MVP completo com ASR, chat e TTS, reutilização de conexões, controles e teste em aparelho |
| A15 | P1 | Chaves de chat, TTS e ASR ainda são configurações independentes | Cofre com referências compartilhadas por serviço; interfaces antigas também precisam ocultar chaves |
| A16 | P1 | Claude Code, HyperFrames e criação de APK pelo próprio app não estão homologados | Provas pequenas e reproduzíveis no Android antes de anunciá-los como disponíveis |
| A17 | P1 | Build distribuído é depurável e usa a assinatura de desenvolvimento existente | Preparar política de release e migração de assinatura antes de distribuição ampla, preservando caminho de recuperação |
| A18 | P2 | Português e fluxo simples não abrangem todas as telas herdadas | Revisar mensagens de erro, voz, projetos e estados vazios; teste de usuário sem instrução técnica |
| A19 | P2 | Estatísticas não explicam a estratégia nem a causa da troca | Eventos locais sem conteúdo: provedor/modelo, causa, duração, quota e resultado; mostrar por que houve troca |
| A20 | P2 | Dependências incluem pré-releases/SNAPSHOT e binários herdados | Inventário/SBOM, travas de versão, verificação de artefatos e revisão de vulnerabilidades/licenças |
| A21 | P1 | O gate completo de Android Lint continua reprovado | Corrigir traduções e as 49 ocorrências restantes de acesso a recursos; não ocultar o resultado com baseline genérico |

### A01 — Garantia financeira ainda incompleta

`isFreeRouteAllowed` exige identificador gratuito quando o host é OpenRouter. Para outros hosts, a função não consulta preço, plano ou saldo. Um usuário autorizar um modelo não transforma a rota em gratuita. As chamadas auxiliares e as integrações de mídia/voz também não recebem todas a mesma proteção do wrapper principal.

Não foi observada cobrança. O achado é a ausência da trava que sustente a promessa de custo zero. A documentação atual do OpenRouter descreve suas variantes gratuitas e o roteador `openrouter/free`; isso não constitui comprovação de gratuidade para todos os outros provedores da lista.

**Aceite:** testar que nenhuma rota de preço desconhecido/pago seja chamada no modo estrito, mesmo após falha, mudança de plano ou indisponibilidade. Toda função automática precisa aplicar a mesma política. Com nenhuma rota elegível, salvar o estado e informar o motivo, sem migrar para uma opção paga.

### A02–A04 — Continuidade e seleção de modelos

O roteador implementa compatibilidade, autorização, heurística de tarefa, cooldown e até seis candidatos. Ainda usa códigos extraídos do texto da exceção e esperas fixas: um minuto em 429, quinze minutos em 401/402/403 e quinze segundos em outras falhas reconhecidas. Não interpreta o prazo enviado pelo serviço nem mantém um orçamento completo de cotas compartilhadas.

A ordenação atual pode concentrar as seis tentativas no mesmo provedor. Também compara `candidate.contextLength` com o limite do modelo inicial, em vez do tamanho real da requisição. Essas escolhas podem impedir uma alternativa que funcionaria ou insistir em uma conta esgotada.

**Aceite:** testes com quota por conta, quota por modelo, Retry-After em segundos/data, timeout, erro de credencial, recusa de conteúdo, catálogo desatualizado, pedido maior que o contexto e retomada após todos os candidatos falharem. Medir qualidade por tarefa em um conjunto pequeno de avaliações, em vez de anunciar uma seleção da melhor IA sem medição.

### A06 — Backup de conversa não é backup de projeto

O backup atual inclui configurações sanitizadas, snapshot do banco e diretórios de anexos específicos: upload, skills, fontes e mídia. As pastas locais de arquivos/rootfs dos workspaces não entram nessa lista. O código de integridade já prevê um registro restaurado cujo diretório de projeto falta, marcando-o como quebrado.

**Impacto:** recuperar um banco pode restaurar o nome do projeto e o vínculo do assistente, sem recuperar o código ou os arquivos produzidos. Isso precisa estar explícito antes de um usuário confiar todo o trabalho ao app.

**Aceite:** exportar um projeto com arquivos, caminhos, manifesto de ferramentas/versões e opções de exclusão de caches; restaurar em instalação limpa e conferir hashes de arquivos. Proteger arquivos confidenciais e tratar symlinks, caminhos maliciosos, arquivo excessivo e interrupção. Rootfs completo pode exigir tratamento separado pelo tamanho.

### A07–A12 — Limites restantes de privacidade e execução

- `SecretCipher` protege campos com Keystore, mas `SettingsStore` encerra o processo em falha de carga. É preciso recuperação controlada sem apagar tudo ou entrar em ciclo de fechamento.
- A permissão global de HTTP e as URLs avançadas precisam de uma política explícita; as configurações simples usam HTTPS.
- O servidor começa desligado com JWT/local por padrão. Contudo, o motor web aceita qualquer origem, e o gerenciador só exige autenticação obrigatória fora de localhost. Desativar JWT em modo local amplia a superfície de acesso de páginas web, dependendo das políticas do navegador. Não houve exploração dessa condição.
- O servidor LAN usa transporte HTTP no caminho lido. Senha e token precisam de análise de proteção de transporte e de resistência a tentativas; senha não vazia, sozinha, não mede força.
- `FileProvider` declara áreas amplas, embora não seja exportado e dependa de concessão de URI. Restringir diretórios e testar cada compartilhamento; isso não prova acesso automático de outro app ao cofre.
- O backup e o QR removem campos estruturados conhecidos. Texto livre, arquivos .env, URLs com segredos, scripts e resultados de ferramenta ainda exigem tratamento próprio. Não prometer prevenção universal de vazamentos com um filtro de nomes de campos.
- Os logs dos adaptadores de IA/voz foram corrigidos. Outros caminhos, como sincronização e mensagens de exceção, permanecem fora de uma garantia global de sanitização.
- PRoot executa processos no contexto do aplicativo e não deve ser apresentado como uma VM de segurança. As montagens, permissões e alcance de processos precisam de teste específico antes de executar repositórios não confiáveis.

## 8. Catálogo de conexões e homologação

A lista contém 42 configurações de serviços. Ela reduz digitação de URLs e facilita o cadastro, mas os serviços podem exigir verificação de conta, plano, autorização, parâmetros adicionais ou API diferente. A interface atual não tem uma prova de acesso gratuito a cada modelo.

A lista não reproduz integralmente os prints. Cloudflare Workers AI, Z.ai, Sail Research, ACLIDE, GitHub Models e OpenCode Zen, por exemplo, não estão nessa lista simples. Isso não é uma conclusão sobre a situação comercial atual desses serviços; é o estado da implementação local. Antes de incluí-los, verificar protocolo, credencial, catálogo e política gratuita por conta.

**Recomendação prática:** homologar primeiro o chat da Groq e o roteador gratuito do OpenRouter, com chaves do próprio usuário no aparelho, incluindo segunda/terceira mensagem, anexos, ferramentas, cancelamento e limite. Expandir o catálogo homologado por etapas, com resultado e data visíveis.

Ficha mínima por integração

| Campo | Critério |
|---|---|
| Identidade | Nome, host, protocolo e versão do adaptador |
| Credencial | Onde criar, formato, escopos e como revogar |
| Verificação | Endpoint que realmente verifica acesso; distinguir catálogo público |
| Recursos | Conversa, visão, ferramentas, ASR, TTS e mídia testados separadamente |
| Idioma | Português comprovado para entrada e saída de voz |
| Gratuidade | Plano gratuito, promoção, crédito de teste ou pago; fonte e validade |
| Quota | Pool por modelo/conta, limites, cabeçalhos, reset e Retry-After |
| Privacidade | Domínio que recebe o conteúdo e política de retenção aplicável |
| Estado | Cadastrado, catálogo carregado, acesso validado, limitado, expirado ou não homologado |

Nenhuma quota numérica externa foi copiada como garantia. A consulta a um adaptador público também não substitui teste da implementação do Projeto IA.

## 9. Chamada de voz — plano de implementação

A base já tem `VoiceSessionController`, filas e estados de ouvindo/processando/falando. O fluxo integrado, porém, exige um provedor ASR configurado com detecção de turnos compatível. O ditado Android adicionado anteriormente não foi conectado como solução completa desse modo. O ciclo atual para ao sair da tela; não é uma chamada de voz homologada em segundo plano.

### MVP obrigatório

1. Iniciar pelo chat e solicitar microfone naquele momento, com estados e falhas em português.
2. Reconhecer fala em português e definir um turno confiável. Preferir recursos gratuitos do sistema quando disponíveis; a preferência offline do Android não comprova execução local em todos os aparelhos.
3. Enviar o texto pelo mesmo contexto e política de custo do chat. Preservar anexos, projeto e aprovações.
4. Gerar e reproduzir resposta em português, com interrupção e controle de áudio. Validar que a resposta do alto-falante não realimenta o microfone.
5. Reutilizar a conexão cadastrada nos recursos compatíveis, sem exigir a mesma chave em telas diferentes. ASR, modelo de conversa e TTS continuam papéis distintos.
6. Encerrar, silenciar microfone, interromper fala e retomar de modo previsível. Não guardar áudio bruto por padrão.

### Aceite no aparelho

Testar voz feminina/português conforme preferência, silêncio, ruído do carro, fala longa, Bluetooth, chamada telefônica concorrente, permissão negada/revogada, troca de rede, quota, tela apagada e retorno ao app. Medir latência da fala até a resposta, falhas, consumo e temperatura. Para manter microfone em serviço de primeiro plano nas versões recentes do Android, seguir os tipos/permissões e restrições de ciclo de vida oficiais.

Não anunciar uma experiência equivalente à chamada natural do ChatGPT/Gemini apenas por combinar um botão de ditado com leitura automática. O MVP pode começar por turnos claros e evoluir após medição.

## 10. Projetos, agentes e processamento

| Procedimento | Responsabilidade na arquitetura atual |
|---|---|
| Decidir e gerar texto/código | Modelo do provedor configurado |
| Ler/gravar arquivos e executar comandos locais | Ferramentas e runtime do aplicativo no Android |
| Executar em máquina remota | Depende de SSH/backend próprio configurado; não há servidor gratuito pronto integrado |
| Renderizar vídeo por FFmpeg/Node/Chromium | Depende do executor e das bibliotecas instaladas, não apenas da API de chat |
| Gerar mídia por uma API específica | Depende de adaptador, acesso e condições daquele serviço |
| Compilar e instalar um programa/APK | Etapas próprias que ainda precisam de prova no fluxo do app |

O APK deste projeto foi compilado em um ambiente Linux de desenvolvimento. Isso não demonstra que o app já gere e compile qualquer APK dentro do próprio telefone.

O agente nativo da base já pode receber ferramentas de arquivos e shell. Homologar esse fluxo para programação é uma entrega útil antes de depender de uma ponte de CLI. Conversar com um modelo, usar um backend de conta e controlar um agente CLI são integrações distintas.

Para Claude Code/Codex CLI: fixar versão, validar ARM64 e autenticação, ler/escrever arquivo de teste, refletir aprovações no chat, cancelar com segurança e retomar uma sessão após interrupção. A existência de um Ubuntu configurado em outro ambiente não prova a instalação ou a sessão no Projeto IA, que mantém diretório e runtime próprios.

Para HyperFrames: identificar o repositório/commit e as dependências exatas antes da instalação guiada. Validar Node, FFmpeg e, se necessário, navegador/renderizador no runtime Android. Começar por um vídeo curto de referência e medir resultado, RAM, disco, temperatura e bateria. Não há resultado de renderização para declarar homologação nesta auditoria.

## 11. Ordem recomendada de trabalho

| Etapa | Entrega | Condição de conclusão |
|---|---|---|
| 1 | Política estrita de custo e revisão residual de segredos | Nenhuma chamada paga/desconhecida no modo gratuito; exportações/logs testados com segredos fictícios |
| 2 | Duas integrações de referência e cotas | Chat, anexos, ferramentas e limites reais funcionando; estado de acesso com data |
| 3 | Roteamento robusto e observável | Retry-After, pools por conta, diversidade de provedores, contexto e retomada com testes |
| 4 | Voz em português no chat | Ciclo completo em aparelho com controles, áudio e conexões reutilizadas |
| 5 | Projetos seguros e recuperáveis | Download verificado, exportação/restauração, interrupção e teste de limites |
| 6 | Ponte de agentes e edição de vídeo | Provas pequenas de Claude Code/CLI e HyperFrames, sem anunciar integração antes dos resultados |
| 7 | Acabamento e distribuição | Português completo, acessibilidade, testes de atualização, build release, dependências e documentação |

Cada etapa precisa de critérios observáveis. Não estimar prazo ou sucesso a partir da potência do aparelho ou do número de modelos cadastrados.

## 12. Roteiro de validação pelo Android

- Instalar alpha04 por cima da anterior; conferir chaves e conversas preservadas.
- Abrir menu, buscar, entrar em projeto/salvos, fixar, mover e excluir conversa de teste, criar novo chat e abrir perfil. Conferir rolagem e fontes grandes.
- Conectar um serviço com chave válida, chave errada e catálogo vazio. Distinguir cadastro de acesso real.
- Fazer três perguntas na mesma conversa, anexar uma imagem e um PDF pequeno, pedir uma ferramenta simples e cancelar uma geração.
- Simular quota e falha de rede antes e depois do primeiro chunk, verificando modelo escolhido e ações preservadas.
- Negar permissões e voltar a conceder sem perder dados ou entrar em ciclo de fechamento.
- Exportar/restaurar um projeto de teste quando o recurso estiver implementado; conferir arquivos, não só o nome no banco.
- Medir voz, execução e renderização com arquivos de referência; registrar versão, modelo, aparelho e resultado.

Este roteiro é um critério de aceite. Não representa testes de aparelho já aprovados.

## 13. Evidências técnicas e rastreabilidade

| Área | Arquivo / ponto principal |
|---|---|
| Menu | `app/.../ui/pages/chat/ChatDrawer.kt` — `ChatDrawerContent`, `DrawerActions` |
| Conversas do menu | `app/.../ui/pages/chat/ConversationList.kt`, `ChatDrawerVM.kt` |
| Política de logs | `common/.../android/ProviderLog.kt`, `ProviderLogPrivacyTest.kt` |
| Compartilhamento | `app/.../ui/components/ui/ShareSheet.kt`, `ShareSheetTest.kt` |
| Chaves e catálogo | `app/.../data/ai/SimpleConnections.kt`, `ui/pages/projeto/ConnectionsPage.kt` |
| Elegibilidade | `ai/.../provider/ChatModelSupport.kt` |
| Roteamento | `app/.../data/ai/FreeModelRouting.kt`, `ChatRequestFactory.kt` |
| Histórico de raciocínio | `ai/.../providers/openai/ChatCompletionsAPI.kt`, `ChatCompletionsHistoryCompatibilityTest.kt` |
| Persistência/cifra | `SettingsFields.kt`, `PreferencesStore.kt`, `utils/SecretCipher.kt` |
| Backup | `data/sync/BackupManager.kt`, `data/security/BackupSanitizer.kt` |
| Servidor | `web/WebServerManager.kt`, `WebApiModule.kt`, módulo `web/.../Entry.kt` |
| Voz | `ui/pages/chat/VoiceMode.kt`, `VoiceSessionController.kt`, `data/speech/AndroidAsrController.kt` |
| Execução | `workspace/.../ProotShellRunner.kt`, `RootfsInstaller.kt`, `app/.../tools/WorkspaceTools.kt` |
| Estatísticas | `data/ai/UsageRecorder.kt`, `data/db/entity/UsageRecordEntity.kt` |
| Build/distribuição | `app/build.gradle.kts`, `gradle/libs.versions.toml`, manifesto e XMLs |

O arquivo `BUILD_VALIDATION.json` do código-fonte contém os resultados e hashes da entrega. O código correspondente inclui os avisos e licenças da base; o inventário completo de dependências e sua revisão continuam necessários antes de produção.

SHA-256 do APK alpha04

```text
9f7159081e2b0af4cf23176d4dab443aea22fbc454e2905aef77591aa849db07
```

Certificado SHA-256 mantido nas alphas

```text
8b52983953d2452c8813b7d39e872248c705053c4469e52f884097959b69251f
```

### Fontes externas consultadas

- [Auditoria de referência anterior](https://chatgpt.com/library?file_id=libfile_228d58cce8b0819198c354da3007fc8a&account_id=personal) — comparação dos APKs originais; não substitui esta análise do fork.
- [Android — divulgação por logs](https://developer.android.com/privacy-and-security/risks/log-info-disclosure) — necessidade de evitar conteúdo sensível e mensagens variáveis nos registros.
- [Groq — limites e pools](https://console.groq.com/docs/rate-limits) — limites por organização e informações de resposta para tratar limites.
- [OpenRouter — modelos gratuitos](https://openrouter.ai/collections/free-models/) e [roteador gratuito](https://openrouter.ai/pt-BR/openrouter/free) — escopo das variantes e do roteador, sem comprovar as outras contas do catálogo.
- [Android — tipos de serviço de primeiro plano](https://developer.android.com/develop/background-work/services/fgs/service-types#microphone) — requisitos para evolução da chamada de voz.
- Licenças e avisos dos componentes incorporados — consultar `LICENSE` e os avisos incluídos em cada módulo.
