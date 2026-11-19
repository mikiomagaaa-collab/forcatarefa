# L.I.L.Y Gateway: o serviço online (passo a passo só com o celular)

O L.I.L.Y Gateway é um serviço pequeno que roda na **sua** conta Cloudflare. Ele existe para que
nenhuma chave de provedor fique dentro do APK ou do site: o app e a L.I.L.Y Web só conhecem o endereço
do gateway e uma **chave de acesso** que você cria. O gateway:

- lê ao vivo os modelos de programação e conversa no **OpenRouter** (Claude, GPT, Gemini, DeepSeek,
  Qwen e outros com uma só chave) e escolhe sempre a versão mais nova de cada família;
- gera vídeos pela fila do **fal.ai** (LTX-2, Wan 2.5, Kling 2.5, Veo 3.1, HunyuanVideo);
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
| fal.ai (pago pelo uso) | Video AI | Para vídeo |
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
2. No GitHub, edite `cristal-cover/gateway/wrangler.toml` (lápis no app do GitHub), tire o `#` das
   linhas do `[[kv_namespaces]]` e cole o ID no lugar de `COLE_O_ID_AQUI`.

### 3.3 Chaves dos provedores

- **OpenRouter**: openrouter.ai > *Keys* > *Create Key*. Adicione créditos em *Credits*.
- **fal.ai**: fal.ai > *Dashboard* > *Keys* > *Add key*. Adicione um método de pagamento.
- **Tavily** (opcional): app.tavily.com > *API Keys*. Ou **Brave**: api-dashboard.search.brave.com.
- **GitHub para o Codex** (opcional): github.com > *Settings* > *Developer settings* > *Personal access
  tokens* > *Fine-grained tokens* > *Generate*. Em *Repository access*, escolha **só** os repositórios
  que o Codex pode tocar. Permissões: *Contents: Read and write*, *Actions: Read and write*,
  *Metadata: Read*.

### 3.4 A sua chave de acesso

Crie uma senha longa e aleatória (40 caracteres ou mais, de um gerador de senhas). Ela é a
`LILY_ACCESS_KEY`: quem tiver essa chave usa os seus créditos. Guarde num gerenciador de senhas.

### 3.5 Segredos no GitHub e publicação

No repositório: *Settings* > *Secrets and variables* > *Actions* > *New repository secret*. Crie:

| Nome | Valor |
| --- | --- |
| `CLOUDFLARE_API_TOKEN` | o token do passo 3.1 |
| `CLOUDFLARE_ACCOUNT_ID` | o ID da conta |
| `LILY_ACCESS_KEY` | a chave do passo 3.4 |
| `OPENROUTER_API_KEY` | (opcional) |
| `FAL_KEY` | (opcional) |
| `TAVILY_API_KEY` ou `BRAVE_API_KEY` | (opcional) |
| `LILY_GITHUB_TOKEN` | (opcional) o token fine-grained |
| `LILY_GITHUB_REPOS` | (opcional) ex.: `seu-usuario/seu-repositorio` |

Depois: *Actions* > **L.I.L.Y Gateway - publicar** > *Run workflow*. O workflow roda os testes do
gateway e da L.I.L.Y Web, publica e copia os segredos. O endereço aparece no log, no formato
`https://lily-gateway.<seu-subdominio>.workers.dev`.

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
pytest), **só em ramos `lily-codex/...`**. As entradas são validadas de novo no workflow. O gateway
nunca grava no ramo principal nem altera arquivos de workflow. Depois de revisar, você junta o ramo
pelo próprio GitHub (pull request) quando quiser.

## 5. Segurança

- Chaves de provedor ficam só como segredos do Worker. O app guarda a chave de acesso cifrada pelo
  Android Keystore; a L.I.L.Y Web, só na aba (ou no computador, se você pedir).
- Sem `LILY_ACCESS_KEY`, o gateway recusa tudo. A comparação da chave não vaza pelo tempo de resposta.
- Os identificadores de vídeo são conferidos para a chave do fal.ai nunca ir para outro servidor.
- CORS fica desligado: a L.I.L.Y Web é servida pelo próprio gateway. Para abrir o site de outro
  endereço, coloque esse endereço em `ALLOWED_ORIGINS` no `wrangler.toml`.
- **A chave vazou?** Crie outra, atualize o segredo `LILY_ACCESS_KEY` no GitHub e rode a publicação
  de novo. A antiga para de funcionar na hora.

## 6. Compartilhar com outras pessoas

Quem usa o seu gateway usa os seus créditos. Para uma turma ou grupo, cada pessoa pode publicar o
próprio gateway (é de graça na Cloudflare) com as próprias chaves. Um gateway público com contas,
cotas e pagamento está no plano (`docs/ROADMAP.md`).

## 7. O que já foi conferido e o que não

- Conferido: os 23 testes do gateway (provedores simulados com o formato das APIs públicas), o app
  inteiro contra um gateway de teste local (conversa, vídeo do pedido ao arquivo, Codex com CI e
  auto-reparo) e a L.I.L.Y Web num Chromium contra o código real do gateway.
- **Não conferido**: chamadas reais aos provedores (nenhuma chave foi usada no desenvolvimento). Os
  identificadores dos modelos de vídeo e os preços vêm das páginas do fal.ai (novembro de 2026); se o
  fal.ai mudar um parâmetro, o gateway tenta de novo só com a descrição e a imagem e avisa no app.
