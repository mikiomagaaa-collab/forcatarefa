FORÇA TAREFA
============

Site real em HTML, CSS e JavaScript modular. Sem framework no navegador, fotografias geradas, integrantes inventados ou resultados eleitorais simulados.

Conteúdo atual
--------------

- 35 propostas originais preservadas, em seis categorias iniciais. Dez propostas adicionais de bem-estar e infraestrutura começam em revisão editorial privada. A gestão pode ocultar ou republicar qualquer proposta. O total público acompanha as publicações da equipe.
- As cinco prioridades continuam sendo 5, 7, 17, 19 e 27. A proposta 8 completa os seis destaques, com aprovação da gestão e implementação a organizar.
- A proposta 19 foi atualizada para apoio direto aos clubes com materiais, planejamento e ideias, complementando os projetos e culminâncias existentes.
- As propostas 31 a 35 estão em “Agrofloresta (Projeto M.C.A.N): Projeto Marasca Com Amor na Natureza”. Elas não são prioridades nem estão aprovadas.
- História com quatro etapas expansíveis e narrativa completa, sem datas inventadas.
- Central FT+ com sete áreas, acompanhamento de projetos, metas, orçamento, decisões, equipe, transparência e retornos revisados.
- Identificação opcional no navegador, com saudação, alteração e remoção.
- Painel administrativo com equipe, sugestões, andamento, atualizações, intenção de voto e controles de segurança.
- Apenas as duas fotografias externas fornecidas. A imagem com estudantes identificáveis foi excluída da distribuição.

O que funciona sem configuração
-------------------------------

Navegação, menu móvel, pesquisa e filtros das propostas publicadas, links diretos, expansão dos textos, história, FT+ e identificação opcional. As páginas catalogo.html e propostas.html consultam o catálogo publicado para respeitar as propostas ocultas.

O que depende de configuração externa
-------------------------------------

Envio persistente de sugestões, registro e contagem de intenções, autenticação, edição compartilhada da equipe, atualização de andamento, publicações e controles de abuso exigem um projeto Supabase e uma verificação Cloudflare Turnstile configurados. Esses serviços estão configurados para o endereço de produção. Envios locais permanecem desabilitados. Não há confirmação simulada.

A publicação usa GitHub Pages. O fluxo em .github/workflows/pages.yml verifica e publica o site a cada alteração na branch main. Os serviços de dados exigem configuração separada e nunca simulam salvamento.

Executar localmente
-------------------

Na pasta do projeto:

```powershell
python -m http.server 4173 --bind 127.0.0.1
```

Abra http://127.0.0.1:4173/. Não abra index.html diretamente pelo explorador, pois a leitura dos dados precisa de um servidor HTTP.

Na prévia local, o voto e a entrada na gestão abrem o site publicado. O formulário de sugestões apresenta um link para o envio online. Assim, a prévia não tenta usar a proteção de produção em um endereço local nem informa uma sessão expirada por esse motivo.

Verificar e preparar a publicação
--------------------------------

As dependências abaixo são exclusivamente de desenvolvimento e testes. Não entram no site público.

```powershell
npm ci
npm run check
npm run test:security
npm run test:types
npm run test:functions
npm run build
```

O resultado de publicação fica em dist/. Somente páginas, estilos, módulos e imagens são copiados. Não são publicados SQL, funções, testes, dependências, segredos nem documentação.

O teste de segurança executa o SQL em PostgreSQL via PGlite. Ele verifica permissões de visitante, usuário comum, administrador, proprietário e serviço, além de bloqueios, validação atômica da equipe e deduplicação concorrente de intenções. O teste de funções verifica validação, erros, CAPTCHA, limites e login, com serviços externos simulados. São testes locais, não uma certificação do projeto Supabase real. A configuração real precisa dos testes descritos abaixo.

Configurar os dados e a autenticação
-----------------------------------

1. Crie um projeto Supabase sob seu controle.
2. No SQL Editor, execute supabase/schema.sql uma vez, em um projeto novo, seguido de 20261009_platform.sql e 20261009_privacy.sql, na pasta supabase/migrations. Não reaplique a inicialização em um banco preexistente.
3. Em Authentication, Users, crie a conta da gestão com uma senha forte de pelo menos 12 caracteres. Ative o requisito de senha forte no serviço de autenticação. Não use senha temporária fraca no ambiente publicado.
4. Copie o UUID real dessa conta e autorize o primeiro proprietário no SQL Editor:

```sql
insert into public.account_controls (user_id, is_admin, is_owner)
values ('SUBSTITUA_PELO_UUID_REAL', true, true);
```

5. Desabilite cadastros públicos se não forem necessários. Não habilite contas de estudantes apenas para os nomes de exibição.
6. O widget Cloudflare Turnstile de produção permite mikiomagaaa-collab.github.io. O ambiente local não está habilitado para os envios online. Não use chaves de teste na publicação.
7. Preencha assets/js/config.js com supabaseUrl, supabasePublicKey e turnstileSiteKey. Use a chave pública anon para manter a verificação JWT das funções. Nunca coloque a chave service_role ou qualquer segredo no navegador.
8. Instale e autentique o CLI oficial do Supabase. Vincule o projeto:

```powershell
supabase login
supabase link --project-ref SEU_PROJECT_REF
```

9. Copie supabase/.env.example para supabase/.env.local e preencha no seu computador:
   - ALLOWED_ORIGINS: origens exatas, separadas por vírgula. No GitHub Pages é https://mikiomagaaa-collab.github.io, sem /forcatarefa. Remova localhost quando não houver teste local.
   - ADMIN_EMAIL: e-mail da conta padrão autorizada, mantido no servidor.
   - PUBLIC_SITE_ANON_KEY: a mesma chave pública anon de assets/js/config.js. O servidor reconhece essa chave como acesso de visitante, sem confundi-la com uma sessão administrativa. A chave pública pode diferir da chave legado fornecida automaticamente pelo ambiente.
   - TURNSTILE_SECRET_KEY: segredo do widget, somente no servidor.
   - ABUSE_HASH_SECRET: segredo aleatório forte, com pelo menos 32 bytes de entropia, somente no servidor. Preserve esse segredo; alterá-lo muda os identificadores de deduplicação.
10. Envie os segredos e as funções:

```powershell
supabase secrets set --env-file supabase/.env.local
supabase functions deploy submit-suggestion
supabase functions deploy register-intention
supabase functions deploy admin-login
```

Mantenha “Verify JWT with legacy secret” ligado nas três funções. O site usa a chave pública anon no cabeçalho Authorization para chamadas públicas. As funções também validam origem, tamanho, sessão, limites e Turnstile. O login valida a senha pelo Supabase Auth e verifica a autorização real. Nenhum visitante pode inserir sugestões ou intenções diretamente pelas tabelas.

SUPABASE_URL, SUPABASE_ANON_KEY e SUPABASE_SERVICE_ROLE_KEY são variáveis do ambiente de funções Supabase. Confirme que estão disponíveis. A chave privada de serviço permanece no servidor.

11. A limpeza automática foi autorizada e ativada pelo proprietário em 09/10/2026. O agendamento de supabase/retention.sql roda a cada hora: remove eventos técnicos com mais de sete dias, limites de tentativas expirados há mais de um dia e suspensões de sessão expiradas há mais de sete dias. Sugestões, intenções, propostas e contas não fazem parte dessa rotina.
12. Teste a integração real em um projeto de desenvolvimento antes de publicar as configurações finais.

Acessar o painel
----------------

No início do site, digite exatamente participantechapasixseven e clique em Entrar. Isso abre a autenticação, sem conceder privilégios. Também existe o link “Área da gestão” no rodapé.

Digite a senha forte da conta padrão autorizada. Para outra conta já autorizada, expanda “Usar outra conta autorizada” e informe seu e-mail. O painel fica em admin/ e exige uma sessão válida e autorização verificada no banco. O conteúdo do painel é ocultado antes dessa verificação.

Para atualizar a equipe: abra Equipe, adicione ou edite os nomes, use Subir/Descer para ordenar, Remover para excluir da edição e “Salvar equipe online” para gravar. Os nomes são salvos em uma transação e ficam públicos para todos ao recarregar. Obtenha autorização para publicar nomes de menores de idade. As funções reais são editáveis. Cadastre somente nomes e funções autorizados; não publique fotografias ou dados pessoais sem autorização.

Somente a conta proprietária pode autorizar ou bloquear outras contas. Novas contas devem ser criadas no serviço de autenticação antes de cadastrar seu UUID no painel. A conta proprietária é protegida contra remoção de privilégios pelo próprio painel. Para situações de perda de acesso, use os controles da conta do projeto Supabase.

Em Propostas, selecione a proposta e clique em “Marcar proposta como realizada” quando ela já tiver sido concluída. O botão salva o status real no banco e preserva as observações. Para corrigir um registro, escolha outra etapa e clique em “Salvar andamento”. As etiquetas de prioridade e início planejado permanecem independentes desse status.

Intenção de voto
----------------

O botão “VOU VOTAR NO FORÇA TAREFA!” envia o registro ao servidor. Somente uma resposta válida confirma a operação. A chave única no banco impede que cliques repetidos com a mesma sessão somem mais de uma intenção, inclusive em requisições concorrentes. A contagem privada no painel atualiza ao entrar, pelo botão Atualizar e a cada 30 segundos enquanto a aba está visível.

Base inicial informada pelo responsável: 310 alunos e 80 votos declarados da outra chapa. Esses números não foram verificados pelo site. O percentual da FORÇA TAREFA é intenções registradas / 310 × 100. A outra chapa fica em 80 / 310 × 100. A diferença aritmética é máximo(310 - 80 - intenções, 0), identificada como estimativa, sem afirmar que corresponde a indecisos. Se a soma ultrapassar a base, o painel mostra um aviso.

Limite fundamental: a sessão identifica um navegador, não um estudante. Limpar dados ou usar outro dispositivo pode permitir novo registro. As duas bases podem se sobrepor. Portanto, a contagem não é uma pesquisa representativa nem uma votação oficial. Uma regra verificável de uma intenção por aluno exigiria autenticação e uma lista de elegibilidade escolar autorizada; esses dados não foram solicitados nem coletados.

Proteções implementadas
-----------------------

- RLS e permissões mínimas em todas as tabelas. Sugestões e intenções não têm leitura pública.
- Texto recebido é validado no servidor e apresentado com textContent. HTML e caracteres de controle são recusados.
- A equipe só pode ser alterada por uma função com autorização real. O status das sugestões é a única coluna editável dessa tabela por administradores.
- O programa original de 35 propostas permanece em dados versionados. O catálogo online permite revisão editorial pela gestão; as dez novas propostas começam privadas.
- Autenticação real, acesso de administrador/proprietário e bloqueios são verificados no banco, inclusive com uma sessão JWT já emitida.
- Turnstile é validado no servidor, com hostname e ação. Tokens expirados ou reutilizados são recusados.
- Limites persistentes por sessão: três tentativas de sugestão/intenção e cinco de login por dez minutos. Limite amplo de conexão para participação, para acomodar a rede compartilhada da escola, e mais restrito para login.
- Honeypot de formulário, tamanho máximo de requisição e mensagens limitadas.
- Hash protegido de sessão e de conexão. Endereços IP em texto aberto, senhas e conteúdo de mensagens não entram nos registros internos de abuso.
- Suspensões justificadas de sessões e controles de contas realmente autenticadas. O nome escolhido no início nunca é usado para bloquear pessoas.
- Política de conteúdo nas páginas principais e administrativas, sem scripts embutidos, objetos ou origens arbitrárias de scripts. Somente o widget de segurança externo é carregado quando configurado.
- Sessão administrativa por aba, expiração, renovação e encerramento. Use dispositivo confiável e encerre a sessão ao terminar.

O GitHub Pages hospeda arquivos públicos. Ele não pode bloquear individualmente a leitura do site por nome ou conta. Uma leitura restrita exigiria hospedagem com controle de acesso no servidor. A política de conteúdo por meta não controla enquadramento por outros sites; para esse requisito, use hospedagem capaz de enviar o cabeçalho frame-ancestors.

Fotografias e organização
-------------------------

assets/img/ contém WebP responsivo e JPEG de compatibilidade. A fachada usa 360/570 pixels; a vista ao entardecer usa 480/800 pixels. Não houve recriação visual, deformação ou remoção de marca d’água. O arquivo manifest.json registra dimensões e hashes das fontes. A segunda fotografia usa carregamento tardio.

A marca é tipográfica, com um símbolo vetorial simples, e pode ser substituída por uma logo oficial. Nenhuma fotografia de estudante ou integrante foi publicada. O arquivo team.json permanece vazio; os nomes publicados são carregados do banco quando configurado.

As fotografias foram fornecidas como referências oficiais pelo responsável. A marca d’água da imagem original foi mantida.

Publicar no GitHub Pages
------------------------

No repositório https://github.com/mikiomagaaa-collab/forcatarefa, autentique sua ferramenta Git com uma conta que tenha escrita. Na pasta entregue:

```powershell
git status
git add .
git commit -m "Implementa site da Força Tarefa"
git branch -M main
git push -u origin main
```

Se já houver um commit local pronto, não é necessário criar outro antes do push. O remoto da pasta de trabalho já aponta para o repositório informado. Se estiver usando apenas o ZIP, ele não contém o histórico Git: clone o repositório e copie para a nova pasta os arquivos extraídos antes de executar os comandos acima. Não coloque tokens na URL remota ou nos arquivos.

No GitHub, abra Settings, Pages e selecione GitHub Actions em Source. Na aba Actions, execute ou acompanhe “Publicar FORÇA TAREFA”. A publicação usa exclusivamente dist/. Confirme que os testes e a etapa de publicação terminaram com sucesso antes de anunciar o endereço:

https://mikiomagaaa-collab.github.io/forcatarefa/

Não há CNAME nem domínio personalizado presumido. Contatos e redes sociais só aparecem se cadastrados em config.contacts, como objetos com label e url. Não cadastre perfis sem confirmação.

Teste da configuração real
--------------------------

1. Confira desktop, celular, ampliação de texto, navegação por teclado e preferência por movimento reduzido.
2. Busque “agrofloresta”: devem aparecer somente as propostas 31 a 35, sem prioridade. A proposta 19 mantém prioridade e seu novo texto de apoio aos clubes.
3. Abra /#proposta-35 diretamente e confira a expansão do catálogo.
4. Como visitante, tente ler sugestões ou intenções pela API: deve ser recusado. Tente inserir diretamente: também deve ser recusado.
5. Digite o identificador especial e confira que não há privilégios sem autenticação.
6. Envie uma sugestão válida no ambiente de teste e confira o registro privado no painel. Teste HTML, campos inválidos, CAPTCHA expirado e falha do serviço. Nunca deve haver sucesso em caso de falha.
7. Registre uma intenção duas vezes com a mesma sessão. A contagem deve aumentar uma única vez. Os dados de teste não devem ser importados para produção.
8. Autorize uma conta de teste, confirme o acesso, bloqueie-a e confira a rejeição inclusive com uma sessão anterior.
9. Edite e reordene nomes, salve e confira em outro navegador. Verifique o andamento e uma atualização institucional real.
10. Confirme a limpeza de segurança. Defina com a gestão o prazo necessário para guardar sugestões e intenções e exclua-as quando deixarem de ser necessárias.

Referências de configuração
----------------------------

[Supabase RLS](https://supabase.com/docs/guides/database/postgres/row-level-security), [Supabase Edge Functions](https://supabase.com/docs/guides/functions), [validação Turnstile](https://developers.cloudflare.com/turnstile/get-started/server-side-validation/) e [GitHub Pages com Actions](https://docs.github.com/en/pages/getting-started-with-github-pages/using-custom-workflows-with-github-pages).

Novas áreas e configuração do banco
----------------------------------

Após aplicar supabase/schema.sql, execute supabase/migrations/20261009_platform.sql. Para banco já existente, aplique somente a migração. Ela preserva as propostas e separa as etiquetas do andamento real.

As etiquetas sem backend vêm de assets/data/classifications.json. As cinco prioridades são 05, 07, 17, 19 e 27. As oito ações iniciais planejadas são 05, 06, 10, 13, 20, 21, 22 e 27. Ambos os filtros podem coexistir.

O painel autorizado permite editar etiquetas, acompanhamento, publicações de projetos, respostas do FAQ e informações eleitorais. Os registros dos projetos começam privados; publicar exige confirmar as autorizações. Não há armazenamento público de fotografias de estudantes nem envio de arquivos habilitado.

O FAQ tem 20 perguntas em assets/data/faq.json, pesquisa e categorias. Respostas editadas no painel persistem em faq_entries. O painel mostra o total real de intenções em dados agregados, sem percentuais de apoio escolar.

Em Gerenciamento Eleitoral 2026, edite a retrospectiva e a mensagem e marque a revisão de cada texto. O resultado final exige data, fonte e a confirmação explícita de publicação. Antes de 13/10/2026 às 00h00 de Brasília, o servidor impede sua publicação. A prévia do painel é privada. A data sozinha nunca inicia propostas ou declara vitória.

A seção eleitoral usa a hora do banco. Sem backend, verifica o cabeçalho Date do servidor de hospedagem e não apresenta contagens ou resultados. Uma vitória oficialmente publicada ativa detalhes comemorativos por sete dias; depois, o registro histórico permanece. Publicar o resultado encerra novas intenções no servidor com bloqueio transacional.

Fotografia com crédito: os arquivos atuais preservam a marca d'água. Para substituir por fotografia própria ou licenciada, gere as variantes fachada-360.webp, fachada-570.webp e fachada.jpg e atualize o manifesto. Não remova artificialmente os créditos.

Configuração publicada em 09/10/2026
---------------------------------

Site: https://mikiomagaaa-collab.github.io/forcatarefa/

O banco e as três funções estão instalados no projeto Supabase uiqmftvdtryuhmrlvdxr. O widget Turnstile está restrito ao hostname público. Os segredos permanecem no Supabase; somente as chaves públicas entram em assets/js/config.js. O proprietário criou sua senha pessoal e autorizou o acesso de gestão. O agendamento forcatarefa-security-retention está ativo, com execução a cada hora, após autorização do proprietário.

A migração 20261009_privacy.sql permite que a gestão exclua uma sugestão após validar a necessidade de remoção e confirmar a exclusão no painel. Nenhuma sugestão ou intenção foi criada para demonstrar contagens de produção.

O painel mostra uma área por vez: visão geral, propostas, Central FT+, conteúdo do site, equipe, sugestões, projetos especiais, transparência, perguntas frequentes, eleição e segurança. A navegação preserva os campos ainda não salvos.

A etiqueta Desportivo usa uma bola em SVG e identifica inicialmente as propostas 17 e 24. Pode ser editada pela gestão, junto às demais etiquetas, sem mudar o andamento. Em projetos existentes, aplique `supabase/migrations/20261009_sports.sql` uma vez. A correção de gravação da equipe está em `supabase/migrations/20261009_team_save.sql` e mantém a proteção contra operações sem condição.


Central FT+ e preparação para 13/10/2026
-------------------------------------

A atualização mantém o login, as funções de participação, as permissões e os dados existentes. As migrações adicionais, em ordem, são `20261009_central.sql` e `20261009_planning_drafts.sql`. Elas foram instaladas no projeto de produção em 09/10/2026. Não reaplique uma migração já instalada.

`proposal_catalogue` contém as 35 propostas originais e as propostas 36 a 45, que começam em revisão. Visitantes só podem ler linhas “Publicada”. Aprovação editorial, autorização da escola e execução são conceitos separados. A publicação exige ação explícita da equipe no painel. Todas as propostas podem ser ocultadas e republicadas, preservando seu número e histórico.

O catálogo público fica em `catalogo.html`. Pesquisa, filtros por categoria, prioridade, início planejado e situação funcionam com os dados publicados. Os totais da página inicial e da Central vêm desse catálogo. Se a consulta ao banco configurado falhar, o site informa a indisponibilidade, sem mostrar propostas ocultas a partir dos arquivos de base. `propostas.html` também consulta o catálogo online.

`gestao.html` apresenta a Central FT+, inicialmente em planejamento. Projetos têm responsável opcional, prazo estimado, dependências, autorizações, custo e recursos opcionais, andamento e resultado. O histórico é criado no banco quando etapa ou atualização são alteradas, sem evolução automática por data. Os registros e o histórico são públicos somente após revisão e publicação. Revise todo o histórico antes de publicar um projeto.

Metas, orçamento, decisões, retornos gerais, abertura e história persistem em `management_records`. Registros começam privados e só aparecem após revisão e publicação. Campos monetários vazios significam desconhecidos, não zero. Não há somas que possam contar o mesmo recurso duas vezes nem gráficos demonstrativos. Registre cada decisão financeira e os projetos beneficiados com base em informações confirmadas.

O formulário de sugestões pede apenas categoria, texto e nome opcional. A turma deixou de ser coletada pela interface; o servidor mantém compatibilidade com dados anteriores. A mensagem original continua privada. O retorno público é um resumo independente, revisado sem dados pessoais. A moderação de mensagens e os controles de segurança anteriores continuam disponíveis.

Em **Propostas**, use a revisão editorial para editar descrição, categoria e campos de planejamento. Nas propostas 36 a 45, “Aprovada pela equipe” ainda é privado. Selecione “Publicada”, confirme a autorização da equipe e salve para disponibilizar aos visitantes. Etiquetas e andamento têm formulários próprios e continuam independentes.

Em **Central FT+**, cadastre projetos e registros de metas, orçamento, decisões ou retornos. Revise e marque “Publicar” somente quando houver autorização. Desmarcar a publicação permite manter o registro privado. Em **Conteúdo do site**, edite a abertura e a trajetória. As etapas originais permanecem como base; revisões publicadas nas posições 1 a 4 substituem a etapa correspondente.

Os modelos de metas para 30, 60 e 90 dias, quatro etapas da história e abertura inicial estão cadastrados como rascunhos editáveis. A página de planejamento apresenta esses horizontes claramente como modelos sujeitos à aprovação, sem afirmar compromissos iniciados.

A página eleitoral agora oferece informação antes de 13/10. Os registros agregados continuam ocultos no servidor antes dessa data. A votação oficial, a apuração e o resultado permanecem separados das intenções. A confirmação manual exige data e fonte; a vitória só ativa comemoração depois de publicação oficial, por sete dias.

Novas páginas: Bem-estar, Infraestrutura, Nossa História, Como funciona o Grêmio, planejamento inicial e 404. Metadados de compartilhamento usam a fotografia real da fachada. O menu funciona no celular e a navegação interna do FT+ respeita o mesmo espaço da página, sem sobrepor a abertura.

Na eleição, registre somente informações oficialmente confirmadas. Não são necessárias novas senhas ou chaves no JavaScript público.

Área de trabalho da gestão e calendário 2027 e 2028
------------------------------------------------

A migração adicional `20261009_admin_workspace.sql` está instalada no projeto de produção. Ela preserva os registros existentes, amplia os números das propostas e cria a agenda privada. Não reaplique uma migração já instalada.

O painel privado em `admin/` tem navegação lateral no computador e menu recolhido no celular. A visão geral usa os totais reais de propostas, publicações, revisão, andamento, equipe e compromissos. Intenções de voto ficam em uma seção própria recolhida.

Em **Propostas**, busque por número ou texto e filtre categoria e publicação. Clique no título para abrir texto, andamento e etiquetas da mesma proposta. Selecione propostas na lista para aprovar pela equipe, publicar com confirmação explícita ou ocultar mantendo em rascunho. Isso vale também para as 35 originais. A ação em conjunto é atômica: uma seleção inválida não altera parcialmente os registros.

**Nova proposta** gera um número único no servidor e sempre cria um rascunho privado. Aprovação editorial permanece independente da autorização escolar e do andamento. O programa original fica preservado nos arquivos versionados, e revisões online autorizadas continuam no banco. As propostas 36 a 45 foram publicadas pelo administrador em 09/10/2026.

Em **Calendário**, cadastre reuniões, atividades e prazos entre 01/01/2027 e 31/12/2028, inclusive intervalos de datas. Horário de Brasília, responsável e vínculo com proposta são opcionais. Proposto, Confirmado, Concluído e Cancelado são registros manuais. O calendário não muda etapas de propostas por chegada de uma data e não é exposto ao público. Somente contas administrativas autorizadas podem consultar e editar a agenda; não há exclusão permanente pelo painel.

Na Central FT+ pública e nos atalhos da página inicial, áreas sem registros públicos não aparecem. Publicar um registro revisado faz a área correspondente aparecer na próxima consulta. Os controles internos permanecem disponíveis para que a equipe possa cadastrar os primeiros dados.


Revisão de equipe e publicação reversível

Aplique `supabase/migrations/20261009_reversible_proposals.sql` após a migração do painel. Ela permite ocultar e republicar as propostas originais, preservando texto, número, etiquetas e andamento. Não permite apagar propostas.

No editor, use **Ocultar do site**. Para recuperar, filtre os rascunhos, abra a proposta, marque a confirmação de revisão e use **Republicar no site**. A lista pública ocupa os espaços automaticamente; os identificadores e links continuam estáveis.

Em **Equipe**, escolha uma das oito funções. Os cargos existentes fora dessa lista continuam preservados até uma alteração autorizada. As coordenações geral e vice-geral usam laranja e negrito. As demais frentes têm cores próprias e símbolos em SVG. Após alterar, clique em **Salvar equipe online**.

A equipe aparece em duas fileiras de quatro cartões em telas largas, duas colunas em telas intermediárias e uma coluna no celular. A ordem segue a configuração do painel.


Aprovação escolar, calendário público e acessos

O editor de cada proposta permite registrar **Aprovada pela gestão da escola**. Isso atualiza a indicação no catálogo e inclui a proposta publicada na Transparência, sem alterar sua etapa de execução. Novas propostas devem primeiro ser salvas em rascunho. O FT+ apresenta novamente seus cinco compromissos e o modelo de planejamento.

Aplique `20261009_public_calendar.sql` e `20261009_site_traffic.sql`. Os eventos existentes continuam privados. Para publicar um evento, preencha a descrição pública, marque Publicar e confirme a revisão. Anotações internas e responsáveis não fazem parte dos campos públicos. O calendário de 2027 e 2028 fica em `calendario.html`, no menu Mais. A troca de mês usa uma transição suave que respeita a preferência por menos animações.

Publique a função `record-visit`, utilizando os segredos de servidor já configurados. Ela valida origem, formato, limites de requisições e identificadores aleatórios; não precisa de CAPTCHA nem de login para visitas. Os registros e totais não têm leitura anônima. A permanência é limitada ao tempo decorrido no servidor e contabilizada de maneira idempotente. Uma visita acompanha a navegação da mesma aba e se renova após 30 minutos sem atualizações. Prévias locais não contam.

Em Segurança, a gestão pode consultar visitas totais, visitas de hoje, navegadores estimados nos últimos sete dias, tempo médio visível e uma tabela por dia. O histórico começa na ativação; ele não reconstrói acessos anteriores. Bloqueadores, não rastreamento, encerramentos abruptos e falhas de rede podem reduzir a contagem. Ative `traffic-retention.sql` para apagar registros técnicos de visitas após sete dias, preservando os totais diários sem identificadores.

A página Nossa História apresenta a narrativa completa enviada pela equipe, com navegação entre os capítulos. O resumo da página inicial continua direcionando à história completa.
