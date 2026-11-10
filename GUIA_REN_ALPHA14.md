# Ren alpha14

APK `0.1.0-alpha14-ren`, código 14, ARM64. Mesmo pacote e certificado da alpha13, permitindo atualização por cima da instalação anterior.

## Alterações

- Verificação separada de resposta em texto e suporte real a ferramentas, com chamada sintética que nunca executa comandos.
- O teste envia a definição da ferramenta mesmo quando o catálogo não declara suporte. A capacidade é registrada somente após resposta válida e usada pelo seletor e pelo roteador.
- Diagnóstico por status HTTP e tipo de erro, sem salvar corpos das respostas ou credenciais.
- Até dois modelos de texto aprovados e testados podem sugerir um plano no Claude Code, por opção da conversa. As consultas ocorrem em paralelo, com limite de 20 segundos por consulta. Recebem somente a tarefa escrita, sem arquivos nem ferramentas.
- A confirmação de custos continua separada na tela de roteamento. Testar uma conexão não autoriza uso pago futuro. Autorizações anteriores são preservadas quando apenas metadados de capacidade são atualizados pelo teste.
- Seleção manual e prioridade Google AI Studio, DeepSeek, NVIDIA, Groq e OpenRouter preservadas. O fallback continua limitado aos modelos autorizados e verificados.

## Validação

Compilação aprovada e assinatura APK v2 verificada. 653 testes passaram, 3 foram ignorados e não houve falhas. Os três testes ignorados exigem um executável externo oficial do Claude Code, ausente nesta execução.

App: {'tests': 559, 'failures': 0, 'errors': 0, 'skipped': 3, 'passed': 556}. Workspace: {'tests': 97, 'failures': 0, 'errors': 0, 'skipped': 0, 'passed': 97}.

Não houve instalação no Android físico nem chamadas com as chaves reais do usuário. Isso exige homologação no aparelho; cotas e disponibilidade variam conforme cada provedor. A aprovação automática das ferramentas e a consulta de modelos de texto permanecem opções por conversa.

## Uso

Atualize o APK. Em Chaves de IA, execute o teste de um modelo, consulte o diagnóstico e autorize o uso dele no roteamento. Modelos aprovados no teste de ferramentas podem executar no modo Programação e Claude Code. Os que passam apenas no teste de texto podem participar como consultores quando essa opção for ativada na conversa.
