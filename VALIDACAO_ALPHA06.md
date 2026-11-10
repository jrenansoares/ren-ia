# Validação do Projeto IA — alpha06

Data — 7 de novembro de 2026  
Versão — 0.1.0-alpha06 / código 6  
Pacote — br.com.projetoia.app.debug  
Código verificado — `e7f0269650bce3b3361503c6e9aac51dff5fb56a`

## Resultado

Compilação Android, formatação e 584 testes JVM aprovados. O módulo workspace passou na análise estática com dois avisos. A análise estática global do aplicativo continua reprovada, com 1920 erros, 346 avisos e 1 sugestão. Não houve instalação nem teste visual desta versão no Poco F7. Continua sendo uma alpha experimental.

Esta entrega corrige o acompanhamento da preparação. Não comprova a causa da falha da alpha05 mostrada nos prints, nem afirma que todo erro de instalação foi resolvido. O alerta antigo juntava falha e tempo excedido, e a captura limitada podia perder as mensagens finais.

## Mudanças verificadas

| Área | Comportamento | Limite |
|---|---|---|
| Download do Linux | Bytes recebidos, tamanho conhecido, porcentagem e velocidade média | Sem tamanho informado, mostra somente a quantidade recebida |
| Conferência e extração | Conferência SHA-256 e contagem real de itens extraídos | A extração não recebe uma porcentagem global inventada |
| Instalação | Saída ao vivo e porcentagem de etapa emitida pelo APT | Porcentagem de download ou de leitura do banco não significa preparação concluída |
| Inatividade | Tempo decorrido e última atividade; aviso após 90 segundos sem avanço informado | Aviso não comprova travamento e não cancela sozinho |
| Resultado | Cancelamento, limite de 30 minutos e código de saída separados | O limite de 30 minutos não é uma estimativa da duração normal |
| Detalhes | Últimas mensagens preservadas em até 12 mil caracteres, com área expansível de altura limitada | Histórico mantido no processo do aplicativo, sem persistência própria |
| Atualização | Mesmo pacote e certificado; código de versão 6 | Preservação de dados e atualização ainda precisam ser conferidas no aparelho |

O progresso usa o relógio monotônico do Android. Registros repetidos de download sem mudança de percentual ou de item não são contados como avanço. Os dois fluxos de saída são processados separadamente para preservar registros divididos entre leituras. A captura para acompanhamento continua recebendo dados mesmo após o limite do resultado comum dos comandos. O encaminhamento é encerrado ao concluir ou interromper o comando.

A receita tenta configurar pacotes pendentes antes de atualizar a lista e instalar ferramentas. A atualização da lista passa a tratar falha de aquisição como erro. Ambientes quebrados, dependências incompletas, rede e espaço insuficiente ainda podem impedir a instalação. Não foram feitas alterações destrutivas nos projetos do usuário.

## Testes

| Módulo | Testes | Falhas | Erros | Ignorados |
|---|---:|---:|---:|---:|
| app | 520 | 0 | 0 | 0 |
| workspace | 64 | 0 | 0 | 0 |
| Total | 584 | 0 | 0 | 0 |

Os testes novos verificam saída antes do fim do processo, mensagens após truncamento, registros APT divididos entre leituras e fluxos, rejeição de porcentagem de leitura do banco como instalação, pulsos repetidos sem avanço, alteração de fase, último erro sem quebra de linha, downloads pequenos ou sem Content-Length e leitura de progresso da conferência SHA-256. Uma receita com gerenciador de pacotes simulado verifica preservação do código de erro e ausência de sinal de conclusão após falha. O teste da receita local de arquivos, Git, Python e Node também passou.

Os testes dos outros 12 módulos não foram repetidos nesta atualização. Os resultados gerais da alpha05 permanecem históricos. Os testes com gerenciador simulado e HTTP local não comprovam o funcionamento dos espelhos de pacotes ou do PRoot no telefone. Não houve chamada real de IA, Claude Code, execução de HyperFrames ou teste de voz.

## Análise estática

- `spotlessCheck` — aprovado.
- `workspace:lintDebug` — aprovado, com dois avisos de permissões de arquivos já existentes.
- `app:lintGithubDebug` — reprovado. São 1871 erros de tradução ausente e 49 de uso de recursos via LocalContext.
- Há 19 novos erros de tradução ausente associados às mensagens desta atualização, fornecidas em português brasileiro e inglês, sem tradução dos demais idiomas. Isso não foi escondido por supressão ou baseline.
- A lista completa está em `AUDIT_EVIDENCE/alpha06/lint-issues.csv`. A contagem de achados estáticos não equivale a uma contagem de falhas de execução ou vulnerabilidades comprovadas.

## Pendências

Validar atualização, apresentação visual, download, retomada, cancelamento e os códigos de erro no Poco F7. Conferir uso em segundo plano e comportamento após morte real do processo. O backup geral ainda não inclui integralmente os projetos e o Linux. As pendências gerais registradas na alpha05 e na auditoria da alpha04 não foram encerradas por esta entrega.

Claude Code continua sendo um requisito pendente, não um recurso entregue. O agente próprio não o substitui. A documentação oficial oferece Linux ARM64 e integração programática, mas isso não valida automaticamente o ambiente PRoot do Android. Instalação, autenticação, sessões e aprovações precisam de implementação e testes próprios.

## Integridade

APK SHA-256 — `e7398412c5232c62f5e579faf20b123ed5220dbfe82913a8611bd3b940768263`  
Certificado SHA-256 — `8b52983953d2452c8813b7d39e872248c705053c4469e52f884097959b69251f`

Referências de implementação do protocolo — [teste oficial de progresso do APT](https://sources.debian.org/src/apt/1.4.10/test/integration/test-apt-progress-fd/) e [formato de progresso](https://sources.debian.org/src/apt/3.3.6/doc/progress-reporting.md). Documentação do Claude Code — [plataformas](https://code.claude.com/docs/en/setup) e [integração programática](https://code.claude.com/docs/en/headless).
