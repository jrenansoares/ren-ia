# Projeto IA — alpha08

## Linux compartilhado

O Linux agora pode ser preparado uma vez e usado por todos os projetos locais. A instalação compartilhada não pertence a um projeto e não é excluída quando um projeto é apagado.

1. Instale a alpha08 sobre a versão atual, ou faça uma instalação limpa depois de salvar os dados necessários.
2. Abra Programação.
3. No início do painel, abra a seção Linux compartilhado e toque em Baixar Linux para todos os projetos. Não é necessário criar um projeto antes.
4. Aguarde a preparação do Linux e das ferramentas básicas, com os contadores de download, conferência, extração e instalação. Mantenha o aplicativo aberto.
5. Quando aparecer Ambiente compartilhado pronto, crie ou escolha um projeto.
6. Novos projetos usam essa instalação automaticamente. O botão Testar ambiente verifica o projeto sem baixar Linux novamente. Toque em Usar neste chat para ativá-lo.

O Linux, Git, Python e Node básicos são compartilhados. Os arquivos de trabalho, a pasta pessoal e os temporários usados pelos comandos e terminais são separados por projeto. Dependências instaladas dentro da pasta do projeto continuam pertencendo a ele. Instalar ou alterar pacotes do sistema afeta os projetos que usam o Linux compartilhado.

O preparo tem um estado único, visível nas conversas que abrirem o painel. Pedidos simultâneos não iniciam outro preparo. É possível cancelar pelo painel. Um Linux já concluído é reaproveitado ao tentar novamente preparar ferramentas que falharam. Um download interrompido antes de concluir a instalação pode precisar ser repetido.

## Projetos anteriores

Os projetos que já tinham uma instalação própria mantêm essa instalação e seus arquivos.

Depois de preparar o Linux compartilhado, selecione o projeto antigo e toque em Usar Linux compartilhado. A instalação anterior permanece salva. O botão Voltar ao Linux anterior permite retornar quando ela ainda está disponível.

Feche as abas do terminal do projeto antes de trocar de ambiente. A troca é bloqueada enquanto há terminais abertos, geração ou aprovação pendente na conversa atual. Reabra o terminal depois da troca.

A alpha08 baixa uma instalação compartilhada nova. Ela não move nem copia automaticamente o Linux antigo para o ambiente compartilhado. Portanto, um projeto antigo e o ambiente novo podem ocupar espaço ao mesmo tempo. Os próximos projetos não precisam de outra instalação do Linux.

## Instalação limpa e backup

Limpar os dados do Android ou desinstalar o aplicativo apaga conversas, configurações, chaves de API, projetos e todas as instalações do Linux. Uma instalação limpa permite testar o fluxo inicial, mas não elimina defeitos do código.

Antes de limpar os dados

- Exporte o backup das conversas e configurações que quiser preservar.
- Copie ou exporte os arquivos importantes dos projetos separadamente. O backup atual do app não inclui esses arquivos nem o Linux, seja ele próprio ou compartilhado.
- Guarde suas chaves de API para cadastrá-las novamente. O backup portátil remove credenciais.

Após uma instalação limpa, prepare o Linux compartilhado uma vez. Depois crie os projetos e importe seus arquivos. Restaurar o backup geral não restaura as instalações Linux nem os arquivos dos projetos.

## Aprovação automática e vídeo

A opção Aprovação automática nesta conversa da alpha07 permanece em Programação. Ela controla as ferramentas de projeto na conversa escolhida. As cotas e a seleção de rotas gratuitas continuam valendo.

Para vídeo, importe o arquivo em Programação → Arquivos. HyperFrames e Claude Code não vêm instalados e não foram homologados no telefone. Antes de uma edição longa, gere e confira um MP4 curto. O compartilhamento do Linux não comprova a compatibilidade dessas ferramentas no Android.

## Compilar

Requer JDK 21, Gradle 9.6.0, plataformas Android 37.0 e 37.2 e Build Tools 36.0.0. A verificação do APK desta entrega usa os utilitários dos Build Tools 37.0.0. O SDK deve estar configurado no ambiente local.

O caminho opcional `-I tools/build/reuse-alpha06-native.gradle` reaproveita as duas bibliotecas nativas da alpha06, com conferência dos hashes dos binários e dos arquivos C++ inalterados. A compilação padrão continua usando CMake/NDK.

Os 32 arquivos web estáticos da alpha06 permanecem no código-fonte. É possível reaproveitá-los com `-x :web:buildWebUi`. Para os comandos de formatação em uma importação sem histórico, inicialize o Git, faça um commit de importação, marque a referência usada e passe `-PformatBase=<referência>`.
