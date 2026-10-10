begin;
alter table public.proposal_catalogue drop constraint proposal_catalogue_check;
create or replace function public.set_proposal_editorial(proposal_ids integer[],next_state text,review_confirmed boolean default false) returns integer language plpgsql security definer set search_path='' as $$
declare targets integer[]; changed integer;
begin
 if not public.is_admin() then raise exception 'Not authorized'; end if;
 select array_agg(distinct id) into targets from unnest(proposal_ids) id;
 if targets is null or cardinality(targets)>100 then raise exception 'Select between 1 and 100 proposals'; end if;
 if next_state not in ('Aguardando aprovação da equipe','Rascunho','Aprovada pela equipe','Publicada') or next_state is null then raise exception 'Invalid editorial state'; end if;
 if next_state='Publicada' and not coalesce(review_confirmed,false) then raise exception 'Publication confirmation required'; end if;
 if (select count(*) from public.proposal_catalogue where id=any(targets))<>cardinality(targets) then raise exception 'Proposal not found'; end if;
 update public.proposal_catalogue set editorial_state=next_state where id=any(targets);
 get diagnostics changed=row_count;
 return changed;
end; $$;
revoke all on function public.set_proposal_editorial(integer[],text,boolean) from public,anon;
grant execute on function public.set_proposal_editorial(integer[],text,boolean) to authenticated;
notify pgrst,'reload schema';
commit;
