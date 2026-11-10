# Validação do Projeto IA — alpha07

APK experimental recompilado em 8 de novembro de 2026. A aprovação automática das ferramentas de projeto é uma escolha explícita por conversa.

## Resultado

- Compilação GitHub Debug concluída.
- Spotless concluído sem falhas.
- 524 testes do aplicativo e 64 do módulo workspace passaram. Total de 588, sem falhas, erros ou testes ignorados.
- Assinatura APK v2 verificada e mesmo certificado das versões anteriores.
- Pacote br.com.projetoia.app.debug, versão 0.1.0-alpha07, código 7, somente ARM64.
- SHA-256 do APK `74319a67feb865b2e994dbe341c822f58bf2317e5f08a5748f6b3b96acd9e1a5`. Tamanho 59725176 bytes.
- Código funcional auditado no commit `80dada255a4b8777b5f072b3985843d13559edb2`.

## Escopo verificado

A escolha é serializada na conversa, respeitada pelo pedido enviado ao modelo e pelas decisões de aprovação das ferramentas. O padrão permanece manual. Sair do modo Programação ou ramificar a conversa limpa a opção. A alteração fica bloqueada durante geração ou aprovação pendente. Outros tipos de ferramenta e a política de rotas gratuitas não mudaram.

Os testes cobrem configuração antiga, persistência, isolamento entre conversas, ativação e desativação, ramificação e decisão de aprovação das ferramentas. A validação é por testes JVM e compilação; não houve interação com um telefone.

## Compilação recuperada

O ambiente anterior perdeu os arquivos temporários antes da entrega. O código foi recuperado da alpha06 e do registro de alterações salvo, e foi compilado novamente. JDK, Gradle e SDK foram obtidos de fontes oficiais com conferência dos checksums publicados.

As duas bibliotecas nativas libworkspace.so e libtermux.so foram reaproveitadas da alpha06. O manifesto confere os arquivos C++ inalterados e os binários. Os binários empacotados e 32 arquivos web estáticos foram comparados byte a byte com a alpha06. Kotlin, Java e recursos Android foram recompilados.

## Limitações

O Android Lint não foi reexecutado. A última execução da alpha06 falhou com 1.920 erros, 346 avisos e 1 observação. Os erros incluíam 1.871 MissingTranslation e 49 LocalContextGetResourceValueCall. A alpha07 adiciona cinco mensagens em inglês e português brasileiro, sem nova supressão.

A instalação, atualização e interface no Poco F7 não foram testadas. HyperFrames e Claude Code não foram instalados ou homologados no Android. É preciso produzir e inspecionar um MP4 curto no telefone antes de contar com esse fluxo para vídeos maiores. Cotas, sobrevivência em segundo plano, desempenho e bateria não foram medidos.

Os limites existentes de 24 chamadas de modelo por execução e 600 segundos por comando permanecem.
