alter table if exists plans
  add column if not exists max_whatsapp_numbers integer not null default 1;

alter table if exists plans
  add column if not exists max_ai_profiles integer not null default 1;

alter table if exists plans
  add column if not exists max_team_members integer not null default 1;

alter table if exists plans
  add column if not exists calendar_enabled boolean not null default false;

alter table if exists plans
  add column if not exists inbox_enabled boolean not null default true;

alter table if exists plans
  add column if not exists custom_prompt_enabled boolean not null default true;

alter table if exists plans
  add column if not exists advanced_automation_enabled boolean not null default false;

alter table if exists plans
  add column if not exists realtime_voice_enabled boolean not null default false;

alter table if exists plans
  add column if not exists future_features_enabled boolean not null default false;

alter table if exists plans
  add column if not exists priority_support_enabled boolean not null default false;

insert into plans (
  id,
  code,
  name,
  price_monthly,
  message_limit,
  audio_limit,
  automation_limit,
  max_whatsapp_numbers,
  max_ai_profiles,
  max_team_members,
  calendar_enabled,
  inbox_enabled,
  custom_prompt_enabled,
  advanced_automation_enabled,
  realtime_voice_enabled,
  future_features_enabled,
  priority_support_enabled,
  created_at,
  updated_at
)
values (
  '11111111-1111-1111-1111-111111111111',
  'trial',
  'Trial',
  0.00,
  500,
  0,
  0,
  1,
  1,
  1,
  false,
  true,
  true,
  false,
  false,
  false,
  false,
  now(),
  now()
)
on conflict (code) do update set
  name = excluded.name,
  price_monthly = excluded.price_monthly,
  message_limit = excluded.message_limit,
  audio_limit = excluded.audio_limit,
  automation_limit = excluded.automation_limit,
  max_whatsapp_numbers = excluded.max_whatsapp_numbers,
  max_ai_profiles = excluded.max_ai_profiles,
  max_team_members = excluded.max_team_members,
  calendar_enabled = excluded.calendar_enabled,
  inbox_enabled = excluded.inbox_enabled,
  custom_prompt_enabled = excluded.custom_prompt_enabled,
  advanced_automation_enabled = excluded.advanced_automation_enabled,
  realtime_voice_enabled = excluded.realtime_voice_enabled,
  future_features_enabled = excluded.future_features_enabled,
  priority_support_enabled = excluded.priority_support_enabled,
  updated_at = excluded.updated_at;

insert into plans (
  id,
  code,
  name,
  price_monthly,
  message_limit,
  audio_limit,
  automation_limit,
  max_whatsapp_numbers,
  max_ai_profiles,
  max_team_members,
  calendar_enabled,
  inbox_enabled,
  custom_prompt_enabled,
  advanced_automation_enabled,
  realtime_voice_enabled,
  future_features_enabled,
  priority_support_enabled,
  created_at,
  updated_at
)
values (
  '22222222-2222-2222-2222-222222222222',
  'starter',
  'Starter',
  49.00,
  2000,
  0,
  1,
  1,
  1,
  1,
  true,
  true,
  true,
  false,
  false,
  false,
  false,
  now(),
  now()
)
on conflict (code) do update set
  name = excluded.name,
  price_monthly = excluded.price_monthly,
  message_limit = excluded.message_limit,
  audio_limit = excluded.audio_limit,
  automation_limit = excluded.automation_limit,
  max_whatsapp_numbers = excluded.max_whatsapp_numbers,
  max_ai_profiles = excluded.max_ai_profiles,
  max_team_members = excluded.max_team_members,
  calendar_enabled = excluded.calendar_enabled,
  inbox_enabled = excluded.inbox_enabled,
  custom_prompt_enabled = excluded.custom_prompt_enabled,
  advanced_automation_enabled = excluded.advanced_automation_enabled,
  realtime_voice_enabled = excluded.realtime_voice_enabled,
  future_features_enabled = excluded.future_features_enabled,
  priority_support_enabled = excluded.priority_support_enabled,
  updated_at = excluded.updated_at;

insert into plans (
  id,
  code,
  name,
  price_monthly,
  message_limit,
  audio_limit,
  automation_limit,
  max_whatsapp_numbers,
  max_ai_profiles,
  max_team_members,
  calendar_enabled,
  inbox_enabled,
  custom_prompt_enabled,
  advanced_automation_enabled,
  realtime_voice_enabled,
  future_features_enabled,
  priority_support_enabled,
  created_at,
  updated_at
)
values (
  '33333333-3333-3333-3333-333333333333',
  'pro',
  'Pro',
  99.00,
  8000,
  200,
  5,
  2,
  3,
  3,
  true,
  true,
  true,
  false,
  false,
  false,
  false,
  now(),
  now()
)
on conflict (code) do update set
  name = excluded.name,
  price_monthly = excluded.price_monthly,
  message_limit = excluded.message_limit,
  audio_limit = excluded.audio_limit,
  automation_limit = excluded.automation_limit,
  max_whatsapp_numbers = excluded.max_whatsapp_numbers,
  max_ai_profiles = excluded.max_ai_profiles,
  max_team_members = excluded.max_team_members,
  calendar_enabled = excluded.calendar_enabled,
  inbox_enabled = excluded.inbox_enabled,
  custom_prompt_enabled = excluded.custom_prompt_enabled,
  advanced_automation_enabled = excluded.advanced_automation_enabled,
  realtime_voice_enabled = excluded.realtime_voice_enabled,
  future_features_enabled = excluded.future_features_enabled,
  priority_support_enabled = excluded.priority_support_enabled,
  updated_at = excluded.updated_at;

insert into plans (
  id,
  code,
  name,
  price_monthly,
  message_limit,
  audio_limit,
  automation_limit,
  max_whatsapp_numbers,
  max_ai_profiles,
  max_team_members,
  calendar_enabled,
  inbox_enabled,
  custom_prompt_enabled,
  advanced_automation_enabled,
  realtime_voice_enabled,
  future_features_enabled,
  priority_support_enabled,
  created_at,
  updated_at
)
values (
  '44444444-4444-4444-4444-444444444444',
  'scale',
  'Scale',
  199.00,
  25000,
  1000,
  50,
  10,
  20,
  10,
  true,
  true,
  true,
  true,
  true,
  true,
  true,
  now(),
  now()
)
on conflict (code) do update set
  name = excluded.name,
  price_monthly = excluded.price_monthly,
  message_limit = excluded.message_limit,
  audio_limit = excluded.audio_limit,
  automation_limit = excluded.automation_limit,
  max_whatsapp_numbers = excluded.max_whatsapp_numbers,
  max_ai_profiles = excluded.max_ai_profiles,
  max_team_members = excluded.max_team_members,
  calendar_enabled = excluded.calendar_enabled,
  inbox_enabled = excluded.inbox_enabled,
  custom_prompt_enabled = excluded.custom_prompt_enabled,
  advanced_automation_enabled = excluded.advanced_automation_enabled,
  realtime_voice_enabled = excluded.realtime_voice_enabled,
  future_features_enabled = excluded.future_features_enabled,
  priority_support_enabled = excluded.priority_support_enabled,
  updated_at = excluded.updated_at;
