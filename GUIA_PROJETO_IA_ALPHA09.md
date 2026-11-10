# Projeto IA — alpha09

## Vídeo pelo chat

A mensagem Unsupported file type ao escolher um MP4 pela opção Arquivo vinha do filtro de documentos do chat. A alpha09 reconhece vídeos nesse seletor e acrescenta Vídeo para o projeto no modo Programação.

1. Instale a alpha09 sobre a versão atual para manter conversas, projetos e Linux.
2. Abra Programação e ative o projeto preparado que será usado para a edição.
3. Toque no botão + do chat e escolha Vídeo para o projeto. A opção Arquivo também reconhece MP4 quando o modo está ativo.
4. Selecione o vídeo. A importação mostra o arquivo atual, a quantidade de arquivos e os MiB efetivamente copiados. É possível cancelar.
5. Quando o anexo aparecer no campo de mensagem, escreva o que deseja fazer e envie.

A importação copia o arquivo para a área de anexos do aplicativo e não modifica o original no celular. Durante a cópia, o envio fica bloqueado para evitar uma mensagem sem o anexo esperado. Uma cópia cancelada ou com falha não publica o arquivo incompleto. Anexos que terminaram antes de um cancelamento continuam disponíveis no campo da mensagem.

No modo Programação, o agente recebe um caminho local acessível às ferramentas, em vez do vídeo codificado no pedido de chat. Isso permite usar modelos de programação que não recebem vídeo diretamente. O anexo continua sendo mostrado como vídeo na conversa; o agente deve preservar essa fonte e salvar cópias e resultados nos arquivos do projeto.

Anexar o vídeo não inicia uma edição automaticamente. É necessário enviar a instrução. Se o arquivo de um anexo antigo estiver ausente ou não estiver disponível ao projeto, o aplicativo pedirá para anexá-lo novamente.

Sem um projeto ativo, use Programação para editar vídeos. O fluxo nativo de vídeo dos provedores da base permanece separado, sujeito à compatibilidade, cotas e limites do provedor.

## Mensagem inicial de edição

> Use o vídeo que anexei. Primeiro confirme que consegue acessar o arquivo e confira duração, resolução e áudio com FFprobe. Prepare o HyperFrames oficial e verifique seus requisitos. Gere um MP4 de teste de cinco segundos antes da edição completa. Se funcionar, prepare uma versão para Reels em 1080 × 1920, preservando a fala e as legendas já existentes. Use cortes discretos, aproximações suaves e poucos destaques. Preserve o original e salve a edição nos arquivos do projeto. Trabalhe localmente, sem contratar serviços pagos, e informe qualquer erro real que impedir a conclusão.

HyperFrames, FFmpeg e o navegador necessário ainda precisam ser instalados e testados no Linux do telefone. O recebimento do MP4 não comprova que a renderização funciona. Claude Code continua sem integração.

## Linux e aprovações

A instalação compartilhada da alpha08 permanece disponível em Programação → Baixar Linux para todos os projetos. Novos projetos a reutilizam. Projetos antigos podem manter seu Linux próprio ou adotar o compartilhado.

A aprovação automática da alpha07 continua sendo uma escolha por conversa. O recebimento de vídeo não altera permissões, rotas gratuitas, cotas ou políticas de outros tipos de ferramenta.

## Atualização e dados

Não é necessário apagar os dados para esta correção. Atualizar sobre a versão anterior preserva o mesmo pacote e a assinatura. A atualização real no aparelho ainda precisa de validação.

Caso escolha uma instalação limpa, exporte seu backup e os arquivos importantes dos projetos separadamente e guarde as chaves para cadastrá-las novamente. O backup geral não inclui os arquivos dos projetos nem o Linux e remove credenciais.

## Validação

A entrega distingue testes JVM, compilação e assinatura de um teste no Android. O seletor de arquivos, a cópia pelo provedor de arquivos do Poco F7 e o uso real do vídeo pelo agente precisam de teste no telefone. Não houve renderização de vídeo nesta preparação.
