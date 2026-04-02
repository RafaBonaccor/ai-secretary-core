create table if not exists contacts (
  id uuid primary key,
  tenant_id uuid not null references tenants(id) on delete cascade,
  channel_instance_id uuid not null references channel_instances(id) on delete cascade,
  phone_number varchar(100) not null,
  remote_jid varchar(255) not null,
  display_name varchar(255),
  push_name varchar(255),
  first_seen_at timestamp not null,
  last_seen_at timestamp not null,
  created_at timestamp not null,
  updated_at timestamp not null
);

create table if not exists conversations (
  id uuid primary key,
  tenant_id uuid not null references tenants(id) on delete cascade,
  channel_instance_id uuid not null references channel_instances(id) on delete cascade,
  contact_id uuid not null references contacts(id) on delete cascade,
  status varchar(50) not null,
  last_message_at timestamp,
  last_inbound_text text,
  created_at timestamp not null,
  updated_at timestamp not null
);

create table if not exists messages (
  id uuid primary key,
  tenant_id uuid not null references tenants(id) on delete cascade,
  channel_instance_id uuid not null references channel_instances(id) on delete cascade,
  conversation_id uuid not null references conversations(id) on delete cascade,
  contact_id uuid not null references contacts(id) on delete cascade,
  provider_message_id varchar(255),
  direction varchar(20) not null,
  message_type varchar(50) not null,
  body text,
  raw_json text,
  sent_at timestamp not null,
  created_at timestamp not null
);

create unique index if not exists uk_contacts_channel_remote_jid on contacts(channel_instance_id, remote_jid);
create unique index if not exists uk_conversations_channel_contact on conversations(channel_instance_id, contact_id);
create index if not exists idx_messages_conversation_id on messages(conversation_id);
create index if not exists idx_messages_contact_id on messages(contact_id);
create index if not exists idx_messages_channel_instance_id on messages(channel_instance_id);
