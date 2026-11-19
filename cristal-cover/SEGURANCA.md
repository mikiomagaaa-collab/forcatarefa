# Segurança

## Como relatar um problema

Encontrou uma falha de segurança (por exemplo, um jeito de ler dados de outro app, de usar a chave
de um gateway sem autorização ou de fazer o Codex gravar fora do projeto autorizado)?

1. **Não publique os detalhes** em issues públicas.
2. Use o recurso "Report a vulnerability" (Security Advisories) do repositório no GitHub, ou escreva
   para [contato de segurança do projeto].
3. Descreva como reproduzir, a versão dos apps e o aparelho.

Respondemos assim que possível e combinamos a divulgação depois da correção.

## O que o projeto já faz para proteger você

- Chaves de provedores ficam só no L.I.L.Y Gateway (segredos do Worker), nunca no APK.
- A chave de acesso ao gateway fica cifrada com o Android Keystore.
- O gateway falha fechado: sem `LILY_ACCESS_KEY` configurada, recusa tudo.
- O gateway valida os identificadores de vídeo para a chave do fal.ai nunca ser enviada a outro
  servidor, limita o gasto diário e só aceita repositórios autorizados.
- O Codex só grava em ramos `lily-codex/...`, nunca altera workflows do GitHub Actions e pede
  aprovação do diff (com ponto de restauração e desfazer).
- Nenhum app contorna PIN, biometria ou outras proteções do Android.
- Relatórios de erro ficam no aparelho até a pessoa compartilhar.

## Versões com suporte

Recebem correções de segurança a versão mais recente de cada app e o código mais recente do gateway.
