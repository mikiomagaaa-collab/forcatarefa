# Plano do PROJECT LIQUID FLIP (lista de planejamentos)

Atualizado em 19 de novembro de 2026. Status: ✅ feito · 🔨 em andamento · 📋 planejado.

## 1. Já entregue

| Item | Status |
| --- | --- |
| Cristal Cover (tela externa, launcher, bloqueio, Liquid Island, Central, widgets, transição de dobra) | ✅ |
| L.I.L.Y AI no aparelho (conversa, Model Hub, roteador, ferramentas, voz, diagnóstico) | ✅ |
| L.I.L.Y Keyboard e L.I.L.Y Gallery | ✅ |
| L.I.L.Y Gateway (serviço online seguro, com limite de gasto) | ✅ |
| L.I.L.Y Codex, Video AI e L.I.L.Y Avatar | ✅ (dependem do gateway configurado; ainda não usados com chaves reais) |
| Termos de Uso, Privacidade, Créditos, licença Apache 2.0, segurança e contribuição | ✅ (modelos para revisão jurídica) |
| L.I.L.Y Web para PC | ✅ primeira versão |

## 2. Compatibilidade com todos os celulares

### 2.1 Versão mínima do Android para produção: **Android 12 (API 31)**

Por quê:

- **Quem consegue rodar IA no aparelho já está no Android 12 ou mais novo.** Os modelos da L.I.L.Y
  pedem processador de 64 bits e de 4 a 8 GB de RAM, o que é comum em aparelhos de 2021 para cá, que
  chegaram com o Android 11 ou 12 e foram atualizados.
- **O custo de compatibilidade é pequeno e foi medido.** Com o lint do Android (verificação
  `NewApi`), simulando a versão mínima mais baixa, o resultado foi:

| App | Chamadas acima do Android 12 que precisariam de proteção | Situação |
| --- | --- | --- |
| L.I.L.Y AI | 6 (efeito da esfera com `RuntimeShader`, Android 13) | Trocar por um degradê comum abaixo do 13 |
| L.I.L.Y Keyboard | 0 | Já compatível |
| L.I.L.Y Gallery | 0 | Já compatível |
| Cristal Cover | 54 (efeitos de vidro, ilha e transição com `RuntimeShader`, permissões do Android 13) | Fica no Android 13+ (ver 2.3) |

Para o Android 10 e 11, o custo sobe (L.I.L.Y AI: mais 20 chamadas; Cristal: mais 47), e os aparelhos
dessa época raramente têm memória para os modelos. Nesses aparelhos, o caminho é o **modo online** da
L.I.L.Y (pelo gateway) ou a **L.I.L.Y Web**.

### 2.2 Uma versão para cada tipo de aparelho

| Versão | Para quem | Diferença |
| --- | --- | --- |
| L.I.L.Y AI (arm64) | Celulares de 64 bits (quase todos desde 2019) | Completa: IA no aparelho e online |
| L.I.L.Y AI Lite (armeabi-v7a) | Celulares de 32 bits antigos | Só online e ML Kit (os motores LiteRT-LM não têm versão de 32 bits estável: confirmar antes) |
| L.I.L.Y AI (x86_64) | Chromebooks e emuladores | A confirmar se o LiteRT-LM publica bibliotecas x86_64 |
| L.I.L.Y Keyboard e Gallery | Todos os Android 12+ | Sem diferenças |
| Cristal Launcher | Qualquer celular Android 13+ | Tela inicial, widgets, bloqueio, Liquid Island, Central |
| Cristal Cover | Celulares dobráveis com tela externa | O que depende da tela externa (por fabricante, ver 2.3) |

### 2.3 Fabricantes

| Fabricante | O que muda | Plano |
| --- | --- | --- |
| **Motorola** (razr 40/50/60, Edge, Moto G) | Tela externa só abre apps liberados; economia de bateria suave | ✅ Suportado hoje (Razr 40 Ultra como referência). Testar razr 50/60 |
| **Samsung** (Galaxy S, A, Z Flip, Z Fold) | "Apps em suspensão" e "suspensão profunda" encerram apps em segundo plano; a tela externa do Z Flip usa o Good Lock (MultiStar) e widgets próprios | 📋 Tela de ajustes com o caminho até "Não suspender"; adaptar a tela externa do Z Flip (tamanhos e regras diferentes do Razr) |
| **Xiaomi / Redmi / POCO** (HyperOS, MIUI) | "Início automático" desligado por padrão; sobreposição precisa de "Exibir janelas pop-up em segundo plano"; economia de bateria agressiva | 📋 Tela de ajustes com atalhos para Início automático, Sem restrições de bateria e Pop-ups em segundo plano (com plano B para as configurações do app quando o atalho não existir) |
| **Google Pixel** | Android puro | 📋 Testar Live Updates e Liquid Island |
| **OnePlus, OPPO, realme, vivo** | Gestão de bateria própria, parecida com a da Xiaomi | 📋 Mesmo modelo de tela de ajustes |
| **Huawei / Honor sem Google** | Sem Google Play Services: o ML Kit não funciona | 📋 Versão sem ML Kit (OCR e tradução viram online) |

### 2.4 Como garantir

1. 📋 Baixar a versão mínima da L.I.L.Y AI, do Teclado e da Gallery para o Android 12 (as 6 trocas
   da tabela 2.1) e rodar os testes em várias versões (Robolectric com SDK 31 a 36 no CI).
2. 📋 Uma tela "Ajustes deste aparelho" que detecta o fabricante e mostra só o que vale para ele.
3. 📋 Matriz de testes em aparelhos reais (Motorola, Samsung, Xiaomi, Pixel): abrir, conversar,
   voz, avatar flutuante, notificações ao vivo, Codex e Vídeo.
4. 📋 APKs separados por arquitetura no GitHub Releases e no workflow.

## 3. Projeto distribuído e global

| Item | Status |
| --- | --- |
| Licença aberta (Apache 2.0), termos, privacidade, créditos, segurança, contribuição | ✅ (revisão jurídica recomendada) |
| Preencher autor e contato nos documentos (procure por colchetes: `[nome do autor]`, `[e-mail ou canal de contato]`) | 📋 você |
| Inglês e espanhol nos apps e na L.I.L.Y Web | 📋 |
| Página de download com os APKs e a L.I.L.Y Web | 📋 |
| Gateway público para quem não quer montar o seu (contas, cotas, pagamento e prevenção de abuso) | 📋 Exige decisão sobre quem paga os custos |
| F-Droid | 📋 Exige uma versão sem ML Kit (é proprietário) |
| Google Play | 📋 A L.I.L.Y AI, o Teclado e a Gallery podem ir; o Cristal Cover usa o serviço de acessibilidade para funções de launcher, o que a política da Play restringe: distribuir pelo GitHub |

## 4. Próximos recursos

- 📋 Codex: executar testes de projetos JavaScript e Python no próprio navegador (L.I.L.Y Web).
- 📋 Vídeo: modelos que rodem no aparelho quando houver algum compatível com o Snapdragon 8+ Gen 1.
- 📋 Avatar: widget próprio na tela inicial e versão na Liquid Island do Cristal.
- 📋 L.I.L.Y Web: instalar como app (PWA) e funcionar sem internet com os modelos do navegador.

## Google Play (publicar a L.I.L.Y AI na loja)

Requisitos conferidos nas páginas da Google em novembro de 2026 (confira de novo antes de começar):

| Passo | Situação |
| --- | --- |
| Conta de desenvolvedor no Play Console (taxa única, informada como US$ 25) e verificação de identidade | 📋 você |
| Conta pessoal nova: **teste fechado com pelo menos 12 testadores por 14 dias seguidos** antes de liberar para todos | 📋 você (amigos, turma) |
| **Denúncia de conteúdo dentro do app** (política de conteúdo gerado por IA: conversa, imagens e vídeos precisam de um jeito de denunciar respostas ofensivas sem sair do app) | ⏳ falta fazer no app |
| Pacote **AAB** assinado com uma **chave de envio própria** guardada como segredo do GitHub (a chave de CI que está no repositório não pode ser usada na loja) | ⏳ falta fazer no build |
| Política de privacidade num endereço público (pode ser `https://<seu-gateway>/legal/PRIVACIDADE.md` com o autor e o contato preenchidos) | 📋 você |
| Formulário de segurança dos dados e classificação etária no Play Console (usar `legal/PRIVACIDADE.md`) | 📋 você |
| Declarações de permissões: serviço em primeiro plano (dataSync e mediaProcessing, com vídeo mostrando o uso), sobreposição (avatar flutuante) | 📋 você, com textos prontos a preparar |
| O launcher **Cristal** usa serviço de acessibilidade e leitor de notificações: a Play tem regras rígidas para isso; publicar primeiro só a L.I.L.Y AI | Recomendado |

