# Validação — Projeto IA 0.1.0-alpha11

## Resultado

- Compilação GitHub Debug e Spotless aprovados.
- 537 testes JVM do aplicativo e 91 do workspace passaram. Total de 628, sem falhas, erros ou testes ignorados. Os testes foram executados nesta revisão.
- Pacote `br.com.projetoia.app.debug`, versão `0.1.0-alpha11`, código 11, somente ARM64.
- Assinatura APK v2 validada com o certificado original das versões anteriores.
- APK com 59810064 bytes. SHA-256 `0d4c46a2e8ac26df467bbfccceb5bbdb9ced02b9b13f9d7a32a1e4ec3571fa47`.
- Código funcional no commit `f8c416d328f40002174feef7c68edde9e5e97b1c`. Evidências em `AUDIT_EVIDENCE/alpha11`.

## Instalação e atualizações

A preparação compartilhada instala Linux, ferramentas básicas e Claude Code em sequência. Reutiliza o rootfs e o pacote existentes. As receitas são fixas, executadas no diretório de preparação, fora dos projetos. Um mutex global mantém preparação, consulta e atualização serializadas entre as telas. Há cancelamento e progresso informado pelo download e pelo APT.

O Claude Code é instalado como pacote de sistema pelo repositório APT oficial estável. O instalador confere a impressão digital da chave antes de cadastrar o repositório; o APT verifica os pacotes assinados. A chave, a assinatura do InRelease e o hash do índice ARM64 oficial foram conferidos no ambiente de compilação. Isso não equivale a instalar ou executar o pacote ARM64 no Android.

A consulta de atualizações renova os índices e lê uma simulação do APT, sem instalar programas. A aplicação usa upgrade com novas dependências permitidas e preservação de configurações modificadas, sem solicitar remoções ou troca de distribuição. Atualiza o Ubuntu instalado e o Claude Code na mesma instalação compartilhada. Ao terminar, verifica as ferramentas e o executável. A comparação de versões usa dpkg, evitando comparação alfabética incorreta.

Os testes novos cobrem sequência inicial, reutilização, retomada após falha, executável instalado mas inutilizável sem redownload, consulta separada da aplicação, falha de índices, registro incompleto ou contraditório, chave incorreta, candidato desconhecido, comparação de versões, probe de execução com falha e atualização sem novo rootfs. Também verificam que um log longo não transforma instalação com saída zero em falha; registros estruturados truncados continuam sendo rejeitados.

## Limites

Não houve instalação, atualização, teste visual nem execução do Claude Code no Poco F7. Os testes de receitas usam comandos simulados; a sequência APT real permanece pendente de validação no aparelho. O instalador executa claude --version no próprio telefone e só marca a execução verificada quando recebe a versão esperada. Erros preservam o Linux e aparecem na interface e nos detalhes.

Esta entrega gerencia o CLI, disponível no Terminal após instalação bem-sucedida. A autenticação, a ponte de API para os modelos gratuitos cadastrados e o controle das sessões do Claude Code pelo chat continuam pendentes. O chat mantém o agente próprio. Claude Code com modelos de outras empresas não tem suporte oficial da Anthropic. HyperFrames ainda precisa de instalação e renderização no Android. O relato de unsupported file não foi reproduzido e o importador não mudou.

Android Lint não foi reexecutado. O relatório histórico da alpha06 já apresentava falhas gerais, incluindo traduções e uso de recursos em Compose. Foram criadas ou atualizadas 26 chaves em inglês e português brasileiro pelo CLI do projeto. Outros idiomas continuam com pendências; não foi adicionada supressão.

As bibliotecas nativas e 32 arquivos web estáticos foram comparados ao APK alpha10. As quatro receitas novas foram verificadas dentro do APK. O esquema do banco, as aprovações por conversa e as regras de rotas gratuitas não foram alterados.
