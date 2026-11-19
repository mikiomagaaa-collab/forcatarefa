# Cristal Cover

Launcher e tela de bloqueio em **vidro líquido** para o Motorola Razr 40 Ultra (Android 16), na tela
externa e na interna, e três apps irmãos (instalados separados): o **L.I.L.Y Keyboard** (pasta
`keyboard/`), a **L.I.L.Y AI**, assistente que roda no aparelho e, se você quiser, usa um serviço online
seu para o Codex, o Video AI e modelos maiores (pasta `ai/`), e a **L.I.L.Y Gallery**, galeria e editor
de fotos (pasta `gallery/`). Kotlin + Jetpack Compose. Junto vêm o **L.I.L.Y Gateway** (o serviço
online, pasta `gateway/`) e a **L.I.L.Y Web**, a versão para o computador em HTML e JavaScript (pasta
`web/`).

> **Este projeto é independente do site que divide o repositório com ele.** Tudo o que é do app está
> dentro de `cristal-cover/`; o workflow `build.yml` só roda quando algo nessa pasta muda e não publica
> nada no GitHub Pages. A L.I.L.Y Web é publicada no gateway (Cloudflare), nunca no GitHub Pages.

- Guia de instalação (só com o celular): [docs/GUIA.md](docs/GUIA.md)
- Checklist de testes no aparelho: [docs/TESTES.md](docs/TESTES.md)
- Serviço online (hospedagem, chaves e custos): [docs/GATEWAY.md](docs/GATEWAY.md)
- Planejamento e compatibilidade com outros celulares: [docs/ROADMAP.md](docs/ROADMAP.md)
- Termos de uso, privacidade e créditos: [legal/](legal/); licença: [LICENSE](LICENSE) (Apache 2.0) e
  [NOTICE](NOTICE); [segurança](SEGURANCA.md); [como contribuir](CONTRIBUINDO.md)

## Roteiro "Liquid Flip"

O projeto evoluiu de "launcher da tela externa" para três experiências que compartilham identidade
(tela interna, tela externa e bloqueio). O que já existe e o que ainda não foi começado:

| Parte | Estado |
| --- | --- |
| Vidro Liquid Glass (AGSL), tipografia, 12 papéis de parede | Feito |
| Tela interna e externa com telas iniciais, grades e docks independentes | Feito |
| Wallpaper Studio: 4 áreas independentes, enquadramento, filtros e "igual a outra área" | Feito |
| Cover Clock: bloqueio minimalista da tela externa | Feito |
| Relógio: 12 fontes (10 embutidas, licença OFL) e formato adaptativo (cresce, estica, empilha; com profundidade passa por trás do objeto) | Feito (1.3.0) |
| Flip Transition: em **qualquer app**, pela dobradiça a tela vira um cartão que encolhe em direção à dobra com a faixa de luz da ilha (desfaz no modo flex); a tela inicial chega com mola | Feito (1.6.0) |
| Widgets (9 embutidos + de outros apps), gaveta | Feito |
| Liquid Control Center: 29 tipos de módulo, 6 tamanhos, páginas, editor, predefinições Liquid Flip/iOS/One UI, centrais separadas por tela | Feito (1.3.0) |
| Central de Controle em **qualquer app**: puxar do canto superior direito nas duas telas, acompanhando o dedo, com o vidro refletindo o app de baixo | Feito (1.6.0) |
| Controles reais: brilho, rotação, Não perturbe, avião, localização, NFC, modo escuro, economia de bateria | Feito (API do Android, Shizuku ou ajuste do sistema) |
| Depth Effect: objeto da foto recortado no aparelho (ML Kit) na frente do relógio, no bloqueio interno e no Cover Clock | Feito (1.3.0) |
| Papel de parede espacial (move com a inclinação; presets com camadas de profundidade) | Feito (1.3.0) |
| L.I.L.Y Keyboard (teclado, L.I.L.Y Correct, deslize, emojis, área de transferência, voz, Keyboard Studio) | Feito (1.2.0: Info Brief; 1.3.0: painel L.I.L.Y com a IA do aparelho) |
| Liquid Island: ilha dinâmica em volta da câmera, por cima de qualquer app (música, chamadas, timers, carregando, silencioso, notificações, música expandida) | Feito (1.4.0) |
| Bloqueio no estilo iOS 26: relógio de vidro esticado, data com clima, widgets redondos, notificações embaixo | Feito (1.4.0) |
| Central de Controle no estilo iOS 26 (botões ativos brancos, status, desligar) e fonte Inter | Feito (1.4.0) |
| Design System Liquid Flip: tokens (cores, tipografia Inter, espaço de 4 dp, cantos concêntricos, molas), 6 materiais configuráveis, 7 temas e componentes base, com página de prévia | Fase A feita (1.5.0); as telas adotam o sistema fase a fase |
| Editor do bloqueio: segurar o bloqueio, autenticar e arrastar o relógio (X, Y e profundidade Z), interna e externa separadas | Feito (1.6.0) |
| Wallpaper Library: coleções com papéis de parede, relógio, posição, fontes, cores e efeitos; criar, renomear, duplicar, aplicar | Feito (1.6.0) |
| Liberdade na tela inicial: dock maior e configurável (altura, ícones, estilo, rótulos), ícones monocromáticos e tingidos com espectro de cor (forçado também em apps sem ícone temático), widget de foto, tamanho dos widgets | Feito (1.6.0) |
| Notificações duplicadas (aviso do sistema + ilha): guia por app e desligar de uma vez com o Shizuku | Feito (1.6.0) |
| Liquid Island 2: lente expandida (uma gota que se solta da cápsula e fica abaixo da barra de status, sem cobrir ícones), espaço livre entre os ícones, modo de precisão opcional, estilos Nativa e Liquid Glass | Feito (1.6.0) |
| L.I.L.Y Live Integration: Live Updates do Android 16 e perfis de apps de entrega, corrida e navegação, sem inventar dados | Feito (1.6.0) |
| Info Brief: o teclado reconhece compromissos no aparelho, a esfera voa até a ilha, lembrete com confirmação (alarme exato, repetir, alarme sonoro) | Feito (1.6.0) |
| Now Brief no lugar do painel Hoje: atividades em andamento, linha do tempo da agenda e dos lembretes, tarefas e sugestões | Feito (1.6.0) |
| L.I.L.Y AI: app próprio com conversa, roteador de modelos, Model Hub, ferramentas de texto, imagem e áudio, geração de imagem experimental, memória e versão de voz para a tela externa | Feito (1.0.0); a inferência ainda precisa ser medida no Razr |
| L.I.L.Y Gallery: biblioteca do aparelho, álbuns, visualizador, editor, lixeira oficial, importação pelo seletor do Android (inclui o Google Fotos) e ações da L.I.L.Y | Feito (1.0.0) |
| L.I.L.Y no Cristal: segurar a Liquid Island chama a L.I.L.Y por voz (uma lente logo abaixo da ilha); respostas, downloads de modelos e geração de imagem aparecem na ilha como Live Updates com o orbe; widget L.I.L.Y; "Perguntar à L.I.L.Y" na busca da gaveta; módulo L.I.L.Y na Central de Controle | Feito (Cristal 1.7.0 e L.I.L.Y AI 1.1.0) |
| Correções: ML Kit de volta no APK final da IA, relatório de erro no aparelho, L.I.L.Y liberada na tela externa antes de abrir, ponte do teclado com prazo (nunca fica esperando para sempre) | Feito (L.I.L.Y AI 1.1.1, Cristal 1.7.1, teclado 1.3.1) |
| L.I.L.Y AI que abre sempre: núcleo em módulos (os opcionais caem num substituto em vez de derrubar o app), interface antes dos modelos, modo seguro depois de duas aberturas com falha e o app **L.I.L.Y Diagnóstico** (ícone próprio, processo separado) com o registro da partida, os erros guardados e o histórico de saídas do Android (falhas Java, nativas e ANR) para copiar, compartilhar ou salvar | Feito (L.I.L.Y AI 1.2.0) |
| Correção da L.I.L.Y fechando ao abrir: o Diagnóstico mostrou, no Razr, que o WorkManager (biblioteca que vem com o LiteRT) quebrava na partida porque o R8 do AGP 9 apagava o construtor do banco interno dele. O modo estrito das regras foi desligado, as regras ficaram explícitas e o WorkManager só inicia sob demanda | Feito (L.I.L.Y AI 1.2.1) |
| Transição de dobra inspirada no iPhone Duo: desfoque regional que segue o ângulo da dobradiça, gradiente de nitidez a partir da dobra, luminosidade, papel de parede e componentes em ritmos próprios, linha de luz na dobra; respeita "remover animações" | Feito no launcher (Cristal 1.8.0); por cima de outros apps só o fechamento (limite do Android) |
| Widgets redesenhados no estilo Liquid Glass: relógio, relógio de ponteiros, data, agenda, clima, bateria, música, foto, L.I.L.Y, Info Brief, atalhos e busca, cada um reorganizado em faixa, pequeno, médio, grande e extra grande; composições próprias da tela externa; galeria com prévias reais; página Configurações > Widgets (Liquid Glass, claro, escuro, colorido, monocromático, transparência, desfoque, destaque, cantos e densidade), guardada nas coleções | Feito (Cristal 1.8.0) |
| Relógio do bloqueio com preenchimento interno: sólido, gradiente, translúcido ou Liquid Glass colorido, segunda cor, horas e minutos com cores próprias, intensidade e data na cor do relógio, com prévia ao vivo, nas telas interna e externa e nas coleções | Feito (Cristal 1.8.0) |
| L.I.L.Y Codex: agente de programação com catálogo de modelos lido ao vivo, roteador por tipo de tarefa (correção rápida, refatoração, arquitetura, depuração, revisão final por um modelo independente), leitura e edição de projetos autorizados (pasta do aparelho ou repositório do GitHub), diff, aprovação, desfazer, pontos de restauração, pesquisa na internet com fontes, testes no GitHub Actions e auto-reparo de até 3 tentativas que para quando o mesmo erro volta | Feito (L.I.L.Y AI 1.3.0); precisa do serviço online |
| Video AI: texto para vídeo, imagem para vídeo, animar foto, continuar um vídeo, movimento de câmera, formato, duração e resolução, preço antes de gerar, progresso na Liquid Island, histórico e Movies/L.I.L.Y | Feito (L.I.L.Y AI 1.3.0); precisa do serviço online (fal.ai) |
| L.I.L.Y Avatar: personagem original desenhada no app, 7 estados (parada, ouvindo, pensando, falando, trabalhando, sucesso, erro) ligados a todos os módulos, aparência, cores, estilo, tamanho, posição, transparência, animações e modo flutuante opcional (também na tela externa) | Feito (L.I.L.Y AI 1.3.0) |
| L.I.L.Y Gateway: serviço online na sua conta Cloudflare, sem chaves no APK, chave de acesso própria, limite de gasto por dia | Feito; falta você publicar com as suas chaves ([docs/GATEWAY.md](docs/GATEWAY.md)) |
| L.I.L.Y Web: a L.I.L.Y no computador (conversa, Codex, vídeo, ferramentas, avatar, memória, modelos locais no navegador com WebGPU) | Feito; publicada junto com o gateway |
| Termos de uso, privacidade (LGPD), créditos, licença Apache 2.0, política de segurança e guia de contribuição | Feito; falta o seu nome e contato e, de preferência, uma revisão jurídica |
| Compatibilidade com outros celulares (Motorola, Samsung, Xiaomi e outros) | Planejado com dados medidos ([docs/ROADMAP.md](docs/ROADMAP.md)) |
| Biblioteca de apps, páginas próprias da tela externa | Planejado |

## Estado real do projeto

O que **foi verificado** durante o desenvolvimento:

- compila do zero (debug e release com R8) com as versões estáveis atuais das ferramentas;
- 312 testes automáticos em JVM passam (124 no Cristal, 45 no teclado, 16 no módulo compartilhado, 119 na
  L.I.L.Y AI e 8 na Gallery), mais 23 do gateway, 9 da L.I.L.Y Web e o teste de ponta a ponta da Web
  num Chromium. No launcher: lógica da grade, detecção da tela externa,
  regras de compatibilidade, segurança dos comandos de shell, backup, papéis de parede por área,
  encaixe e edição da Central de Controle, **compilação real do shader AGSL** e **16 telas montadas e
  desenhadas** pelo pipeline de hardware (incluindo a central e o efeito de profundidade), no tamanho
  da tela externa (422×426 dp) e da interna (411×1004 dp). No teclado: o corretor com o dicionário
  real, a digitação por deslize com traços simulados, o controlador de texto num campo de mentira
  (correção, desfazer, maiúsculas, pontuação, senha) e 6 telas desenhadas. No Design System: escala
  tipográfica, molas, cantos, contraste dos 7 temas, materiais, leitura das preferências e a página de
  prévia desenhada em três temas. Na 1.6.0: o editor do bloqueio, as coleções, o dock, os ícones
  forçados, o widget de foto, a lente da ilha (um teste confere que nenhum texto ou botão sai da curva),
  a integração com apps, o reconhecedor do Info Brief com os exemplos do pedido e os casos que não podem
  virar sugestão (hipóteses, perguntas, negações, passado), os gatilhos do teclado, o contrato da ponte,
  os lembretes (aviso único, repetição, adiar, gravação), o Now Brief, a folha de confirmação e a
  transição de fechamento, com 55 telas desenhadas. Na L.I.L.Y AI: o roteador (perfis, bateria,
  temperatura, memória livre, escolha manual, nunca um modelo não instalado), a compatibilidade com o
  aparelho, as fichas do catálogo (versões fixadas, tamanhos e SHA-256), a loja de modelos, o download com
  retomada por Range, servidor que ignora o Range, pausa e recusa de arquivo adulterado, a memória
  (recusa senhas, CPF, cartão e códigos), o histórico (busca sem acento, anexos apagados juntos), o áudio
  (WAV e reamostragem), os filtros clássicos, documentos, o markdown, a costura dos blocos da ampliação
  (sem emendas, em vários tamanhos) e 12 telas desenhadas (incluindo a tela externa). O fluxo inteiro da
  conversa (pedaços, gravação, histórico sem erros, parar, memória ligada e desligada, documentos) e a
  ponte com o teclado rodam com um motor de teste no lugar do LiteRT-LM (só nos testes). Fora dos
  testes: as 32 URLs do catálogo foram conferidas no Hugging Face (todas respondem, com o tamanho e o
  SHA-256 do catálogo) e os formatos do Real-ESRGAN foram lidos do próprio arquivo (entrada
  [1,128,128,3] e saída [1,3,512,512], FLOAT32), os mesmos que o app usa. No teclado, o painel da L.I.L.Y e as regras de campos protegidos. Na Gallery, o editor
  (corte, giro e cores de verdade) e 7 telas;
- o APK de release da L.I.L.Y AI foi conferido por dentro. Na 1.1.0, o R8 (no modo estrito do AGP 9)
  apagava o construtor dos nove "registrars" do ML Kit, e o texto das fotos, os rótulos, o idioma, a
  tradução e o recorte não funcionavam no APK final. Na 1.1.1 os construtores estão lá (conferido no dex), e
  o código encolhido do release, refeito com o mesmo R8 e as mesmas regras, abre no Robolectric: tela
  inicial, tela externa, painel de voz, ponte do teclado e o ML Kit iniciando com os componentes. As
  atividades também passaram a ser abertas de verdade nos testes (antes só as telas eram montadas);
- na L.I.L.Y AI 1.2.0: a partida com módulos opcionais quebrados (motor, memória e geração de imagem
  falhando de propósito) abre a tela principal; a falha de um módulo obrigatório leva ao Diagnóstico em vez
  de fechar; um erro numa linha de fundo fica gravado com a pilha e o módulo provável; duas partidas com
  falha ligam o modo seguro e uma saída pedida pelo usuário não conta; o histórico de saídas lê lápides de
  erros nativos e a pilha principal de um ANR; o Diagnóstico copia e compartilha o relatório. O release
  encolhido pelo R8 foi aberto de novo no Robolectric (tela principal sem modelos, assistente, ponte e ML Kit);
- na L.I.L.Y AI 1.2.1: o erro do Razr ("Failed to create an instance of class androidx.work.impl.WorkDatabase")
  foi reproduzido no código encolhido da 1.2.0, instalando os provedores do manifesto como o Android faz ao
  abrir o processo. Na 1.2.1 os provedores iniciam, o banco do WorkManager é criado e as telas abrem (6 de 6),
  também com o modo estrito ligado (só com as regras novas). No APK, o construtor que faltava está no dex;
- no Cristal 1.8.0: o modelo da transição de dobra (abrindo, fechando, reabrindo no meio, parada no meio,
  tela externa, movimento reduzido), a nitidez medida nos pixels desenhados pela GPU (borda nítida e dobra
  desfocada no meio da transição, tudo nítido no fim), os tamanhos e as cinco aparências dos widgets, a
  galeria com prévias reais, as cores do relógio medidas nos pixels (horas e minutos em cores próprias) e a
  aparência dos widgets e do relógio guardada nas coleções;
- na L.I.L.Y AI 1.3.0, contra um gateway de teste local que fala o protocolo real (HTTP de verdade,
  pedaços em SSE, fila de vídeo, GitHub e CI simulados): a chave só vai por https; a conversa online sai
  marcada com o modelo e o custo; o roteador só usa modelo online com o consentimento e nunca para imagem
  ou áudio; o vídeo vai do pedido ao arquivo acompanhando a fila; os verificadores de Kotlin, Java, XML,
  JSON, YAML e outros pegam erros reais sem reclamar de código certo; os erros do compilador viram arquivo
  e linha; o agente lê, edita e confere **sem gravar antes da aprovação**; descartar não grava nada;
  aprovar cria o ponto de restauração e desfazer volta o arquivo; o modo automático só aplica correções
  pequenas; o auto-reparo num repositório falha no CI, lê o log, corrige, roda de novo e confirma, e
  **para quando o mesmo erro volta**; a presença do avatar segue a prioridade dos módulos; 8 telas novas
  desenhadas (avatar em todos os estados, estilos e cores, serviço online, vídeo, Codex). No gateway: a
  chave errada é recusada, o catálogo pega a versão mais nova de cada família, o limite diário recusa
  antes de gastar, o identificador do vídeo não consegue mandar a chave do fal.ai para outro servidor,
  só os repositórios liberados são tocados e nunca o ramo principal. A L.I.L.Y Web foi aberta num Chromium
  contra o código do gateway: configuração, conversa com custo, Codex até o diff, vídeo, termos e tela
  estreita, sem erros na página;
- lint sem erros.

O que **não foi verificado**: os apps **nunca rodaram em um Razr de verdade**. Na L.I.L.Y AI, os
motores nativos (LiteRT-LM e LiteRT, bibliotecas do Google) não rodam nos testes em JVM: a conversa,
a visão, o áudio, a ampliação com Real-ESRGAN e a geração de imagem estão **implementados com as APIs
oficiais, mas faltam testar no aparelho** (velocidade, memória, temperatura e a GPU do Snapdragon). Tudo o que depende de
comportamento não documentado da Motorola está marcado com ⚠️ no [checklist](docs/TESTES.md) e precisa
ser confirmado no aparelho. O gateway **nunca foi publicado nem chamou os provedores de verdade**
(OpenRouter, fal.ai, Tavily, Brave, GitHub): falta você criar as contas e as chaves
([docs/GATEWAY.md](docs/GATEWAY.md)); os preços mostrados vêm dos provedores (ao vivo no catálogo de
conversa, das páginas do fal.ai nos vídeos) e podem mudar.

## L.I.L.Y Keyboard (pasta `keyboard/`)

App próprio (`app.lily.keyboard`), instalado e atualizado separado do launcher. Não tem permissão de
internet. Ativa-se pelo mecanismo oficial (Configurações > Teclados); nada é trocado sem você.

| Parte | Como funciona |
| --- | --- |
| Teclas | Um só Canvas com vidro desenhado (reflexo, borda clara, sombra), vários dedos ao mesmo tempo, prévia da tecla, toque longo com acentos e números |
| Layouts | QWERTY PT-BR e inglês, símbolos (2 páginas), números e telefone, teclas de apoio em e-mail/endereço, fileira de números e Ç opcionais |
| L.I.L.Y Correct | Dicionário de frequências (wordfreq, 90 mil palavras PT, 60 mil EN), distância entre teclas vizinhas, acentos esquecidos, contexto da palavra anterior ("não esta" → "não está"), estilos formal/natural/informal, dicionário pessoal; só troca quando é claro, e apagar logo depois desfaz |
| Gestos | Digitação por deslize (decodificador próprio), cursor arrastando o espaço, apagar palavras deslizando o apagar |
| Painéis | Emojis com busca em português, recentes e favoritos; área de transferência (1 hora, fixados, sem senhas e códigos); edição de texto; ditado por voz no aparelho |
| Ergonomia | 3 tamanhos + altura fina, margens, subir o teclado, modo de uma mão (lado e largura), última fileira maior; compacto sozinho na tela externa |
| Keyboard Studio | 11 temas (Liquid Glass, One UI, Minimalista, AMOLED, Clássico, Lavanda, Violeta, Prata, Aurora…), editor de cores, vidro, transparência, cantos, espaçamento, fundo da galeria, prévia antes de aplicar, exportar/importar |
| Info Brief | Reconhece no aparelho horários, datas, tarefas, lugares, rotinas e viagens (PT-BR e EN) 1,5 s depois de você parar de digitar e ao enviar; a expressão brilha em azul, vira uma esfera e voa até a Liquid Island. Nunca em senha, código, e-mail, endereço web ou modo anônimo; frases hipotéticas, perguntas e passado não viram sugestão. Ao Cristal vai só o compromisso entendido, por uma permissão de assinatura (os dois apps precisam da mesma chave). Sem o Cristal, "Criar lembrete" abre a agenda do aparelho |

| L.I.L.Y (1.3.0) | Botão na barra: Perguntar, Corrigir, Reescrever (5 tons), Traduzir (6 idiomas), Resumir, Responder (à mensagem copiada) e Escrever, sobre o texto selecionado ou antes do cursor. A resposta aparece numa prévia; só muda o campo com "Substituir" ou "Inserir". No estilo informal, "vc", "pq", "tbm" e "nn" ficam. Nada vai para a IA sem um toque; senha, números e códigos nunca vão; textos copiados que o Android marca como sensíveis também não. Precisa da L.I.L.Y AI instalada com a mesma assinatura |

Não tem (de propósito): GIFs (exigem internet). O próprio teclado continua sem permissão de internet:
a L.I.L.Y roda em outro app do aparelho.

## L.I.L.Y AI (pasta `ai/`)

App próprio (`app.lily.ai`). **Por padrão tudo roda no aparelho**: a internet só é usada para baixar
modelos (do Hugging Face, fixados numa versão e conferidos por SHA-256) e pacotes de tradução do ML Kit,
quando você pede. Desde a 1.3.0 há um **serviço online opcional e seu** (o L.I.L.Y Gateway): só é usado
depois que você o configura e liga "Permitir processamento externo", e cada resposta diz onde rodou
("No aparelho" ou "modelo · online", com o custo).

| Parte | Como funciona |
| --- | --- |
| Conversa | Respostas em pedaços (streaming), parar, gerar de novo, copiar, ler em voz alta, compartilhar, markdown (listas, títulos, código copiável), fotos, áudios e documentos anexados (PDF e texto: o texto embutido do PDF é lido pelo Android e as páginas escaneadas passam pelo OCR, tudo no aparelho), ditado e modo de voz (pergunta falada → resposta lida). Histórico com busca (sem acento), fixadas, projetos, renomear e apagar (anexos juntos) |
| Roteador (AI Router) | Escolhe o mecanismo por tarefa com dados reais: o que está instalado, compatibilidade, memória livre, temperatura, bateria, economia de energia e o perfil (Eco, Equilibrado, Qualidade). GPU com volta automática para a CPU. Escolha manual por conversa. A tela mostra os motivos da escolha |
| Model Hub | Ficha completa (desenvolvedor, arquitetura, parâmetros, quantização, modalidades, tamanho, espaço, memória, contexto, mecanismo, licença, versão, página oficial), classificação real (Compatível, Compatível com limitações, Experimental, Incompatível) calculada com o aparelho, download em primeiro plano com pausar, retomar, cancelar e conferir, atualizar, desinstalar, padrão por categoria, histórico de instalações e o que foi **medido neste aparelho** (carregamento e caracteres por segundo, só depois de usar) |
| Modelos de linguagem (LiteRT-LM) | Qwen3 0.6B e 1.7B, Gemma 4 E2B (texto, imagem e áudio), Ministral 3 3B, Phi-4 mini, SmolVLM2 500M e Qwen3.5 0.8B Vision. Um carregado por vez, descarregado depois de alguns minutos sem uso, quando o Android pede memória ou antes da geração de imagem; recusa com o aparelho crítico de quente |
| Visão | Descrever e perguntar sobre fotos (modelo de visão); sem ele, o ML Kit diz o que reconhece e avisa o que falta. Extrair texto (OCR), identificar objetos e remover fundo (ML Kit) |
| Aprimoramento | Melhorar foto, remover ruído (filtro bilateral) e ampliar 4× com o Real-ESRGAN x4v3 (LiteRT, em blocos, com progresso real e parar); sem o modelo, ampliação clássica 2×. Antes e depois com divisória, salvar em Pictures/L.I.L.Y |
| Geração de imagem | iris-3b Turbo (motor Kotlin oficial do litert-community, Apache 2.0), **experimental**: 768 × 768, minutos por imagem, ~3,2 GB de memória, sem imagem de referência. Serviço em primeiro plano com Live Update (a Liquid Island mostra o progresso), parar, semente, estilos |
| Áudio | Ditado pelo reconhecedor do Android (no aparelho quando o idioma está instalado), transcrição e resumo de áudios com o Gemma 4 E2B, leitura em voz alta (vozes do Android), identificação de idioma (ML Kit) |
| Ferramentas de texto | Corrigir, reescrever (5 tons), resumir, expandir, traduzir (ML Kit ou o modelo no perfil Qualidade), explicar, organizar, escrever e sugerir resposta, com o jeito informal preservado |
| Memória | Só o que você manda guardar ("Guardar na memória" numa mensagem ou na tela Memória); recusa senhas, documentos, cartões e códigos; ligar e desligar cada uma ou todas; histórico de conversas não vira memória |
| L.I.L.Y por voz | Um painel em lente no topo da tela, logo abaixo da Liquid Island, por cima de qualquer app: abre ouvindo, mostra a pergunta, a resposta chegando e o modelo; dá para falar de novo (acompanhamento), digitar, ouvir a resposta e continuar no app. Abre pela ilha, pelo widget e pela Central do Cristal e também como **assistente digital do Android** (Apps padrão). Fechado no meio, a resposta continua: um Live Update mostra "respondendo" na ilha e um aviso traz a resposta |
| Integrações | Teclado (ponte de assinatura), Cristal (voz pela ilha, widget, busca da gaveta e Central de Controle; lembretes pela confirmação do Info Brief, nada é criado sem o toque), Gallery (fotos chegam com permissão só delas), Liquid Island (Live Updates oficiais da voz, dos downloads e da geração, com o orbe da L.I.L.Y) |
| Tela externa | Versão de voz: tocar no orbe, falar e ouvir; ao abrir o aparelho, a mesma conversa continua na tela interna, até com a resposta em andamento |
| Aparência | Orbe em AGSL, fundo aurora, vidro, temas Liquid Glass, AMOLED, Claro, Roxo e Personalizado |
| Relatório de erro | Se a L.I.L.Y fechar por um erro, abre a tela **A L.I.L.Y AI fechou por um erro** (num processo separado, sem depender do resto do app) com a pilha completa e **Copiar relatório**. Fica só no aparelho e também vai no fim do Teste do aparelho |
| Teste do aparelho | Configurações > **Testar este aparelho**: roda cada modelo de conversa instalado na GPU e na CPU, o OCR, a ampliação e a voz, e mede carregamento, velocidade, memória e temperatura. Gera um relatório para copiar e enviar; o que falha aparece como falha |
| Serviço online (1.3.0) | Configurações > **Serviço online**: endereço do seu gateway e a chave de acesso (guardada cifrada pelo Android Keystore), o que o gateway oferece, gasto do dia e limite. Três modos: desligado, só quando não há modelo no aparelho, ou preferir online. Nada sai do aparelho sem o consentimento ligado. No roteador, os modelos online aparecem como opção manual com preço; imagem e áudio continuam só no aparelho |
| L.I.L.Y Codex (1.3.0) | Projeto autorizado (pasta escolhida pelo seletor do Android ou repositório do GitHub liberado no gateway). O agente lê a estrutura e as dependências, procura, lê, cria e edita arquivos em Kotlin, Java, XML, Gradle, JavaScript, TypeScript, HTML, CSS, Python, JSON, YAML e SQL, e pesquisa na internet separando o verificado das hipóteses. As mudanças ficam **preparadas**: diff por arquivo, explicação, riscos, aprovação, ponto de restauração e desfazer. No repositório, grava num ramo `lily-codex/...` e roda os testes no GitHub Actions; se falharem, lê o erro e tenta de novo (até 3 vezes; para se o mesmo erro voltar). Uma revisão final por um modelo de outra empresa. Modo automático opcional só para correções pequenas. "Consertar com o Codex" no Diagnóstico e `/codex` na conversa |
| Video AI (1.3.0) | Texto ou uma imagem do aparelho (seletor de fotos do Android) para vídeo, continuar o último quadro de um vídeo, movimento de câmera, 16:9, 9:16 ou 1:1, duração e resolução, com o preço estimado antes. Modelos: LTX-2, Wan 2.5, Kling 2.5 Turbo, Veo 3.1 Fast e HunyuanVideo (escolha automática por qualidade e custo ou manual). Roda no fal.ai pelo gateway, com progresso na Liquid Island, histórico, prévia, compartilhar e salvar em Movies/L.I.L.Y (aparece na Gallery). `/video` na conversa |
| L.I.L.Y Avatar (1.3.0) | Personagem original desenhada em código (sem imagens de terceiros), com inspiração cyberpunk e roupa fechada. Respira, pisca, olha, move a boca ao falar e reage ao estado de todos os módulos (voz, conversa, Codex, vídeo, imagem). Estilos Holograma, Vidro e Mínimo; busto, rosto ou orbe; cores, tamanho, transparência e intensidade das animações. Modo flutuante opcional (só com a permissão "Sobrepor a outros apps"; desligado por padrão), compacto na tela externa, arrastável, some com um toque longo. Nunca liga câmera nem microfone |
| Termos e créditos | Configurações > Sobre: termos de uso, privacidade e créditos dentro do app, os mesmos arquivos de `legal/` |

Limitações reais da L.I.L.Y AI:

- **Falta testar no aparelho**: velocidade, memória e temperatura de cada modelo no Snapdragon 8+ Gen 1, a
  GPU (OpenCL) com cada modelo e a geração de imagem (os números do iris-3b publicados são de outros
  aparelhos: 128–133 s no Galaxy S26 e 469–504 s no Pixel 8a).
- Arquivos de áudio só são transcritos com um modelo que ouve (Gemma 4 E2B); o reconhecedor do
  Android só trabalha com o microfone.
- O SmolVLM2 responde melhor em inglês. O iris-3b gera só 768 × 768 (recorte na Gallery).
- O APK é grande (as bibliotecas nativas do LiteRT e do ML Kit somam perto de 75 MB); os modelos
  ficam fora dele e somam de 330 MB a 11 GB, conforme o que você instalar.
- O Codex, o Video AI e os modelos online **dependem do seu gateway e de créditos nos provedores**
  (OpenRouter e fal.ai). Nenhuma chamada real a esses provedores foi feita no desenvolvimento: tudo foi
  testado contra simulações com o formato das APIs públicas. O progresso dos vídeos é estimado pelo
  tempo (a fila do fal.ai não informa porcentagem).
- Numa pasta do aparelho, o Codex só confere a sintaxe (o Android não roda Gradle nem Node); os testes de
  verdade rodam quando o projeto é um repositório do GitHub com o CI ligado.
- O avatar flutuante no modo "sempre" pode ser fechado pelo Android para economizar bateria; ele volta
  quando a L.I.L.Y é aberta.

## L.I.L.Y Gateway (pasta `gateway/`)

Cloudflare Worker em JavaScript, sem dependências. As chaves dos provedores ficam só nele; o app e a
L.I.L.Y Web conhecem apenas o endereço e uma chave de acesso sua. Passo a passo, custos e segurança em
[docs/GATEWAY.md](docs/GATEWAY.md).

| Rota | O que faz |
| --- | --- |
| `GET /v1/status` | O que está ligado, gasto do dia e limite |
| `GET /v1/models`, `POST /v1/chat` | Catálogo do OpenRouter lido ao vivo (a versão mais nova de cada família) e conversa em pedaços com o custo real no fim |
| `POST /v1/search` | Pesquisa (Tavily ou Brave) para o Codex |
| `/v1/video/...` | Modelos, orçamento, envio, situação e cancelamento na fila do fal.ai |
| `/v1/repos`, `/v1/repo/...` | Árvore, arquivos, gravação em ramos `lily-codex/...` e testes no GitHub Actions, só nos repositórios liberados |

Proteções: recusa tudo sem a chave de acesso (comparação em tempo constante), limite de gasto por dia,
CORS fechado, identificadores de vídeo conferidos (a chave do fal.ai nunca vai para outro servidor),
nada grava no ramo principal nem em workflows. 23 testes (`node --test test/*.test.js`).

## L.I.L.Y Web (pasta `web/`)

A L.I.L.Y no computador, em HTML, CSS e JavaScript puro (módulos ES, sem etapa de build), com o
visual da L.I.L.Y num layout inspirado em assistentes de conversa: barra lateral com as conversas e a
área principal. Publicada no mesmo endereço do gateway.

| Parte | Como funciona |
| --- | --- |
| Conversa | Pedaços em tempo real, markdown seguro, histórico com busca, memória que você manda guardar, ditado e leitura em voz alta (Web Speech do navegador) |
| Modelos | Online pelo gateway (o mesmo roteador do app) ou **no próprio computador** pela WebLLM 0.2.87 com WebGPU (Qwen3, Gemma, Llama 3.2 e Phi; aparecem só os que existem na lista oficial da biblioteca), baixados só quando você pede |
| Codex | O mesmo agente do app: repositório do GitHub (com CI) ou uma pasta do computador (File System Access, no Chrome e no Edge), diff, aprovação, desfazer e auto-reparo |
| Vídeo | O mesmo Video AI, com preço antes, histórico e download |
| Ferramentas | Corrigir, reescrever, resumir, traduzir, explicar e escrever |
| Avatar | A mesma personagem, desenhada em Canvas, com os mesmos estados |
| Legal | Termos, privacidade e créditos |

O que a Web não tem (só existe no celular): modelos LiteRT, ML Kit (OCR, rótulos, recorte), geração de
imagem iris, Liquid Island, tela externa e avatar flutuante por cima de outros apps. Continuar um vídeo
gerado também fica só no app. Testes: 9 de lógica (`node --test test/*.test.js`) e um de ponta a ponta
num Chromium com o código real do gateway (`node test/e2e.mjs`).

## L.I.L.Y Gallery (pasta `gallery/`)

App próprio (`app.lily.gallery`), sem IA dentro: as ações de IA abrem a L.I.L.Y AI.

| Parte | Como funciona |
| --- | --- |
| Biblioteca | Fotos e vídeos do MediaStore, por mês, com miniaturas do cache oficial do Android; busca por nome, álbum ou mês; acesso total ou só a fotos escolhidas (Android 14+) |
| Álbuns | Pastas do aparelho; "Criadas pela L.I.L.Y" (Pictures/L.I.L.Y) em destaque; favoritas e lixeira |
| Visualizador | Deslizar entre fotos, zoom com pinça e toque duplo, vídeos com o reprodutor do Android, informações (data, tamanho, pasta), compartilhar |
| Editor | Exposição, contraste, saturação, temperatura, matiz, vinheta, "Automático" pelo histograma, 8 filtros, corte com proporções (livre, original, 1:1, 4:5, 9:16, 16:9), girar e espelhar. Salva como cópia ao lado da original ou por cima (com a confirmação do Android) |
| Lixeira e favoritos | Pela API oficial (createTrashRequest, createFavoriteRequest): o Android confirma, e os itens ficam 30 dias na lixeira. Com "gerenciamento de mídia" liberado, sem confirmar a cada vez |
| Google Fotos | Pelos caminhos oficiais: importar pelo seletor do Android (que mostra o Google Fotos como app de mídia na nuvem), enviar fotos ao Google Fotos para o backup e abrir o app |
| L.I.L.Y | Perguntar sobre a foto, descrever, extrair texto, identificar objetos, melhorar, remover ruído, ampliar e remover fundo; gerar imagens. Os resultados voltam ao álbum da L.I.L.Y |

## Arquitetura

MVVM em camadas, um módulo (`app`), injeção com Hilt.

| Camada | Pasta | O que faz |
| --- | --- | --- |
| Núcleo | `core/` | Módulo Hilt, dispatchers, log, `safely { }` |
| Dados | `data/` | Repositórios; cada um esconde um serviço do Android e nunca lança exceção para a UI |
| Serviços | `service/` | NotificationListener, AccessibilityService, controle da tela de bloqueio, boot |
| UI | `ui/` | Compose: vidro, launcher, bloqueio, widgets, configurações, onboarding |

- **Estado**: `StateFlow` nos repositórios e ViewModels; a UI só observa.
- **Persistência**: DataStore (preferências) e Room (tela inicial e ajustes por app).
- **Erros**: toda chamada a serviço do sistema passa por `safely { }` ou `try/catch` e vira um estado
  ("sem permissão", "indisponível") em vez de crash.

### Design System

- `ui/design/LiquidTokens.kt`: paleta, espaço (grade de 4 dp), cantos concêntricos, escala tipográfica
  com a Inter (espaçamento entre letras pela fórmula de métricas dinâmicas da própria Inter) e as
  quatro molas do sistema (rápida, suave, elástica, calma), descritas por amortecimento e resposta.
- `ui/design/LiquidThemes.kt`: Liquid Dark (padrão), Liquid Light, AMOLED, Lavender, Classic iOS,
  One UI e Personalizado (parte de um tema e troca o destaque). Um teste garante contraste mínimo
  de texto em todos.
- `ui/design/LiquidMaterials.kt`: Liquid, Clear, Frosted, Dark, Colored e Sólido. Só o Liquid e o
  Clear usam a refração do shader; os painéis usam desfoque simples, que custa menos.
- `ui/design/LiquidComponents.kt`: interruptor, controle deslizante, seletor segmentado, listas,
  botões e chips. A página Configurações > Aparência > Temas e materiais mostra tudo.

### Liquid Glass

- `ui/glass/GlassBackdrop.kt`: o "fundo" (papel de parede ou a cena inteira) é gravado em uma
  `GraphicsLayer`.
- `ui/glass/LiquidGlass.kt`: cada elemento redesenha essa camada dentro de si, deslocada para a própria
  posição, e aplica um `RenderEffect` encadeado: **blur** e depois o **shader AGSL**.
- `ui/glass/GlassShaders.kt`: o shader recorta o retângulo arredondado por SDF, **refrata** a imagem
  perto da borda (perfil de lente), separa R/G/B (**dispersão**), desenha o **brilho especular** na
  borda conforme a direção da luz e reage ao **toque** com uma pequena lente sob o dedo.
- O vidro é **claro**, como no iOS 26/27: pouco desfoque e pouca tinta; o destaque vem da lente, do fio de
  luz e da faixa escurecida na borda. Um controle "mais transparente ↔ mais fosco" ajusta isso, e a cor do
  texto acompanha o papel de parede (claro ou escuro).
- Três níveis: **Líquido** (blur + shader), **Blur** e **Leve** (sem camada extra). Se o shader não
  compilar em algum aparelho, o app cai sozinho para Blur.
- `ui/launcher/PerformanceGovernor.kt` mede o tempo real dos quadros: se passarem de 60 fps de
  orçamento, reduz a qualidade; se não couberem em 144 Hz, deixa de pedir a taxa máxima.
- Tipografia no espírito do Liquid Glass da Apple, com a fonte do sistema (a San Francisco não pode ser
  distribuída): pesos firmes, espaçamento apertado em tamanhos grandes, numerais de largura fixa.

### Tela externa

- `data/display/CoverDisplayRepository.kt` detecta a tela externa pelo formato (pequena e quase
  quadrada; no Razr é a tela 1) e acompanha o sensor da dobradiça.
- `data/launch/AppLauncher.kt` abre apps com `LauncherApps.startMainActivity` +
  `ActivityOptions.setLaunchDisplayId`. Com Shizuku, antes de abrir pede ao serviço `climanager` da
  Motorola para liberar o pacote, e cai para `am start --display` se o caminho normal falhar.
- `data/launch/CompatAdvisor.kt` avisa antes de abrir apps que tendem a não caber.
- `ui/launcher/LauncherState.kt` (`rememberSafeLayout`) lê o recorte das câmeras e, se houver uma faixa
  livre ao lado das lentes, põe o dock nela.

### Tela de bloqueio

Dois modos, e em nenhum deles o bloqueio do Android é removido:

1. **Activity** (`ui/lock/LockActivity.kt`, recomendado): `setShowWhenLocked(true)`, o mesmo mecanismo
   de alarmes e chamadas. Destravar chama `KeyguardManager.requestDismissKeyguard`, e quem pede
   PIN/padrão/digital é o sistema.
2. **Sobreposição** (`service/LockOverlay.kt`, experimental): janela `TYPE_ACCESSIBILITY_OVERLAY` sobre
   o keyguard, para o caso de a Motorola não deixar a activity aparecer na tela externa.

## Limitações reais (Android 16 e Motorola)

- **Central em qualquer app:** a faixa invisível do canto superior direito fica com os toques daquele
  canto da barra de status (puxe a cortina do sistema pelo resto da barra). Ela some com a tela
  bloqueada, em tela cheia e deitado. Apps com conteúdo protegido (banco, DRM) aparecem pretos na
  captura do vidro e da transição de fechamento: é o próprio Android que esconde.
- **Transição de fechamento** depende do sensor da dobradiça; sem ele, só a tela inicial anima.
- **Info Brief:** o destaque azul no campo usa o texto em composição do Android; em apps com editor
  próprio pode aparecer só como sublinhado. O gatilho "ao enviar" vale quando o app limpa o campo ou
  quando você envia pela tecla do teclado. O aviso no minuto exato precisa de "Alarmes e lembretes".

- **Não dá para substituir a tela de bloqueio.** O Cristal desenha por cima. Com uma tela sobre o
  bloqueio, o leitor de digital lateral só é ativado depois que o usuário desliza para destravar.
- **Abrir a tela de bloqueio com o aparelho apagado exige o serviço de acessibilidade.** O Android
  bloqueia activities iniciadas em segundo plano; serviços de acessibilidade são uma das exceções.
- **O gesto de início na tela externa sempre vai para o launcher da Motorola.** O Cristal declara
  `SECONDARY_HOME`, mas a Motorola fixa o dela. "Assumir a tela externa" reabre o Cristal quando o
  launcher da Motorola aparece; não é uma substituição de verdade.
- **A Motorola decide quais apps aparecem na tela externa.** A liberação por `cmd climanager` foi
  documentada por terceiros no Razr+ 2024; no 40 Ultra com Android 16 os comandos podem ter outro nome
  ou não existir. Nesse caso o comando falha em silêncio e vale a liberação manual nas Configurações.
- **O vidro refrata o papel de parede do próprio Cristal.** Apps comuns não podem ler o papel de parede
  do sistema.
- **Wi-Fi, Bluetooth e dados** não podem ser alternados por apps comuns. Sem Shizuku, os botões abrem o
  painel do sistema. **Brilho** não tem controle próprio.
- **A "escala" de um app** muda a densidade da tela externa inteira enquanto ele está aberto (via
  Shizuku) e é desfeita ao voltar ao Cristal. É um recurso experimental.
- **Apps instalados por APK** caem na "Configuração restrita" do Android ao ativar notificações e
  acessibilidade; o guia explica como liberar.
- **Widgets de outros apps** funcionam (AppWidgetHost), mas não foram testados em aparelho: alguns podem
  não se ajustar bem ao tamanho da célula, e o menu deles abre pelo botão de três pontos no canto.
- **`compileSdk` é 37** porque as bibliotecas AndroidX atuais exigem; o `targetSdk` continua 36.

## Compilar

Pelo GitHub Actions (recomendado, veja o guia) ou localmente:

```
./gradlew testDebugUnitTest assembleRelease
```

O APK sai em `app/build/outputs/apk/release/`. Requisitos: JDK 21 e Android SDK com a plataforma 37.

| Ferramenta | Versão |
| --- | --- |
| Gradle / AGP | 9.9.1 / 9.5.1 |
| Kotlin / KSP | 2.4.21 / 2.3.14 |
| Compose BOM | 2026.09.00 |
| Hilt / Room / DataStore | 2.60.1 / 2.8.5 / 1.3.0 |
| Shizuku API | 13.1.5 |

`ci/build.yml` é a versão do workflow para um repositório em que este projeto fica na raiz.

## Árvore de arquivos

```
cristal-cover/
├── CONTRIBUINDO.md
├── LICENSE
├── NOTICE
├── README.md
├── SEGURANCA.md
├── ai/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── assets/
│       │   ├── java/app/lily/ai/
│       │   │   ├── AiGraph.kt
│       │   │   ├── LilyAiApp.kt
│       │   │   ├── avatar/          (personagem, poses, avatar flutuante)
│       │   │   ├── bridge/
│       │   │   │   ├── AiBridgeService.kt
│       │   │   │   └── CristalBridge.kt
│       │   │   ├── codex/           (agente, roteador de código, diff, linguagens, projetos)
│       │   │   ├── core/
│       │   │   │   ├── Device.kt
│       │   │   │   ├── Router.kt
│       │   │   │   └── Tasks.kt
│       │   │   ├── data/
│       │   │   │   ├── AiSettings.kt
│       │   │   │   ├── Conversations.kt
│       │   │   │   ├── MediaSaver.kt
│       │   │   │   ├── Memory.kt
│       │   │   │   └── Perf.kt
│       │   │   ├── engine/
│       │   │   │   ├── Audio.kt
│       │   │   │   ├── Enhance.kt
│       │   │   │   ├── Llm.kt
│       │   │   │   ├── Prompts.kt
│       │   │   │   ├── SuperRes.kt
│       │   │   │   └── Vision.kt
│       │   │   ├── imagegen/
│       │   │   │   ├── ImageGen.kt
│       │   │   │   └── iris/
│       │   │   │       ├── HostOps.kt
│       │   │   │       ├── IrisPipeline.kt
│       │   │   │       ├── Json.kt
│       │   │   │       ├── LiteRtExec.kt
│       │   │   │       ├── PromptTokens.kt
│       │   │   │       ├── QwenTokenizer.kt
│       │   │   │       └── RunFiles.kt
│       │   │   ├── models/
│       │   │   │   ├── Catalog.kt
│       │   │   │   ├── Compatibility.kt
│       │   │   │   ├── DownloadService.kt
│       │   │   │   ├── Downloader.kt
│       │   │   │   ├── Downloads.kt
│       │   │   │   └── ModelStore.kt
│       │   │   ├── presence/        (estado global que o avatar mostra)
│       │   │   ├── remote/          (cliente do gateway, chave no Keystore)
│       │   │   ├── tools/
│       │   │   │   └── AiTools.kt
│       │   │   ├── video/           (fila, serviço em primeiro plano, roteador de vídeo)
│       │   │   └── ui/
│       │   │       ├── MainActivity.kt
│       │   │       ├── Nav.kt
│       │   │       ├── screens/
│       │   │       │   ├── AvatarScreen.kt
│       │   │       │   ├── ChatScreen.kt
│       │   │       │   ├── ChatSession.kt
│       │   │       │   ├── ChatViews.kt
│       │   │       │   ├── CodexScreen.kt
│       │   │       │   ├── Common.kt
│       │   │       │   ├── CoverScreen.kt
│       │   │       │   ├── HistoryScreen.kt
│       │   │       │   ├── HomeScreen.kt
│       │   │       │   ├── HubScreen.kt
│       │   │       │   ├── ImageGenScreen.kt
│       │   │       │   ├── ImageToolScreen.kt
│       │   │       │   ├── LegalScreen.kt
│       │   │       │   ├── Markdown.kt
│       │   │       │   ├── MemoryScreen.kt
│       │   │       │   ├── ModelScreen.kt
│       │   │       │   ├── Network.kt
│       │   │       │   ├── OnlineScreen.kt
│       │   │       │   ├── SettingsScreen.kt
│       │   │       │   ├── Shared.kt
│       │   │       │   ├── TextToolScreen.kt
│       │   │       │   ├── ToolsScreen.kt
│       │   │       │   └── VideoScreen.kt
│       │   │       └── theme/
│       │   │           ├── Components.kt
│       │   │           ├── LilyTheme.kt
│       │   │           └── Visuals.kt
│       │   └── res/
│       └── test/java/app/lily/ai/
│           ├── AiScreensTest.kt
│           ├── DownloaderTest.kt
│           ├── EnhanceTest.kt
│           ├── ModelsTest.kt
│           ├── NavTest.kt
│           ├── RouterTest.kt
│           └── TextAndDataTest.kt
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── aidl/app/cristal/cover/data/shizuku/
│       │   │   └── IShellService.aidl
│       │   ├── java/app/cristal/cover/
│       │   │   ├── CristalApp.kt
│       │   │   ├── core/
│       │   │   │   ├── AppModule.kt
│       │   │   │   ├── Logx.kt
│       │   │   │   └── Qualifiers.kt
│       │   │   ├── data/
│       │   │   │   ├── apps/
│       │   │   │   │   ├── AppInfo.kt
│       │   │   │   │   ├── AppRepository.kt
│       │   │   │   │   ├── IconPackManager.kt
│       │   │   │   │   └── IconRenderer.kt
│       │   │   │   ├── backup/
│       │   │   │   │   └── BackupManager.kt
│       │   │   │   ├── battery/
│       │   │   │   │   └── BatteryRepository.kt
│       │   │   │   ├── calendar/
│       │   │   │   │   └── CalendarRepository.kt
│       │   │   │   ├── control/
│       │   │   │   │   ├── ControlCenterRepository.kt
│       │   │   │   │   └── ControlModels.kt
│       │   │   │   ├── db/
│       │   │   │   │   ├── CristalDatabase.kt
│       │   │   │   │   ├── Daos.kt
│       │   │   │   │   └── Entities.kt
│       │   │   │   ├── display/
│       │   │   │   │   └── CoverDisplayRepository.kt
│       │   │   │   ├── home/
│       │   │   │   │   ├── GridMath.kt
│       │   │   │   │   ├── HomeMaintenance.kt
│       │   │   │   │   ├── HomeModels.kt
│       │   │   │   │   └── HomeRepository.kt
│       │   │   │   ├── launch/
│       │   │   │   │   ├── AppLauncher.kt
│       │   │   │   │   └── CompatAdvisor.kt
│       │   │   │   ├── media/
│       │   │   │   │   └── MediaRepository.kt
│       │   │   │   ├── notifications/
│       │   │   │   │   └── NotificationRepository.kt
│       │   │   │   ├── prefs/
│       │   │   │   │   ├── Settings.kt
│       │   │   │   │   ├── SettingsRepository.kt
│       │   │   │   │   └── Wallpapers.kt
│       │   │   │   ├── shizuku/
│       │   │   │   │   ├── ShellService.kt
│       │   │   │   │   └── ShizukuManager.kt
│       │   │   │   ├── system/
│       │   │   │   │   ├── PermissionCenter.kt
│       │   │   │   │   ├── QuickSettingsRepository.kt
│       │   │   │   │   └── SystemBridge.kt
│       │   │   │   ├── wallpaper/
│       │   │   │   │   ├── SubjectDetector.kt
│       │   │   │   │   └── WallpaperRepository.kt
│       │   │   │   ├── weather/
│       │   │   │   │   └── WeatherRepository.kt
│       │   │   │   └── widgets/
│       │   │   │       └── WidgetHostManager.kt
│       │   │   ├── service/
│       │   │   │   ├── BootReceiver.kt
│       │   │   │   ├── CoverTileService.kt
│       │   │   │   ├── CristalAccessibilityService.kt
│       │   │   │   ├── CristalNotificationListener.kt
│       │   │   │   ├── LockController.kt
│       │   │   │   └── LockOverlay.kt
│       │   │   └── ui/
│       │   │       ├── components/
│       │   │       │   ├── AppIcon.kt
│       │   │       │   ├── Common.kt
│       │   │       │   └── Wallpaper.kt
│       │   │       ├── glass/
│       │   │       │   ├── GlassBackdrop.kt
│       │   │       │   ├── GlassLight.kt
│       │   │       │   ├── GlassShaders.kt
│       │   │       │   ├── GlassSurface.kt
│       │   │       │   ├── GlassTouch.kt
│       │   │       │   ├── LiquidGlass.kt
│       │   │       │   └── Motion.kt
│       │   │       ├── launcher/
│       │   │       │   ├── AppDrawer.kt
│       │   │       │   ├── AppPicker.kt
│       │   │       │   ├── ControlCenter.kt
│       │   │       │   ├── HomePager.kt
│       │   │       │   ├── LauncherActivity.kt
│       │   │       │   ├── LauncherRoot.kt
│       │   │       │   ├── LauncherScreen.kt
│       │   │       │   ├── LauncherState.kt
│       │   │       │   ├── LauncherViewModel.kt
│       │   │       │   ├── Overlays.kt
│       │   │       │   ├── PerformanceGovernor.kt
│       │   │       │   ├── QuickPanel.kt
│       │   │       │   └── TodayPanel.kt
│       │   │       ├── lock/
│       │   │       │   ├── CoverLockScreen.kt
│       │   │       │   ├── LockActivity.kt
│       │   │       │   ├── LockOverlayContent.kt
│       │   │       │   ├── LockPresenter.kt
│       │   │       │   └── LockScreen.kt
│       │   │       ├── onboarding/
│       │   │       │   └── OnboardingScreen.kt
│       │   │       ├── settings/
│       │   │       │   ├── SettingsActivity.kt
│       │   │       │   ├── SettingsComponents.kt
│       │   │       │   ├── SettingsScreens.kt
│       │   │       │   ├── SettingsViewModel.kt
│       │   │       │   └── WallpaperStudio.kt
│       │   │       ├── theme/
│       │   │       │   └── Theme.kt
│       │   │       └── widgets/
│       │   │           ├── AppWidgetCell.kt
│       │   │           ├── MoreWidgets.kt
│       │   │           └── Widgets.kt
│       │   └── res/
│       └── test/java/app/cristal/cover/
│           ├── ControlCenterTest.kt
│           ├── CoverLogicTest.kt
│           ├── GlassShaderTest.kt
│           ├── GridMathTest.kt
│           └── ScreensSmokeTest.kt
├── build.gradle.kts
├── ci/
│   └── build.yml
├── docs/
│   ├── GATEWAY.md
│   ├── GUIA.md
│   ├── ROADMAP.md
│   └── TESTES.md
├── gateway/                 (L.I.L.Y Gateway, Cloudflare Worker)
│   ├── package.json
│   ├── wrangler.toml
│   ├── scripts/build-web.mjs
│   ├── src/                 (index, http, budget, code, search, video, repo)
│   └── test/
├── gradle/
│   └── libs.versions.toml
├── gradle.properties
├── gradlew
├── gradlew.bat
├── gallery/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── assets/
│       │   ├── java/app/lily/gallery/
│       │   │   ├── LilyAi.kt
│       │   │   ├── data/
│       │   │   │   ├── Edits.kt
│       │   │   │   └── Media.kt
│       │   │   └── ui/
│       │   │       ├── Editor.kt
│       │   │       ├── Library.kt
│       │   │       ├── MainActivity.kt
│       │   │       ├── Permission.kt
│       │   │       ├── Theme.kt
│       │   │       ├── Trash.kt
│       │   │       └── Viewer.kt
│       │   └── res/
│       └── test/java/app/lily/gallery/
│           └── GalleryScreensTest.kt
├── keyboard/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── assets/
│       │   ├── java/app/lily/keyboard/
│       │   │   ├── LilyApp.kt
│       │   │   ├── ai/
│       │   │   │   └── KeyboardAi.kt
│       │   │   ├── brief/
│       │   │   │   ├── BriefBridge.kt
│       │   │   │   └── InfoBrief.kt
│       │   │   ├── clipboard/
│       │   │   │   └── ClipboardHistory.kt
│       │   │   ├── core/
│       │   │   │   ├── KeyLayouts.kt
│       │   │   │   ├── KeyboardSettings.kt
│       │   │   │   └── KeyboardStore.kt
│       │   │   ├── emoji/
│       │   │   │   └── EmojiData.kt
│       │   │   ├── engine/
│       │   │   │   ├── Corrector.kt
│       │   │   │   ├── Dictionary.kt
│       │   │   │   ├── GestureDecoder.kt
│       │   │   │   ├── InputController.kt
│       │   │   │   ├── LanguageModels.kt
│       │   │   │   ├── TextTools.kt
│       │   │   │   └── UserDictionary.kt
│       │   │   ├── ime/
│       │   │   │   └── LilyInputMethodService.kt
│       │   │   ├── studio/
│       │   │   │   ├── PreviewKeyboard.kt
│       │   │   │   ├── StudioActivity.kt
│       │   │   │   ├── StudioComponents.kt
│       │   │   │   └── VoicePermissionActivity.kt
│       │   │   ├── ui/
│       │   │   │   ├── AiPanel.kt
│       │   │   │   ├── BriefOverlay.kt
│       │   │   │   ├── Feedback.kt
│       │   │   │   ├── KeyGeometry.kt
│       │   │   │   ├── KeyboardRoot.kt
│       │   │   │   ├── KeysPad.kt
│       │   │   │   └── Panels.kt
│       │   │   └── voice/
│       │   │       └── VoiceInput.kt
│       │   └── res/
│       └── test/java/app/lily/keyboard/
│           ├── EngineTest.kt
│           ├── InfoBriefTest.kt
│           ├── InputControllerTest.kt
│           ├── KeyboardAiTest.kt
│           └── KeyboardScreensTest.kt
├── keystore/
│   └── cristal-ci.jks
├── legal/                   (termos de uso, privacidade, créditos; também dentro do app)
├── settings.gradle.kts
└── shared/
    ├── build.gradle.kts
    ├── consumer-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   └── java/app/lily/shared/
        │       ├── bridge/
        │       │   ├── AiBridge.kt
        │       │   └── CristalBrief.kt
        │       └── brief/
        │           ├── BriefDetector.kt
        │           ├── BriefIntents.kt
        │           └── BriefText.kt
        └── test/java/app/lily/shared/
            └── BriefDetectorTest.kt
```

## Melhorias futuras

O planejamento completo (compatibilidade com outros celulares, versão mínima do Android para a produção,
distribuição e o gateway público) está em [docs/ROADMAP.md](docs/ROADMAP.md). Pequenas melhorias:

1. Redimensionar widgets arrastando as bordas (hoje: grade e botões de largura/altura).
2. Extrair uma cor de destaque do papel de parede para botões e seleção.
3. Brilho do vidro reagindo também ao conteúdo que passa por trás (adaptação por elemento).
4. Atalhos de apps (app shortcuts) no menu do ícone e selo de notificações nos ícones.
5. Arrastar apps direto da gaveta para a tela inicial e reordenar dentro das pastas.
6. Respostas rápidas e ações das notificações no painel.
7. Layouts separados para a tela externa e a interna.
8. Mais estilos de relógio e personalização da tela de bloqueio.
9. Controle de brilho e mais atalhos via Shizuku.
10. Textos em `strings.xml` para tradução.
11. Perfil de baseline para abrir mais rápido e testes instrumentados em aparelho.
12. Empacotar uma fonte aberta parecida com a San Francisco (por exemplo, Inter).

## Referências consultadas

Usadas só como referência de funcionalidades; nenhum código ou recurso foi copiado.

- [CoverScreen OS](https://ijp.app/apps/coverscreen-os/) e a
  [discussão no XDA sobre o Razr 40 Ultra](https://xdaforums.com/t/coverscreen-os-now-ready-for-razr-40-ultra-a-mini-os-for-your-cover-screen.4737125/)
- [Comet Launcher na Play Store](https://play.google.com/store/apps/details?id=com.vancod.launcher)
- [launch-anything-on-Razr-2024-plus-cover-screen](https://github.com/dexuantang/launch-anything-on-Razr-2024-plus-cover-screen)
  (tela 1, serviço `climanager`)
- [Início de activities em segundo plano](https://developer.android.com/guide/components/activities/background-starts)
- [Liquid Glass: ajustes Transparente e Fosco do iOS 26.1](https://www.iclarified.com/98836/ios-261-beta-4-adds-new-tinted-setting-to-reduce-liquid-glass-transparency)
  e [visão geral da linguagem visual](https://www.createwithswift.com/exploring-a-new-visual-language-liquid-glass/)
- [Open-Meteo](https://open-meteo.com/) (dados de clima)
