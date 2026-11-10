# Validação — Projeto IA 0.1.0-alpha12

## Resultado

- Compilação GitHub Debug e Spotless aprovados.
- 556 testes JVM do aplicativo e 97 do workspace passaram. Total de 653, sem falhas, erros ou testes ignorados na execução registrada.
- Pacote `br.com.projetoia.app.debug`, versão `0.1.0-alpha12`, código 12, somente ARM64, mínimo Android API 26 e alvo 37.
- Assinatura APK v2 validada com o mesmo certificado das versões anteriores.
- APK com 59911487 bytes. SHA-256 `7db2c7ae5902fb30a8d5e213026c295dadcb17a69f7149dd92699b68ef4227d8`.
- Código funcional no commit `5233e473fc10297e99694639f37a97980b45b6c7`. Evidências em `AUDIT_EVIDENCE/alpha12`.

## Integração nativa

Três testes executaram o Claude Code oficial 2.1.311 em Linux amd64, com o bootstrap e o gateway Ktor usados pelo aplicativo. A inferência foi simulada, sem chaves reais. Foi verificado que um comando Bash aguarda aprovação, executa uma única vez quando permitido, não cria o arquivo quando negado e que o processo termina ao cancelar durante a aprovação. O arquivo DEB foi conferido pelo hash do índice amd64; o índice foi conferido contra o InRelease cuja assinatura já havia sido validada na alpha11. A verificação e os hashes de referência do protocolo SDK ficam nas evidências; o executável não é incluído no APK ou no código-fonte.

Os demais testes novos cobrem conversão Anthropic de texto/imagens/ferramentas/resultados, IDs de chamadas, enquadramento SSE, impedimento de execução de ferramentas pelo gateway, rejeição de modelo arbitrário e chamadas sem correspondência, autenticação do endpoint, contagem identificada como estimativa, erro de provedor sem segredo, isolamento de decisões por conversa, proteção contra clique repetido, interrupção de ferramentas sem alegar sucesso e encerramento nativo que libera uma aprovação pendente. O chat usa o escritor de mensagens da sessão existente. As decisões ao vivo são devolvidas ao stdin, sem criar outra geração ou entregar a chamada ao agente próprio.

Cada pedido de inferência relê autorizações e aplica o filtro gratuito do modo Programação. OCR remoto é excluído desse modo. O aplicativo conserva as chaves do provedor; o CLI recebe apenas uma credencial temporária por turno para a ponte em 127.0.0.1. A inicialização usa --bare, sem descoberta de plugins, hooks ou MCP e com um conjunto explícito de seis ferramentas nativas. O modo permanece optativo por conversa. As permissões nativas de leitura e a aprovação automática explícita continuam distintas.

Não há transmissão de cada token upstream nesta versão: cada etapa recebe uma resposta completa e a reenquadra para o cliente Anthropic. O histórico selecionado no aplicativo é fornecido a um processo novo por turno; não há --resume persistente. O contador do endpoint count_tokens é uma estimativa conservadora explicitamente sinalizada; a contabilização exibida no chat é obtida somente do uso informado pelo provedor. Falhas não ativam rotas pagas. O status informa o tempo desde a última saída observada, sem inventar uma porcentagem.

## Vídeo e backup

O importador recebeu reconhecimento limitado de cabeçalhos MP4/QuickTime/AVI quando faltam extensão e MIME, e tolerância a espaços finais no nome. Testes cobrem formatos de imagem/áudio que não devem ser confundidos, nomes de vídeo, cópia e referência local. A recorrência original de unsupported file não foi reproduzida no aparelho e não deve ser anunciada como corrigida de forma comprovada.

O ZIP de projeto inclui arquivos binários, arquivos ocultos, pastas vazias, permissão de execução e links relativos internos. Seis testes cobrem restauração, links externos, travessia de caminhos, manifesto ausente, destino existente, cadeias de links e cancelamento. A extração é validada em área temporária e a importação cria um projeto novo. Linux, HOME, temporários e conversas ficam fora desse ZIP. Conversas/anexos continuam no backup geral separado. As chaves cadastradas são removidas desse backup geral. A restauração dos arquivos não é uma restauração da instalação Linux.

## Limites

Não houve instalação, execução visual, teste de aprovação ou transferência de vídeo no Poco F7 ou em outro Android físico. Os testes nativos rodam amd64 no ambiente de compilação, não no Android ARM64/PRoot. Não houve chamada com uma chave real de OpenRouter, teste de quotas reais, renderização HyperFrames ou chamada contínua de voz. Homologação da ponte no aparelho permanece necessária. Compatibilidade da rede local no Android 17 também não foi homologada em aparelho. A Anthropic não dá suporte oficial ao uso de modelos de outras empresas no Claude Code.

Android Lint não foi reexecutado; as falhas gerais históricas continuam fora desta validação. As bibliotecas nativas e os 32 arquivos web foram comparados ao APK alpha11. O bootstrap e as quatro receitas de ambiente foram localizados no APK e comparados aos arquivos-fonte. O esquema Room não mudou. Os testes nativos são optativos para quem recompilar: definir PROJETO_CLAUDE_TEST_BIN apontando para um executável oficial previamente verificado; sem isso, os três casos são ignorados. Nesta execução a variável foi definida e os três passaram.
