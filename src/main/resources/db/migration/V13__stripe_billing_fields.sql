alter table if exists subscriptions
  add column if not exists provider_customer_id varchar(255);

alter table if exists subscriptions
  add column if not exists provider_price_id varchar(255);
