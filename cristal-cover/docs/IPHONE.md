# L.I.L.Y para iPhone

Há dois jeitos de ter a L.I.L.Y no iPhone.

## 1. App nativo (SwiftUI) — pasta `cristal-cover/ios`

O que tem:

- **Conversa** pelo seu L.I.L.Y Gateway, em pedaços, com o modelo grátis primeiro (o mesmo roteador do
  Android e da Web). Uma pergunta nova a qualquer hora para a resposta anterior e é respondida na hora.
- **Voz**: falar (reconhecimento de fala do iPhone, no aparelho quando o português está disponível) e
  ouvir as respostas (vozes do iPhone). Conversa contínua opcional (ouve de novo depois de responder).
- **Avatar** desenhada em código, com os estados da L.I.L.Y (ouvindo, pensando, falando, sucesso, erro).
- **Criar**: imagens grátis (FLUX pelo Workers AI do gateway) e vídeos grátis (LTX-Video pelo Hugging
  Face), com salvar nas Fotos e compartilhar.
- **Ajustes**: endereço do gateway, chave de acesso no **Keychain**, consentimento, perfil de respostas,
  voz, termos, privacidade e créditos.

O que ainda não tem no iPhone (só no Android): modelos rodando no próprio aparelho (no iPhone ela precisa
do gateway e de internet), Codex, OCR, Liquid Island e avatar por cima de outros apps.

### Como o app é compilado

O workflow **L.I.L.Y iPhone - compilar** (`.github/workflows/lily-ios.yml`) roda num Mac do GitHub a
cada mudança em `cristal-cover/ios`: gera o projeto do Xcode (XcodeGen), roda os testes num simulador
de iPhone e compila para iPhone. Sai o artefato **lily-iphone-ipa** com o arquivo
`LILY-iPhone-N-sem-assinatura.ipa`.

> Os Macs do GitHub Actions são de graça em repositórios públicos; em privados, cada minuto de Mac conta
> 10 vezes na cota mensal.

### Como instalar no iPhone (a Apple exige assinatura)

A Apple não deixa instalar um app sem assinatura. Os caminhos são:

| Caminho | Custo | Precisa de computador? | Validade |
| --- | --- | --- | --- |
| **AltStore / SideStore / Sideloadly** com o seu Apple ID (assinam o .ipa) | Grátis | Sim, na primeira vez (o SideStore depois renova pelo próprio iPhone) | 7 dias (renova) |
| **TestFlight** (conta Apple Developer) | US$ 99 por ano | Não (o envio é pelo GitHub com os segredos da Apple) | 90 dias por versão |
| **App Store** | US$ 99 por ano + revisão da Apple | Não | Permanente |

Passos com o AltStore (Windows ou Mac): instale o AltServer no computador, conecte o iPhone, instale o
AltStore no iPhone, abra o `.ipa` baixado do GitHub pelo AltStore e entre com o seu Apple ID. Depois,
**Ajustes > Geral > VPN e Gerenciamento de Dispositivo** > confie no seu Apple ID. Com o Apple ID grátis,
o app vale 7 dias; o AltStore renova sozinho quando o computador está na mesma rede.

O envio automático para o TestFlight fica pronto para ligar quando houver a conta Apple Developer
(segredos do certificado, do perfil e da chave da App Store Connect).

## 2. Sem instalar nada: a L.I.L.Y Web na Tela de Início

No Safari do iPhone, abra o endereço do seu gateway > botão **Compartilhar** > **Adicionar à Tela de
Início**. Ela abre em tela cheia, como um app, com conversa, Codex, vídeo, imagens e avatar. Funciona hoje,
sem conta da Apple e sem computador.

## O que foi conferido e o que não

- Conferido no Mac do GitHub: o projeto gera, compila para iPhone e os testes de lógica passam no
  simulador (pedaços do OpenRouter e do Workers AI, roteador com o grátis primeiro, histórico sem
  respostas paradas, só https, respostas do gateway com campos faltando, instrução da L.I.L.Y).
- **Não conferido**: o app rodando num iPhone de verdade (microfone, voz, Fotos) e as chamadas reais ao
  gateway publicado.
