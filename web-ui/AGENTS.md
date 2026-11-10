# Instruções de desenvolvimento da interface web

`web-ui` contém a interface web incorporada ao Projeto IA. É uma aplicação React Router, React, TypeScript, Tailwind e Zustand; o módulo Android `web` hospeda os recursos estáticos.

## Comandos

```bash
pnpm run dev
pnpm run typecheck
pnpm run build
pnpm run fmt:check
```

## Integração com o aplicativo

- Os tipos compartilhados desta interface ficam em `app/types/`.
- Os modelos Kotlin correspondentes ficam principalmente em `app/src/main/java/br/com/projetoia/app/data/` e nos módulos `ai` e `web`.
- Ao mudar DTOs ou tipos serializados, confira os dois lados.
- Não inclua segredos de provedores nos estados enviados à interface ou nos logs.
