# Projeto IA — alpha06

Atualização de diagnóstico da preparação — 7 de novembro de 2026.

Instale a alpha06 por cima da versão atual. O pacote e a assinatura são mantidos. Não desinstale nem limpe os dados para fazer esta atualização. A preservação dos dados precisa ser conferida no aparelho.

## O que aparece durante a preparação

- Download do Linux — quantidade recebida, total e porcentagem quando o servidor fornece o tamanho. Velocidade média em KiB/s. Sem total, aparecem somente os MiB recebidos.
- Conferência — progresso da leitura do arquivo para verificar o SHA-256.
- Extração — quantidade real de itens extraídos e nome do item atual. Não há porcentagem global inventada.
- Ferramentas — etapa de recuperação de pacotes, atualização da lista, download e configuração. A porcentagem vem dos registros emitidos pelo APT e se refere à etapa indicada. A atualização das listas pode ter tamanho desconhecido e não recebe barra percentual.
- Acompanhamento — tempo decorrido e tempo desde a última atividade informada. Pulsos de download com a mesma porcentagem não contam como avanço.
- Detalhes — as últimas mensagens chegam durante a execução. Toque em Ver detalhes da execução para expandir uma área limitada de rolagem.
- Interrupção — o erro aparece acima dos detalhes. A tela diferencia cancelamento, limite de tempo e código de saída do comando.

A ausência de progresso por 90 segundos gera um aviso. Ela pode significar espera pela rede ou uma etapa demorada. O aviso não comprova travamento e não cancela sozinho a preparação. O limite da instalação das ferramentas passou de 10 para 30 minutos.

## Retomar a preparação que falhou

1. Mantenha o app aberto, com conexão e espaço disponíveis.
2. Volte ao projeto em Programação e toque em Preparar ambiente.
3. O Linux já instalado é reaproveitado. A receita tenta concluir a configuração de pacotes pendentes antes de continuar. Um ambiente quebrado ou uma falha persistente ainda pode exigir diagnóstico.
4. Aguarde Teste local aprovado. Porcentagem de leitura do banco de pacotes ou de download não significa que toda a preparação terminou.
5. Toque em Autorizar opções gratuitas compatíveis e depois em Usar neste chat.

Se houver nova falha, confira a mensagem no topo e as últimas linhas em Ver detalhes da execução. A causa da falha da alpha05 vista nos prints não pôde ser isolada. Esta atualização melhora o acompanhamento e o diagnóstico, sem prometer que resolve toda falha de instalação.

## Limites

O agente continua sendo o agente próprio do Projeto IA com as rotas gratuitas autorizadas de Programação. Claude Code, HyperFrames e chamada de voz contínua continuam pendentes. O suporte oficial do Claude Code a Linux ARM64 não comprova funcionamento dentro do PRoot do Android. A instalação e a integração ao chat precisam de testes próprios.

O acompanhamento fica no processo do aplicativo. Não é um serviço persistente e o histórico de preparação não é um backup. O backup geral continua sem incluir todo o Linux e os diretórios dos projetos.

Consulte VALIDACAO_ALPHA06.md para os testes desta entrega. A instalação no Poco F7 e o fluxo visual no Android continuam exigindo validação no aparelho.
