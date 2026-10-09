begin;
alter table public.proposal_catalogue drop constraint proposal_catalogue_id_check;
alter table public.proposal_catalogue add constraint proposal_catalogue_id_check check(id between 1 and 100000);
alter table public.proposal_progress drop constraint proposal_progress_proposal_id_check;
alter table public.proposal_progress add constraint proposal_progress_proposal_id_check check(proposal_id between 1 and 100000);
alter table public.proposal_progress add constraint progress_catalogue_reference foreign key(proposal_id) references public.proposal_catalogue(id);
alter table public.proposal_classifications drop constraint proposal_classifications_proposal_id_check;
alter table public.proposal_classifications add constraint proposal_classifications_proposal_id_check check(proposal_id between 1 and 100000);
alter table public.proposal_classifications add constraint classification_catalogue_reference foreign key(proposal_id) references public.proposal_catalogue(id);
create sequence public.proposal_number_sequence minvalue 46 maxvalue 100000 start 46;
select setval('public.proposal_number_sequence',greatest(46,(select coalesce(max(id),45)+1 from public.proposal_catalogue)),false);
revoke all on sequence public.proposal_number_sequence from public,anon,authenticated;
create function public.create_new_proposal(payload jsonb) returns integer language plpgsql security definer set search_path='' as $$
declare new_id integer;
begin
 if not public.is_admin() then raise exception 'Not authorized'; end if;
 if jsonb_typeof(payload) is distinct from 'object' then raise exception 'Invalid proposal'; end if;
 new_id:=nextval('public.proposal_number_sequence');
 insert into public.proposal_catalogue(id,title,description,category,objective,approach,responsible,authorization_notes,editorial_state)
 values(new_id,payload->>'title',payload->>'description',(payload->>'category')::integer,coalesce(payload->>'objective',''),coalesce(payload->>'approach',''),coalesce(payload->>'responsible',''),coalesce(payload->>'authorization_notes',''),'Rascunho');
 insert into public.proposal_classifications(proposal_id) values(new_id);
 return new_id;
end; $$;
revoke all on function public.create_new_proposal(jsonb) from public,anon;
grant execute on function public.create_new_proposal(jsonb) to authenticated;
create function public.set_proposal_editorial(proposal_ids integer[],next_state text,review_confirmed boolean default false) returns integer language plpgsql security definer set search_path='' as $$
declare targets integer[]; changed integer;
begin
 if not public.is_admin() then raise exception 'Not authorized'; end if;
 select array_agg(distinct id) into targets from unnest(proposal_ids) id;
 if targets is null or cardinality(targets)>100 then raise exception 'Select between 1 and 100 proposals'; end if;
 if next_state not in ('Aguardando aprovação da equipe','Rascunho','Aprovada pela equipe','Publicada') or next_state is null then raise exception 'Invalid editorial state'; end if;
 if next_state='Publicada' and not coalesce(review_confirmed,false) then raise exception 'Publication confirmation required'; end if;
 if next_state<>'Publicada' and exists(select 1 from unnest(targets) id where id<=35) then raise exception 'Original proposals must remain public'; end if;
 if (select count(*) from public.proposal_catalogue where id=any(targets))<>cardinality(targets) then raise exception 'Proposal not found'; end if;
 update public.proposal_catalogue set editorial_state=next_state where id=any(targets);
 get diagnostics changed=row_count;
 return changed;
end; $$;
revoke all on function public.set_proposal_editorial(integer[],text,boolean) from public,anon;
grant execute on function public.set_proposal_editorial(integer[],text,boolean) to authenticated;
create table public.management_calendar (
 id uuid primary key default gen_random_uuid(),
 title text not null check(public.ft_plain(title,120,3)),
 description text not null default '' check(char_length(description)<=2000 and description !~ '[<>]'),
 event_date date not null check(event_date between date '2027-01-01' and date '2028-12-31'),
 end_date date check(end_date between date '2027-01-01' and date '2028-12-31' and end_date>=event_date),
 start_time time,
 event_kind text not null default 'Reunião' check(event_kind in ('Reunião','Atividade','Prazo','Outro')),
 stage text not null default 'Proposto' check(stage in ('Proposto','Confirmado','Concluído','Cancelado')),
 responsible text not null default '' check(public.ft_plain(responsible,120)),
 proposal_id integer references public.proposal_catalogue(id),
 created_at timestamptz not null default now(),
 updated_at timestamptz not null default now()
);
create index calendar_event_date on public.management_calendar(event_date);
alter table public.management_calendar enable row level security;
revoke all on public.management_calendar from public,anon,authenticated;
grant select,insert,update on public.management_calendar to authenticated;
grant all on public.management_calendar to service_role;
create policy calendar_admin_only on public.management_calendar for all to authenticated using((select public.is_admin())) with check((select public.is_admin()));
create trigger calendar_stamp before update on public.management_calendar for each row execute function public.ft_stamp_record();
commit;
