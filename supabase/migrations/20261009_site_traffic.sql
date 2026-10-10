begin;
create table public.site_visits(
 id uuid primary key,
 visitor_hash text not null check(visitor_hash ~ '^[a-f0-9]{64}$'),
 landing_page text not null check(landing_page in ('index.html','catalogo.html','propostas.html','gestao.html','proposta.html','projetos.html','calendario.html','faq.html','eleicoes.html','planejamento.html','historia.html','gremio.html','bem-estar.html','infraestrutura.html','privacidade.html','404.html')),
 started_at timestamptz not null default now(),
 last_seen timestamptz not null default now(),
 active_seconds integer not null default 0 check(active_seconds between 0 and 43200)
);
create index site_visits_started on public.site_visits(started_at);
create table public.site_traffic_daily(day date primary key,visits bigint not null default 0,active_seconds bigint not null default 0);
alter table public.site_visits enable row level security;
alter table public.site_traffic_daily enable row level security;
revoke all on public.site_visits,public.site_traffic_daily from public,anon,authenticated;
grant all on public.site_visits,public.site_traffic_daily to service_role;
create function public.record_site_visit(visit_id uuid,browser_hash text,target_page text,seconds integer) returns boolean language plpgsql security definer set search_path='' as $$
declare previous public.site_visits%rowtype; current_seconds integer; delta integer; visit_day date;
begin
 if browser_hash is null or browser_hash !~ '^[a-f0-9]{64}$' or seconds is null or seconds<0 or seconds>43200 then raise exception 'Invalid visit'; end if;
 perform pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtext(visit_id::text));
 select * into previous from public.site_visits where id=visit_id for update;
 if found then
  if previous.visitor_hash<>browser_hash then raise exception 'Invalid visit'; end if;
  current_seconds:=greatest(previous.active_seconds,least(seconds,greatest(0,floor(extract(epoch from now()-previous.started_at))::integer+2)));
  delta:=current_seconds-previous.active_seconds;
  visit_day:=(previous.started_at at time zone 'America/Sao_Paulo')::date;
  update public.site_visits set active_seconds=current_seconds,last_seen=now() where id=visit_id;
  update public.site_traffic_daily set active_seconds=active_seconds+delta where day=visit_day;
 else
  insert into public.site_visits(id,visitor_hash,landing_page) values(visit_id,browser_hash,target_page);
  visit_day:=(now() at time zone 'America/Sao_Paulo')::date;
  insert into public.site_traffic_daily(day,visits) values(visit_day,1) on conflict(day) do update set visits=public.site_traffic_daily.visits+1;
 end if;
 return true;
end; $$;
revoke all on function public.record_site_visit(uuid,text,text,integer) from public,anon,authenticated;
grant execute on function public.record_site_visit(uuid,text,text,integer) to service_role;
create function public.site_traffic_summary() returns jsonb language plpgsql security definer set search_path='' as $$
declare result jsonb;
begin
 if not public.is_admin() then raise exception 'Not authorized'; end if;
 select jsonb_build_object('total_visits',coalesce((select sum(visits) from public.site_traffic_daily),0),'today_visits',coalesce((select visits from public.site_traffic_daily where day=(now() at time zone 'America/Sao_Paulo')::date),0),'week_visits',(select count(*) from public.site_visits where started_at>=now()-interval '7 days'),'week_browsers',(select count(distinct visitor_hash) from public.site_visits where started_at>=now()-interval '7 days'),'average_seconds',coalesce((select round(avg(active_seconds)) from public.site_visits where started_at>=now()-interval '7 days'),0),'daily',coalesce((select jsonb_agg(t order by day desc) from (select day,visits,active_seconds from public.site_traffic_daily order by day desc limit 14)t),'[]'::jsonb)) into result;
 return result;
end; $$;
revoke all on function public.site_traffic_summary() from public,anon;
grant execute on function public.site_traffic_summary() to authenticated;
notify pgrst,'reload schema';
commit;
