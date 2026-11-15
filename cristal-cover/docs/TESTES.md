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
