# Projeto IA — alpha05

## Instalar a atualização

Instale `projeto_ia_0.1.0_alpha05_arm64.apk` por cima da versão anterior. Não desinstale o aplicativo. O pacote e o certificado de assinatura foram mantidos. A atualização e a preservação dos dados precisam ser conferidas no seu aparelho.

## Ativar Programação

1. Abra **Programação** no menu. Isso inicia uma conversa com o painel de preparação aberto. Para usar uma conversa existente, toque em **Programação**, acima do campo de mensagem.
2. Escolha um projeto existente ou informe um nome e toque em **Criar projeto**.
3. Toque em **Preparar ambiente**. O aplicativo baixa o Linux quando necessário, confere o arquivo baixado e prepara Git, Python, Node e npm. Reserve espaço e internet e mantenha o app aberto. Os downloads e as ferramentas podem ocupar centenas de MB.
4. Aguarde **Teste local aprovado**. O teste cria e corrige um arquivo temporário, executa um teste Python, verifica Git e Node e remove apenas os arquivos temporários que criou.
5. Toque em **Autorizar opções gratuitas compatíveis**. Nesta primeira versão, o modo Programação aceita apenas rotas gratuitas identificadas do OpenRouter com suporte a ferramentas. Se não aparecer nenhuma, abra **Chaves de IA**, conecte o OpenRouter e carregue os modelos.
6. Toque em **Usar neste chat** e envie a tarefa. A conversa fica vinculada ao projeto. Não é necessário alterar o assistente global ou escolher nomes de modelos.

O botão de autorização adiciona apenas as opções compatíveis deste modo à seleção já existente. Não liga a troca automática das outras conversas. A seleção automática de Programação funciona dentro do próprio modo.

## Primeiro teste pelo chat

Envie

> Crie um arquivo hello.py neste projeto que imprima Olá, Renan. Execute o arquivo e me mostre a saída real.

Leia e aprove as solicitações para escrever o arquivo e executar o comando. A resposta deve incluir o resultado da ferramenta. Uma frase da IA dizendo que fez a tarefa, sem resultado de execução, não comprova sucesso.

Depois, saia e volte à mesma conversa e envie

> Leia o hello.py que criamos e altere a mensagem para Meu projeto está funcionando. Execute novamente.

Os botões **Arquivos** e **Terminal** abrem os recursos do projeto escolhido. Para clonar um repositório público, informe o endereço do GitHub no chat e peça uma pasta nova dentro do projeto. Instalação de dependências e execução também passam pelas aprovações.

## Cancelar, retomar e sair

- O botão de parar do chat cancela a geração ou o comando em andamento. Resultados já registrados permanecem no histórico. Não pressuponha que uma operação externa interrompida deixou de acontecer.
- **Cancelar preparação** interrompe a preparação atual. É possível tentar novamente. A próxima abertura recupera o estado de uma instalação de Linux interrompida, conforme a presença do ambiente.
- O aplicativo tenta concluir configurações de pacotes pendentes antes de repetir a preparação. Falhas de rede, falta de espaço e ambientes personalizados ainda podem exigir diagnóstico pelo terminal.
- **Voltar ao chat sem acesso ao projeto** desliga o modo nesta conversa e desvincula o projeto. Não exclui seus arquivos. Conclua ou cancele a resposta e resolva aprovações pendentes antes de mudar de modo.
- O vínculo é salvo na ativação, inclusive antes da primeira mensagem. A retomada após encerramento real do Android ainda precisa de teste no aparelho.

## Gratuidade e processamento

O celular executa os comandos e armazena os projetos. A IA configurada recebe a conversa e o conteúdo dos arquivos usado na tarefa para orientar o trabalho.

Neste modo, o aplicativo bloqueia chamadas de inferência para modelos pagos ou de preço desconhecido e para outros provedores. São aceitos `openrouter/free` e identificadores terminados em `:free`, no endpoint padrão HTTPS do OpenRouter, com ferramentas e autorização. Configurações avançadas de cabeçalhos, corpos e endpoints não entram nesse fluxo.

Groq e outros provedores continuam disponíveis nas conversas comuns. Ainda não entram neste modo porque o app não confirma automaticamente o plano gratuito de cada conta. As cotas continuam existindo; esgotar todas as rotas exige aguardar ou tentar novamente mais tarde.

Títulos de Programação são locais, sugestões automáticas e consolidação de memória ficam fora deste fluxo e a compactação usa o mesmo filtro de rotas gratuitas. Essa proteção não é um bloqueio universal de cobrança em todas as telas do app e não controla serviços que um script de terceiros possa chamar. Nenhuma chave de IA é deliberadamente injetada pela preparação nos comandos do projeto.

## Limites desta entrega

- Este é o agente próprio do Projeto IA. Claude Code e HyperFrames não vêm instalados nem homologados nesta versão.
- Chamada de voz contínua em português continua pendente. Ditado e leitura existentes não equivalem à chamada completa.
- O backup geral ainda não inclui os diretórios completos dos projetos ou o Linux. Preserve seus arquivos separadamente antes de remover dados ou desinstalar.
- A compilação, os testes JVM e o teste da receita em Linux foram executados. O fluxo visual, a instalação do Linux, as APIs reais, o desempenho e a retomada precisam de validação no Android.
- O resultado da análise estática e os demais bloqueadores estão em `VALIDACAO_ALPHA05.md`. Continua sendo uma versão experimental.
