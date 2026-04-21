update plans
set name = 'Free',
    updated_at = now()
where code = 'trial'
  and name <> 'Free';

insert into subscriptions (
  id,
  tenant_id,
  plan_id,
  provider,
  provider_subscription_id,
  status,
  period_start,
  period_end,
  created_at,
  updated_at
)
select
  (
    substr(md5(t.id::text || '-free-subscription'), 1, 8) || '-' ||
    substr(md5(t.id::text || '-free-subscription'), 9, 4) || '-' ||
    substr(md5(t.id::text || '-free-subscription'), 13, 4) || '-' ||
    substr(md5(t.id::text || '-free-subscription'), 17, 4) || '-' ||
    substr(md5(t.id::text || '-free-subscription'), 21, 12)
  )::uuid,
  t.id,
  p.id,
  'migration',
  'free-' || t.id::text,
  'active',
  now(),
  now() + interval '3650 days',
  now(),
  now()
from tenants t
join plans p on p.code = 'trial'
where not exists (
  select 1
  from subscriptions s
  where s.tenant_id = t.id
);
