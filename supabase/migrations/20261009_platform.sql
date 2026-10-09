begin;
create table public.proposal_classifications (
  proposal_id integer primary key check(proposal_id between 1 and 35),
  priority boolean not null default false,
  early boolean not null default false,
  initial_action text not null default '' check(char_length(initial_action)<=1000 and initial_action !~ '[<>]')
);
insert into public.proposal_classifications(proposal_id,priority,early,initial_action)
select i,i in (5,7,17,19,27),i in (5,6,10,13,20,21,22,27),case i
when 5 then 'Iniciar a organização e o levantamento de estudantes interessados.'
when 6 then 'Começar a divulgar oportunidades educacionais.'
when 10 then 'Iniciar o levantamento das demandas de infraestrutura.'
when 13 then 'Organizar campanhas iniciais de arrecadação.'
when 20 then 'Consultar os estudantes sobre melhorias prioritárias.'
when 21 then 'Iniciar a publicação dos registros de atividades e encaminhamentos.'
when 22 then 'Organizar as primeiras reuniões com representantes.'
when 27 then 'Estabelecer o planejamento inicial e as metas da gestão.' else '' end
from generate_series(1,35) i;
alter table public.proposal_progress drop constraint proposal_progress_stage_check;
alter table public.proposal_progress add constraint proposal_progress_stage_check check(stage in ('Apresentada','Em planejamento','Encaminhada à gestão','Aprovada','Em execução','Realizada','Não autorizada'));
alter table public.proposal_progress add column needs text not null default '' check(char_length(needs)<=1000 and needs !~ '[<>]');
alter table public.proposal_progress add column official_response text not null default '' check(char_length(official_response)<=2000 and official_response !~ '[<>]');
create table public.project_records (
  id uuid primary key default gen_random_uuid(),
  kind text not null check(kind in ('laboratorio','talentos','jogos','ft','mcan')),
  category text not null check(char_length(category) between 1 and 100 and category !~ '[<>]'),
  title text not null check(char_length(btrim(title)) between 3 and 120 and title !~ '[<>]'),
  description text not null check(char_length(btrim(description)) between 10 and 3000 and description !~ '[<>]'),
  stage text not null default 'Em análise' check(stage in ('Ideia recebida','Em análise','Em planejamento','Encaminhada à gestão','Aprovada','Não autorizada','Em execução','Realizada')),
  needs text not null default '' check(char_length(needs)<=1000 and needs !~ '[<>]'),
  official_response text not null default '' check(char_length(official_response)<=2000 and official_response !~ '[<>]'),
  pseudonym text not null default '' check(char_length(pseudonym)<=60 and pseudonym !~ '[<>]'),
  scheduled_at timestamptz,
  published boolean not null default false,
  consent_confirmed boolean not null default false,
  updated_at timestamptz not null default now(),
  check(not published or consent_confirmed)
);
create table public.faq_entries (
  id integer primary key check(id between 1 and 15),
  question text not null check(char_length(question) between 3 and 200 and question !~ '[<>]'),
  answer text not null check(char_length(answer) between 10 and 3000 and answer !~ '[<>]'),
  category text not null check(char_length(category) between 1 and 100 and category !~ '[<>]'),
  link text not null default '' check(link='' or link ~ '^(index\.html|proposta\.html|projetos\.html)([?#][a-zA-Z0-9_=&-]+)?$')
);
create table public.election_2026 (
  id boolean primary key default true check(id),
  status text not null default 'aguardando' check(status in ('aguardando','apuracao','eleita','nao_eleita')),
  confirmed_at timestamptz,
  official_votes integer check(official_votes>=0),
  source text not null default '' check(char_length(source)<=1000 and source !~ '[<>]'),
  message text not null default '' check(char_length(message)<=3000 and message !~ '[<>]'),
  retrospective text not null default '' check(char_length(retrospective)<=3000 and retrospective !~ '[<>]'),
  retrospective_approved boolean not null default false,
  message_approved boolean not null default false,
  published_at timestamptz,
  check(published_at is null or (status in ('eleita','nao_eleita') and confirmed_at is not null and char_length(btrim(source))>=3))
);
insert into public.election_2026(id) values(true);
alter table public.proposal_classifications enable row level security;
alter table public.project_records enable row level security;
alter table public.faq_entries enable row level security;
alter table public.election_2026 enable row level security;
revoke all on public.proposal_classifications,public.project_records,public.faq_entries,public.election_2026 from anon,authenticated;
grant select on public.proposal_classifications,public.project_records,public.faq_entries to anon,authenticated;
grant insert,update,delete on public.proposal_classifications,public.project_records,public.faq_entries to authenticated;
grant select on public.election_2026 to authenticated;
grant all on public.proposal_classifications,public.project_records,public.faq_entries,public.election_2026 to service_role;
create policy classification_read on public.proposal_classifications for select to anon,authenticated using(true);
create policy classification_edit on public.proposal_classifications for all to authenticated using((select public.is_admin())) with check((select public.is_admin()));
create policy project_public on public.project_records for select to anon,authenticated using(published and consent_confirmed);
create policy project_admin on public.project_records for all to authenticated using((select public.is_admin())) with check((select public.is_admin()));
create policy faq_public on public.faq_entries for select to anon,authenticated using(true);
create policy faq_admin on public.faq_entries for all to authenticated using((select public.is_admin())) with check((select public.is_admin()));
create policy election_admin on public.election_2026 for select to authenticated using((select public.is_admin()));
create function public.save_election(payload jsonb,confirm_publication boolean) returns void language plpgsql security definer set search_path='' as $$
declare old public.election_2026; target text; pub timestamptz;
begin
  if not public.is_admin() then raise exception 'Unauthorized'; end if;
  perform pg_advisory_xact_lock(845002);
  select * into old from public.election_2026 where id for update;
  target:=payload->>'status';
  if old.published_at is not null and target<>old.status then raise exception 'Published result requires verified correction process'; end if;
  pub:=old.published_at;
  if confirm_publication and pub is null then
    if now()<'2026-10-13 03:00:00+00'::timestamptz then raise exception 'Election period not started'; end if;
    if target not in ('eleita','nao_eleita') then raise exception 'Confirmed result required'; end if;
    pub:=now();
  end if;
  update public.election_2026 set status=target,confirmed_at=nullif(payload->>'confirmed_at','')::timestamptz,official_votes=nullif(payload->>'official_votes','')::integer,source=coalesce(payload->>'source',''),message=coalesce(payload->>'message',''),retrospective=coalesce(payload->>'retrospective',''),retrospective_approved=coalesce((payload->>'retrospective_approved')::boolean,false),message_approved=coalesce((payload->>'message_approved')::boolean,false),published_at=pub where id;
end;
$$;
create function public.election_public() returns jsonb language plpgsql stable security definer set search_path='' as $$
declare e public.election_2026; visible boolean; aggregate jsonb;
begin
  visible:=now()>='2026-10-13 03:00:00+00'::timestamptz;
  if not visible then return jsonb_build_object('server_now',now(),'visible',false,'closed',false); end if;
  select * into e from public.election_2026 where id;
  select jsonb_build_object('count',count(*),'first',min(created_at),'last',max(created_at)) into aggregate from public.vote_intentions;
  return jsonb_build_object('server_now',now(),'visible',true,'closed',e.published_at is not null,'intentions',aggregate,'status',case when e.published_at is not null then e.status when e.status='apuracao' then 'apuracao' else 'aguardando' end,'confirmed_at',case when e.published_at is not null then e.confirmed_at end,'official_votes',case when e.published_at is not null then e.official_votes end,'source',case when e.published_at is not null then e.source else '' end,'published_at',e.published_at,'message',case when e.message_approved then e.message else '' end,'retrospective',case when e.retrospective_approved then e.retrospective else '' end);
end;
$$;
create or replace function public.record_intention(target_hash text) returns boolean language plpgsql security definer set search_path='' as $$
declare inserted_count integer;
begin
  perform pg_advisory_xact_lock(845002);
  if exists(select 1 from public.election_2026 where published_at is not null) then raise exception 'Collection closed'; end if;
  insert into public.vote_intentions(session_hash) values(target_hash) on conflict do nothing;
  get diagnostics inserted_count=row_count;
  return inserted_count=1;
end;
$$;
revoke all on function public.save_election(jsonb,boolean),public.election_public() from public;
grant execute on function public.save_election(jsonb,boolean) to authenticated;
grant execute on function public.election_public() to anon,authenticated;
commit;
