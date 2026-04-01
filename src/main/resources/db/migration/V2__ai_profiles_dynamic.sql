alter table ai_profiles
  add column if not exists slug varchar(255),
  add column if not exists profile_type varchar(100),
  add column if not exists business_type varchar(100),
  add column if not exists language varchar(20),
  add column if not exists welcome_message text,
  add column if not exists config_json text,
  add column if not exists is_default boolean not null default false,
  add column if not exists active boolean not null default true;

update ai_profiles
set
  slug = coalesce(slug, lower(replace(name, ' ', '-'))),
  profile_type = coalesce(profile_type, 'appointment_secretary'),
  business_type = coalesce(business_type, 'generic'),
  language = coalesce(language, 'pt-BR'),
  config_json = coalesce(config_json, '{}')
where slug is null
   or profile_type is null
   or business_type is null
   or language is null
   or config_json is null;

alter table ai_profiles
  alter column slug set not null,
  alter column profile_type set not null,
  alter column business_type set not null,
  alter column language set not null,
  alter column config_json set not null;

alter table channel_instances
  add column if not exists ai_profile_id uuid references ai_profiles(id) on delete set null;

create unique index if not exists uk_ai_profiles_tenant_slug on ai_profiles(tenant_id, slug);
create index if not exists idx_ai_profiles_tenant_business_type on ai_profiles(tenant_id, business_type);
create index if not exists idx_channel_instances_ai_profile_id on channel_instances(ai_profile_id);
