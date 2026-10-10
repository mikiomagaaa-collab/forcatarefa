begin;
alter table public.management_calendar add column published boolean not null default false;
alter table public.management_calendar add column publication_confirmed boolean not null default false;
alter table public.management_calendar add column public_description text not null default '' check(public.ft_plain(public_description,1000));
alter table public.management_calendar add constraint calendar_publication_review check(not published or publication_confirmed);
grant select(id,title,event_date,end_date,start_time,event_kind,stage,public_description) on public.management_calendar to anon;
create policy calendar_public_read on public.management_calendar for select to anon using(published and publication_confirmed);
notify pgrst,'reload schema';
commit;
