# Instruções de desenvolvimento do Projeto IA

## Visão geral

Aplicativo Android nativo feito com Kotlin, Jetpack Compose e Material 3. O código do produto fica principalmente em `app`; módulos compartilhados incluem `ai`, `common`, `document`, `highlight`, `material3`, `search`, `speech`, `ui`, `web`, `workspace` e `terminal`.

## Estrutura e caminhos

- Lógica de conversas e roteamento: `app/src/main/java/br/com/projetoia/app/data/ai/`.
- Preferências, provedores e configurações: `app/src/main/java/br/com/projetoia/app/data/datastore/`.
- Telas Compose: `app/src/main/java/br/com/projetoia/app/ui/pages/`.
- Componentes reutilizáveis: `app/src/main/java/br/com/projetoia/app/ui/components/`.
- Runtime de projetos e terminal: módulos `workspace` e `terminal`.
- Recursos localizados: `app/src/main/res/values*/strings.xml`.
- Front-end web incorporado: `web-ui/`, servido pelo módulo `web`.

Use os caminhos reais do pacote `br.com.projetoia.app`. Não copie caminhos de documentos antigos.

## Regras de implementação

- Preserve alterações existentes e leia os arquivos relevantes antes de editar.
- Configuração específica de chat deve ser gravada pela sessão do chat; não substitua uma conversa inteira por uma cópia antiga.
- Não renomeie identificadores existentes de banco de dados ou preferências sem uma migração compatível.
- Não registre prompts, chaves, cabeçalhos sensíveis nem corpos de requisição em logs.
- Para strings visíveis ao usuário, use recursos localizados em português brasileiro e inglês quando a tela já tiver essas traduções.
- Mostre custos desconhecidos como desconhecidos; não trate uma API key como prova de gratuidade.
- Operações sobre arquivos e shell devem respeitar as aprovações configuradas para a conversa.
- Mantenha avisos de licença e atribuição exigidos pelos componentes incorporados.

## Compilação

```bash
./gradlew assembleGithubDebug -x :web:buildWebUi
```

O wrapper pode precisar baixar Gradle. A compilação precisa ser executada e concluída antes de afirmar que o código compila; instalação e comportamento no aparelho exigem validação própria.
