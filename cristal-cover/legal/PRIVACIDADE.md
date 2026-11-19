# Política de Privacidade — PROJECT LIQUID FLIP e L.I.L.Y

Vigência: 19 de novembro de 2026 · Versão 1.0

Esta política explica quais dados os apps do PROJECT LIQUID FLIP (Cristal Cover, L.I.L.Y AI,
L.I.L.Y Keyboard, L.I.L.Y Gallery), a L.I.L.Y Web e o L.I.L.Y Gateway usam, onde ficam e para onde
podem ir. Ela segue a Lei Geral de Proteção de Dados (LGPD, Lei 13.709/2018).

> Este texto é um modelo preparado junto com o projeto e descreve o que o código faz hoje. Antes de
> distribuir publicamente, peça a revisão de alguém da área jurídica e preencha os campos entre
> colchetes.

## Resumo

- **Por padrão, tudo fica no seu aparelho.** Conversas, memórias, fotos, áudios, documentos e o que
  você digita são processados localmente.
- **O projeto não tem servidores próprios que coletam dados**, não tem contas, anúncios, rastreadores
  nem estatísticas de uso.
- **Recursos online são opcionais.** Só funcionam depois que você conecta um L.I.L.Y Gateway e aceita
  o aviso de processamento externo. Cada tela avisa quando algo é online.
- Você pode apagar seus dados no próprio app ou desinstalando-o.

## 1. Dados que ficam no aparelho

| Dado | Onde | Para quê |
| --- | --- | --- |
| Conversas, projetos e anexos da L.I.L.Y | Armazenamento privado do app | Histórico e continuidade das conversas |
| Memórias | Armazenamento privado do app | Só o que você pede para guardar; a L.I.L.Y recusa senhas, CPF, cartões e códigos |
| Modelos de IA baixados | Armazenamento privado do app | Rodar a IA sem internet |
| Imagens e vídeos gerados | Galeria do aparelho (Imagens/L.I.L.Y, Filmes/L.I.L.Y) | Você vê e compartilha onde quiser |
| Configurações | Armazenamento privado do app | Preferências |
| Chave de acesso do gateway | Cifrada com o Android Keystore | Conectar ao seu gateway |
| Registro de diagnóstico e relatórios de erro | Armazenamento privado do app | Entender falhas; **só sai do aparelho se você tocar em Compartilhar ou Salvar** |
| Projetos do Codex (pastas) | Onde você escolheu | O app só acessa as pastas que você autorizou no seletor do Android |
| Notificações lidas pelo Cristal | Memória do app, no aparelho | Mostrar na Liquid Island e na tela externa |
| Texto digitado no L.I.L.Y Keyboard | Processado no aparelho; as palavras aprendidas ficam no dicionário pessoal do app | Correção e sugestões; textos de campos de senha e de códigos nunca vão para a L.I.L.Y |

## 2. Quando algo sai do aparelho

Somente nestes casos, e só quando você usa o recurso:

| Recurso | O que é enviado | Para quem |
| --- | --- | --- |
| Modelos online na conversa (opcional) | A mensagem, o histórico recente da conversa e as memórias ativas | O seu L.I.L.Y Gateway, que repassa ao OpenRouter e ao fornecedor do modelo escolhido |
| L.I.L.Y Codex | A tarefa, os trechos de código que o agente lê e os logs de erro | O gateway, o OpenRouter e o fornecedor do modelo; a pesquisa vai ao Tavily ou ao Brave; páginas de documentação são abertas pelo próprio aparelho |
| Repositórios no Codex | As mudanças aprovadas | O GitHub (pelo token guardado no gateway), num ramo `lily-codex/...` |
| Video AI | A descrição e, se você escolher, a imagem de referência | O gateway e quem roda o modelo: no vídeo **grátis**, o Space público da Lightricks no Hugging Face (LTX-Video); nos pagos, o fal.ai |
| Imagem online grátis | A descrição da imagem | O gateway e o Workers AI da própria conta Cloudflare do gateway (FLUX.1 [schnell]) |
| Download de modelos | Pedido de arquivo (seu endereço IP fica visível ao servidor) | Hugging Face ou Google |
| Tradução do ML Kit | Download do pacote de idioma (a tradução em si é no aparelho) | Google |
| Clima no Cristal | Latitude e longitude **arredondadas** da cidade escolhida | Open-Meteo |
| Compartilhar algo | O que você escolheu compartilhar | O app de destino que você escolheu |

Cada provedor tem a própria política de privacidade, que vale para o que ele recebe. Consulte as
políticas de OpenRouter, do fornecedor do modelo usado, fal.ai, Hugging Face (e do Space da Lightricks, no vídeo grátis), Tavily ou Brave, GitHub e Cloudflare (inclusive o Workers AI).

### O L.I.L.Y Gateway

- O gateway roda na conta Cloudflare de quem o instala. O código do gateway **não grava o conteúdo**
  das conversas, do código ou dos vídeos: ele repassa o pedido ao provedor e devolve a resposta.
- Ele guarda apenas um contador do gasto do dia (para o limite diário), por até 3 dias.
- A Cloudflare pode manter registros técnicos de acesso conforme a política dela.
- **Quem opera o gateway é o controlador** dos dados enviados por ele (art. 5º, VI, da LGPD). Se você
  usa o gateway de outra pessoa ou instituição, peça a ela a política de privacidade correspondente.

## 3. Permissões do Android

| Permissão | Por quê | Quando |
| --- | --- | --- |
| Microfone | Falar com a L.I.L.Y, ditado | Só depois que você toca no microfone ou segura a ilha. Com a "Conversa contínua" (pode ser desligada), depois de responder uma pergunta **que você falou**, ouve mais uma vez para a próxima pergunta e para no silêncio |
| Notificações | Avisos de respostas, downloads, vídeos e lembretes | Ao usar esses recursos |
| Sobreposição (exibir sobre outros apps) | Avatar flutuante e Liquid Island | Só se você ligar |
| Acesso às notificações (Cristal) | Mostrar notificações na ilha e na tela externa | Só se você ativar |
| Acessibilidade (Cristal) | Tela externa, Central de Controle, transição de fechamento | Só se você ativar |
| Fotos e vídeos | Gallery e anexos | Pelo seletor do Android ou com a sua permissão |
| Pastas escolhidas (Codex) | Ler e alterar o projeto autorizado | Só a pasta que você escolher |
| Internet | Recursos online e downloads | Quando você usa um desses recursos |

A câmera e o microfone **nunca** são ligados pela avatar ou por qualquer recurso sem uma ação sua.

## 4. Seus direitos (LGPD)

Como os dados ficam no seu aparelho, você mesmo exerce a maior parte dos direitos:

- **Acesso e portabilidade**: o histórico, as memórias e o relatório de diagnóstico podem ser vistos,
  copiados e compartilhados no app.
- **Correção e exclusão**: apague conversas, memórias, medições e arquivos temporários em
  Configurações > Privacidade, ou desinstale o app.
- **Revogação do consentimento**: desligue "Permitir processamento externo" em Configurações >
  Serviço online, ou remova a conexão com o gateway.
- Para dados enviados por um gateway, fale com quem o opera.

## 5. Segurança

- Nenhuma chave de provedor fica no app: elas ficam como segredos do gateway.
- A chave de acesso ao gateway é cifrada com o Android Keystore.
- O gateway recusa pedidos sem a chave de acesso, limita o gasto diário e só aceita repositórios
  autorizados.
- Relatórios de erro ficam no aparelho até você decidir enviá-los.

Encontrou um problema de segurança? Veja `SEGURANCA.md`.

## 6. Crianças e adolescentes

O projeto não é direcionado a crianças. O tratamento de dados de crianças e adolescentes deve
seguir o art. 14 da LGPD; recomenda-se o uso com acompanhamento dos responsáveis, em especial dos
recursos online.

## 7. Mudanças nesta política

Esta política pode mudar com novas versões. A data de vigência no topo indica a versão atual.

## 8. Contato

Responsável pelo projeto: [nome do responsável] · [e-mail ou canal de contato]
