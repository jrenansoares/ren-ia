# Validação do Projeto IA — alpha08

APK experimental de 8 de novembro de 2026. O Linux compartilhado pode ser preparado no painel Programação antes da criação de projetos.

## Resultado

- Compilação GitHub Debug e Spotless aprovados.
- 524 testes do aplicativo e 74 do módulo workspace passaram. Total de 598, sem falhas, erros ou testes ignorados.
- Dez testes novos verificam instalação compartilhada e compatibilidade com arquivos e ambientes anteriores.
- Assinatura APK v2 verificada, com o mesmo certificado das versões anteriores.
- Pacote br.com.projetoia.app.debug, versão 0.1.0-alpha08, código 8, somente ARM64.
- SHA-256 do APK `78e0a663adc4b34fa4b3d9604385685e660e4f319ede53ddc08523037becb1cb`. Tamanho 60587251 bytes.
- Código funcional auditado no commit `affbd226ed00aa6dd8b24b33fd7b40ea3eeff566`.

## Linux compartilhado

A instalação comum fica fora das pastas dos projetos. Novos projetos a usam automaticamente. Comandos e terminais compartilham o Linux e os pacotes de sistema, mas usam arquivos, HOME e temporários próprios de cada projeto.

Projetos antigos continuam usando seu Linux próprio. O usuário pode adotar o compartilhado e retornar à instalação anterior. A troca exige fechar as abas do terminal do projeto. A instalação anterior não é movida nem apagada automaticamente. Arquivos dos projetos e banco de dados mantêm seu formato; não há migração do esquema.

A preparação usa o escopo do aplicativo e um único estado de progresso entre conversas. Um mutex impede preparações duplicadas. A instalação compartilhada também evita downloads repetidos, inclusive depois de reiniciar o gerenciador. O arquivo é conferido, extraído e ajustado antes de publicar o Linux. Cancelamento ou falha não publicam uma instalação parcial.

## Testes novos

Os testes usam diretórios temporários, contextos capturados de execução e um servidor HTTP local. Eles verificam

- Dois projetos usando o mesmo Linux e arquivos, HOME e temporários distintos.
- Adoção do Linux compartilhado, persistência da escolha e retorno ao Linux próprio com arquivos preservados.
- Exclusão de projeto sem remover o Linux compartilhado ou outros projetos.
- Os mesmos caminhos e montagens no terminal e no agente.
- Limpeza de temporários preservando o instalador comum.
- Recuperação da instalação anterior após uma publicação interrompida.
- Duas solicitações simultâneas e outra após reiniciar o gerenciador, com apenas um download HTTP.
- Falha de integridade e nova tentativa sem perder arquivos do projeto.
- Preparação em uma pasta própria, fora dos arquivos dos projetos.
- Cancelamento sem publicar Linux parcial e nova tentativa concluída.

## Compilação e limites

Kotlin, Java e recursos Android foram recompilados. As bibliotecas libworkspace.so e libtermux.so foram reaproveitadas da alpha06, com os arquivos C++ inalterados e hashes conferidos. Os binários empacotados e 32 arquivos web estáticos foram comparados byte a byte com a alpha06.

O Android Lint não foi reexecutado. A última execução, na alpha06, falhou com 1.920 erros, 346 avisos e 1 observação. A alpha08 acrescenta 14 chaves e altera duas mensagens em inglês e português brasileiro usando o CLI de tradução do projeto, sem nova supressão. Outros idiomas seguem com pendências.

Não houve instalação limpa, atualização, interação com a interface ou execução real do PRoot no telefone. Os testes automatizados de arquivos não equivalem à homologação no Android. Download real do Ubuntu, APT, APIs e renderização com HyperFrames precisam de teste no aparelho. Claude Code continua sem integração. Sobrevivência em segundo plano, desempenho e bateria não foram medidos.

O backup geral não inclui os arquivos dos projetos nem Linux e remove credenciais. Antes de limpar os dados do app, exporte os arquivos separadamente e guarde as chaves para cadastrar novamente. Instalação limpa não elimina defeitos do código.
