select cron.schedule('forcatarefa-traffic-retention','25 * * * *',$$delete from public.site_visits where started_at < now() - interval '7 days';$$);
