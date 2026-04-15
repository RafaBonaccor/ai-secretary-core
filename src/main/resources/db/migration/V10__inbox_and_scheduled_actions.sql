alter table conversations
  add column if not exists last_message_preview text;

alter table conversations
  add column if not exists last_message_direction varchar(20);

update conversations
set
  last_message_preview = coalesce(last_message_preview, last_inbound_text),
  last_message_direction = coalesce(last_message_direction, case when last_inbound_text is not null then 'inbound' else null end)
where last_message_preview is null or last_message_direction is null;

create table if not exists scheduled_conversation_actions (
  id uuid primary key,
  tenant_id uuid not null references tenants(id) on delete cascade,
  channel_instance_id uuid not null references channel_instances(id) on delete cascade,
  conversation_id uuid not null references conversations(id) on delete cascade,
  contact_id uuid not null references contacts(id) on delete cascade,
  action_type varchar(50) not null,
  title varchar(120),
  body text not null,
  scheduled_for timestamp not null,
  status varchar(30) not null,
  executed_at timestamp,
  cancelled_at timestamp,
  error_message text,
  created_at timestamp not null,
  updated_at timestamp not null
);

create index if not exists idx_conversations_tenant_last_message_at on conversations(tenant_id, last_message_at desc);
create index if not exists idx_scheduled_actions_tenant on scheduled_conversation_actions(tenant_id);
create index if not exists idx_scheduled_actions_conversation on scheduled_conversation_actions(conversation_id);
create index if not exists idx_scheduled_actions_status_scheduled_for on scheduled_conversation_actions(status, scheduled_for);
