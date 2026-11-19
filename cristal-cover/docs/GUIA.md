# Guia de instalação e configuração (só com o celular)

Este guia assume que você **não tem computador** e faz tudo pelo próprio Razr 40 Ultra.
Siga na ordem. Cada parte diz o que é obrigatório e o que é opcional.

- [1. Conseguir o APK](#1-conseguir-o-apk)
- [2. Instalar](#2-instalar)
- [3. Primeira abertura](#3-primeira-abertura)
- [4. Usar o Cristal na tela externa](#4-usar-o-cristal-na-tela-externa)
- [5. Shizuku (opcional, recomendado)](#5-shizuku-opcional-recomendado)
- [6. ADB sem fio direto do celular (alternativa ao Shizuku)](#6-adb-sem-fio-direto-do-celular-alternativa-ao-shizuku)
- [7. Ativar a tela de bloqueio](#7-ativar-a-tela-de-bloqueio)
- [8. Assinar com a sua própria chave (opcional)](#8-assinar-com-a-sua-própria-chave-opcional)
- [9. Colocar o projeto em um repositório só dele (opcional)](#9-colocar-o-projeto-em-um-repositório-só-dele-opcional)
- [10. Problemas comuns](#10-problemas-comuns)

---

## 1. Conseguir o APK

O APK é gerado pelo GitHub Actions, de graça, toda vez que algo muda na pasta `cristal-cover/`.

1. No navegador do celular, abra o seu repositório no GitHub e entre com a sua conta.
2. Toque na aba **Actions**. (No celular ela pode estar escondida: role a barra de abas para o lado.)
3. Na lista à esquerda (ou no menu, no celular), toque em **Cristal Cover - compilar APK**.
4. Toque na execução mais recente com um ✓ verde. Se estiver com um ● amarelo, ainda está compilando; espere
   uns 5 a 10 minutos e atualize a página.
5. Role até o fim da página, na seção **Artifacts**, e toque em **cristal-cover-apk**. O navegador baixa um
   arquivo `.zip`.
6. Abra o app **Arquivos** (Files), entre em **Downloads**, toque no `.zip` e escolha **Extrair**. Dentro
   está o arquivo `cristal-cover-1.0.0-N.apk`.

> **Qual arquivo usar?** O `cristal-cover-apk` é a versão otimizada (cerca de 4 MB): é essa que você quer.
> O teclado vem no artefato **lily-keyboard-apk** (arquivo `lily-keyboard-….apk`, sem `-debug`), a IA no
> **lily-ai-apk** (`lily-ai-1.0.0-N.apk`, cerca de 85 MB) e a galeria no **lily-gallery-apk**
> (`lily-gallery-1.0.0-N.apk`).
> O `cristal-cover-apk-debug` é dezenas de vezes maior e mais lento; só serve para investigar problemas.

### Rodar o build de novo quando quiser

Depois que o arquivo `.github/workflows/build.yml` estiver na branch principal do repositório, aparece o
botão **Run workflow** na página do workflow (aba Actions). Marque **Publicar o APK em uma Release** se
quiser que o APK fique na página **Releases**, onde dá para baixar o `.apk` direto, sem `.zip`.

## 2. Instalar

1. Toque no arquivo `.apk`.
2. O Android avisa que o navegador (ou o app Arquivos) não pode instalar apps. Toque em **Configurações**,
   ative **Permitir desta fonte** e volte.
3. Toque em **Instalar**.
4. Se o Play Protect disser que não reconhece o app, toque em **Mais detalhes** e depois em
   **Instalar mesmo assim**. Isso acontece com qualquer app que não veio da Play Store.

Para **atualizar**, basta instalar o APK novo por cima: as configurações são mantidas. (Isso funciona
enquanto os APKs forem assinados com a mesma chave; veja a parte 8.)

### Teclado (L.I.L.Y Keyboard)

É um app separado: instale o `lily-keyboard-….apk` do mesmo jeito. Depois abra o **L.I.L.Y Keyboard**
e siga os dois passos da tela (ligar em Configurações > Teclados e escolher como teclado). O Android
mostra um aviso padrão de que "o teclado pode coletar o que você digita": ele aparece para qualquer
teclado; o L.I.L.Y não tem acesso à internet.

### L.I.L.Y AI e L.I.L.Y Gallery

Também são apps separados: instale `lily-ai-….apk` e `lily-gallery-….apk` do mesmo jeito. Instale os
APKs da **mesma rodada** do GitHub Actions: o teclado e a Gallery só conversam com a IA quando os apps
têm a mesma assinatura (a chave de CI do repositório já garante isso).

## 3. Primeira abertura

Abra o **Cristal Cover** com o aparelho aberto, na tela de dentro: é mais confortável para as permissões.
A configuração guiada tem 6 etapas e todas podem ser puladas.

### 3.1 Acesso às notificações

Serve para mostrar as notificações no painel e na tela de bloqueio e para controlar a música.

1. Toque em **Ativar**.
2. Na lista, toque em **Cristal Cover** e ligue a chave.

### 3.2 Serviço de acessibilidade

Serve para bloquear a tela por gesto, abrir os painéis do sistema, assumir a tela externa e mostrar a tela de
bloqueio. O serviço **não lê o conteúdo das telas**: só fica sabendo qual app está em primeiro plano.

1. Toque em **Abrir acessibilidade**.
2. Entre em **Apps instalados** (ou "Serviços baixados"), toque em **Cristal Cover** e ligue a chave.

### 3.3 Se aparecer "Configuração restrita"

É uma proteção do Android para apps instalados por APK. Ela bloqueia os dois acessos acima até você liberar:

1. Tente ligar a chave uma vez, para o aviso **Configuração restrita** aparecer. Toque em **OK**.
2. Volte ao Cristal e toque em **Abrir detalhes do app** (ou vá em Configurações > Apps > Cristal Cover).
3. Toque nos **três pontinhos** no canto superior direito e em **Permitir configurações restritas**.
   Confirme com a sua digital ou PIN.
4. Agora volte e ligue as chaves das partes 3.1 e 3.2.

> Os três pontinhos só aparecem **depois** que você tentou ligar a chave e viu o aviso.
> Com o Shizuku (parte 5) você pula tudo isso: um botão concede os dois acessos.

### 3.4 Extras (opcionais)

- **Agenda**: para o widget de compromissos.
- **Localização aproximada**: para o clima do lugar onde você está. Se preferir, escolha a cidade pelo nome
  em Configurações > Clima.
- **Launcher padrão**: só se quiser o Cristal também na tela de dentro. **Não é preciso** para a tela externa.

## 4. Usar o Cristal na tela externa

Há três caminhos. Use o que funcionar no seu aparelho; eles não se atrapalham. Os botões dos dois primeiros
ficam em **Configurações do Cristal > Tela externa**.

**Caminho 1: "Abrir na tela externa".** Com o aparelho aberto, toque em **Abrir na tela externa** e feche o
aparelho. O mesmo botão aparece no toque longo no fundo da tela inicial (**Tela externa**).

**Caminho 2: bloco nos Ajustes rápidos.** Toque em **Adicionar o bloco** e confirme. Depois, na tela externa,
puxe os ajustes rápidos para baixo e toque em **Cristal Cover**. Esse caminho não depende do painel de apps da
Motorola.

**Caminho 3: painel de apps da Motorola.**

1. Abra **Configurações > Tela externa > Painéis**.
2. Edite o painel **Apps** e adicione o **Cristal Cover** (e os apps que você quer usar por lá).
3. Feche o aparelho, deslize até o painel de apps e toque em **Cristal Cover**.

Se nenhum dos três abrir, a Motorola está bloqueando o app na tela externa. Nesse caso ative o **Shizuku**
(parte 5): com ele o Cristal pede a liberação ao sistema sozinho.

### Fazer o Cristal aparecer sozinho

O gesto de "início" na tela externa sempre leva ao launcher da Motorola. Isso é do sistema e não dá para
mudar sem root. Para contornar:

- Em **Configurações do Cristal > Tela externa**, ligue **Assumir a tela externa**. O Cristal abre sozinho ao
  fechar o aparelho e sempre que a tela inicial da Motorola aparecer na tela externa. Precisa do serviço de
  acessibilidade.
- Para ver a tela original da Motorola: toque longo no fundo do Cristal > **Tela Motorola**. Você tem 20
  segundos antes de o Cristal reassumir.

## 5. Shizuku (opcional, recomendado)

O Shizuku dá ao Cristal os mesmos poderes do ADB, **sem root e sem computador**. Com ele o Cristal:

- concede os acessos da parte 3 em um toque;
- libera apps na tela externa sem você entrar nas configurações;
- ajusta a escala e força o redimensionamento de apps que não cabem na tela externa;
- liga e desliga Wi-Fi, Bluetooth e dados pelo painel rápido.

### 5.1 Instalar e iniciar

1. Instale o **Shizuku** pela Play Store (ou pelo site oficial, `shizuku.rikka.app`).
2. Ative as **Opções do desenvolvedor**: Configurações > Sobre o telefone > toque 7 vezes em
   **Número da versão**.
3. Conecte-se a uma rede **Wi-Fi** (a depuração sem fio exige Wi-Fi, mesmo sem internet).
4. Vá em Configurações > Sistema > **Opções do desenvolvedor** e ligue **Depuração por Wi-Fi**
   (em inglês, *Wireless debugging*). Confirme.
5. Abra o **Shizuku** e, na parte "Iniciar via depuração sem fio", toque em **Parear**. Ele mostra uma
   notificação pedindo o código.
6. Toque em **Depuração por Wi-Fi** (no texto, não na chave) > **Parear dispositivo com código de
   pareamento**. Aparece um código de 6 dígitos.
7. **Sem sair dessa tela**, puxe a barra de notificações, toque na notificação do Shizuku, digite o código e
   envie.
8. Volte ao Shizuku e toque em **Iniciar**. Deve aparecer "Shizuku está em execução".

### 5.2 Autorizar o Cristal

1. Abra **Configurações do Cristal > Permissões**.
2. Em Shizuku, toque em **Autorizar o Cristal** e escolha **Permitir sempre**.
3. Toque em **Conceder acessos automaticamente**. Pronto: notificações e acessibilidade ficam ativos.

### 5.3 Depois de reiniciar o aparelho

O Shizuku para quando o aparelho reinicia. Ligue o Wi-Fi, abra o Shizuku e toque em **Iniciar** (não precisa
parear de novo). As versões recentes do Shizuku têm uma opção para iniciar sozinho ao ligar; se existir no
seu, ative.

O Cristal continua funcionando sem o Shizuku rodando: você só perde, até iniciar de novo, a liberação
automática de apps, a escala e os botões de Wi-Fi/Bluetooth/dados.

## 6. ADB sem fio direto do celular (alternativa ao Shizuku)

Se não quiser o Shizuku, dá para rodar comandos ADB do próprio aparelho com o **Termux**.

1. Instale o **Termux** pelo F-Droid ou pela página do projeto no GitHub (a versão da Play Store é limitada).
2. No Termux, digite:

   ```
   pkg update
   pkg install android-tools
   ```

3. Ative a **Depuração por Wi-Fi** (parte 5.1, passos 2 a 4).
4. Abra a tela **Depuração por Wi-Fi** e o Termux **em tela dividida** (abra os apps recentes, toque no
   ícone do app e em "Tela dividida"). Isso é importante: o código some se você sair da tela.
5. Toque em **Parear dispositivo com código de pareamento**. Anote a porta (o número depois dos dois
   pontos) e o código. No Termux:

   ```
   adb pair localhost:PORTA_DO_PAREAMENTO
   ```

   Digite o código quando ele pedir.
6. Na tela principal da Depuração por Wi-Fi há **outra** porta, em "Endereço IP e porta". Use essa:

   ```
   adb connect localhost:PORTA_PRINCIPAL
   ```

7. Agora os comandos funcionam. Os mais úteis:

   ```
   # Acesso às notificações
   adb shell cmd notification allow_listener app.cristal.cover/app.cristal.cover.service.CristalNotificationListener

   # Deixa o Cristal ligar e religar o próprio serviço de acessibilidade
   adb shell pm grant app.cristal.cover android.permission.WRITE_SECURE_SETTINGS

   # Serviço de acessibilidade (ATENÇÃO: este comando substitui a lista; veja a nota abaixo)
   adb shell settings put secure enabled_accessibility_services app.cristal.cover/app.cristal.cover.service.CristalAccessibilityService
   adb shell settings put secure accessibility_enabled 1

   # Motorola: liberar um app na tela externa (vale até reiniciar)
   adb shell cmd climanager set-pkg-allowed-oncli app.cristal.cover true

   # Abrir o Cristal direto na tela externa (tela 1)
   adb shell am start --display 1 -n app.cristal.cover/.ui.launcher.LauncherActivity

   # Conferir quais telas existem e seus números
   adb shell dumpsys display | grep mDisplayId
   ```

> **Nota sobre a acessibilidade:** se você já usa outro serviço de acessibilidade (TalkBack, um gerenciador
> de senhas etc.), veja a lista atual com `adb shell settings get secure enabled_accessibility_services` e
> coloque o Cristal no fim, separado por dois pontos, em vez de substituir tudo.

> Os comandos `climanager` são de um serviço interno da Motorola. Se o seu sistema responder
> "Can't find service", ele não existe nessa versão: use a tela de Configurações da parte 4.

## 7. Ativar a tela de bloqueio

A tela de bloqueio do Cristal vem **desligada**.

1. Abra **Configurações do Cristal > Tela de bloqueio**.
2. Escolha **Activity (recomendado)**.
3. Toque em **Pré-visualizar** para ver como fica.
4. Feche o aparelho, apague a tela, acenda de novo e veja se a tela do Cristal aparece.

Se a tela **não aparecer** na tela externa (a Motorola pode manter a tela de bloqueio dela por cima), troque
o modo para **Sobreposição** e teste de novo.

Como destravar: deslize para cima. Quem pede o PIN, o padrão ou a digital é o **próprio Android**. O Cristal
nunca vê a sua credencial e não desliga o bloqueio do sistema.

## 8. Assinar com a sua própria chave (opcional)

Sem configurar nada, os APKs são assinados com uma **chave de CI que fica no repositório**
(`keystore/cristal-ci.jks`, senha `cristal-ci`). Para uso pessoal isso é suficiente e tem uma vantagem:
todo build tem a mesma assinatura, então um instala por cima do outro.

A desvantagem: a chave é pública. Qualquer pessoa poderia assinar um APK com ela. Se você for **distribuir**
o app, use uma chave só sua:

1. No Termux:

   ```
   pkg install openjdk-21
   keytool -genkeypair -v -keystore minha-chave.jks -storetype PKCS12 -alias cristal -keyalg RSA -keysize 2048 -validity 10000
   base64 -w0 minha-chave.jks
   ```

   O primeiro comando pergunta uma senha e alguns dados. O último imprime um texto longo: copie-o inteiro.
2. No GitHub, abra o repositório > **Settings > Secrets and variables > Actions > New repository secret** e
   crie quatro secrets:

   | Nome | Valor |
   | --- | --- |
   | `CRISTAL_KEYSTORE_BASE64` | o texto longo do `base64` |
   | `CRISTAL_KEYSTORE_PASSWORD` | a senha que você escolheu |
   | `CRISTAL_KEY_ALIAS` | `cristal` |
   | `CRISTAL_KEY_PASSWORD` | a mesma senha |

3. Guarde o arquivo `minha-chave.jks` e a senha em lugar seguro. Sem eles você não consegue mais atualizar
   o app por cima.

Ao trocar de chave, o Android recusa instalar por cima: **desinstale** a versão antiga antes (faça um
backup em Configurações do Cristal > Backup).

## 9. Colocar o projeto em um repositório só dele (opcional)

O projeto fica na pasta `cristal-cover/` e não depende de mais nada. Para ter um repositório dedicado, o
caminho mais simples no celular é o Termux:

1. Baixe o arquivo `.zip` do projeto para a pasta Downloads.
2. No Termux:

   ```
   pkg update
   pkg install git gh unzip
   termux-setup-storage
   ```

   Toque em **Permitir** quando o Android pedir acesso aos arquivos.
3. Entre na sua conta do GitHub:

   ```
   gh auth login
   ```

   Escolha **GitHub.com**, **HTTPS**, **Yes** e **Login with a web browser**. Ele mostra um código: abra o
   endereço indicado no navegador, entre na conta e digite o código.
4. Extraia e publique:

   ```
   cd ~
   unzip ~/storage/downloads/cristal-cover-projeto.zip
   cd cristal-cover
   mkdir -p .github/workflows
   cp ci/build.yml .github/workflows/build.yml
   git init -b main
   git add .
   git commit -m "Cristal Cover"
   gh repo create cristal-cover --private --source=. --push
   ```

   O arquivo `ci/build.yml` é a versão do workflow para um repositório em que o projeto fica na raiz.
5. Abra o repositório novo no navegador, toque em **Actions** e acompanhe o build. Depois siga a parte 1.

## 9.1 Novidades da 1.6.0 que pedem um passo seu

**Info Brief (teclado + Cristal).** Instale os dois APKs da mesma rodada (`cristal-cover-1.6.0.apk` e
`lily-keyboard-1.2.0.apk`): a ponte entre eles só funciona com a mesma assinatura. No teclado, abra
**L.I.L.Y Keyboard > Info Brief** para ligar, escolher o que reconhecer e o destaque no texto. No Cristal,
**Configurações > Info Brief** mostra os lembretes, o histórico e duas permissões:

- **Notificações:** o Cristal pede na primeira vez que você criar um lembrete. Sem ela, o lembrete só
  aparece no Now Brief.
- **Alarmes e lembretes:** toque em **Abrir** e ligue o Cristal. Sem isso o aviso funciona, mas pode
  atrasar alguns minutos.

**Notificações aparecendo duas vezes (aviso do sistema + ilha).** Configurações > Liquid Island >
**Notificação aparecendo duas vezes?**: cada app com aviso flutuante aparece numa lista; toque nele para
abrir o ajuste do canal e desligue **Pop-up na tela** (ou "Mostrar como pop-up"). Com o Shizuku ativo, o
interruptor **Pop-ups do sistema** desliga os avisos flutuantes de uma vez (e religa quando quiser).

**Central de Controle em qualquer app.** Já vem ligada: puxe para baixo a partir do canto superior
direito, em qualquer app e nas duas telas. Para desligar: Configurações > Gestos > Liquid Control Center >
**Em qualquer app**. Depois de atualizar, se o Android pedir, reative o serviço de acessibilidade do
Cristal (a captura de tela usada pelo vidro e pela transição é uma capacidade nova do serviço).

**Transição de fechamento em qualquer app.** Usa o sensor da dobradiça e a acessibilidade; liga e
desliga em Configurações > Aparência > **Flip Transition**.

**Modo de precisão da ilha (opcional).** Configurações > Liquid Island > **Modo de precisão**: um segundo serviço de
acessibilidade, restrito à barra de status do sistema, mede os ícones reais para a ilha nunca cobri-los.
Vem desligado.

## 9.2 L.I.L.Y AI, Gallery e a L.I.L.Y no teclado (novidade)

**Primeiro modelo.** Abra a **L.I.L.Y AI**. Na primeira vez, o cartão **Comece por aqui** recomenda o
modelo certo para o aparelho (no Razr 40 Ultra, o Qwen3 1.7B, com menos de 1 GB). Toque em **Baixar**:

- o Android pergunta se a L.I.L.Y pode mostrar notificações. Permita para ver o progresso na notificação
  e na Liquid Island (o download funciona mesmo sem);
- por padrão, os downloads são só no Wi-Fi; nos dados móveis a IA pergunta antes (Configurações > Downloads);
- o download continua com o app fechado e pode ser pausado e retomado; no fim, o arquivo é conferido
  (SHA-256) antes de ficar disponível.

Depois disso tudo funciona **sem internet**. Para fotos e áudio, baixe o **Gemma 4 E2B** (2,4 GB) no
**Model Hub** (ícone de cubo na tela inicial). Cada ficha mostra a compatibilidade calculada para o seu
aparelho, o espaço necessário, a licença e, depois do primeiro uso, a velocidade medida aí.

**Teste do aparelho.** Depois de baixar um ou mais modelos, abra **Configurações > Testar este aparelho**
e toque em **Começar o teste** (leva de 1 a 5 minutos; feche outros apps antes). No fim, toque em
**Copiar o relatório** e cole numa conversa comigo: com os números reais do seu Razr dá para ajustar o
roteador (qual modelo, GPU ou CPU) para ele.

**Microfone.** Só é pedido quando você toca no microfone. Nada fica ouvindo em segundo plano.

**Tela externa.** A L.I.L.Y também abre na tela externa (no Cristal, ou pelo atalho de apps da Motorola),
numa versão de voz. Ao abrir o aparelho, a conversa continua na tela interna.

**Gerar imagens (experimental).** No Model Hub, **iris-3b Turbo** (11,3 GB). Cada imagem leva minutos e
usa cerca de 3,2 GB de memória: feche outros apps antes. Você pode sair do app durante a geração.

**L.I.L.Y no teclado.** Com a IA e um modelo instalados, toque no botão ✦ da barra do teclado (para quem
atualizou, ele aparece sozinho no começo da barra). Escolha uma ação: a resposta aparece numa prévia e o
texto só muda quando você toca em **Substituir** ou **Inserir**. Para **Responder**, copie antes a
mensagem que recebeu. Em campos de senha e de códigos, o painel não lê nada.

**Gallery.** Na primeira abertura, toque em **Permitir acesso às fotos**: você pode liberar todas ou só
algumas (dá para escolher mais depois). Para não confirmar a cada vez que mandar algo para a lixeira ou
favoritar: menu ⋮ > **Lixeira e favoritos sem confirmar sempre** e ligue a Gallery.

**Google Fotos.** Menu ⋮ > **Importar fotos**: o seletor do Android mostra também as fotos do Google Fotos
quando ele está instalado e escolhido como app de mídia na nuvem (no seletor: ⋮ > **Configurações** >
**App de mídia na nuvem** > Google Fotos). As fotos escolhidas são copiadas para Pictures/Importadas.
Para mandar fotos ao backup do Google Fotos, selecione-as e toque no ícone de nuvem.

## 9.3 L.I.L.Y no Cristal (Cristal 1.7.1 e L.I.L.Y AI 1.1.1)

Instale os dois APKs da mesma rodada (a ponte entre eles só funciona com a mesma assinatura).

**Na tela externa**, a Motorola só deixa abrir apps liberados, e fecha os outros logo que abrem. Com o
Shizuku ativo e **Liberar apps automaticamente** ligado, o Cristal libera a L.I.L.Y sozinho antes de abri-la (widget, busca e
Central). Sem o Shizuku, adicione a **L.I.L.Y AI** no painel **Apps** da tela externa (parte 4, caminho 3).
Se mesmo assim ela não aparecer, o Cristal avisa "L.I.L.Y AI não abriu nesta tela" com o botão **Por quê?**.

- **Pela ilha:** com a cápsula à mostra (Configurações > Liquid Island > **Mostrar sempre**), **segure a ilha**
  e fale. A L.I.L.Y responde numa lente logo abaixo dela, por cima de qualquer app. Para desligar:
  Configurações > Liquid Island > **Segurar a ilha chama a L.I.L.Y**. Segurar a ilha com música ou uma
  atividade continua expandindo, como antes.
- **Fechou no meio?** A resposta continua: a ilha mostra "L.I.L.Y respondendo" e, no fim, chega o aviso
  "L.I.L.Y respondeu". Tocar abre a resposta.
- **Widget:** segure um espaço vazio da tela inicial (interna ou externa) > Widgets > **L.I.L.Y**. Tocar no
  widget abre a conversa; o microfone abre a voz na mesma tela.
- **Busca:** digite na busca da gaveta de apps e toque em **Perguntar à L.I.L.Y**.
- **Central de Controle:** no editor da central, em Atalhos, adicione o módulo **L.I.L.Y**.
- **Como assistente do Android (opcional):** Configurações do Android > Apps > Apps padrão > **Assistente
  digital** > L.I.L.Y AI. Depois, o gesto de assistente do aparelho (como segurar o botão liga/desliga, se a
  Motorola oferecer essa opção) abre a L.I.L.Y por voz.

O microfone só liga quando você chama a L.I.L.Y (segurar a ilha, tocar no microfone ou no assistente) e
para sozinho no fim da fala. **Conversa contínua:** depois de responder uma pergunta falada (e de ler a resposta), a L.I.L.Y ouve mais uma vez para você continuar o assunto, por exemplo "Quem é o Neymar?" e, em seguida, "Quantos anos ele tem?". O painel mostra "Pode continuar: estou ouvindo…"; se você não falar, o microfone desliga sozinho. Para desligar: Configurações > Voz > **Conversa contínua**.

## 9.4 Novidades do Cristal 1.8.0 e da L.I.L.Y AI 1.2.0 (e 1.2.1)

### L.I.L.Y Diagnóstico (novo ícone)

Depois de instalar a L.I.L.Y AI 1.2.0, aparece um segundo ícone na gaveta: **L.I.L.Y Diagnóstico**. Ele roda
num processo separado, então abre **mesmo quando a L.I.L.Y não abre**. Mostra, em português:

- **Situação**: se a última abertura terminou bem, se o modo seguro está ligado e quantas aberturas falharam;
- **Módulos**: quais partes da L.I.L.Y subiram e quais caíram num substituto (e por quê);
- **Como o app terminou**: o histórico que o próprio Android guarda (erro Java, erro nativo com a pilha da
  lápide, "não respondeu" com a pilha da linha principal, encerrado por falta de memória ou pelo usuário);
- **Erros guardados**: os últimos 10 relatórios, com a pilha completa e o módulo provável;
- **Registro**: cada etapa da partida, com o tempo de cada uma.

Botões: **Copiar relatório**, **Compartilhar** (WhatsApp, e-mail…), **Salvar em Downloads**, **Abrir a
L.I.L.Y**, **Abrir em modo seguro** e **Apagar histórico**. Nada sai do aparelho sem você tocar em
Compartilhar.

**Modo seguro:** se a L.I.L.Y falhar ao abrir duas vezes seguidas, a próxima abertura já vem em modo
seguro (sem o orbe animado e sem carregar modelos sozinha), com um aviso no alto e o botão **Ver
diagnóstico**. Saia dele pelo L.I.L.Y Diagnóstico > **Sair do modo seguro**. Se uma parte opcional (motor
de IA, memória, histórico, geração de imagem) não subir, a L.I.L.Y abre assim mesmo e avisa "Abriu sem
algumas partes".

### Transição de dobra

Com **Flip Transition** ligado (Configurações > Aparência, vem ligado), ao abrir e fechar o Razr com o
Cristal na tela, a tela acompanha a dobradiça: a região perto da dobra fica
desfocada e mais escura e vai ficando nítida por último, o papel de parede assenta antes dos ícones e
widgets, e uma linha de luz em espectro aparece na dobra e some ao terminar. Parou no meio (modo flex)? Em
menos de meio segundo tudo volta a ficar nítido. Com **remover animações** ligado no Android, não há efeito.

O que o Android permite e o que não permite:

- **No Cristal (tela inicial interna e externa):** a transição completa, ao vivo, sobre a própria interface.
- **Por cima de outros apps:** só o **fechamento** (Configurações > Aparência > **Flip Transition**, com a
  acessibilidade ligada), usando uma captura da tela. A **abertura** por cima de outros apps e a animação
  do próprio sistema (desbloqueio, troca de tela da Motorola) não podem ser alteradas por um app comum.
- Na qualidade **Leve**, o desfoque é menor para economizar bateria.

### Widgets

Todos os widgets foram redesenhados. Cada tamanho reorganiza o conteúdo (não só aumenta): faixa (4×1),
pequeno (2×2), médio (4×2), grande (4×3, 4×4, 2×4) e extra grande. Para mudar o tamanho, segure o widget >
**Redimensionar**. A galeria (segurar um espaço vazio > Widgets) mostra cada widget de verdade, com os seus
dados de agora.

Em **Configurações > Visual > Widgets**: aparência (**Liquid Glass**, **Claro**, **Escuro**, **Colorido**,
**Monocromático**), transparência, desfoque do vidro, cor de destaque, cantos e densidade, com prévia. A
aparência fica guardada nas coleções da Wallpaper Library.

O widget **Info Brief** mostra o próximo lembrete ou compromisso do Now Brief; tocar nele abre o painel.
O widget **L.I.L.Y** mostra uma sugestão a partir da agenda e dos lembretes; tocar na sugestão já pergunta
à L.I.L.Y.

### Relógio colorido

No editor do bloqueio (segurar o bloqueio e autenticar; escolha **Interna** ou **Externa** no alto), a seção
**Preenchimento**:
**Original** (o vidro de antes, sem mudança), **Sólido**, **Gradiente**, **Translúcido** e **Liquid Glass
colorido**; intensidade; segunda cor; **Horas e minutos com cores próprias**; **Data na cor do relógio**. A
prévia muda na hora. Vale para a tela interna e para a externa (cada uma com a sua escolha) e fica guardado
nas coleções.

## 9.5 L.I.L.Y AI 1.3.0: Codex, Video AI, avatar e serviço online

Instale a 1.3.0 por cima da 1.2.1 (não precisa desinstalar). Sem fazer mais nada, a L.I.L.Y continua
**inteira no aparelho**: a novidade que aparece sozinha é a **avatar**. O Codex, o Video AI e os modelos
online usam um serviço seu, o **L.I.L.Y Gateway**, que você cria uma vez (30 a 40 minutos, só pelo celular).

### Avatar

Configurações > **Avatar**: estilo (Holograma, Vidro, Mínimo), forma (busto, rosto, orbe), cor, tamanho,
transparência e intensidade das animações, com prévia. Ela respira, pisca, olha em volta e mexe a boca ao
falar; muda conforme o que a L.I.L.Y está fazendo: ouvindo, pensando, falando, trabalhando (Codex, vídeo,
imagem), sucesso e erro. A avatar **nunca liga a câmera nem o microfone sozinha**.

**Avatar flutuante (opcional):** em Configurações > Avatar, escolha "Durante tarefas" ou "Sempre". O
Android pede a permissão **Sobrepor a outros apps**; sem ela, o app só explica e nada muda. Arraste para
mudar de lugar, toque para abrir a L.I.L.Y e segure para esconder. Na tela externa ela aparece pequena.
No modo "Sempre", o Android pode fechá-la para poupar bateria; ela volta quando você abre a L.I.L.Y.

### Criar o serviço online (uma vez)

Siga o passo a passo de [GATEWAY.md](GATEWAY.md). Em resumo: conta grátis na Cloudflare, uma chave de
acesso inventada por você (uma senha de 40+ caracteres do seu gerador de senhas; não vem de nenhum site), as chaves dos provedores que quiser usar (OpenRouter para o Codex e a conversa
online, fal.ai para vídeo), guardar tudo como segredos do GitHub e pedir a publicação editando o arquivo
`cristal-cover/gateway/PUBLICAR.md` pelo navegador (o GitHub não mostra o botão "Run workflow" fora do
ramo principal). Depois, na L.I.L.Y AI: Configurações > **Serviço online** > endereço e chave > **Salvar e
testar** > ligar **Permitir processamento externo**.

Três modos (Serviço online): **Só no aparelho** (nada sai), **Online quando faltar** (usa o modelo online
só quando não há modelo instalado para a tarefa) e **Online primeiro**. Imagem e áudio nunca vão para fora.
Cada resposta online mostra o modelo e o custo.

**Custos:** a hospedagem é grátis no plano básico da Cloudflare; os modelos e os vídeos são cobrados pelos
provedores pelo uso (a tela mostra o preço antes). O gateway tem um limite de gasto por dia (US$ 3 por
padrão). Quem tiver a sua chave de acesso gasta os seus créditos: guarde-a como uma senha.

### Codex

Toque em **Codex** na tela inicial ou escreva `/codex` na conversa. Escolha o projeto:

- **Repositório do GitHub** (precisa do token e da lista `LILY_GITHUB_REPOS` no gateway): o Codex lê, edita
  num ramo `lily-codex/...` e roda os testes no GitHub Actions. O ramo principal nunca é tocado.
- **Pasta do aparelho**: você escolhe a pasta pelo seletor do Android e o Codex só enxerga o que está
  nela. Aqui ele confere a sintaxe, mas **não roda testes** (o Android não roda Gradle nem Node).

Descreva o problema ou cole um erro. O Codex mostra o plano, lê os arquivos, pesquisa na internet quando
precisa (com as fontes) e **prepara** as mudanças: você vê o diff e a explicação, e só depois de tocar em
**Aprovar e aplicar** algo é gravado, com um ponto de restauração e o botão **Desfazer**. Se os testes
falharem, ele lê o erro e tenta de novo, no máximo 3 vezes, e para se o mesmo erro voltar. No fim ele diz
o que foi **verificado**, o que é **hipótese** e os riscos, e um segundo modelo (de outra empresa) revisa.
Compilar não basta para ele considerar a correção boa.

Modo automático (na tela do Codex): aplica sozinho só correções pequenas (até o número de linhas que você
escolher) sem problemas na verificação; dá para desfazer.

Se a L.I.L.Y fechar por um erro, o **L.I.L.Y Diagnóstico** tem o botão **Consertar com o Codex**: ele abre o
Codex com o relatório já preenchido.

### Fotos e vídeos grátis (1.3.2)

Com o gateway publicado (versão 1.1 ou mais nova), dá para criar **sem pagar nada**:

- **Imagens**: na tela inicial, **Gerar imagem** > *Onde gerar*: **Online grátis** (FLUX.1 [schnell],
  1024 × 1024, em segundos; até 150 por dia) ou **No aparelho** (iris-3b, lento e experimental). A tela
  mostra quantas grátis ainda restam hoje.
- **Vídeos**: o modelo **LTX-Video (grátis)** vem escolhido sozinho nos níveis Rápido e Equilibrado.
  Usa a cota grátis de GPU do Hugging Face: dá **1 a 2 vídeos por dia** (até 8 s, ~736×416). Com um
  token grátis do Hugging Face no gateway (`HF_TOKEN`), a cota é da sua conta; sem ele, é a cota
  anônima, menor. Mantenha a internet ligada até o fim: se a conexão cair, o Hugging Face cancela o vídeo.
- Os modelos pagos (fal.ai) só aparecem se a `FAL_KEY` estiver no gateway, marcados como **pago**. Sem
  essa chave, nada é cobrado.

### Video AI

Toque em **Vídeo** ou escreva `/video`. Descreva a cena (ou escolha uma foto para animar, ou continue um
vídeo pronto a partir do último quadro), escolha formato, duração, resolução e movimento de câmera. A tela
mostra o **preço estimado e o modelo** antes de gerar. Enquanto o vídeo é feito, você pode usar o celular
normalmente: a Liquid Island mostra o progresso e um aviso chega quando termina. Salve em Filmes/L.I.L.Y
(aparece na Gallery) ou compartilhe. A barra de progresso é uma estimativa pelo tempo; o provedor não informa
a porcentagem.

### L.I.L.Y Web (no computador)

Abra o endereço do gateway no Chrome ou no Edge: é a L.I.L.Y para o PC, com conversa, Codex (repositório do
GitHub ou uma pasta do computador), vídeo, ferramentas e a avatar. Em Configurações, cole a chave de acesso.
Para usar sem internet, escolha um **modelo local** (roda no navegador com WebGPU; o download só começa
depois que você escolhe e mostra o tamanho). Ela não tem o que só existe no celular (OCR, geração de imagem,
Liquid Island, avatar por cima de outros apps).

### Termos, privacidade e créditos

Configurações > Sobre > **Termos de uso**, **Privacidade** e **Créditos**. São os mesmos arquivos da pasta
`legal/` e também ficam na L.I.L.Y Web. Antes de compartilhar o projeto, preencha o seu nome e contato nos
arquivos (procure por colchetes, como `[nome do autor]` e `[e-mail ou canal de contato]`) e, de preferência, peça uma revisão jurídica.

## 10. Problemas comuns

**A L.I.L.Y AI abre e fecha (até a 1.2.0).** Era o WorkManager, uma biblioteca do Google que vem junto com o
LiteRT: ele iniciava antes da L.I.L.Y e quebrava porque o encolhimento do APK final apagava uma parte dele
(o Diagnóstico mostrou "Failed to create an instance of class androidx.work.impl.WorkDatabase"). A
**1.2.1** corrige: instale por cima, sem desinstalar.

**A L.I.L.Y AI abre e fecha (outro motivo).** Na tela externa, é a Motorola bloqueando um app não liberado: veja o começo
da parte 9.3. Na tela interna, abra o ícone **L.I.L.Y Diagnóstico** (parte 9.4) e toque em **Copiar
relatório** ou **Compartilhar**: o relatório diz em qual etapa a abertura parou e como o Android encerrou o
app. Tente também **Abrir em modo seguro**. Na 1.1.0, o texto das fotos (OCR), os rótulos, a
detecção de idioma, a tradução (também a do teclado) e o recorte não funcionavam no APK final; a 1.1.1
corrige isso.

**"App não instalado" ao atualizar.** O APK novo foi assinado com outra chave. Desinstale o antigo e
instale de novo (parte 8).

**O Cristal não aparece no painel de apps da tela externa.** Falta liberar na parte 4. Se a lista da
Motorola não mostrar o Cristal, use o Shizuku ou o comando `climanager` da parte 6.

**Toquei em um app e ele abriu na tela de dentro ou apareceu "abra o telefone para continuar".** A Motorola
bloqueou esse app na tela externa. O Cristal mostra um aviso com o botão **Por quê?** explicando como
liberar.

**Um app abre cortado ou com faixas pretas.** Toque longo no ícone > **Opções na tela externa**. Com o
Shizuku, reduza a **Escala** (por exemplo, 80%) ou ligue **Forçar redimensionamento**.

**As notificações pararam de aparecer depois de atualizar.** O Android às vezes desliga os acessos de apps
instalados por APK quando eles são atualizados. Abra Configurações do Cristal > Permissões e reative. Com o
Shizuku ativo uma vez (parte 5.2), o Cristal passa a religar a acessibilidade sozinho.

**A tela ficou lenta ou a bateria está indo rápido.** Configurações do Cristal > Aparência > Qualidade:
troque **Líquido** por **Blur** ou **Leve**, e a taxa de atualização para **60 Hz**. Deixe o **Desempenho
adaptativo** ligado.

**O vidro mostra o papel de parede do Cristal, não o do sistema.** É uma limitação do Android: apps comuns
não podem ler o papel de parede do sistema. Escolha a mesma imagem em Configurações do Cristal > Papel de
parede.

**Quero voltar ao normal.** Desligue "Assumir a tela externa" e a tela de bloqueio nas Configurações do
Cristal, ou simplesmente desinstale o app. Nada no sistema fica alterado. Se você usou a escala de algum
app e a tela externa ficou com tudo pequeno, abra o Cristal uma vez (ele desfaz a escala) ou rode
`adb shell wm density reset -d 1`.

**A esfera do Info Brief não chega na ilha e aparece um chip no teclado.** O Cristal não está instalado,
a Liquid Island está desligada, o teclado está na tela externa ou os dois APKs não são da mesma rodada.
Reinstale o teclado depois do Cristal, os dois da mesma versão.

**O lembrete não tocou na hora.** Confira Configurações > Info Brief: notificações permitidas e
"Alarmes e lembretes" liberado. Se o aparelho estava desligado na hora, o Cristal avisa assim que ligar.
