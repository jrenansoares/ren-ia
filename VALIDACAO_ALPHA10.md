# Validação — Projeto IA 0.1.0-alpha10

## Resultado

- Compilação GitHub Debug e Spotless aprovados.
- 537 testes JVM do aplicativo e 75 do workspace passaram. Total de 612, sem falhas, erros ou testes ignorados. O workspace foi executado nesta revisão, inclusive o teste novo; os resultados não são uma alegação de teste físico no Android.
- Pacote `br.com.projetoia.app.debug`, versão `0.1.0-alpha10`, código 10, somente ARM64.
- Assinatura APK v2 verificada com o certificado original das versões anteriores.
- SHA-256 do APK `c20b8dfe9b1dd6fac3e7a383bc5de8b3aaa42c6a8b4e1feaa8ddd797f1c6888e`. Tamanho 59787760 bytes.
- Código funcional no commit `bcabd96b7a3e39e2f8099a8ebc1b1de08345dcac`. Logs e XMLs dos testes em `AUDIT_EVIDENCE/alpha10`.

## Alteração entregue

A tela Linux foi adicionada ao menu lateral, à página inicial e à lista de Projetos. Ela funciona sem criar uma conversa ou um projeto, usando o repositório único que já mantém a preparação compartilhada. O painel Programação e a tela Linux usam o mesmo componente de status, progresso real e cancelamento.

Os próximos projetos locais usam a instalação compartilhada por padrão. Seus detalhes abrem Gerenciar Linux compartilhado em vez de outro instalador por projeto. As informações do projeto são atualizadas ao concluir uma preparação ou verificação global. Não foi adicionada migração de ambientes anteriores.

O teste novo verifica projetos criados antes e depois de o Linux ficar disponível, persistência ao reabrir o gerenciador, a mesma ferramenta de sistema nos dois projetos e ausência de instalação privada. A suíte existente também cobre download único em pedidos concorrentes, nova tentativa, cancelamento, terminais, diretórios separados e exclusão de um projeto sem apagar o Linux comum.

O endereço oficial do Ubuntu respondeu HTTP 200 e informou 29.936.675 bytes. O SHA-256 configurado foi comparado ao manifesto oficial. Esta conferência não executou o Linux do telefone nem baixou e instalou o rootfs completo neste ambiente.

## Limites e pendências

Não houve instalação, atualização, interação com a interface ou teste de download no Poco F7. Compilação e testes JVM aprovados não substituem essa validação. O menu ainda precisa ser observado no aparelho com a escala de fonte utilizada pelo usuário.

O novo relato de unsupported file continua sem causa isolada. O caminho de importação de MP4 da alpha09 foi preservado. A versão instalada, a opção utilizada e uma captura atual são necessárias para localizar essa ocorrência. Esta entrega não declara a recorrência corrigida.

Claude Code continua sem integração e HyperFrames/FFmpeg ainda precisam de validação de instalação e renderização no Android. A aprovação automática por conversa e as restrições de rotas gratuitas não foram alteradas.

Android Lint não foi reexecutado. O relatório histórico da alpha06 continha falhas gerais, inclusive traduções incompletas. Foram adicionadas cinco chaves em inglês e português brasileiro com o CLI do projeto, sem nova supressão. Os demais idiomas continuam com pendências.

As bibliotecas nativas não mudaram. Seus hashes e os fontes C++ foram conferidos pelo script de compilação; os bytes empacotados são iguais aos do APK alpha09. Os 32 arquivos web estáticos tiveram os hashes conferidos e foram reutilizados. Não houve alteração de esquema do banco.
