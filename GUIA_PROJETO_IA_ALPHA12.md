# Projeto IA 0.1.0-alpha12

## Instalar e começar

Instale `projeto_ia_0.1.0_alpha12_arm64.apk` sobre a versão anterior. O pacote e o certificado são os mesmos; não é necessário limpar os dados para atualizar. Confira a versão em Sobre.

1. Abra **Linux** no menu. Se o ambiente ainda não estiver preparado, conclua a preparação. Linux e Claude Code são compartilhados entre os novos projetos locais. Abrir outro projeto não dispara outra instalação.
2. Em **Chaves de IA**, cadastre ou confira a conexão OpenRouter. O teste da chave consulta o catálogo; isso não comprova acesso a todos os modelos. Autorize opções gratuitas com suporte a ferramentas em **Programação**.
3. Crie ou selecione um projeto local e toque em **Usar neste chat**. Depois, abra novamente **Programação** e ligue **Usar Claude Code no chat**. O seletor depende de o teste do executável no Linux comum ter passado. O chip da conversa passa a indicar Claude Code.
4. Escreva a tarefa no chat. O Claude Code executa ferramentas no Linux. A inferência usa os modelos gratuitos autorizados, com o mesmo filtro conservador do modo Programação. A chave do provedor fica no aplicativo; uma credencial temporária conecta o executável à ponte local.
5. Se quiser executar comandos e edições sem confirmar cada ferramenta, ative **Aprovação automática** nessa conversa. O padrão permanece manual. Pare a execução antes de mudar as opções. As permissões nativas que já permitem leitura continuam valendo.

Não é necessário cadastrar uma chave Anthropic para essa ponte. Ela não fornece uma assinatura Claude nem transforma os modelos selecionados em modelos Claude. As cotas das APIs gratuitas continuam valendo. No momento, o modo aceita rotas gratuitas identificadas do OpenRouter com ferramentas; contas de outros provedores com preço desconhecido ficam bloqueadas nesse modo.

## Continuação, saída e erros

O contexto é reconstruído a partir do histórico selecionado no aplicativo a cada turno. Os arquivos permanecem no projeto. Esta implementação não usa `--resume` nem mantém um processo Claude entre mensagens. Regenerar usa o histórico anterior ao ramo escolhido. Evite pedir para repetir comandos cujo resultado ficou desconhecido; solicite primeiro a inspeção do estado atual.

As respostas da API chegam completas a cada etapa. Há atualizações de mensagens e resultados entre etapas, mas ainda não há transmissão de cada token do modelo. Após 15 segundos sem nova saída, o status informa o tempo desde a última atividade observada. Esse contador não é uma porcentagem de execução nem uma afirmação de travamento.

O botão Parar encerra a geração e o processo local. Chamadas sem resultado confirmado ficam marcadas como interrompidas; não são entregues ao agente próprio para execução. Uma nova tentativa deve conferir os arquivos. Falhas da API não expõem a chave na ponte. Não há fallback automático pago. Mantenha o aplicativo aberto durante os primeiros testes no telefone.

## Vídeo

Ative Programação e use **Vídeo para o projeto** ou **Arquivo**. O importador reconhece extensões/MIME de vídeo e agora também alguns cabeçalhos MP4/QuickTime/AVI quando o provedor de arquivos perde essa identificação. Mostra bytes realmente copiados e permite cancelar. O vídeo fica em `/upload`, acessível às ferramentas; ele não é enviado automaticamente como vídeo nativo ao modelo.

Exemplo de primeira tarefa:

> Inspecione o vídeo anexado com ffprobe. Informe duração, resolução e codecs com base na saída real. Preserve o original e salve um trecho de teste de cinco segundos em /workspace. Instale somente as dependências necessárias e relate o resultado dos comandos.

Abra os arquivos do projeto para exportar ou compartilhar o resultado. A mudança do importador não comprova a correção do erro original de unsupported file no telefone; esse caso ainda precisa ser repetido na alpha12. Esta versão não inclui instalação guiada ou renderização homologada de HyperFrames, Remotion, Whisper e yt-dlp. A descoberta automática de plugins, hooks e MCP do Claude está desativada; as ferramentas nativas disponíveis são Read, Write, Edit, Bash, Glob e Grep. Repositórios podem ser utilizados por comandos quando compatíveis.

## Backup antes de limpar dados

Em **Projetos**, abra o menu de um projeto local e escolha **Exportar projeto em ZIP**. Pare as execuções antes de exportar. Guarde o arquivo fora do armazenamento privado do app. **Restaurar projeto de um ZIP** cria outro projeto e preserva os existentes; depois, selecione o projeto restaurado em Programação.

O ZIP inclui a pasta `/workspace`, arquivos ocultos, diretórios vazios, permissões de execução e links relativos que permanecem dentro do projeto. Links externos e arquivos especiais são rejeitados. Arquivos podem conter seus próprios segredos, como `.env`; escolha onde guardar o backup. O ZIP não inclui Linux, HOME, temporários, conversas nem chaves cadastradas no aplicativo. Para conversas e anexos de `/upload`, use também o **Backup** geral com registros e arquivos selecionados. O backup geral remove credenciais; será necessário cadastrar novamente as chaves.

Limpar dados apaga também a instalação Linux local. Nessa situação, será preciso preparar o ambiente uma vez novamente. A regra de reutilização vale entre os projetos enquanto os dados do app forem mantidos.

## Atualizações e limites

Os botões **Verificar atualizações** e **Instalar atualizações** do menu Linux continuam disponíveis para pacotes do Ubuntu instalado e para o Claude Code estável. A consulta não instala pacotes. Atualizar não troca automaticamente a distribuição Ubuntu.

A ponte com modelos de outras empresas é experimental e não tem suporte oficial da Anthropic. Consulte a documentação de [gateways do Claude Code](https://code.claude.com/docs/en/llm-gateway) e do [CLI](https://code.claude.com/docs/en/cli-reference). A chamada contínua de voz permanece para outra versão.

O APK foi compilado e assinado e os testes automatizados passaram. Três testes executaram Claude Code 2.1.311 em Linux amd64 com inferência simulada. Isso não equivale à execução no Android ARM64, ao uso de uma chave real ou à renderização de vídeo no Poco F7. Veja a validação da versão para os limites completos.
