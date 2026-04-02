create table if not exists calendar_connections (
  id uuid primary key,
  tenant_id uuid not null references tenants(id) on delete cascade,
  provider varchar(100) not null,
  google_account_email varchar(255),
  google_calendar_id varchar(255),
  google_calendar_name varchar(255),
  access_token text,
  refresh_token text,
  token_expires_at timestamp,
  status varchar(50) not null,
  sync_mode varchar(50) not null default 'manual',
  created_at timestamp not null,
  updated_at timestamp not null
);

create table if not exists working_hours (
  id uuid primary key,
  calendar_connection_id uuid not null references calendar_connections(id) on delete cascade,
  weekday integer not null,
  is_enabled boolean not null default true,
  start_time varchar(10),
  end_time varchar(10),
  break_start_time varchar(10),
  break_end_time varchar(10),
  slot_interval_minutes integer not null default 30,
  buffer_before_minutes integer not null default 0,
  buffer_after_minutes integer not null default 0,
  created_at timestamp not null,
  updated_at timestamp not null,
  constraint uq_working_hours_connection_weekday unique (calendar_connection_id, weekday)
);

create table if not exists appointment_types (
  id uuid primary key,
  calendar_connection_id uuid not null references calendar_connections(id) on delete cascade,
  name varchar(255) not null,
  slug varchar(255) not null,
  duration_minutes integer not null,
  buffer_before_minutes integer not null default 0,
  buffer_after_minutes integer not null default 0,
  price_amount numeric(10,2),
  currency varchar(10),
  active boolean not null default true,
  description text,
  created_at timestamp not null,
  updated_at timestamp not null,
  constraint uq_appointment_types_connection_slug unique (calendar_connection_id, slug)
);

create index if not exists idx_calendar_connections_tenant_id on calendar_connections(tenant_id);
create index if not exists idx_calendar_connections_tenant_status on calendar_connections(tenant_id, status);
create index if not exists idx_working_hours_connection_id on working_hours(calendar_connection_id);
create index if not exists idx_appointment_types_connection_id on appointment_types(calendar_connection_id);
