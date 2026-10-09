begin;
alter table public.proposal_classifications add column if not exists sport boolean not null default false;
update public.proposal_classifications set sport = true where proposal_id in (17,24);
notify pgrst, 'reload schema';
commit;
