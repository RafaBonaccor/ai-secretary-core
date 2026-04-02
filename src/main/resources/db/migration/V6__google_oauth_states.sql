create table if not exists oauth_states (
  id uuid primary key,
  provider varchar(100) not null,
  state_token varchar(255) not null unique,
  calendar_connection_id uuid references calendar_connections(id) on delete cascade,
  created_at timestamp not null,
  expires_at timestamp not null,
  consumed_at timestamp
);

create index if not exists idx_oauth_states_provider on oauth_states(provider);
create index if not exists idx_oauth_states_connection_id on oauth_states(calendar_connection_id);
