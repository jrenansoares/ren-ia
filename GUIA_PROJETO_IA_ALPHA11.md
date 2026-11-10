# Projeto IA — 0.1.0-alpha11

## Instalação automática compartilhada

1. Instale o APK e confirme `0.1.0-alpha11` em Sobre.
2. Abra Linux no menu e toque em Baixar Linux e Claude Code.
3. Mantenha o app aberto. A sequência prepara o Linux, instala as ferramentas básicas, testa o ambiente, instala o pacote oficial do Claude Code e executa `claude --version`.
4. Acompanhe download, extração, progresso informado pelo APT, tempo decorrido e última atividade. Os detalhes mostram a saída das etapas. É possível cancelar e tentar novamente.
5. Crie os próximos projetos locais. Eles usam o Linux e o executável do Claude Code da instalação compartilhada. Arquivos, pasta pessoal e temporários permanecem próprios de cada projeto.

Se o Linux já estiver instalado e faltar o Claude Code, o botão Concluir instalação do Claude Code retoma a sequência. O Linux não é baixado novamente. Um pacote do Claude Code já instalado também não é baixado outra vez durante a preparação. Se o teste do executável falhar, a tela mostra a falha, preserva o Linux e permite consultar os detalhes.

O Claude Code é instalado pelo repositório APT oficial no canal estável. A chave do repositório é conferida antes de cadastrá-lo e o APT verifica as assinaturas dos pacotes. A instalação no sistema compartilhado evita que o programa fique restrito à pasta pessoal de um projeto.

## Atualizações

Na tela Linux, toque em Verificar atualizações. A consulta atualiza os índices e informa separadamente a quantidade de pacotes do Ubuntu com atualização e a versão candidata do Claude Code. Os programas não são reinstalados durante essa consulta. O resultado corresponde à última verificação manual.

Quando houver atualizações, aparece Instalar atualizações. Essa ação atualiza os pacotes na instalação compartilhada e verifica os programas ao terminar. Não substitui a imagem Linux nem pede outro download por projeto. Os pacotes atualizados precisam ser baixados uma vez para instalar suas novas versões.

As atualizações do Linux abrangem os pacotes da versão Ubuntu instalada. A rotina não troca automaticamente para outra distribuição ou outra versão principal do Ubuntu. O Claude Code acompanha o canal estável do repositório oficial.

## Uso e limites

Após uma instalação e teste bem-sucedidos no aparelho, o comando `claude` fica disponível no Terminal do projeto. Ainda é necessário configurar o acesso à IA para iniciar uma sessão. A instalação não inclui uma conta, assinatura ou créditos de inferência.

Esta versão implementa instalação e gerenciamento do CLI. O chat do aplicativo continua usando o agente próprio. A ponte de API para conectar o Claude Code aos modelos gratuitos cadastrados e o controle de suas sessões pelo chat continuam pendentes. A Anthropic não oferece suporte oficial ao uso do Claude Code com modelos de outras empresas.

Não houve instalação física nem execução do Claude Code no Poco F7 nesta entrega. O teste de execução ocorre no aparelho durante a preparação e só confirma sucesso quando o executável devolve a versão esperada. A validação completa de sessões, ferramentas e HyperFrames permanece necessária.

O relato anterior de unsupported file continua sem reprodução confirmada. O importador de vídeo não foi alterado nesta revisão.

Resultados da compilação e dos testes ficam em `VALIDACAO_ALPHA11.md`.
