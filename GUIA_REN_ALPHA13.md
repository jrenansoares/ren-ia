# Ren — 0.1.0-alpha13-ren

APK Android ARM64, código de versão 13, pacote `br.com.projetoia.app.debug`.

## Identidade visual

Fundo raiz preto `#000000`, texto principal `#F3F3F3` e elementos ativos verdes `#00FF00`. A tipografia padrão é JetBrains Mono. Componentes e seletores usam formas retangulares, sem elevação ou sombra, e os seletores de IA mostram nomes em texto. As transições de tela e de estado foram tornadas imediatas, e os efeitos decorativos da tela Sobre foram removidos. Ícones de inicialização e de notificação usam `>_`. O prompt Ren é o padrão dos assistentes novos e integra as instruções de Programação; prompts personalizados existentes não são apagados.

## Recursos da revisão anterior incluídos

O fonte inclui o menu Repositórios, seleção manual de modelo no campo de mensagem, verificação individual de modelos e contingência opcional para chat normal e Programação. Essas integrações foram compiladas; não houve teste com chaves reais nem homologação no telefone nesta entrega. Os modelos continuam sujeitos aos testes, autorizações e limites da conta. Uma confirmação de custo desconhecido não comprova gratuidade.

## Validação desta entrega

- `assembleGithubDebug -x :web:buildWebUi` concluído com sucesso.
- Assinatura do APK final verificada, com o mesmo certificado da alpha12.
- Nome Ren, pacote, versão 13, ABI ARM64, fonte e licença OFL conferidos no APK.
- Os 32 arquivos de esquema do Room foram comparados ao fonte anterior ao Ren e preservados. Somente o diretório dos arquivos exportados foi ajustado ao pacote Kotlin atual. A versão do banco continua 32 e seu nome de armazenamento foi mantido.
- Testes unitários e Spotless não foram reexecutados nesta entrega. Os resultados históricos da alpha12 não são resultados desta versão.
- Não houve instalação ou teste visual no Android, uso de APIs reais, teste de quotas, renderização HyperFrames nem medição de desempenho.

O APK mantém o pacote e o certificado anteriores para permitir instalação como atualização. A instalação sobre a versão anterior ainda precisa ser confirmada no aparelho.

## Compilação em outro ambiente

Use JDK 21 com compilador, Android SDK e a versão de Gradle declarada no wrapper. O projeto usa as plataformas 37.0 e 37.2 e CMake 3.22.1; o Gradle pode instalar as ferramentas nativas necessárias após aceitar as licenças do SDK. Configure o SDK por `ANDROID_HOME` ou por um `local.properties` próprio.

```bash
bash gradlew assembleGithubDebug -x :web:buildWebUi
```

No ambiente desta entrega, o Java precisava receber explicitamente os parâmetros do proxy HTTP/HTTPS e usar `-Djava.net.preferIPv4Stack=true`. Os valores do proxy são específicos de cada sessão e não foram gravados no projeto. Também faltavam o SDK e os executáveis do compilador. Repetir o build sem recuperar essas ferramentas não resolveria o problema.

A chave de assinatura permanece separada do fonte. Para distribuir uma atualização, use a chave original e confira o certificado do APK final; a chave automática de debug de outro computador pode ser diferente.

## Licenças

Foram preservados o arquivo LICENSE, os avisos do código incorporado e a licença OFL da JetBrains Mono. A nova identidade visual não remove esses avisos.
