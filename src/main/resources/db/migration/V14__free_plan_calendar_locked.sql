update plans
set calendar_enabled = false,
    updated_at = now()
where code = 'trial';
