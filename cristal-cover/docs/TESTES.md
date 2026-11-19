# Checklist de testes no aparelho

O projeto foi compilado, passou pelo lint e pelos testes automáticos em JVM, mas **não foi executado em um
Razr 40 Ultra de verdade** durante o desenvolvimento. Use esta lista para validar no seu aparelho. Marque o
que funcionar e anote o que não funcionar: os itens marcados com ⚠️ são os que dependem de comportamento da
Motorola que não é documentado.

Para relatar um problema, anote: o item, o que você esperava, o que aconteceu e a versão do app
(Configurações do Cristal > Sobre).

## 1. Instalação e primeira abertura

- [ ] O APK instala e o ícone "Cristal Cover" aparece na lista de apps.
- [ ] A configuração guiada abre na primeira execução e as 6 etapas cabem na tela (interna e externa).
- [ ] "Ativar" leva à tela de acesso às notificações; ao voltar, o item fica verde.
- [ ] "Abrir acessibilidade" leva à tela certa; ao voltar, o item fica verde.
- [ ] Se aparecer "Configuração restrita", o botão "Abrir detalhes do app" leva à tela com os três pontinhos.
- [ ] "Abrir o launcher" encerra a configuração e ela não volta a aparecer.
- [ ] Na primeira vez, a tela inicial vem com relógio, alguns apps e o dock preenchido.

## 2. Tela interna

- [ ] A tela interna tem a sua própria tela inicial (grade 4×8 por padrão), diferente da externa.
- [ ] Mover, adicionar ou remover itens na tela interna não muda nada na tela externa, e vice-versa.
- [ ] Mudar a grade de uma tela (Configurações > Tela inicial e ícones) não mexe na outra.
- [ ] Depois de atualizar o app, a sua tela inicial antiga continua na tela interna, sem itens perdidos.

- [ ] O launcher abre na tela de dentro e a grade usa a altura toda.
- [ ] Girar o aparelho não fecha nem reinicia a tela.
- [ ] Voltar (gesto) fecha gaveta/painel/pasta; na tela inicial, sai do app (se não for o launcher padrão).

## 3. Tela externa

- [ ] ⚠️ "Abrir na tela externa" (Configurações do Cristal > Tela externa) leva o Cristal para lá.
- [ ] ⚠️ O bloco "Cristal Cover" dos Ajustes rápidos abre o Cristal na tela externa.
- [ ] ⚠️ O Cristal pode ser adicionado ao painel Apps em Configurações > Tela externa > Painéis.
- [ ] ⚠️ Com "Assumir a tela externa" ligado, fechar o aparelho abre o Cristal sozinho.
- [ ] O papel de parede ocupa a tela inteira, inclusive atrás das câmeras.
- [ ] Nenhum ícone ou botão fica embaixo das lentes.
- [ ] ⚠️ Com "Aproveitar a área ao lado das câmeras" ligado, o dock fica ao lado das lentes (se a Motorola
      informar o recorte). Com a opção desligada, o dock fica acima.
- [ ] Configurações > Tela externa mostra "Tela 1 · externa" com resolução perto de 1056×1066.
- [ ] O estado "fechado/aberto" muda ao abrir e fechar o aparelho.

## 4. Flip (abrir e fechar)

- [ ] Com o Cristal aberto na tela externa, abrir o aparelho não trava nem fecha o app.
- [ ] Com o Cristal aberto na tela interna, fechar o aparelho não trava nem fecha o app.
- [ ] Depois de apagar e acender a tela, o launcher volta na página principal, sem gaveta ou painel aberto.
- [ ] ⚠️ Com "Assumir a tela externa" ligado: ir para o início na tela externa reabre o Cristal em menos de 1 s.
- [ ] Toque longo no fundo > "Tela Motorola" mostra a tela original e o Cristal só reassume depois de ~20 s.
- [ ] Com "Assumir a tela externa" desligado, o Cristal nunca abre sozinho.

## 5. Tela inicial

- [ ] Deslizar para os lados troca de página; a página mais à esquerda é o painel "Hoje".
- [ ] O indicador de páginas acompanha o dedo.
- [ ] Tocar em um app abre o app.
- [ ] Toque longo em um app abre o menu; continuar segurando e arrastar move o ícone.
- [ ] Soltar em uma célula vazia move; soltar sobre outro app cria uma pasta; soltar sobre uma pasta adiciona.
- [ ] Encostar o ícone na borda troca de página; soltar em uma página nova cria a página.
- [ ] Arrastar para o dock e tirar do dock funciona; com o dock cheio aparece o aviso.
- [ ] Tocar em uma pasta abre; dá para renomear; tirar o penúltimo app desfaz a pasta.
- [ ] Toque longo no fundo abre Widgets / Papel de parede / Configurações.
- [ ] Adicionar cada um dos 9 widgets embutidos; toque longo no widget permite remover e arrastar.
- [ ] ⚠️ Adicionar um widget de outro app (Widgets > De outros apps); o sistema pede permissão na primeira vez.
- [ ] O botão de três pontos no canto do widget de outro app abre o menu para remover.
- [ ] Toque longo no fundo > Apps abre a lista; tocar em um app o coloca na página atual.
- [ ] Mudar a grade para 3×3 e para 6×6 não perde nem sobrepõe itens.

## 6. Gaveta e busca

- [ ] Deslizar para cima abre a gaveta acompanhando o dedo; soltar no meio decide abrir ou fechar.
- [ ] A busca acha "Câmera" digitando "camera" e "Configurações" digitando "config".
- [ ] A tecla de busca do teclado abre o primeiro resultado.
- [ ] Rolar a lista até o topo e continuar puxando para baixo fecha a gaveta.
- [ ] Toque longo em um app: adicionar à tela inicial, ao dock, ocultar, informações, desinstalar.
- [ ] Um app oculto some da gaveta e reaparece em Configurações > Apps ocultos > Reexibir.
- [ ] Instalar e desinstalar um app atualiza a gaveta sem reabrir o Cristal.

## 7. Central de Controle e notificações

- [ ] Deslizar para baixo abre a central; a alça de baixo e o gesto de voltar fecham.
- [ ] Lanterna, Som/Vibrar e volume funcionam e mostram o estado.
- [ ] Brilho: o primeiro toque pede "Alterar configurações do sistema"; depois o controle muda o brilho de verdade.
- [ ] Rotação e Não perturbe: pedem a permissão uma vez e depois alternam direto.
- [ ] Sem Shizuku: Wi-Fi, Bluetooth, Dados, Avião, Localização, NFC, Modo escuro e Economia abrem o ajuste do sistema. Com Shizuku: alternam direto. ⚠️
- [ ] Toque longo num controle abre o ajuste dele.
- [ ] Lápis: o modo de edição balança os módulos; tocar num módulo mostra mover, tamanho, próxima página e remover; "Concluir" salva.
- [ ] Predefinições (Liquid Flip, iOS, One UI) trocam a central só da tela em que você está.
- [ ] A central da tela externa e a da interna são diferentes.
- [ ] Deslizar para o lado chega à página de notificações; deslizar uma notificação dispensa; tocar abre o app.
- [ ] Com uma música tocando, o módulo mostra capa, título, artista e os botões funcionam.

### Relógio

- [ ] Configurações > Tela de bloqueio: a galeria mostra as 12 fontes; escolher uma muda o bloqueio interno e o widget de relógio.
- [ ] "Relógio em cartão de vidro" desligado: números grandes direto sobre a foto.
- [ ] Cover Clock no meio, sem notificações: o relógio empilha ("07" sobre "34") e ocupa o espaço; com notificações, encolhe.
- [ ] Com o efeito de profundidade e o relógio em cima, ele cresce até passar por trás da pessoa da foto.

### Profundidade

- [ ] Wallpaper Studio > uma área > imagem da galeria com uma pessoa ou pet: "Recortando o objeto…" e depois "Objeto recortado". ⚠️ (o Google Play Services baixa o módulo na primeira vez)
- [ ] No bloqueio interno o relógio grande fica atrás da pessoa; no Cover Clock também.
- [ ] Com "Desfocar" acima de 5% o efeito some (aviso aparece no Studio).
- [ ] "Papel de parede espacial": inclinar o aparelho move a imagem de leve; segurar parado volta ao centro.

## 8. Vidro e desempenho

- [ ] No modo Líquido, a borda dos cards distorce o papel de parede e tem brilho no canto superior esquerdo.
- [ ] Trocar o papel de parede muda o que aparece através do vidro.
- [ ] Tocar em um card faz o vidro "afundar" e brilhar sob o dedo.
- [ ] Abrir a gaveta desfoca a tela inicial por trás.
- [ ] Os modos Blur e Leve funcionam e são visivelmente diferentes.
- [ ] O controle de intensidade muda o desfoque.
- [ ] "Reflexo acompanha o movimento": o brilho se desloca ao inclinar o aparelho.
- [ ] Animações fluidas ao trocar de página e ao abrir a gaveta.
- [ ] Com a economia de bateria ligada, o vidro passa para o modo leve.
- [ ] Depois de 10 minutos de uso o aparelho não esquenta de forma anormal.
- [ ] Em Configurações > Bateria, o Cristal não aparece entre os maiores consumos depois de um dia.

## 9. Tela de bloqueio

- [ ] Wallpaper Studio: as 4 prévias aparecem com a proporção de cada tela.
- [ ] Escolher uma imagem só para "Bloqueio · tela externa" não muda as outras três áreas.
- [ ] "Igual a ..." faz uma área acompanhar outra; "Papel de parede próprio" desfaz.
- [ ] Zoom e posição movem a foto na prévia e na tela de verdade; saturação 0% deixa em preto e branco.
- [ ] ⚠️ Na tela externa, o bloqueio é o Cover Clock: só papel de parede, relógio e notificações.
- [ ] Tocar em uma notificação do Cover Clock expande e mostra "Abrir"; sem notificações, sobra só o relógio.
- [ ] Os 4 estilos, o tamanho e a posição do relógio mudam na pré-visualização do Cover Clock.
- [ ] ⚠️ Flip Transition: ao abrir/fechar, a tela inicial chega com uma mola; ao fechar devagar, ela recolhe.

- [ ] "Pré-visualizar" mostra relógio, data, bateria e atalhos; deslizar para cima fecha.
- [ ] ⚠️ Modo Activity: fechar o aparelho, apagar e acender a tela mostra a tela do Cristal.
- [ ] ⚠️ Se o modo Activity não aparecer na tela externa, o modo Sobreposição aparece.
- [ ] Deslizar para cima faz o sistema pedir PIN/padrão/digital.
- [ ] **Segurança:** cancelar o PIN volta para a tela de bloqueio; o aparelho NÃO destrava.
- [ ] **Segurança:** forçar o fechamento do Cristal com a tela bloqueada mostra a tela de bloqueio original.
- [ ] Destravar pela digital (depois de deslizar) fecha a tela do Cristal.
- [ ] Com "Ocultar conteúdo sensível", as notificações mostram só o nome do app.
- [ ] Tocar em uma notificação pede o desbloqueio e depois abre o app.
- [ ] A lanterna funciona sem destravar.
- [ ] A câmera abre sem destravar e não dá acesso à galeria antiga.
- [ ] Os controles de música funcionam sem destravar.
- [ ] Com "Só na tela externa", abrir o aparelho mostra a tela de bloqueio original.
- [ ] Alarmes e chamadas recebidas aparecem normalmente por cima.
- [ ] Desligar a tela de bloqueio do Cristal volta ao comportamento original.

## 10. Apps de terceiros na tela externa

Teste pelo menos: WhatsApp, YouTube, Spotify, Google Maps, Chrome, Câmera, um app de banco e um jogo.

- [ ] Um app liberado abre na tela externa.
- [ ] Um app NÃO liberado mostra, depois de ~2 s, o aviso "não abriu nesta tela" com o botão "Por quê?".
- [ ] Um app só em paisagem (jogo) mostra o aviso de compatibilidade antes de abrir.
- [ ] "Não avisar mais para este app" é respeitado na vez seguinte.
- [ ] ⚠️ Com Shizuku e "Liberar apps automaticamente", um app não liberado passa a abrir.
- [ ] ⚠️ "Opções na tela externa" > Escala 80%: o app abre com tudo menor; ao voltar ao Cristal a escala é desfeita.
- [ ] ⚠️ "Forçar redimensionamento" faz um app teimoso abrir.
- [ ] ⚠️ "Ignorar orientação pedida pelos apps" faz um app em paisagem ocupar a tela inteira.
- [ ] O teclado aparece e dá para digitar em apps na tela externa.
- [ ] Um app do perfil de trabalho (se houver) aparece na gaveta e abre.

## 11. Configurações, ícones e backup

- [ ] Tema claro, escuro e automático mudam os cards.
- [ ] As 6 formas de ícone funcionam; o tamanho muda na grade, no dock e na gaveta.
- [ ] O estilo "Vidro monocromático" muda os apps que têm ícone temático.
- [ ] Um pacote de ícones instalado aparece na lista e é aplicado.
- [ ] Clima: buscar a cidade, escolher e ver a temperatura no widget; trocar para °F.
- [ ] Backup: exportar gera um arquivo .json; mudar a grade e restaurar traz tudo de volta.
- [ ] Restaurar um arquivo qualquer que não seja backup mostra o erro certo e não muda nada.

## 12. Robustez

- [ ] Negar todas as permissões: o app continua abrindo e os cards explicam o que falta.
- [ ] Revogar o acesso às notificações com o app aberto não derruba o app.
- [ ] Desligar o serviço de acessibilidade com a tela de bloqueio ativa não derruba nada.
- [ ] Parar o Shizuku com o app aberto: as funções que dependem dele avisam em vez de travar.
- [ ] Desinstalar o Shizuku: o app continua funcionando.
- [ ] Reiniciar o aparelho: notificações e acessibilidade reconectam sozinhos.
- [ ] Atualizar o APK por cima mantém a tela inicial e as configurações.
- [ ] Modo avião: o widget de clima mostra o último valor ou a mensagem de erro, sem travar.

## 13. L.I.L.Y Keyboard

- [ ] Instalar o `lily-keyboard-….apk`; abrir o app; os dois passos de ativação ficam com ✓.
- [ ] Digitar no WhatsApp: a primeira letra sai maiúscula, "nao " vira "não ", apagar logo depois desfaz.
- [ ] "vc" fica "vc" no estilo Natural (com "você" sugerido) e vira "você" no Formal.
- [ ] Deslizar o dedo por "obrigado", "trabalho", "amanhã": a palavra certa aparece.
- [ ] Arrastar o espaço move o cursor; arrastar o apagar para a esquerda apaga palavras.
- [ ] Toque longo no "a" mostra á ã â à; no "q" mostra o 1.
- [ ] Campo de senha: nada sublinhado, sem sugestões.
- [ ] Copiar um texto e abrir o painel da área de transferência: o texto aparece; um código de 6 dígitos não.
- [ ] Emojis: busca "coração", favoritos com toque longo.
- [ ] Voz: pede o microfone na primeira vez; com o idioma baixado, funciona sem internet. ⚠️
- [ ] Modo de uma mão, temas e fundo da galeria no Keyboard Studio.
- [ ] Na tela externa (app aberto na tela de fora), o teclado fica compacto. ⚠️
- [ ] Latência: digitar rápido com dois polegares não perde letras.

## 14. Liquid Island

- [ ] Configurações > Liquid Island > Ativar (com a acessibilidade ligada): "Ver demonstração" mostra carregando, uma notificação e o modo silencioso. ⚠️
- [ ] A cápsula fica centralizada no furo da câmera; se não ficar, ajuste "Altura".
- [ ] Com música tocando: capa à esquerda e ondinha à direita; tocar expande com progresso e controles; deslizar para cima recolhe.
- [ ] Chamada em andamento: telefone verde e contador; tocar abre o app da chamada.
- [ ] Ligar o carregador mostra "Carregando" por alguns segundos; mudar para vibrar mostra "Silencioso".
- [ ] Uma notificação nova aparece na ilha; com o aparelho bloqueado, só o nome do app.
- [ ] A barra de status continua respondendo a toques fora da ilha.

### Liquid Island 2 (1.6.0)

- [ ] Música expandida (tocar na cápsula): uma gota sai da cápsula, desce até logo abaixo da barra de status e vira a lente; nenhum ícone da barra fica coberto. ⚠️
- [ ] Com muitas notificações na barra, a cápsula fica só com o ícone ou com o anel em volta da câmera.
- [ ] Em vídeo em tela cheia ou deitado, a ilha some (chamada continua como anel).
- [ ] Pedido no iFood/Uber (ou qualquer app com Live Update): a ilha mostra o tempo que o app escreveu e a etapa; expandida, a barra de progresso. Nada inventado. ⚠️
- [ ] Estilo Liquid Glass (Configurações > Liquid Island > Aparência) muda o vidro da ilha.

## 15. Info Brief e Now Brief (1.6.0)

- [ ] Num chat, escreva "Vou ir ao show às 7 horas da noite." e pare: em 1,5 s a expressão brilha em azul no campo e na barra, vira uma esfera e voa até a ilha. ⚠️
- [ ] A ilha mostra "Compromisso identificado · Show · 19:00" com **Criar lembrete** e **Agora não**.
- [ ] **Criar lembrete** com a data incerta abre a folha de confirmação (dia, horário, repetir, alarme); confirmar cria o lembrete e a ilha mostra "Lembrete criado".
- [ ] Na hora, a notificação do lembrete aparece (na ilha, sem aviso duplicado quando a ilha está ligada); **Adiar 10 min** e **Concluir** funcionam.
- [ ] "Não posso esquecer de entregar o trabalho" vira tarefa (sem data) e aparece em Tarefas no Now Brief.
- [ ] Enviar a mensagem sem esperar a pausa também gera a sugestão (gatilho "ao enviar").
- [ ] "Se eu for ao show às 7…", perguntas e campos de senha **não** geram sugestão.
- [ ] Desligar em L.I.L.Y Keyboard > Info Brief para tudo; em Cristal > Info Brief, o histórico mostra o que foi criado e dispensado.
- [ ] O painel à esquerda da primeira página agora é o Now Brief: resumo do dia, atividades em andamento, próximos compromissos e lembretes, tarefas e sugestões.

## 16. Em qualquer app (1.6.0)

- [ ] Em qualquer app, puxar para baixo do canto superior direito abre a Central de Controle acompanhando o dedo, com o vidro mostrando o app de baixo; voltar ou deslizar para cima fecha. ⚠️
- [ ] Na tela externa também funciona; com a tela bloqueada, não abre.
- [ ] O resto da barra de status continua abrindo a cortina do sistema.
- [ ] Fechar o aparelho dentro de um app: a tela encolhe em direção à dobra com a faixa de luz; parar no meio (modo flex) desfaz a animação em vez de congelar a tela. ⚠️
- [ ] Ao terminar de fechar, o Cristal aparece na tela externa com a chegada em mola ("Assumir a tela externa" ligado).
- [ ] Editor do bloqueio: segurar o bloqueio, autenticar, arrastar o relógio (X, Y), mudar a profundidade (Z) na interna e na externa; salvar.
- [ ] Wallpaper Library: criar, renomear, duplicar e aplicar uma coleção.
- [ ] Dock: altura, tamanho dos ícones, estilo e rótulos; ícones tingidos com o espectro, inclusive em apps sem ícone temático (TikTok); widget de foto e tamanho dos widgets.

## 17. L.I.L.Y AI (1.0.0)

Os itens com ⚠️ dependem dos motores nativos (LiteRT-LM e LiteRT), que não rodam nos testes em JVM:
estão implementados com as APIs oficiais e precisam ser confirmados no Razr.

- [ ] A tela inicial recomenda um modelo com o tamanho e a compatibilidade; **Baixar** pede a notificação e mostra o progresso na notificação e na Liquid Island
- [ ] ⚠️ **Configurações > Testar este aparelho** com os modelos instalados: o relatório traz carregamento, velocidade, memória e temperatura de cada modelo na GPU e na CPU (copie e envie)
- [ ] Pausar, retomar (de onde parou) e cancelar o download; com dados móveis e "só Wi-Fi", a IA pergunta antes
- [ ] ⚠️ Primeira mensagem: "Carregando Qwen3 1.7B" e depois a resposta em pedaços; anote o tempo de carregamento e a velocidade que aparecem na ficha do modelo (Medido neste aparelho)
- [ ] ⚠️ GPU: no perfil Qualidade a conversa roda na GPU; se a GPU recusar o modelo, a IA volta para a CPU sozinha (a pílula do topo mostra GPU ou CPU)
- [ ] ⚠️ Temperatura e memória: 10 minutos de conversa sem travar o launcher; com o aparelho quente, o roteador passa para a CPU e explica
- [ ] Parar a resposta no meio: o que já chegou fica salvo como "Resposta interrompida"
- [ ] Trocar o modelo da conversa (toque na pílula do topo) e ver "Por que este modelo"
- [ ] ⚠️ Foto na conversa com o Gemma 4 E2B: descreve; sem modelo de visão, o ML Kit lista objetos e texto e oferece o Model Hub
- [ ] ⚠️ Áudio de até 30 s (arquivo ou gravação) com o Gemma 4 E2B: transcrição e resumo
- [ ] ⚠️ Documento na conversa: um PDF com texto e um PDF escaneado (o OCR lê as páginas); perguntar sobre ele e fazer uma pergunta de acompanhamento
- [ ] Ditado e modo de voz: perguntar falando e ouvir a resposta (Configurações > Voz)
- [ ] "Guardar na memória" numa mensagem; uma senha ou CPF é recusado; desligar a memória
- [ ] Mensagem com compromisso ("vou ao show às 7 da noite"): aparece "Compromisso identificado" e o Cristal abre a confirmação
- [ ] ⚠️ Ferramentas de imagem: extrair texto, objetos, remover fundo, remover ruído, ampliar 4× (Real-ESRGAN) com antes e depois; salvar vai para Pictures/L.I.L.Y
- [ ] ⚠️ Gerar imagem com o iris-3b: tempo total, memória livre antes e depois; parar no meio; a imagem aparece na Gallery
- [ ] Tela externa: falar com o orbe; abrir o aparelho e ver a mesma conversa
- [ ] Apagar todas as conversas e memórias em Configurações > Privacidade

## 18. L.I.L.Y Gallery (1.0.0)

- [ ] Permitir todas as fotos ou só algumas; no acesso parcial, "Escolher mais" mostra o seletor
- [ ] A grade por mês rola sem travar com milhares de fotos; vídeos mostram a duração
- [ ] Álbuns: "Criadas pela L.I.L.Y" aparece primeiro depois de salvar algo na IA
- [ ] Visualizador: deslizar, zoom com pinça e toque duplo, vídeo toca
- [ ] Editor: ajustes, filtros, corte 1:1, girar; **Salvar como cópia** cria a foto ao lado da original; **Substituir a original** pede a confirmação do Android
- [ ] Lixeira: mover, restaurar e apagar para sempre (com a confirmação do Android)
- [ ] Importar pelo seletor: com o Google Fotos como app de mídia na nuvem, fotos da nuvem aparecem e são copiadas para Pictures/Importadas
- [ ] Botão ✦ da L.I.L.Y numa foto: cada ação abre a L.I.L.Y AI já com a foto

## 19. L.I.L.Y no teclado (1.3.0)

- [ ] O botão ✦ aparece no começo da barra depois de atualizar
- [ ] Corrigir "vc viu q a prova mudou?" no estilo informal: "vc" continua "vc"
- [ ] Reescrever com um tom, traduzir, resumir e escrever; **Substituir** troca só o texto usado; **Inserir** acrescenta
- [ ] Responder: copiar uma mensagem recebida e tocar em Responder
- [ ] Campo de senha e campo de código: o painel diz que nada vai para a IA
- [ ] Sem a L.I.L.Y AI instalada, o painel explica e não mostra ações

## 20. L.I.L.Y no Cristal (Cristal 1.7.1 e L.I.L.Y AI 1.1.1)

- [ ] Com "Mostrar sempre" ligado, segurar a ilha parada abre a lente da L.I.L.Y logo abaixo dela, já ouvindo
- [ ] Segurar a ilha com música ou uma atividade continua expandindo (não chama a L.I.L.Y)
- [ ] ⚠️ Perguntar por voz com um modelo instalado: a resposta aparece na lente e é lida em voz alta
- [ ] Fechar a lente no meio da resposta: a ilha mostra "L.I.L.Y respondendo" com o orbe; no fim, chega o aviso "L.I.L.Y respondeu"
- [ ] Baixar um modelo e gerar uma imagem: a ilha mostra o orbe e a porcentagem
- [ ] Widget L.I.L.Y na tela interna e na externa: tocar abre a conversa; o microfone abre a voz na mesma tela
- [ ] Busca da gaveta: digitar e tocar em "Perguntar à L.I.L.Y" abre a conversa já respondendo
- [ ] Módulo L.I.L.Y na Central de Controle (no launcher e por cima de outro app)
- [ ] Sem a L.I.L.Y AI instalada: o widget diz "Instale a L.I.L.Y AI" e nada abre
- [ ] ⚠️ Escolher a L.I.L.Y AI como assistente digital do Android e chamá-la pelo gesto do aparelho

## 21. Correções da L.I.L.Y AI 1.1.1, do Cristal 1.7.1 e do teclado 1.3.1

- [ ] A L.I.L.Y AI abre pelo ícone na tela interna e continua aberta
- [ ] ⚠️ Na tela externa, com o Shizuku ativo: widget, busca e Central abrem a L.I.L.Y e ela fica na tela
- [ ] ⚠️ Na tela externa, sem o Shizuku e sem a L.I.L.Y AI no painel Apps da Motorola: o Cristal mostra "L.I.L.Y AI não abriu nesta tela" com "Por quê?"
- [ ] No teclado, "Traduzir": baixa o modelo de tradução (com Wi-Fi) e traduz
- [ ] Ferramentas de imagem: "Ler texto" e "O que tem na foto" funcionam no APK final
- [ ] Se a L.I.L.Y fechar por um erro, aparece "A L.I.L.Y AI fechou por um erro" e "Copiar relatório" copia o texto
- [ ] O relatório do Teste do aparelho traz o último erro no fim (quando houver)
- [ ] No teclado (1.3.1), se a L.I.L.Y AI não subir, o painel avisa "A L.I.L.Y AI não respondeu" em até 10 s, em vez de ficar carregando
- [ ] Depois de uma resposta do teclado, fechar a L.I.L.Y AI (ou o Android encerrá-la) não troca o resultado por um erro

## 22. Cristal 1.8.0 e L.I.L.Y AI 1.2.0

L.I.L.Y AI (prioridade):

- [ ] A L.I.L.Y AI abre pelo ícone na tela interna, sem nenhum modelo instalado, e continua aberta
- [ ] O ícone **L.I.L.Y Diagnóstico** abre e mostra Situação, Módulos, Como o app terminou, Erros guardados e Registro
- [ ] No Diagnóstico: Copiar relatório, Compartilhar e Salvar em Downloads funcionam
- [ ] Abrir em modo seguro: a L.I.L.Y abre com o aviso "Modo seguro"; Sair do modo seguro volta ao normal
- [ ] Depois de uma abertura normal, o Registro mostra "✓ Interface pronta (principal)"
- [ ] ⚠️ Se a L.I.L.Y fechar sozinha: o Diagnóstico mostra o motivo em "Como o app terminou" (mande o relatório)
- [ ] O teclado continua respondendo pela L.I.L.Y (Traduzir, Corrigir) e a ilha continua chamando a voz

Transição de dobra (tela inicial do Cristal):

- [ ] Abrir o Razr devagar com o Cristal na tela externa: a tela interna acende desfocada perto da dobra e fica nítida até ~165°
- [ ] Fechar devagar: o desfoque começa perto da dobra e cresce até a tela apagar; reabrir no meio volta a ficar nítido
- [ ] Parar no meio (modo flex): em menos de meio segundo tudo fica nítido
- [ ] O papel de parede assenta antes dos ícones e widgets; a linha de luz aparece na dobra e some
- [ ] Com "Remover animações" ligado no Android: sem efeito nenhum
- [ ] Na tela externa, começar a abrir: a tela se desfaz em direção à dobradiça antes de apagar
- [ ] ⚠️ Por cima de outro app, só o fechamento tem efeito (com a acessibilidade e o Flip Transition ligados)
- [ ] Rolagem da tela inicial continua fluida (sem efeito quando a tela está assentada)

Widgets:

- [ ] Galeria de widgets: cada item mostra o widget de verdade, inteiro, com os dados de agora
- [ ] Cada widget em faixa, pequeno, médio e grande (Redimensionar) reorganiza o conteúdo sem cortar texto
- [ ] Configurações > Widgets: Liquid Glass, Claro, Escuro, Colorido e Monocromático mudam todos os widgets na hora
- [ ] Transparência, desfoque, destaque, cantos e densidade mudam a prévia e a tela inicial
- [ ] Na tela externa: relógio, clima, atalhos e L.I.L.Y legíveis e tocáveis
- [ ] Música: tocar/pausar, anterior e próxima controlam o player ativo
- [ ] Info Brief: mostra o próximo lembrete; tocar abre o Now Brief
- [ ] L.I.L.Y: tocar na sugestão pergunta à L.I.L.Y; o microfone abre a voz
- [ ] Sem permissão de agenda ou sem cidade do clima: o widget pede a permissão ou a cidade (sem dados de mentira)
- [ ] Salvar uma coleção e aplicar outra: a aparência dos widgets acompanha a coleção

Relógio colorido:

- [ ] Editor do bloqueio > Preenchimento: Sólido, Gradiente, Translúcido e Liquid Glass mudam a prévia na hora
- [ ] "Horas e minutos com cores próprias" pinta as horas com a primeira cor e os minutos com a segunda
- [ ] "Data na cor do relógio" acompanha a cor escolhida
- [ ] Original continua igual ao relógio de vidro de antes
- [ ] Tela interna e tela externa guardam escolhas separadas; a coleção guarda as duas
