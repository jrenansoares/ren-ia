# Validação do Projeto IA — alpha09

APK experimental de 8 de novembro de 2026. Corrige a rejeição de MP4 no seletor Arquivo e disponibiliza vídeo local às ferramentas do modo Programação.

## Resultado

- Compilação GitHub Debug e Spotless aprovados.
- 537 testes do aplicativo passaram. Os 74 testes do workspace, cujo código não mudou, foram reconhecidos como atualizados pelo Gradle e tiveram seus resultados anteriores preservados. Total de 611, sem falhas, erros ou testes ignorados.
- Treze testes novos cobrem reconhecimento, cópia e conversão de vídeo em referência para ferramentas.
- Assinatura APK v2 verificada, com o certificado original das versões anteriores.
- Pacote br.com.projetoia.app.debug, versão 0.1.0-alpha09, código 9, somente ARM64.
- SHA-256 do APK `5b0742f29dd7063906a0ab91645955ca3dc1358fbbbba6fc000c53a2de89fad4`. Tamanho 60313605 bytes.
- Código funcional auditado no commit `332276ef090b6b845e9f6ff3a9baccf9ae051c91`.

## Causa e correção

A mensagem do print é emitida pelo seletor Arquivo antes da cópia, quando o arquivo não pertence à lista de documentos permitidos. MP4 não estava nessa lista. A opção nativa Vídeo da base só aparecia para provedor Google, o que não atendia ao uso de ferramentas locais no modo Programação.

A alpha09 reconhece vídeo por MIME ou extensão e oferece Vídeo para o projeto nesse modo. O histórico mantém um anexo de vídeo. Antes da chamada de programação ou de compactação, esse anexo é convertido em texto com um caminho local acessível às ferramentas. O conversor não lê nem codifica os bytes do vídeo no pedido de chat. O modelo pode usar FFprobe/FFmpeg no Linux e salvar resultados no projeto.

O caminho só é aceito para um arquivo local não vazio, legível, dentro da pasta de anexos. Caminhos ausentes, remotos, externos e escapes por symlink são rejeitados com orientação para anexar novamente. Documentos que representam vídeo e saídas de ferramenta com vídeo também são tratados. Textos, imagens, documentos comuns e o vídeo nativo fora de Programação mantêm seu comportamento.

## Importação

Os seletores Arquivo e Vídeo usam cópia em segundo plano. O progresso informa os MiB efetivamente escritos e o arquivo atual. O envio fica bloqueado durante a importação. A cópia passa por um arquivo temporário no cache, fora da pasta exposta às ferramentas, e só é publicada ao terminar. Cancelamento e falha de leitura limpam esse temporário. O arquivo original no telefone é aberto apenas para leitura.

## Testes novos

Os testes JVM verificam MP4 com MIME genérico ou incorreto, MIME de vídeo sem extensão, distinção de TypeScript, cópia maior que o limite de documento de texto, contadores de bytes, falha e nova tentativa, cancelamento, fechamento de streams, preservação de arquivo existente e temporários fora da área de anexos.

Também verificam referência local sem bytes do vídeo no texto do pedido, preservação do anexo original, documentos de vídeo, vídeo dentro de resultado de ferramenta, preservação de outros tipos de parte, rejeição de referências inválidas e escapes por symlink e manutenção do vídeo nativo fora de Programação.

## Limites

Não houve envio de um MP4 real pelo seletor do Poco F7, interação com a interface, chamada real à API nem renderização. Os testes de streams e mensagens não substituem a validação do ContentResolver Android. Reconhecer MP4 não comprova a integridade do arquivo, o suporte ao codec ou o funcionamento do HyperFrames. FFmpeg, navegador e HyperFrames ainda precisam de teste no Linux do telefone. Claude Code continua sem integração.

O Android Lint não foi reexecutado. A última execução, na alpha06, falhou com 1.920 erros, 346 avisos e 1 observação. Foram acrescentadas seis chaves e traduzidas duas mensagens existentes para português, usando o CLI do projeto, sem nova supressão. Outros idiomas continuam com pendências.

As duas bibliotecas nativas e 32 arquivos web estáticos foram preservados e comparados byte a byte com a alpha06. Não houve alteração no C++, no esquema do banco nem no Linux compartilhado. Os arquivos históricos de validação permanecem no código-fonte.
