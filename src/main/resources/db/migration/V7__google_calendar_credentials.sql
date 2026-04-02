create table if not exists google_calendar_credentials (
  id uuid primary key,
  calendar_connection_id uuid not null unique references calendar_connections(id) on delete cascade,
  encrypted_access_token text not null,
  encrypted_refresh_token text,
  token_expires_at timestamp,
  created_at timestamp not null,
  updated_at timestamp not null
);

create index if not exists idx_google_calendar_credentials_connection_id
  on google_calendar_credentials(calendar_connection_id);
