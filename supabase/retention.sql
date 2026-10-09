create extension if not exists pg_cron with schema extensions;
select cron.schedule(
  'forcatarefa-security-retention',
  '15 * * * *',
  $$
    delete from public.security_events where created_at < now() - interval '7 days';
    delete from public.rate_buckets where expires_at < now() - interval '1 day';
    delete from public.session_suspensions where suspended_until < now() - interval '7 days';
  $$
);
