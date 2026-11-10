# Projeto IA — alpha07

A alpha07 acrescenta aprovação automática das ferramentas de projeto na conversa escolhida.

## Instalação e uso

Instale o APK sobre a versão anterior. A assinatura e o identificador do aplicativo devem permanecer iguais. Não desinstale a versão anterior para fazer a atualização.

Abra a conversa e toque em Programação. Selecione e ative o projeto. Com a conversa parada, ligue Aprovação automática nesta conversa. As próximas edições e comandos das ferramentas de projeto serão executados sem novas confirmações.

A opção fica salva nessa conversa. Outras conversas continuam com aprovação manual. Sair do modo Programação ou criar uma ramificação da conversa exige ativar a opção novamente. Para voltar à aprovação manual, desligue a opção quando não houver geração nem aprovação pendente.

Pedir “aprove tudo” na mensagem não modifica as permissões das versões anteriores. O controle fica no aplicativo.

## Vídeo e HyperFrames

Em Programação → Arquivos, use o botão de importar arquivo e selecione o vídeo. O agente pode acessar o arquivo importado pelo projeto.

O HyperFrames não vem instalado ou homologado nesta versão. Antes de editar um vídeo longo, peça ao agente que confira os requisitos oficiais e produza um MP4 de teste de cinco segundos. A renderização depende de Node.js, FFmpeg e navegador compatível com o Linux ARM64/PRoot no telefone. Instalar dependências não comprova que a renderização funciona.

Depois de um teste válido, solicite a edição do vídeo importado. Para um vídeo já legendado, preserve as legendas e não gere outra camada. Informe formato, duração desejada e estilo. Preserve o original e exporte o resultado pela área de arquivos.

Mensagem para colar no chat depois de importar o vídeo

> Prepare o HyperFrames oficial de https://github.com/heygen-com/hyperframes neste projeto. Leia as instruções oficiais e confira Node.js 22+, FFmpeg, FFprobe e um navegador compatível. Primeiro gere um MP4 de teste de cinco segundos e confira duração e resolução com FFprobe. Se funcionar, edite o vídeo importado para Reels em 1080 × 1920, preservando o original, a fala e as legendas existentes. Use cortes discretos, aproximações suaves e poucos destaques visuais, sem cortar o rosto ou as legendas. Não crie outra camada de legendas. Renderize localmente, sem contratar serviços pagos. Se houver falha, mostre o erro real e a etapa que falhou.

O modo Programação usa o agente do próprio aplicativo. O Claude Code oficial é outro programa e tem seu próprio processo de autenticação. A instalação do CLI, por si só, não o integra ao chat deste aplicativo.

Referências oficiais consultadas em 8 de novembro de 2026

- [HyperFrames](https://github.com/heygen-com/hyperframes)
- [Primeiro projeto com HyperFrames](https://hyperframes.app/docs/1-startup/2-quickstart)
- [Instalação e autenticação do Claude Code](https://code.claude.com/docs/en/quickstart)

## Limitações

Versão experimental. A instalação e a interface no telefone, a preparação real do Linux e a renderização com HyperFrames precisam de teste no aparelho. Claude Code não foi integrado. As cotas dos provedores continuam valendo. O limite existente de 24 chamadas por tarefa e o máximo de 600 segundos por comando de shell permanecem.

## Compilação

Requer JDK 21, Gradle 9.6.0, plataformas Android 37.0 e 37.2 e Build Tools 36.0.0. Os utilitários de verificação desta entrega são os Build Tools 37.0.0. O caminho opcional `-I tools/build/reuse-alpha06-native.gradle` reaproveita somente libworkspace.so e libtermux.so da alpha06, conferindo o manifesto de hashes e os arquivos C++ inalterados. A compilação padrão continua usando CMake/NDK.

Use `-PformatBase=imported-alpha06` quando a árvore for importada e etiquetada com esse nome. O código web estático da alpha06 é preservado e pode ser empacotado com `-x :web:buildWebUi`.
