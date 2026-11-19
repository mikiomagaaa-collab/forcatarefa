# L.I.L.Y Gateway: o serviço online (passo a passo só com o celular)

O L.I.L.Y Gateway é um serviço pequeno que roda na **sua** conta Cloudflare. Ele existe para que
nenhuma chave de provedor fique dentro do APK ou do site: o app e a L.I.L.Y Web só conhecem o endereço
do gateway e uma **chave de acesso** que você cria. O gateway:

- lê ao vivo os modelos de programação e conversa no **OpenRouter** (Claude, GPT, Gemini, DeepSeek,
  Qwen e outros com uma só chave) e escolhe sempre a versão mais nova de cada família;
- responde **qualquer pergunta de graça** (beta) com o gpt-oss-20b pelo Workers AI da sua conta
  Cloudflare, sem chave nenhuma (cerca de 150 respostas por dia, divididas com as imagens);
- gera **imagens grátis** pelo Workers AI da sua própria conta Cloudflare (FLUX.1 [schnell], 1024×1024,
  cerca de 150 por dia, sem chave nenhuma);
- gera **vídeos grátis** pelo Space oficial do LTX-Video (Lightricks) no Hugging Face, dentro da cota
  diária grátis de GPU (poucos vídeos por dia), e, se você quiser pagar, pela fila do **fal.ai** (LTX-2,
  Wan 2.5, Kling 2.5, Veo 3.1, HunyuanVideo);
- pesquisa na internet para o Codex (**Tavily** ou **Brave Search**);
- lê e grava nos repositórios autorizados do Codex e roda os testes no **GitHub Actions**;
- serve a **L.I.L.Y Web** (o site para o computador) no mesmo endereço;
- recusa tudo sem a chave de acesso e tem um **limite de gasto por dia**.

Código: `cristal-cover/gateway` (testes: `node --test test/*.test.js`).

## 1. O que você precisa

| Conta | Para quê | Obrigatória? |
| --- | --- | --- |
| Cloudflare (grátis) | Hospedar o gateway e a L.I.L.Y Web | Sim |
| GitHub (o repositório do projeto) | Publicar com um toque (Actions) | Sim, para publicar pelo celular |
| OpenRouter (créditos pré-pagos) | Codex e modelos online na conversa | Para o Codex e a conversa online |
| Hugging Face (grátis) | Vídeo grátis com a cota da sua conta (token "read") | Opcional: sem ela, o vídeo grátis usa a cota anônima, menor |
| fal.ai (pago pelo uso) | Vídeos pagos (mais longos e melhores) | Opcional |
| Tavily (1.000 buscas grátis por mês) ou Brave Search | Pesquisa do Codex | Opcional |
| Token do GitHub "fine-grained" | Codex em repositórios + testes no CI | Opcional |

Cada recurso só aparece quando a chave dele está configurada; o app mostra o que falta em
Configurações > Serviço online.

## 2. Quanto custa (valores de referência de novembro de 2026)

O app e a L.I.L.Y Web mostram o preço **atual** de cada modelo (lido no provedor) e a estimativa antes
de gerar um vídeo. Os números abaixo são só para ter uma ideia:

| Uso | Custo aproximado |
| --- | --- |
| Hospedagem (Cloudflare Workers, plano grátis) | US$ 0 (até 100 mil pedidos por dia) |
| **Conversa online grátis (beta, gpt-oss-20b)** | **US$ 0**: ~55 "neurons" por resposta típica, dentro dos 10.000 grátis do Workers AI por dia (divididos com as imagens; o gateway para em `FREE_NEURONS_PER_DAY` = 9.500) |
| **Imagem online (FLUX.1 [schnell], 1024×1024)** | **US$ 0**: até `FREE_IMAGES_PER_DAY` (150) por dia, dentro dos 10.000 "neurons" grátis do Workers AI |
| **Vídeo grátis (LTX-Video, até 8 s, ~736×416)** | **US$ 0**: cota diária grátis de GPU do Hugging Face (cerca de 1 a 2 vídeos por dia por conta; cada vídeo reserva ~1 min de GPU "xlarge", que conta em dobro) |
| Uma resposta típica de conversa (2 mil tokens de entrada, 800 de saída) com DeepSeek V4 Pro | ~US$ 0,002 |
| A mesma resposta com Claude Opus | ~US$ 0,03 |
| Uma tarefa do Codex (10 a 25 passos do agente + revisão) | de ~US$ 0,02 (modelos fortes baratos) a ~US$ 0,60 (raciocínio máximo) |
| Uma pesquisa do Codex (Tavily) | ~US$ 0,008 (as 1.000 primeiras do mês são grátis) |
| Um clipe de vídeo de 6 s com LTX-2 | ~US$ 0,24 a 0,36 |
| Um clipe de 8 s com Veo 3.1 Fast | ~US$ 0,80 (sem som) a 1,20 (com som) |
| Testes do Codex no GitHub Actions | Grátis em repositórios públicos; 2.000 min/mês nos privados (plano grátis) |

O limite diário do gateway vem em **US$ 3** (`DAILY_BUDGET_USD`). Quando o gasto do dia chega lá, o
gateway recusa novos pedidos até o dia seguinte (horário UTC). Para o limite funcionar, ligue o KV
`LILY_USAGE` (passo 3.2).

> Plano grátis da Cloudflare: cada pedido tem pouco tempo de processador. O gateway foi feito para
> gastar o mínimo (repassa o texto em pedaços sem reprocessar), mas, se aparecer o erro 1102 ("Worker
> exceeded resource limits"), o plano pago (US$ 5 por mês) resolve.

## 3. Passo a passo pelo celular

### 3.1 Cloudflare

1. Crie a conta em dash.cloudflare.com (grátis).
2. **ID da conta**: no painel, abra *Workers e Pages*; o "Account ID" aparece na lateral. Copie.
3. **Token de API**: no ícone de perfil > *My Profile* > *API Tokens* > *Create Token* > modelo
   **"Edit Cloudflare Workers"** > *Continue* > *Create Token*. Copie (só aparece uma vez).

### 3.2 Limite de gasto (recomendado)

1. No painel da Cloudflare: *Storage & Databases* > *KV* > *Create* com o nome `LILY_USAGE`. Copie o ID.
2. No GitHub (no ramo do projeto), edite `cristal-cover/gateway/wrangler.toml` pelo lápis, tire o `#` das
   linhas do `[[kv_namespaces]]` e cole o ID no lugar de `COLE_O_ID_AQUI`.

### 3.3 Chaves dos provedores (todas opcionais)

**Só grátis, sem pagar nada:** pule o OpenRouter e o fal.ai. A conversa grátis e as imagens grátis já
funcionam sozinhas (Workers AI) e o vídeo grátis também (Hugging Face). Para mais vídeos grátis por dia, crie uma conta grátis
no huggingface.co > *Settings* > *Access Tokens* > *Create new token* > tipo **Read**, e salve como o
segredo `HF_TOKEN`. Ligue o KV `LILY_USAGE` (passo 3.2) para o gateway contar as imagens do dia e parar
antes de passar da parte grátis.

- **OpenRouter**: openrouter.ai > *Keys* > *Create Key*. Adicione créditos em *Credits*.
- **fal.ai**: fal.ai > *Dashboard* > *Keys* > *Add key*. Adicione um método de pagamento.
- **Tavily** (opcional): app.tavily.com > *API Keys*. Ou **Brave**: api-dashboard.search.brave.com.
- **GitHub para o Codex** (opcional): github.com > *Settings* > *Developer settings* > *Personal access
  tokens* > *Fine-grained tokens* > *Generate*. Em *Repository access*, escolha **só** os repositórios
  que o Codex pode tocar. Permissões: *Contents: Read and write*, *Actions: Read and write*,
  *Metadata: Read*.

### 3.4 A sua chave de acesso (você mesmo cria)

A chave de acesso **não vem de nenhum site**: é uma senha que você inventa e que o gateway passa a
exigir. Ela é a `LILY_ACCESS_KEY`. Quem tiver essa chave usa os seus créditos, então:

- faça uma senha longa e aleatória, de **40 caracteres ou mais** (o app recusa menos de 16), só com
  letras e números, para não dar problema ao colar;
- no celular, o jeito mais fácil é o **gerador de senhas** do seu gerenciador (Gerenciador de Senhas do
  Google, Samsung Pass, Bitwarden, 1Password…): crie um item "L.I.L.Y Gateway", gere a senha com 40+
  caracteres e salve. Assim ela fica guardada para colar no GitHub e no app;
- não mande a chave por mensagem nem a coloque em arquivos do repositório.

A mesma chave vai em dois lugares: no segredo `LILY_ACCESS_KEY` do GitHub (passo 3.5) e no app (passo
3.6). Para trocar, gere outra, atualize o segredo e publique de novo.

### 3.5 Segredos no GitHub e publicação

No repositório: *Settings* > *Secrets and variables* > *Actions* > *New repository secret*. Crie:

| Nome | Valor |
| --- | --- |
| `CLOUDFLARE_API_TOKEN` | o token do passo 3.1 |
| `CLOUDFLARE_ACCOUNT_ID` | o ID da conta |
| `LILY_ACCESS_KEY` | a chave do passo 3.4 |
| `OPENROUTER_API_KEY` | (opcional) |
| `FAL_KEY` | (opcional, pago) |
| `HF_TOKEN` | (opcional, grátis) token "read" do Hugging Face |
| `TAVILY_API_KEY` ou `BRAVE_API_KEY` | (opcional) |
| `LILY_GITHUB_TOKEN` | (opcional) o token fine-grained |
| `LILY_GITHUB_REPOS` | (opcional) ex.: `seu-usuario/seu-repositorio` |

Depois, **peça a publicação editando um arquivo** (o GitHub só mostra o botão "Run workflow" para
workflows que estão no ramo principal, e o projeto fica no ramo dele):

1. No navegador do celular, abra o repositório no GitHub e troque para o **ramo do projeto** (o botão
   com o nome do ramo, acima da lista de arquivos).
2. Abra `cristal-cover/gateway/PUBLICAR.md`, toque no **lápis** (Edit this file), mude a data da última
   linha e toque em **Commit changes** (deixe "Commit directly" no mesmo ramo).
3. Abra a aba **Actions** > **L.I.L.Y Gateway - publicar**. O workflow roda os testes do gateway e da
   L.I.L.Y Web, publica e copia os segredos. No resumo da execução aparece o endereço, no formato
   `https://lily-gateway.<seu-subdominio>.workers.dev`. Se faltar algum segredo, o resumo diz qual e
   nada é publicado.

Para publicar de novo (chave nova, código novo), edite o `PUBLICAR.md` outra vez. Se um dia o projeto
for para o ramo principal, o botão *Run workflow* também passa a funcionar.

### 3.6 Conectar

- **No app (L.I.L.Y AI)**: Configurações > *Serviço online* > endereço e chave > *Salvar e testar* >
  ligue **Permitir processamento externo**.
- **No computador**: abra o endereço do gateway no navegador (Chrome ou Edge). É a L.I.L.Y Web.
  Em Configurações, cole a chave (marque "lembrar" só num computador seu).

### Pelo computador (alternativa)

```sh
cd cristal-cover/gateway
npm run build:web
npx wrangler@4 deploy
npx wrangler@4 secret put LILY_ACCESS_KEY   # e as outras chaves
```

## 4. Codex com testes no GitHub Actions

O workflow `.github/workflows/lily-codex-check.yml` roda os testes pedidos pelo Codex (Gradle, npm ou
pytest), **só em ramos `lily-codex/...`**. Para pedir os testes, o gateway grava o arquivo
`.lily-codex/check.json` (executor, tarefas e pasta) no ramo do Codex, e o workflow roda nesse envio;
assim funciona mesmo que o workflow não esteja no ramo principal. As entradas são validadas de novo no
workflow. O gateway nunca grava no ramo principal nem altera arquivos de workflow.

**Requisito:** o ramo de origem que você escolhe no Codex precisa ter o arquivo
`.github/workflows/lily-codex-check.yml` (os ramos `lily-codex/...` nascem dele). Neste repositório, o
ramo do projeto já tem. Em outro repositório, copie esse arquivo para o ramo de origem. Sem ele, o
Codex avisa "não tem o workflow de testes do Codex" e a verificação fica só na sintaxe.

Depois de revisar, você junta o ramo pelo próprio GitHub (pull request) quando quiser; o arquivo
`.lily-codex/check.json` pode ser apagado antes.

## 5. Segurança

- Chaves de provedor ficam só como segredos do Worker. O app guarda a chave de acesso cifrada pelo
  Android Keystore; a L.I.L.Y Web, só na aba (ou no computador, se você pedir).
- Sem `LILY_ACCESS_KEY`, o gateway recusa tudo. A comparação da chave não vaza pelo tempo de resposta.
- Os identificadores de vídeo são conferidos para a chave do fal.ai nunca ir para outro servidor.
- CORS fica desligado: a L.I.L.Y Web é servida pelo próprio gateway. Para abrir o site de outro
  endereço, coloque esse endereço em `ALLOWED_ORIGINS` no `wrangler.toml`.
- **A chave vazou?** Crie outra, atualize o segredo `LILY_ACCESS_KEY` no GitHub e edite o
  `PUBLICAR.md` de novo. A antiga para de funcionar assim que a publicação termina.

## 6. Compartilhar com outras pessoas

Quem usa o seu gateway usa os seus créditos. Para uma turma ou grupo, cada pessoa pode publicar o
próprio gateway (é de graça na Cloudflare) com as próprias chaves. Um gateway público com contas,
cotas e pagamento está no plano (`docs/ROADMAP.md`).

## 7. Conversa, fotos e vídeos grátis: como funcionam e os limites reais

- **Conversa (beta)**: o modelo `free/gpt-oss-20b` aparece primeiro na lista de modelos online do app e da
  L.I.L.Y Web e é o escolhido na conversa (menos no perfil Qualidade, que vai aos pagos, se houver). O
  gateway chama `@cf/openai/gpt-oss-20b` (OpenAI, pesos abertos, Apache 2.0) e devolve a resposta em
  pedaços. Cada resposta gasta neurons pela tabela da Cloudflare (18.182 por milhão de tokens de entrada e
  27.273 por milhão de saída); o gateway soma no KV e para em `FREE_NEURONS_PER_DAY` (9.500), o que dá
  cerca de 150 respostas por dia sem imagens. Para desligar: `FREE_CHAT = "off"` no `wrangler.toml`.

- **Imagens**: o gateway chama o modelo `@cf/black-forest-labs/flux-1-schnell` pelo Workers AI da sua
  conta Cloudflare (binding `AI` no `wrangler.toml`, já ligado). A Cloudflare dá 10.000 "neurons" grátis
  por dia; uma imagem 1024×1024 com 4 etapas gasta ~58, então cabem ~170. O gateway para em 150 por dia
  (`FREE_IMAGES_PER_DAY`), contando no KV `LILY_USAGE`. Sem o KV, no plano grátis da Cloudflare o próprio
  Workers AI recusa depois do limite (sem cobrar); no plano pago, **ligue o KV** para não passar.
- **Vídeos**: o gateway usa a API do Space público `Lightricks/ltx-video-distilled` (LTX-Video 0.9.8). O
  Space roda em GPU compartilhada (ZeroGPU); cada vídeo reserva ~60 s de GPU no tamanho "xlarge", que
  conta em dobro. A cota diária grátis do Hugging Face é de poucos minutos por conta, então saem 1 a 2
  vídeos por dia com o seu `HF_TOKEN` (menos sem ele). Quando acaba, o app mostra a mensagem do Hugging
  Face com o tempo de espera.
- O Space cancela o vídeo se a conexão de acompanhamento cair. Por isso o app e a L.I.L.Y Web ficam
  conectados até o fim (no app, num serviço em primeiro plano). Se a internet cair, gere de novo.
- O Space é de terceiros (Lightricks/Hugging Face): pode ficar lento, mudar ou sair do ar. O gateway
  confere o formato da resposta e só baixa vídeos do próprio Space.

## 8. O que já foi conferido e o que não

- Conferido: os 32 testes do gateway (inclui as imagens grátis e o vídeo grátis com o formato real do
  Gradio 5.42, lido no código do próprio Gradio e do Space) (provedores simulados com o formato das APIs públicas), o app
  inteiro contra um gateway de teste local (conversa, vídeo do pedido ao arquivo, Codex com CI e
  auto-reparo) e a L.I.L.Y Web num Chromium contra o código real do gateway.
- **Não conferido**: chamadas reais aos provedores (nenhuma chave foi usada no desenvolvimento; o
  endereço do Space `*.hf.space` não abre deste ambiente de desenvolvimento, só o huggingface.co). Os
  identificadores dos modelos de vídeo e os preços vêm das páginas do fal.ai (novembro de 2026); se o
  fal.ai mudar um parâmetro, o gateway tenta de novo só com a descrição e a imagem e avisa no app.
