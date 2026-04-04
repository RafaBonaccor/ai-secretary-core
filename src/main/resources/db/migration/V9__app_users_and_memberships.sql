create table if not exists app_users (
  id uuid primary key,
  supabase_user_id varchar(255) not null unique,
  email varchar(255) not null,
  full_name varchar(255),
  status varchar(50) not null,
  created_at timestamp not null,
  updated_at timestamp not null
);

create table if not exists tenant_user_memberships (
  id uuid primary key,
  tenant_id uuid not null references tenants(id) on delete cascade,
  app_user_id uuid not null references app_users(id) on delete cascade,
  role varchar(50) not null,
  created_at timestamp not null,
  updated_at timestamp not null,
  constraint uk_tenant_user_membership unique (tenant_id, app_user_id)
);

create index if not exists idx_app_users_supabase_user_id on app_users(supabase_user_id);
create index if not exists idx_tenant_user_memberships_tenant_id on tenant_user_memberships(tenant_id);
create index if not exists idx_tenant_user_memberships_app_user_id on tenant_user_memberships(app_user_id);
