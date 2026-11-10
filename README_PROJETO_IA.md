# Ren — 0.1.0-alpha13-ren

Aplicativo Android em Kotlin e Jetpack Compose para conversas com provedores de IA, projetos locais, terminal e ferramentas de programação. O APK alpha13 foi compilado e sua assinatura foi verificada. A validação no aparelho permanece pendente. Consulte GUIA_REN_ALPHA13.md e BUILD_VALIDATION.json para os resultados desta entrega.

## Recursos incluídos na compilação

- Menu **Repositórios** acessível pela área de projetos e pelo menu lateral.
- Lista repositórios Git encontrados nos diretórios de todos os projetos locais, inclusive clones criados durante conversas de projeto. A identificação usa o marcador `.git`; arquivos-fonte extraídos de ZIP e projetos SSH não entram no scanner atual.
- Instalação de repositórios por URL pública HTTPS dentro de um projeto local selecionado. O Linux compartilhado precisa estar preparado e Git disponível. A pasta de destino não pode existir.
- Seletor de modelo junto ao chat, com escolha persistida na conversa atual.
- Roteamento automático opcional para chat normal e Programação. Os modelos precisam ser testados e selecionados pelo usuário. A ordem preferida de contingência é Google AI Studio, DeepSeek, NVIDIA, Groq e os demais provedores.
- O teste em lote alcança apenas rotas gratuitas confirmadas do OpenRouter. Outros provedores exigem teste individual e confirmação explícita de possível cobrança. Essa confirmação é vinculada à configuração testada e fica inválida quando chave, endpoint ou modelo mudam.
- A contingência só ocorre antes de a resposta ou uma chamada de ferramenta começar a ser emitida; conteúdo parcial não é enviado novamente para outro provedor.

## Repositório e privacidade

O menu clona URLs HTTPS públicas. Não coloque credenciais ou tokens na URL. A lista informa nome, projeto, caminho, branch e origem sem expor credenciais eventualmente encontradas no endereço remoto.

Chaves de API e cotas são gerenciadas diretamente nas contas dos provedores. O aplicativo não consegue inferir se a chave tem plano gratuito nem impedir cobranças externas. Confira os limites da conta antes de autorizar um modelo de custo desconhecido.

As ferramentas de projeto operam sobre os arquivos e o ambiente Linux do projeto. A aprovação de alterações e comandos segue a configuração da conversa.

## Compilação

Use JDK e Android SDK compatíveis com os arquivos Gradle do projeto. O wrapper pode precisar baixar a versão de Gradle declarada em `gradle/wrapper/gradle-wrapper.properties`.

```bash
./gradlew assembleGithubDebug -x :web:buildWebUi
```

Não use esse comando como evidência de que o APK está pronto para distribuição. É necessário concluir a compilação e validar instalação, navegação, chamadas de modelo, roteamento e ferramentas em um aparelho Android.

## Licenças

Consulte `LICENSE` e os avisos de cada módulo. Partes incorporadas e dependências continuam sujeitas às respectivas licenças, inclusive AGPL-3.0 quando aplicável. A marca Ren e alterações próprias não transferem direitos sobre código de terceiros nem eliminam obrigações de atribuição.
