update plans
set calendar_enabled = true,
    updated_at = now()
where code = 'starter';
