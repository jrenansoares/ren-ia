# Ren alpha15

APK `0.1.0-alpha15-ren`, código 15, ARM64. Usa o mesmo pacote e certificado da alpha14; pode ser instalado por cima.

## Como ativar

1. Abra Chaves de IA e toque em **Verificar e selecionar automaticamente**.
2. Marque os provedores cujas cotas ou créditos você autoriza usar nos testes e no chat. A autorização vale para o endpoint e chave atuais, incluindo modelos futuros desse catálogo. Preços desconhecidos continuam indicados como desconhecidos.
3. Confirme e mantenha o app aberto durante a verificação. O resultado e os provedores em verificação aparecem na tela.
4. Use os modelos prontos no chat. No modo Programação/Claude Code, o modelo também precisa ter passado na chamada sintética de ferramenta. A seleção manual próxima ao chat e os consultores de texto da alpha14 continuam disponíveis.

OpenRouter continua restrito às rotas gratuitas confirmadas. Autorizações individuais de modelos são preservadas. Alterar chave, endpoint ou configurações invalida resultados vinculados à configuração antiga.

## Catálogo e seleção

- O catálogo vem diretamente da API configurada, sem depender de um feed externo ou de uma espera de 30 dias.
- Ao abrir o app, o catálogo é consultado se a última verificação bem-sucedida tiver mais de 12 horas. Falhas de consulta têm intervalo de 15 minutos. O comando manual permite uma nova verificação imediata.
- Google e Anthropic seguem a paginação do catálogo. Respostas inválidas ou páginas repetidas não publicam um catálogo parcial.
- Modelos novos desconhecidos do registro interno entram como candidatos e precisam passar na inferência antes da seleção automática. Modelos de áudio, imagem, moderação e embeddings não são tratados como modelos de conversa.
- Cada passagem verifica no máximo quatro candidatos por provedor, em até 75 segundos por provedor, com dois provedores em paralelo. Uma passagem pode terminar sem encontrar um modelo utilizável. Não há benchmark de centenas de modelos em cada abertura.
- Resultados de inferência bem-sucedidos recentes são reutilizados. A verificação de ferramentas é sintética e não executa comandos.
- Modelos ausentes no catálogo saem da seleção automática, preservando UUIDs, conversas e escolhas anteriores. Falhas de rede na consulta preservam o catálogo anterior.
- Modelos desmarcados manualmente não são reativados pela gestão automática.

## Roteamento durante o uso

O modelo escolhido manualmente tem preferência quando está disponível. A ordem solicitada de provedores permanece Google AI Studio, DeepSeek, NVIDIA, Groq e OpenRouter. Dentro dessa ordem, o roteador considera adequação declarada à tarefa, histórico de sucesso e tempo de resposta observado. Esse histórico não é um benchmark de qualidade de conteúdo.

O histórico persiste entre reinícios. Falhas e limites geram suspensão temporária; o cabeçalho Retry-After é respeitado quando fornecido. Falha de autenticação ou saldo insuficiente impede repetir a mesma chave nos demais modelos daquele provedor. Uma resposta parcialmente emitida não é reiniciada em outro modelo. Erros ao salvar estatísticas não repetem a inferência nem fazem uma resposta concluída falhar.

A tela de modelos tem filtros Todos, Prontos, Ferramentas e Visão. Visão reflete os metadados declarados; a verificação sintética desta versão não envia imagens. O botão de teste ocupa uma linha própria para não estreitar a descrição.

## Verificação das chaves

A tela distingue catálogo acessível de chave confirmada em uma chamada real. Listar modelos não é prova de acesso a todas as inferências. Os estados mostram horário, HTTP quando disponível e quantidade de modelos prontos. O aplicativo não guarda corpos de erro do provedor nas estatísticas.

## Validação e limites

Compilação e assinatura APK v2 verificadas. Foram aprovados 955 testes, sem falhas, e três foram ignorados por dependerem de um executável externo do Claude Code. Os testes cobrem catálogo, paginação, troca de chave, consentimento, migração, suspensão por cota, classificação de resultados, preferência manual e preservação de respostas em falhas de estatística.

Não foram usadas chaves reais nem um Android físico. Disponibilidade, gratuidade, cotas e permissões reais dependem da conta de cada provedor. A interface ainda precisa de homologação visual no aparelho. A instalação Linux e a ponte Claude Code existentes foram preservadas. Um novo servidor local compatível com OpenAI/Anthropic foi adiado por instrução do usuário.
