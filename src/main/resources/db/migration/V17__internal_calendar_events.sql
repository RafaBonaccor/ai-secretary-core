create table if not exists calendar_events (
  id uuid primary key,
  calendar_connection_id uuid not null references calendar_connections(id) on delete cascade,
  status varchar(50) not null,
  source varchar(50) not null,
  summary varchar(255) not null,
  description text,
  start_at timestamp not null,
  end_at timestamp not null,
  timezone varchar(100),
  customer_name varchar(255),
  customer_phone varchar(50),
  customer_remote_jid varchar(255),
  appointment_type_slug varchar(255),
  private_metadata_json text,
  created_at timestamp not null,
  updated_at timestamp not null
);

create index if not exists idx_calendar_events_connection_start
  on calendar_events(calendar_connection_id, start_at);

create index if not exists idx_calendar_events_connection_status_start
  on calendar_events(calendar_connection_id, status, start_at);
