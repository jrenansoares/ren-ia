# Requisitos do Projeto IA

Requisitos e estado da implementação em 08/11/2026. Este documento diferencia o produto desejado dos recursos já entregues.

## Experiência e aparência

- Referência de simplicidade e aparência: capturas fornecidas pelo usuário. A implementação e a identidade visual do Projeto IA são próprias.
- Fundo escuro neutro, verde nos controles, textos claros, navegação curta, listas de serviços com ícones e pesquisa. Marca própria Projeto IA.
- Menu lateral inspirado no print do ChatGPT: marca e busca no topo, atalhos simples, conversas/fixados abaixo, Novo chat e perfil no rodapé. Ajustes de assistente ficam dentro do perfil; não criar atalhos sem recurso funcional.
- Adicionar chave deve exigir escolher serviço e colar chave. Endereços e opções técnicas ficam em Avançado. Chaves ocultas por padrão.
- O usuário comum não deve precisar saber nomes de modelos, distinguir voz de chat nem adivinhar quais suportam ferramentas.
- Catálogo de provedores inspirado na lista dos prints. Endereços conhecidos podem ser pré-configurados; compatibilidade real, acesso e quota dependem da conta e devem ser confirmados antes de rotular uma integração como validada.
- Carregar /models não comprova que a chave possui acesso a todo o catálogo. Não apresentar contagem de modelos como promessa de acesso gratuito.
- Mostrar apenas modelos identificados como compatíveis com a tarefa. Desconhecidos ficam fora da seleção automática até identificação. Nenhuma operação paga deve ser autorizada apenas porque um modelo consta no catálogo.

## Conversa e projetos

- Todo o uso e gerenciamento deve ser possível pelo Android, sem computador obrigatório.
- Roteamento automático entre IAs gratuitas autorizadas, levando em conta a tarefa e as capacidades. Preservar contexto, arquivos, resultados de ferramentas e progresso.
- Nunca repetir uma ação já iniciada ao trocar de modelo. Comunicar esgotamento de quota e permitir retomada.
- Manter projetos, arquivos, ambiente Linux e terminal. Claude Code, HyperFrames e outros repositórios precisam de instalação e validação próprias; não declarar suporte comprovado antes de testar no aparelho.
- A alpha05 entregou um agente próprio no modo Programação, com projeto por conversa, preparação assistida do Linux, Git, Python e Node, teste de arquivos/execução, aprovações e retomada. Isso não conclui o pedido de usar Claude Code dentro do aplicativo. A homologação em aparelho e com conta real continua necessária.
- Na primeira versão do modo Programação, limitar as chamadas de inferência a rotas gratuitas identificadas do OpenRouter com ferramentas e autorização do usuário. Outros provedores precisam de uma forma confiável de confirmar a gratuidade da conta antes de entrar nesse modo. O bloqueio inicial não abrange todos os demais recursos do aplicativo ou os serviços externos usados por scripts de terceiros.
- Claude Code foi solicitado para uso dentro do aplicativo. Sua instalação e integração continuam pendentes; a entrega do agente próprio não substitui esse requisito. Validar execução no Linux do Android, autenticação, sessões e aprovações. Não apresentar o agente próprio como Claude Code nem prometer compatibilidade com qualquer modelo gratuito.

## Preparação com progresso real — revisão de 07/11/2026

- Mostrar os bytes recebidos e o total do download de Linux quando o servidor informar esse total. Sem total informado, mostrar somente os bytes recebidos.
- Conferência do arquivo e extração são etapas próprias. Mostrar a quantidade de itens extraídos, sem inventar uma porcentagem geral.
- Acompanhar a saída da instalação durante a execução, incluindo o progresso de download e configuração informado pelo gerenciador de pacotes.
- Mostrar tempo decorrido, última atividade e aviso quando não houver novo progresso informado. O aviso não deve afirmar travamento sem evidência.
- Distinguir cancelamento, tempo excedido e saída com código de erro. Preservar as últimas mensagens e manter o erro visível acima dos detalhes.
- A alpha06 implementa essa revisão. A falha vista nos prints da alpha05 não teve sua causa isolada. A validação no Android continua necessária.

## Chamada de voz — requisito obrigatório, integração pendente

- Conversa contínua em português brasileiro, iniciada no chat, com o mesmo histórico e acesso autorizado aos mesmos projetos.
- Tratar separadamente reconhecimento de fala, modelo de conversa e síntese de voz. Modelos de áudio não entram na seleção de chat.
- Controles para iniciar/encerrar, silenciar microfone e interromper a resposta; estados claros de ouvindo, processando, falando e falha.
- Reutilizar uma conexão cadastrada nos recursos compatíveis, sem pedir a mesma chave em várias telas. Não enviar uma chave para outro serviço.
- Priorizar reconhecimento e síntese gratuitos do Android quando disponíveis. Alternativas remotas precisam de suporte confirmado a português e de cotas gratuitas conhecidas.
- Gerenciar perda de rede, suspensão do app, reprodução interrompida, dispositivos Bluetooth e áudio capturado da própria resposta. Medir latência e bateria em aparelho real.
- Pedir permissão de microfone no momento de iniciar. Gravação contínua não deve começar ao abrir o app. Não guardar áudio bruto por padrão.
- Ditado e leitura de respostas são componentes, mas não bastam para declarar uma chamada contínua integrada. A alpha05 ainda não conclui essa integração.

## Entrega e validação

- Cada atualização mantém pacote e assinatura para preservar os dados.
- Testar importação de chaves sem expor segredos, compatibilidade por tarefa, continuação de conversas e falha por quota.
- Distinguir testes automatizados, compilação e teste real no Android. Publicar limites conhecidos de cada alpha.


## Alpha07 — aprovação automática por conversa

Opção explícita em Programação para executar as ferramentas do projeto sem confirmação a cada chamada. O padrão continua manual, a escolha persiste na conversa e é desativada ao sair do modo ou criar uma ramificação. Mudanças ficam bloqueadas durante geração ou aprovação pendente. Demais tipos de ferramentas, restrições de rotas gratuitas e cotas permanecem. HyperFrames e Claude Code não estão homologados no Android.

## Alpha08 — Linux compartilhado

- Opção explícita de baixar e preparar um Linux comum antes de criar projetos. Todos os novos projetos locais podem reutilizar a instalação e as ferramentas básicas.
- Manter os arquivos de cada projeto separados, inclusive HOME e temporários usados em comandos e terminais. Excluir um projeto não pode excluir o Linux compartilhado.
- Preservar instalações anteriores. Permitir que um projeto antigo use o Linux comum e volte à instalação anterior. Não mover ou apagar automaticamente a instalação antiga.
- Preparação única entre conversas, com progresso real, cancelamento e nova tentativa. Um Linux já instalado não deve ser baixado novamente ao preparar outro projeto.
- Preservar o funcionamento de projetos SSH, as aprovações por conversa, os filtros de rotas gratuitas e o banco de dados existente.
- Cobrir o cenário de dados vazios e o de projetos existentes nos testes. Distinguir esses testes automatizados de uma instalação e atualização reais no telefone.
- A instalação limpa sugerida pelo usuário é opcional e apaga os dados locais. O backup geral não inclui os arquivos dos projetos nem o Linux e remove credenciais; orientar exportação separada e novo cadastro das chaves.

## Alpha09 — vídeo anexado pelo chat

- Corrigir a rejeição de MP4 pelo seletor Arquivo, identificando vídeo por MIME ou extensão, inclusive com MIME genérico do Android.
- Mostrar Vídeo para o projeto no modo Programação, sem exigir entrada nativa de vídeo no modelo escolhido.
- Manter o vídeo como anexo local. Antes do pedido ao modelo de programação, convertê-lo em referência acessível às ferramentas do projeto, preservando o histórico e evitando tratar o vídeo como documento de texto.
- Copiar anexos em segundo plano, com contagem real de dados copiados e cancelamento. Não publicar arquivos incompletos nem enviar a mensagem antes do fim da importação.
- Rejeitar referências ausentes ou fora da área de anexos com orientação para anexar novamente. Não declarar análise do vídeo sem resultado das ferramentas.
- Preservar a instalação compartilhada, os projetos, a assinatura, as aprovações por conversa e as rotas gratuitas. A importação de MP4 não conclui a homologação de HyperFrames ou Claude Code.
