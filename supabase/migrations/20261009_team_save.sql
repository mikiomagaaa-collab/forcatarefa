begin;
create or replace function public.replace_team(members jsonb) returns void language plpgsql security definer set search_path = '' as $$
declare item jsonb; n integer := 0;
begin
  if not public.is_admin() then raise exception 'Unauthorized'; end if;
  if jsonb_typeof(members) <> 'array' or jsonb_array_length(members) > 100 then raise exception 'Invalid members'; end if;
  perform pg_advisory_xact_lock(845001);
  for item in select * from jsonb_array_elements(members) loop
    if jsonb_typeof(item) <> 'string' or char_length(btrim(item #>> '{}')) not between 1 and 60 or (item #>> '{}') ~ '[<>]' then raise exception 'Invalid name'; end if;
  end loop;
  delete from public.team_members where position between 0 and 99;
  for item in select * from jsonb_array_elements(members) loop
    insert into public.team_members(name, position) values (btrim(item #>> '{}'), n);
    n := n + 1;
  end loop;
end;
$$;
notify pgrst, 'reload schema';
commit;
