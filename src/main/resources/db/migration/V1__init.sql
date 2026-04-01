create table if not exists tenants (
  id uuid primary key,
  name varchar(255) not null,
  slug varchar(255) not null unique,
  status varchar(50) not null,
  timezone varchar(100) not null,
  created_at timestamp not null,
  updated_at timestamp not null
);

create table if not exists plans (
  id uuid primary key,
  code varchar(100) not null unique,
  name varchar(255) not null,
  price_monthly numeric(10,2) not null default 0,
  message_limit integer not null default 0,
  audio_limit integer not null default 0,
  automation_limit integer not null default 0,
  created_at timestamp not null,
  updated_at timestamp not null
);

create table if not exists subscriptions (
  id uuid primary key,
  tenant_id uuid not null references tenants(id) on delete cascade,
  plan_id uuid not null references plans(id),
  provider varchar(100) not null,
  provider_subscription_id varchar(255),
  status varchar(50) not null,
  period_start timestamp not null,
  period_end timestamp not null,
  created_at timestamp not null,
  updated_at timestamp not null
);

create table if not exists channel_instances (
  id uuid primary key,
  tenant_id uuid not null references tenants(id) on delete cascade,
  provider_type varchar(100) not null,
  channel_type varchar(100) not null,
  phone_number varchar(100) not null,
  display_name varchar(255),
  instance_name varchar(255) not null unique,
  status varchar(50) not null,
  created_at timestamp not null,
  updated_at timestamp not null
);

create table if not exists ai_profiles (
  id uuid primary key,
  tenant_id uuid not null references tenants(id) on delete cascade,
  name varchar(255) not null,
  model varchar(100) not null,
  system_prompt text not null,
  temperature numeric(4,2) not null default 0.3,
  voice varchar(100),
  tools_json text,
  created_at timestamp not null,
  updated_at timestamp not null
);

create index if not exists idx_subscriptions_tenant_id on subscriptions(tenant_id);
create index if not exists idx_channel_instances_tenant_id on channel_instances(tenant_id);
create index if not exists idx_channel_instances_phone_number on channel_instances(phone_number);
create index if not exists idx_ai_profiles_tenant_id on ai_profiles(tenant_id);
