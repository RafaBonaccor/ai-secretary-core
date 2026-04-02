alter table channel_instances
  add column if not exists provider_external_id varchar(255);

alter table channel_instances
  add column if not exists provider_config_json text;
