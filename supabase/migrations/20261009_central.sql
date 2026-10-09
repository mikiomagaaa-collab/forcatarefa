begin;
create function public.ft_plain(value text, maximum integer, minimum integer default 0) returns boolean language sql immutable set search_path='' as $$
select char_length(btrim(value)) between minimum and maximum and value !~ '[<>[:cntrl:]]';
$$;
create table public.proposal_catalogue (
 id integer primary key check(id between 1 and 45),
 title text not null check(public.ft_plain(title,120,3)),
 description text not null check(char_length(btrim(description)) between 10 and 3000 and description !~ '[<>]'),
 category integer not null check(category between 1 and 8),
 approved boolean not null default false,
 objective text not null default '' check(char_length(objective)<=1000 and objective !~ '[<>]'),
 approach text not null default '' check(char_length(approach)<=2000 and approach !~ '[<>]'),
 responsible text not null default '' check(public.ft_plain(responsible,300)),
 authorization_notes text not null default '' check(char_length(authorization_notes)<=1000 and authorization_notes !~ '[<>]'),
 editorial_state text not null default 'Aguardando aprovação da equipe' check(editorial_state in ('Aguardando aprovação da equipe','Rascunho','Aprovada pela equipe','Publicada')),
 created_at timestamptz not null default now(),
 updated_at timestamptz not null default now(),
 check(id>35 or editorial_state='Publicada')
);
insert into public.proposal_catalogue(id,title,description,category,approved,editorial_state) values(1,'Plantão FORÇA TAREFA','Organizar grupos voluntários de estudo e revisão antes das avaliações, incentivando estudantes a ajudarem colegas com dificuldades em determinadas matérias.',1,false,'Publicada');
insert into public.proposal_catalogue(id,title,description,category,approved,editorial_state) values(2,'Reconhecimento pela Evolução','Reconhecer as turmas que mais evoluírem na Prova Paulista, propondo recompensas coletivas autorizadas pela gestão, valorizando o esforço e não apenas as maiores notas.',1,false,'Publicada');
insert into public.proposal_catalogue(id,title,description,category,approved,editorial_state) values(3,'Participação nas Eletivas','Consultar os estudantes sobre temas de interesse e apresentar sugestões à gestão, buscando eletivas mais próximas das preferências dos alunos.',1,false,'Publicada');
insert into public.proposal_catalogue(id,title,description,category,approved,editorial_state) values(4,'Apoio Tecnológico Estudantil','Organizar estudantes voluntários para ajudar colegas com dificuldades em plataformas escolares, computadores e ferramentas digitais.',1,false,'Publicada');
insert into public.proposal_catalogue(id,title,description,category,approved,editorial_state) values(5,'Vitrine de Talentos','Criar um programa de apresentações e exposições em música, artes, escrita, tecnologia e outras áreas, valorizando especialmente estudantes que ainda não possuem oportunidades de apresentar suas habilidades.',1,false,'Publicada');
insert into public.proposal_catalogue(id,title,description,category,approved,editorial_state) values(6,'Mural de Oportunidades','Divulgar olimpíadas acadêmicas, concursos, cursos gratuitos, projetos e outras oportunidades educacionais para ampliar as experiências dos estudantes.',1,false,'Publicada');
insert into public.proposal_catalogue(id,title,description,category,approved,editorial_state) values(7,'Área de Descanso','Propor a instalação de bancos e assentos próximos à biblioteca, criando um espaço tranquilo e confortável para descansar, ler e conversar.',2,false,'Publicada');
insert into public.proposal_catalogue(id,title,description,category,approved,editorial_state) values(8,'Saída Antecipada por Rodízio','Implementar a proposta já aprovada pela gestão: a cada semana, uma turma poderá sair cinco minutos mais cedo, seguindo um rodízio organizado.',2,true,'Publicada');
insert into public.proposal_catalogue(id,title,description,category,approved,editorial_state) values(9,'Melhoria do Horário de Almoço','Ouvir estudantes sobre filas, circulação e dificuldades no refeitório, propondo melhorias para um almoço mais organizado.',2,false,'Publicada');
insert into public.proposal_catalogue(id,title,description,category,approved,editorial_state) values(10,'Mapa de Melhorias da Escola','Registrar problemas de infraestrutura, como banheiros, bebedouros, ventilação e mobiliário, encaminhando as solicitações à gestão e acompanhando as respostas.',2,false,'Publicada');
insert into public.proposal_catalogue(id,title,description,category,approved,editorial_state) values(11,'Convivência Participativa','Incentivar acordos de convivência entre estudantes e professores para melhorar o ambiente das salas e diminuir conflitos.',2,false,'Publicada');
insert into public.proposal_catalogue(id,title,description,category,approved,editorial_state) values(12,'Acessibilidade na Prática','Ouvir estudantes com deficiência sobre dificuldades de acesso e participação, encaminhando propostas de melhorias à gestão.',2,false,'Publicada');
insert into public.proposal_catalogue(id,title,description,category,approved,editorial_state) values(13,'Banco Solidário de Materiais','Criar um estoque comunitário de materiais escolares por meio de doações e parcerias, permitindo empréstimos ou doações aos estudantes que precisarem, sem exposição ou constrangimento.',3,false,'Publicada');
insert into public.proposal_catalogue(id,title,description,category,approved,editorial_state) values(14,'Feira de Trocas Solidárias','Organizar eventos para troca e doação de livros, apostilas e materiais escolares em boas condições, incentivando o reaproveitamento.',3,false,'Publicada');
insert into public.proposal_catalogue(id,title,description,category,approved,editorial_state) values(15,'Guia do Estudante','Criar um guia acessível com informações sobre clubes, espaços, serviços, regras e contatos úteis da escola, especialmente para novos estudantes.',3,false,'Publicada');
insert into public.proposal_catalogue(id,title,description,category,approved,editorial_state) values(16,'Escuta Estudantil','Facilitar a comunicação dos alunos com o Grêmio, acolhendo sugestões e encaminhando problemas sensíveis aos profissionais responsáveis da escola.',3,false,'Publicada');
insert into public.proposal_catalogue(id,title,description,category,approved,editorial_state) values(17,'Intervalo em Jogo','Organizar desafios semanais, torneios amistosos e atividades entre turmas utilizando os jogos já disponíveis. Os estudantes também poderão apresentar jogos criados por eles.',3,false,'Publicada');
insert into public.proposal_catalogue(id,title,description,category,approved,editorial_state) values(18,'Acolhimento dos Novos Alunos','Organizar estudantes voluntários para apresentar a escola, seus clubes e atividades aos recém-chegados, facilitando a adaptação.',3,false,'Publicada');
insert into public.proposal_catalogue(id,title,description,category,approved,editorial_state) values(19,'Programa de Projetos Estudantis','Permitir que os clubes peçam ajuda diretamente ao Grêmio para obter materiais, planejar atividades e desenvolver ideias, complementando os projetos e culminâncias de clubes e eletivas que já existem na escola.',4,false,'Publicada');
insert into public.proposal_catalogue(id,title,description,category,approved,editorial_state) values(20,'Votação de Prioridades','Realizar consultas para que os estudantes escolham quais melhorias e projetos viáveis devem receber mais atenção do Grêmio.',4,false,'Publicada');
insert into public.proposal_catalogue(id,title,description,category,approved,editorial_state) values(21,'Grêmio Transparente','Divulgar periodicamente ações realizadas, propostas encaminhadas, respostas da gestão e utilização dos recursos disponíveis.',4,false,'Publicada');
insert into public.proposal_catalogue(id,title,description,category,approved,editorial_state) values(22,'Representantes Mais Ativos','Promover reuniões periódicas com representantes de turma para recolher demandas, discutir soluções e informar resultados.',4,false,'Publicada');
insert into public.proposal_catalogue(id,title,description,category,approved,editorial_state) values(23,'Feira de Clubes','Organizar apresentações dos clubes existentes, permitindo que estudantes conheçam as atividades e descubram novas oportunidades de participação.',4,false,'Publicada');
insert into public.proposal_catalogue(id,title,description,category,approved,editorial_state) values(24,'Eventos Estudantis','Promover, com autorização, eventos culturais, exposições, campeonatos, gincanas e outras atividades organizadas com participação dos alunos.',4,false,'Publicada');
insert into public.proposal_catalogue(id,title,description,category,approved,editorial_state) values(25,'Plano Escola Econômica','Propor medidas para reduzir desperdícios de água, energia e papel, incentivando o consumo consciente sem prejudicar as atividades escolares.',5,false,'Publicada');
insert into public.proposal_catalogue(id,title,description,category,approved,editorial_state) values(26,'Reaproveitamento Inteligente','Priorizar a reutilização de materiais, decorações e equipamentos disponíveis antes de solicitar novas compras para projetos do Grêmio.',5,false,'Publicada');
insert into public.proposal_catalogue(id,title,description,category,approved,editorial_state) values(27,'Gestão por Metas e Orçamento Participativo','Estabelecer metas para os projetos do Grêmio, consultar estudantes sobre prioridades, comparar preços e divulgar gastos e resultados quando houver recursos disponíveis.',5,false,'Publicada');
insert into public.proposal_catalogue(id,title,description,category,approved,editorial_state) values(28,'Seleção Transparente de Projetos','Criar critérios públicos para escolher projetos que receberão apoio do Grêmio, considerando viabilidade, benefícios e custos, evitando favoritismos.',5,false,'Publicada');
insert into public.proposal_catalogue(id,title,description,category,approved,editorial_state) values(29,'Educação Financeira na Prática','Promover oficinas voluntárias sobre planejamento de gastos, consumo consciente, organização financeira e noções básicas de economia.',5,false,'Publicada');
insert into public.proposal_catalogue(id,title,description,category,approved,editorial_state) values(30,'Avaliação de Resultados','Realizar pesquisas periódicas para avaliar os projetos desenvolvidos pelo Grêmio, identificar problemas e divulgar os resultados.',5,false,'Publicada');
insert into public.proposal_catalogue(id,title,description,category,approved,editorial_state) values(31,'Investimento na Agrofloresta','Buscar recursos, doações e parcerias para melhorar a infraestrutura da agrofloresta, investindo em ferramentas, equipamentos, irrigação e materiais necessários para sua manutenção.',6,false,'Publicada');
insert into public.proposal_catalogue(id,title,description,category,approved,editorial_state) values(32,'Expansão da Agrofloresta','Propor investimentos na ampliação do cultivo, com novas mudas, árvores frutíferas e espécies adequadas ao ambiente, respeitando o planejamento e as necessidades da agrofloresta existente.',6,false,'Publicada');
insert into public.proposal_catalogue(id,title,description,category,approved,editorial_state) values(33,'Fundo de Desenvolvimento da Agrofloresta','Criar campanhas voluntárias de arrecadação e buscar parcerias autorizadas para financiar melhorias na agrofloresta, com planejamento dos investimentos e prestação de contas sobre os recursos utilizados.',6,false,'Publicada');
insert into public.proposal_catalogue(id,title,description,category,approved,editorial_state) values(34,'Modernização e Tecnologia na Agrofloresta','Propor melhorias tecnológicas, como sistemas de irrigação mais eficientes, monitoramento das condições do solo e ferramentas que facilitem o cultivo, buscando reduzir desperdícios e melhorar a produtividade.',6,false,'Publicada');
insert into public.proposal_catalogue(id,title,description,category,approved,editorial_state) values(35,'Valorização da Produção da Agrofloresta','Criar, junto aos responsáveis, iniciativas para aproveitar melhor a produção da agrofloresta, como exposições, feiras educativas e projetos de utilização dos alimentos cultivados, respeitando as normas da escola.',6,false,'Publicada');
insert into public.proposal_catalogue(id,title,description,category,approved,editorial_state) values(36,'Rotina sem Sobrecarga','Criar um calendário colaborativo para identificar semanas com excesso de avaliações e atividades. O Grêmio poderá reunir dificuldades relatadas pelos estudantes e solicitar diálogo com a coordenação para melhorar a distribuição das demandas, respeitando as decisões pedagógicas.',7,false,'Aguardando aprovação da equipe');
insert into public.proposal_catalogue(id,title,description,category,approved,editorial_state) values(37,'Ninguém Fica de Fora','Promover integração voluntária entre estudantes, especialmente novatos e pessoas que têm dificuldade de fazer amizades. Organizar atividades de acolhimento e encontros acompanhados pela escola, sem exposição ou identificação pública de estudantes.',7,false,'Aguardando aprovação da equipe');
insert into public.proposal_catalogue(id,title,description,category,approved,editorial_state) values(38,'Dignidade no Dia a Dia','Acompanhar a disponibilidade de sabonete, papel higiênico, produtos de higiene menstrual e outros itens essenciais. Criar uma maneira discreta de comunicar faltas e encaminhar solicitações à escola.',7,false,'Aguardando aprovação da equipe');
insert into public.proposal_catalogue(id,title,description,category,approved,editorial_state) values(39,'Semana do Bem-Estar','Propor atividades voluntárias sobre convivência, saúde, descanso, movimento, respeito e hábitos saudáveis, com apoio dos profissionais escolares. Não apresentar isso como atendimento médico ou psicológico.',7,false,'Aguardando aprovação da equipe');
insert into public.proposal_catalogue(id,title,description,category,approved,editorial_state) values(40,'Merenda com Voz','Recolher opiniões sobre quantidade das porções, temperatura, qualidade e aceitação das refeições. Organizar os resultados para solicitar avaliação da direção e dos responsáveis pela alimentação. O Grêmio não pode garantir unilateralmente mudanças no cardápio ou nas porções.',7,false,'Aguardando aprovação da equipe');
insert into public.proposal_catalogue(id,title,description,category,approved,editorial_state) values(41,'Marasca de Cara Nova','Propor melhorias na pintura, conservação e aparência dos espaços da escola. Mapear os locais prioritários e apresentar as demandas à gestão. Eventuais intervenções artísticas estudantis devem ter autorização; manutenção técnica deverá ser realizada por profissionais responsáveis.',8,false,'Aguardando aprovação da equipe');
insert into public.proposal_catalogue(id,title,description,category,approved,editorial_state) values(42,'Banheiros Dignos','Levantar problemas em portas, fechaduras, torneiras, descargas, espelhos e instalações sanitárias. Solicitar orçamento, priorização e reparos conforme a disponibilidade de recursos e autorização escolar.',8,false,'Aguardando aprovação da equipe');
insert into public.proposal_catalogue(id,title,description,category,approved,editorial_state) values(43,'Salas Mais Frescas','Identificar salas com ventilação inadequada, calor excessivo ou ventiladores que precisam de reparos. Encaminhar pedidos de manutenção ou melhorias de conforto térmico.',8,false,'Aguardando aprovação da equipe');
insert into public.proposal_catalogue(id,title,description,category,approved,editorial_state) values(44,'Iluminação e Segurança dos Espaços','Mapear espaços mal iluminados e instalações que precisam de avaliação. Encaminhar os problemas à equipe responsável. Estudantes não devem executar reparos elétricos.',8,false,'Aguardando aprovação da equipe');
insert into public.proposal_catalogue(id,title,description,category,approved,editorial_state) values(45,'Carteiras e Mobiliário em Ordem','Organizar um levantamento de carteiras, cadeiras, mesas e outros móveis danificados. Priorizar necessidades junto aos representantes de turma e encaminhar pedidos de reparo ou substituição.',8,false,'Aguardando aprovação da equipe');
alter table public.proposal_catalogue enable row level security;
revoke all on public.proposal_catalogue from anon,authenticated;
grant select on public.proposal_catalogue to anon,authenticated;
grant insert,update on public.proposal_catalogue to authenticated;
grant all on public.proposal_catalogue to service_role;
create policy catalogue_public on public.proposal_catalogue for select to anon,authenticated using(editorial_state='Publicada');
create policy catalogue_admin on public.proposal_catalogue for all to authenticated using((select public.is_admin())) with check((select public.is_admin()));
alter table public.proposal_progress drop constraint proposal_progress_proposal_id_check;
alter table public.proposal_progress add constraint proposal_progress_proposal_id_check check(proposal_id between 1 and 45);
alter table public.proposal_classifications drop constraint proposal_classifications_proposal_id_check;
alter table public.proposal_classifications add constraint proposal_classifications_proposal_id_check check(proposal_id between 1 and 45);
insert into public.proposal_classifications(proposal_id) select generate_series(36,45);
drop policy progress_public on public.proposal_progress;
create policy progress_public on public.proposal_progress for select to anon,authenticated using(exists(select 1 from public.proposal_catalogue c where c.id=proposal_id and c.editorial_state='Publicada'));
drop policy classification_read on public.proposal_classifications;
create policy classification_read on public.proposal_classifications for select to anon,authenticated using(exists(select 1 from public.proposal_catalogue c where c.id=proposal_id and c.editorial_state='Publicada'));
create table public.ft_projects (
 id uuid primary key default gen_random_uuid(),
 proposal_id integer references public.proposal_catalogue(id),
 title text not null check(public.ft_plain(title,120,3)),
 description text not null check(char_length(btrim(description)) between 10 and 3000 and description !~ '[<>]'),
 category text not null check(public.ft_plain(category,100,1)),
 priority boolean not null default false,
 responsible text not null default '' check(public.ft_plain(responsible,120)),
 deadline date,
 dependencies text not null default '' check(char_length(dependencies)<=1000 and dependencies !~ '[<>]'),
 authorization_required boolean not null default true,
 authorization_status text not null default 'A verificar' check(authorization_status in ('A verificar','Solicitada','Concedida','Dispensada pelos responsáveis','Não concedida')),
 estimated_cost numeric(12,2) check(estimated_cost>=0),
 confirmed_resources numeric(12,2) check(confirmed_resources>=0),
 stage text not null default 'Em planejamento' check(stage in ('Em planejamento','Em análise','Aguardando autorização','Aprovado para execução','Em execução','Concluído','Suspenso','Não viável')),
 update_note text not null default '' check(char_length(update_note)<=2000 and update_note !~ '[<>]'),
 final_result text not null default '' check(char_length(final_result)<=2000 and final_result !~ '[<>]'),
 document_url text not null default '' check(document_url='' or document_url ~ '^https://[^[:space:]<>]+$'),
 published boolean not null default false,
 reviewed boolean not null default false,
 created_at timestamptz not null default now(),
 updated_at timestamptz not null default now(),
 check(not published or reviewed)
);
create table public.ft_project_history (
 id bigint generated always as identity primary key,
 project_id uuid not null references public.ft_projects(id),
 stage text not null,
 note text not null,
 recorded_at timestamptz not null default now()
);
create function public.ft_stamp_project() returns trigger language plpgsql security definer set search_path='' as $$
begin
 new.updated_at:=now();
 if TG_OP='UPDATE' then new.created_at:=old.created_at; end if;
 return new;
end;
$$;
create function public.ft_record_history() returns trigger language plpgsql security definer set search_path='' as $$
begin
 if TG_OP='INSERT' then
  insert into public.ft_project_history(project_id,stage,note) values(new.id,new.stage,new.update_note);
 elsif new.stage is distinct from old.stage or new.update_note is distinct from old.update_note then
  insert into public.ft_project_history(project_id,stage,note) values(new.id,new.stage,new.update_note);
 end if;
 return new;
end;
$$;
create trigger ft_project_stamp before insert or update on public.ft_projects for each row execute function public.ft_stamp_project();
create trigger ft_project_history after insert or update on public.ft_projects for each row execute function public.ft_record_history();
create table public.management_records (
 id uuid primary key default gen_random_uuid(),
 kind text not null check(kind in ('goal','budget','decision','return','timeline','home')),
 title text not null check(public.ft_plain(title,120,3)),
 body text not null check(char_length(btrim(body)) between 10 and 3000 and body !~ '[<>]'),
 period text not null default '' check(period in ('','Curto prazo','Médio prazo','Longo prazo','30 dias','60 dias','90 dias')),
 stage text not null default 'Proposta sujeita à aprovação' check(stage in ('Proposta sujeita à aprovação','Compromisso aprovado','Recebida','Em análise','Encaminhada','Respondida','Registrada','Publicada')),
 project_id uuid references public.ft_projects(id),
 resource_origin text not null default '' check(public.ft_plain(resource_origin,300)),
 confirmed_resources numeric(12,2) check(confirmed_resources>=0),
 approved_expenses numeric(12,2) check(approved_expenses>=0),
 actual_spending numeric(12,2) check(actual_spending>=0),
 reference_date date,
 position integer not null default 0 check(position between 0 and 100),
 published boolean not null default false,
 reviewed boolean not null default false,
 created_at timestamptz not null default now(),
 updated_at timestamptz not null default now(),
 check(not published or reviewed),
 check(kind='budget' or (confirmed_resources is null and approved_expenses is null and actual_spending is null))
);
create unique index one_home_content on public.management_records(kind) where kind='home';
create function public.ft_stamp_record() returns trigger language plpgsql set search_path='' as $$
begin
 new.updated_at:=now();
 if TG_OP='UPDATE' then new.created_at:=old.created_at; end if;
 return new;
end;
$$;
create trigger management_stamp before insert or update on public.management_records for each row execute function public.ft_stamp_record();
create trigger catalogue_stamp before insert or update on public.proposal_catalogue for each row execute function public.ft_stamp_record();
alter table public.ft_projects enable row level security;
alter table public.ft_project_history enable row level security;
alter table public.management_records enable row level security;
revoke all on public.ft_projects,public.ft_project_history,public.management_records from anon,authenticated;
grant select on public.ft_projects,public.ft_project_history,public.management_records to anon,authenticated;
grant insert,update on public.ft_projects,public.management_records to authenticated;
grant all on public.ft_projects,public.ft_project_history,public.management_records to service_role;
grant usage,select on sequence public.ft_project_history_id_seq to service_role;
create policy ft_public on public.ft_projects for select to anon,authenticated using(published and reviewed);
create policy ft_admin on public.ft_projects for all to authenticated using((select public.is_admin())) with check((select public.is_admin()));
create policy ft_history_public on public.ft_project_history for select to anon,authenticated using(exists(select 1 from public.ft_projects p where p.id=project_id and p.published and p.reviewed));
create policy ft_history_admin on public.ft_project_history for select to authenticated using((select public.is_admin()));
create policy management_public on public.management_records for select to anon,authenticated using(published and reviewed);
create policy management_admin on public.management_records for all to authenticated using((select public.is_admin())) with check((select public.is_admin()));
revoke all on function public.ft_stamp_project(),public.ft_record_history(),public.ft_stamp_record() from public;
alter table public.team_members add column role text not null default '' check(public.ft_plain(role,100));
create or replace function public.replace_team(members jsonb) returns void language plpgsql security definer set search_path='' as $$
declare item jsonb; n integer:=0; member_name text; member_role text;
begin
 if not public.is_admin() then raise exception 'Unauthorized'; end if;
 if jsonb_typeof(members)<>'array' or jsonb_array_length(members)>100 then raise exception 'Invalid members'; end if;
 perform pg_advisory_xact_lock(845001);
 for item in select * from jsonb_array_elements(members) loop
  member_name:=case when jsonb_typeof(item)='string' then item #>> '{}' else item->>'name' end;
  member_role:=case when jsonb_typeof(item)='string' then '' else coalesce(item->>'role','') end;
  if member_name is null or not public.ft_plain(member_name,60,1) or not public.ft_plain(member_role,100) then raise exception 'Invalid member'; end if;
 end loop;
 delete from public.team_members where position between 0 and 99;
 for item in select * from jsonb_array_elements(members) loop
  member_name:=case when jsonb_typeof(item)='string' then item #>> '{}' else item->>'name' end;
  member_role:=case when jsonb_typeof(item)='string' then '' else coalesce(item->>'role','') end;
  insert into public.team_members(name,role,position) values(btrim(member_name),btrim(member_role),n);
  n:=n+1;
 end loop;
end;
$$;
alter table public.faq_entries drop constraint faq_entries_id_check;
alter table public.faq_entries add constraint faq_entries_id_check check(id between 1 and 30);
notify pgrst,'reload schema';
commit;
