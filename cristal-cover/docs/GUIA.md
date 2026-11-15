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

> **Qual arquivo usar?** O `cristal-cover-apk` é a versão otimizada (cerca de 3 MB): é essa que você quer.
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

## 10. Problemas comuns

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
