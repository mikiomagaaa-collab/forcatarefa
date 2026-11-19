# Créditos e avisos de terceiros

## PROJECT LIQUID FLIP e L.I.L.Y

- Criação, direção e design do projeto: **[nome do autor]**
- Código escrito com o auxílio de inteligência artificial (Claude Code, da Anthropic), sob a direção
  do autor, que revisou e testou as entregas.
- A L.I.L.Y (nome, avatar holográfica, esfera e identidade visual) é uma criação original do projeto.
  A avatar é desenhada por código, sem usar imagens ou personagens de terceiros.

Licença do código: **Apache License 2.0** (arquivo `LICENSE`).

## Bibliotecas dos apps Android

| Biblioteca | Autor | Licença |
| --- | --- | --- |
| Kotlin, kotlinx.coroutines, kotlinx.serialization | JetBrains | Apache 2.0 |
| Jetpack Compose, AndroidX (Core, Activity, Lifecycle, Window, Room, DataStore, WorkManager, Startup) | Google / AOSP | Apache 2.0 |
| Material Icons (pacote estendido) | Google | Apache 2.0 |
| Hilt / Dagger | Google | Apache 2.0 |
| LiteRT e LiteRT-LM (motores de IA no aparelho) | Google AI Edge | Apache 2.0 |
| ML Kit (texto, rótulos, idioma, tradução, recorte) | Google | [Termos de serviço do ML Kit](https://developers.google.com/ml-kit/terms) |
| Shizuku API | RikkaApps | Licença do projeto Shizuku-API (consulte o repositório) |
| Motor Kotlin do iris-3b | litert-community | Apache 2.0 (texto incluído no app) |

Só nos testes: JUnit (Eclipse Public License 1.0), Robolectric (MIT), AndroidX Test (Apache 2.0).

## Fontes

| Fonte | Autor | Licença |
| --- | --- | --- |
| Inter | Rasmus Andersson | SIL Open Font License 1.1 (texto incluído no app) |

## Modelos de IA (baixados pelo Model Hub, por escolha da pessoa)

| Modelo | Desenvolvedor | Licença |
| --- | --- | --- |
| Qwen3 0.6B e 1.7B, Qwen3.5 0.8B Vision | Alibaba (Qwen) | Apache 2.0 |
| Gemma 4 E2B | Google | Apache 2.0 |
| Ministral 3 3B | Mistral AI | Apache 2.0 |
| Phi-4 mini | Microsoft | MIT |
| SmolVLM2 500M | Hugging Face | Apache 2.0 |
| Real-ESRGAN General x4v3 | Xintao Wang e colaboradores | BSD 3-Clause |
| iris-3b Turbo | litert-community | Apache 2.0 |

Os arquivos dos modelos não fazem parte do código do projeto: são baixados dos repositórios oficiais,
com versões fixadas e conferência de SHA-256.

## Modelos e serviços online (pelo L.I.L.Y Gateway, opcional)

| Serviço | Uso | Termos |
| --- | --- | --- |
| OpenRouter | Acesso aos modelos de programação e conversa (Anthropic, OpenAI, Google, DeepSeek, Qwen e outros) | Termos do OpenRouter e de cada fornecedor de modelo |
| fal.ai | Geração de vídeo (LTX-2 da Lightricks, Wan da Alibaba, Kling da Kuaishou, Veo do Google, HunyuanVideo da Tencent) | Termos do fal.ai e licença de cada modelo |
| Tavily ou Brave Search | Pesquisa técnica do Codex | Termos do provedor |
| GitHub e GitHub Actions | Repositórios autorizados e testes do Codex | Termos do GitHub |
| Cloudflare Workers | Hospedagem do gateway | Termos da Cloudflare |
| Open-Meteo | Clima no Cristal Cover | CC BY 4.0 (dados) |

## L.I.L.Y Web

A L.I.L.Y Web usa apenas HTML, CSS e JavaScript escritos para o projeto. O modo "no navegador"
carrega, só quando você escolhe, a biblioteca **WebLLM** (MLC AI, Apache 2.0) e um modelo compatível
com WebGPU.

## Marcas

Motorola, razr, Android, Google, Gemma, Apple, iPhone, Samsung, Xiaomi, Anthropic, Claude, OpenAI,
GPT, Qwen, Kling, Veo, GitHub, Cloudflare e as demais marcas citadas pertencem aos seus donos. Elas
aparecem apenas para identificar compatibilidade, provedores ou inspirações de design. O projeto não
é afiliado a essas empresas.
