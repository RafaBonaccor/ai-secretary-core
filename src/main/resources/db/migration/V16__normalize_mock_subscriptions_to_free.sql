update assistant_core.subscriptions s
set plan_id = p.id,
    updated_at = now()
from assistant_core.plans p
where p.code = 'trial'
  and lower(coalesce(s.provider, '')) = 'mock'
  and s.plan_id <> p.id;
