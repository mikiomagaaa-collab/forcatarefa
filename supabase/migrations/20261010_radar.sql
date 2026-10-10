begin;

alter table public.proposal_progress
  add column completion_percent smallint check (completion_percent between 0 and 100),
  add column completion_basis text not null default '' check (char_length(completion_basis) <= 1000 and completion_basis !~ '[<>]'),
  add constraint progress_measurement_basis check (completion_percent is null or char_length(btrim(completion_basis)) >= 10);

notify pgrst, 'reload schema';
commit;
