begin;
create function public.erase_suggestion(target_id uuid, confirmed boolean)
returns boolean language plpgsql security definer set search_path = '' as $$
declare erased integer;
begin
  if not public.is_admin() then raise exception 'Acesso não autorizado'; end if;
  if confirmed is distinct from true then raise exception 'Confirme a exclusão'; end if;
  delete from public.suggestions where id = target_id;
  get diagnostics erased = row_count;
  return erased = 1;
end;
$$;
revoke all on function public.erase_suggestion(uuid,boolean) from public;
grant execute on function public.erase_suggestion(uuid,boolean) to authenticated;
create or replace function public.consume_rate(bucket_key text, max_hits integer, window_seconds integer)
returns boolean language plpgsql security definer set search_path = '' as $$
declare new_hits integer;
begin
  insert into public.rate_buckets(bucket,hits,expires_at) values(bucket_key,1,now()+make_interval(secs=>window_seconds))
  on conflict(bucket) do update set hits=case when public.rate_buckets.expires_at<=now() then 1 else public.rate_buckets.hits+1 end,
    expires_at=case when public.rate_buckets.expires_at<=now() then now()+make_interval(secs=>window_seconds) else public.rate_buckets.expires_at end returning hits into new_hits;
  return new_hits <= max_hits;
end;
$$;
commit;
