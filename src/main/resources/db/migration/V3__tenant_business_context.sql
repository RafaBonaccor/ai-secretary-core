alter table tenants
  add column if not exists business_context_json text not null default '{}';
