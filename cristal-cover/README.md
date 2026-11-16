# Cristal Cover

Launcher e tela de bloqueio em **vidro líquido** para o Motorola Razr 40 Ultra (Android 16), na tela
externa e na interna, e o **L.I.L.Y Keyboard**, um teclado completo (app separado, pasta `keyboard/`).
Kotlin + Jetpack Compose.

> **Este projeto é independente do site que divide o repositório com ele.** Tudo o que é do app está
> dentro de `cristal-cover/`; o workflow `build.yml` só roda quando algo nessa pasta muda e não publica
> nada no GitHub Pages.

- Guia de instalação (só com o celular): [docs/GUIA.md](docs/GUIA.md)
- Checklist de testes no aparelho: [docs/TESTES.md](docs/TESTES.md)

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
| L.I.L.Y Keyboard (teclado, L.I.L.Y Correct, deslize, emojis, área de transferência, voz, Keyboard Studio) | Feito (1.2.0: Info Brief), sem IA |
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
| Biblioteca de apps, páginas próprias da tela externa | Planejado |
| L.I.L.Y AI (modelos locais) e as ferramentas de escrita com IA do teclado | Adiado a pedido; exige medições no aparelho real |

Sobre a L.I.L.Y AI: nenhum motor ou modelo foi incluído (o teclado funciona sem ela). O próprio requisito pede testes no Razr 40
Ultra antes de integrar qualquer coisa, e este projeto nunca foi executado no aparelho durante o
desenvolvimento. Incluir um runtime pesado sem medir memória, temperatura e latência seria chute.

## Estado real do projeto

O que **foi verificado** durante o desenvolvimento:

- compila do zero (debug e release com R8) com as versões estáveis atuais das ferramentas;
- 147 testes automáticos em JVM passam (95 no Cristal e 52 no teclado). No launcher: lógica da grade, detecção da tela externa,
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
  transição de fechamento, com 55 telas desenhadas;
- lint sem erros.

O que **não foi verificado**: o app **nunca rodou em um Razr de verdade**. Tudo o que depende de
comportamento não documentado da Motorola está marcado com ⚠️ no [checklist](docs/TESTES.md) e precisa
ser confirmado no aparelho.

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

Não tem (de propósito): ferramentas de escrita com IA, botão da L.I.L.Y, gramática e concordância
(dependem da L.I.L.Y AI), GIFs e tradução (exigem internet).

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
├── README.md
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
│   ├── GUIA.md
│   └── TESTES.md
├── gradle/
│   └── libs.versions.toml
├── gradle.properties
├── gradlew
├── gradlew.bat
├── keyboard/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── assets/
│       │   │   ├── emoji.tsv
│       │   │   ├── en.dict
│       │   │   └── pt_br.dict
│       │   ├── java/app/lily/keyboard/
│       │   │   ├── LilyApp.kt
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
│           ├── InputControllerTest.kt
│           └── KeyboardScreensTest.kt
├── keystore/
│   └── cristal-ci.jks
└── settings.gradle.kts
```

## Melhorias futuras

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
