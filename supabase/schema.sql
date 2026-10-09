begin;

create table public.account_controls (
  user_id uuid primary key references auth.users(id) on delete cascade,
  is_admin boolean not null default false,
  is_owner boolean not null default false,
  blocked boolean not null default false,
  suggestions_suspended_until timestamptz,
  reason text not null default '' check (char_length(reason) <= 300),
  check (not is_owner or is_admin)
);
create table public.team_members (
  id uuid primary key default gen_random_uuid(),
  name text not null check (char_length(btrim(name)) between 1 and 60 and name !~ '[<>]'),
  position integer not null check (position between 0 and 99)
);
create table public.suggestions (
  id uuid primary key default gen_random_uuid(),
  created_at timestamptz not null default now(),
  name text not null default '' check (char_length(name) <= 60 and name !~ '[<>]'),
  classroom text not null default '' check (char_length(classroom) <= 30 and classroom !~ '[<>]'),
  category text not null check (category in ('Educação','Infraestrutura','Bem-estar','Lazer','Tecnologia','Cultura','Outro')),
  message text not null check (char_length(btrim(message)) between 10 and 2000 and message !~ '[<>]'),
  status text not null default 'nova' check (status in ('nova','lida','analisada')),
  session_hash text not null check (session_hash ~ '^[a-f0-9]{64}$'),
  user_id uuid references auth.users(id) on delete set null
);
create index suggestions_created_idx on public.suggestions(created_at desc);
create table public.session_suspensions (
  session_hash text primary key check (session_hash ~ '^[a-f0-9]{64}$'),
  suspended_until timestamptz not null,
  reason text not null check (char_length(btrim(reason)) between 3 and 300),
  changed_by uuid not null references auth.users(id),
  updated_at timestamptz not null default now()
);
create table public.proposal_progress (
  proposal_id integer primary key check (proposal_id between 1 and 35),
  stage text not null check (stage in ('Apresentada','Em planejamento','Encaminhada à gestão','Aprovada','Realizada')),
  note text not null default '' check (char_length(note) <= 1000 and note !~ '[<>]'),
  updated_at timestamptz not null default now()
);
insert into public.proposal_progress values (8, 'Aprovada', 'Aprovada pela gestão. A implementação ainda deve ser organizada.', now());
create table public.institutional_updates (
  id uuid primary key default gen_random_uuid(),
  title text not null check (char_length(btrim(title)) between 3 and 120 and title !~ '[<>]'),
  body text not null check (char_length(btrim(body)) between 10 and 3000 and body !~ '[<>]'),
  published_at timestamptz not null default now()
);
create table public.vote_intentions (
  session_hash text primary key check (session_hash ~ '^[a-f0-9]{64}$'),
  created_at timestamptz not null default now()
);
create table public.election_baseline (
  id boolean primary key default true check (id),
  student_total integer not null check (student_total > 0),
  opposition_reported integer not null check (opposition_reported >= 0 and opposition_reported <= student_total)
);
insert into public.election_baseline values (true, 310, 80);
create table public.rate_buckets (
  bucket text primary key,
  hits integer not null,
  expires_at timestamptz not null
);
create index rate_buckets_expiry_idx on public.rate_buckets(expires_at);
create table public.security_events (
  id bigint generated always as identity primary key,
  created_at timestamptz not null default now(),
  event_type text not null check (event_type in ('rate_limited','suspended','invalid_captcha','login_denied')),
  session_hash text not null check (session_hash ~ '^[a-f0-9]{64}$')
);
create index security_events_created_idx on public.security_events(created_at);

alter table public.account_controls enable row level security;
alter table public.team_members enable row level security;
alter table public.suggestions enable row level security;
alter table public.session_suspensions enable row level security;
alter table public.proposal_progress enable row level security;
alter table public.institutional_updates enable row level security;
alter table public.vote_intentions enable row level security;
alter table public.election_baseline enable row level security;
alter table public.rate_buckets enable row level security;
alter table public.security_events enable row level security;

revoke all on public.account_controls, public.team_members, public.suggestions, public.session_suspensions, public.proposal_progress, public.institutional_updates, public.vote_intentions, public.election_baseline, public.rate_buckets, public.security_events from anon, authenticated;
grant select on public.team_members, public.proposal_progress, public.institutional_updates to anon, authenticated;
grant select on public.account_controls, public.session_suspensions, public.suggestions, public.security_events to authenticated;
grant update(status) on public.suggestions to authenticated;
grant insert, update, delete on public.proposal_progress, public.institutional_updates to authenticated;

create function public.is_admin() returns boolean language sql stable security definer set search_path = '' as $$
  select exists(select 1 from public.account_controls where user_id = (select auth.uid()) and is_admin and not blocked);
$$;
create function public.is_owner() returns boolean language sql stable security definer set search_path = '' as $$
  select exists(select 1 from public.account_controls where user_id = (select auth.uid()) and is_owner and not blocked);
$$;
revoke all on function public.is_admin(), public.is_owner() from public;
grant execute on function public.is_admin(), public.is_owner() to authenticated;

create policy team_public on public.team_members for select to anon, authenticated using (true);
create policy progress_public on public.proposal_progress for select to anon, authenticated using (true);
create policy updates_public on public.institutional_updates for select to anon, authenticated using (true);
create policy account_read on public.account_controls for select to authenticated using (user_id = (select auth.uid()) or (select public.is_owner()));
create policy suggestions_read on public.suggestions for select to authenticated using ((select public.is_admin()));
create policy suggestions_status on public.suggestions for update to authenticated using ((select public.is_admin())) with check ((select public.is_admin()));
create policy suspensions_read on public.session_suspensions for select to authenticated using ((select public.is_admin()));
create policy security_read on public.security_events for select to authenticated using ((select public.is_admin()));
create policy progress_edit on public.proposal_progress for all to authenticated using ((select public.is_admin())) with check ((select public.is_admin()));
create policy updates_edit on public.institutional_updates for all to authenticated using ((select public.is_admin())) with check ((select public.is_admin()));

create function public.replace_team(members jsonb) returns void language plpgsql security definer set search_path = '' as $$
declare item jsonb; n integer := 0;
begin
  if not public.is_admin() then raise exception 'Unauthorized'; end if;
  if jsonb_typeof(members) <> 'array' or jsonb_array_length(members) > 100 then raise exception 'Invalid members'; end if;
  perform pg_advisory_xact_lock(845001);
  for item in select * from jsonb_array_elements(members) loop
    if jsonb_typeof(item) <> 'string' or char_length(btrim(item #>> '{}')) not between 1 and 60 or (item #>> '{}') ~ '[<>]' then raise exception 'Invalid name'; end if;
  end loop;
  delete from public.team_members;
  for item in select * from jsonb_array_elements(members) loop
    insert into public.team_members(name, position) values (btrim(item #>> '{}'), n);
    n := n + 1;
  end loop;
end;
$$;
create function public.suspend_session(target_hash text, until_time timestamptz, justification text) returns void language plpgsql security definer set search_path = '' as $$
begin
  if not public.is_admin() then raise exception 'Unauthorized'; end if;
  if until_time > now() + interval '30 days' then raise exception 'Maximum 30 days'; end if;
  insert into public.session_suspensions(session_hash,suspended_until,reason,changed_by)
  values (target_hash,until_time,justification,auth.uid())
  on conflict(session_hash) do update set suspended_until=excluded.suspended_until, reason=excluded.reason, changed_by=excluded.changed_by,updated_at=now();
end;
$$;
create function public.manage_account(target_user uuid, admin_access boolean, block_access boolean, until_time timestamptz, justification text) returns void language plpgsql security definer set search_path = '' as $$
begin
  if not public.is_owner() then raise exception 'Unauthorized'; end if;
  if target_user = auth.uid() or exists(select 1 from public.account_controls where user_id=target_user and is_owner) then raise exception 'Owner is protected'; end if;
  if char_length(btrim(justification)) not between 3 and 300 then raise exception 'Justification required'; end if;
  insert into public.account_controls(user_id,is_admin,blocked,suggestions_suspended_until,reason)
  values(target_user,admin_access,block_access,until_time,justification)
  on conflict(user_id) do update set is_admin=excluded.is_admin,blocked=excluded.blocked,suggestions_suspended_until=excluded.suggestions_suspended_until,reason=excluded.reason;
end;
$$;
create function public.vote_summary() returns jsonb language plpgsql stable security definer set search_path = '' as $$
declare total integer; other_count integer; ft_count bigint;
begin
  if not public.is_admin() then raise exception 'Unauthorized'; end if;
  select student_total,opposition_reported into total,other_count from public.election_baseline where id;
  select count(*) into ft_count from public.vote_intentions;
  return jsonb_build_object('student_total',total,'opposition_reported',other_count,'ft_intentions',ft_count,'unrepresented_estimate',greatest(total-other_count-ft_count,0),'exceeds_baseline',ft_count+other_count>total,'ft_percentage',round(ft_count*100.0/total,2),'opposition_percentage',round(other_count*100.0/total,2));
end;
$$;
revoke all on function public.replace_team(jsonb),public.suspend_session(text,timestamptz,text),public.manage_account(uuid,boolean,boolean,timestamptz,text),public.vote_summary() from public;
grant execute on function public.replace_team(jsonb),public.suspend_session(text,timestamptz,text),public.manage_account(uuid,boolean,boolean,timestamptz,text),public.vote_summary() to authenticated;

create function public.consume_rate(bucket_key text, max_hits integer, window_seconds integer) returns boolean language plpgsql security definer set search_path = '' as $$
declare new_hits integer;
begin
  delete from public.rate_buckets where expires_at < now() - interval '1 day';
  delete from public.security_events where created_at < now() - interval '7 days';
  insert into public.rate_buckets(bucket,hits,expires_at) values(bucket_key,1,now()+make_interval(secs=>window_seconds))
  on conflict(bucket) do update set hits=case when public.rate_buckets.expires_at<=now() then 1 else public.rate_buckets.hits+1 end,
    expires_at=case when public.rate_buckets.expires_at<=now() then now()+make_interval(secs=>window_seconds) else public.rate_buckets.expires_at end returning hits into new_hits;
  return new_hits <= max_hits;
end;
$$;
create function public.record_intention(target_hash text) returns boolean language plpgsql security definer set search_path = '' as $$
declare inserted_count integer;
begin
  insert into public.vote_intentions(session_hash) values(target_hash) on conflict do nothing;
  get diagnostics inserted_count = row_count;
  return inserted_count=1;
end;
$$;
revoke all on function public.consume_rate(text,integer,integer),public.record_intention(text) from public,anon,authenticated;
grant execute on function public.consume_rate(text,integer,integer),public.record_intention(text) to service_role;
grant all on public.account_controls, public.team_members, public.suggestions, public.session_suspensions, public.proposal_progress, public.institutional_updates, public.vote_intentions, public.election_baseline, public.rate_buckets, public.security_events to service_role;
grant usage,select on sequence public.security_events_id_seq to service_role;

commit;
