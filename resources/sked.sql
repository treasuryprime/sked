create schema sked;

create table sked.scheduler (
  id bigint primary key generated always as identity,
  active boolean not null default true,
  started_at timestamptz not null default now(),
  last_active_at timestamptz not null default now(),
  hostname text not null);

create table sked.cron (
  id bigint primary key generated always as identity,
  schedule text not null,
  time_zone text not null default 'UTC',
  created_at timestamptz not null default now(),
  active boolean not null default true);

create table sked.event (
  id bigint primary key generated always as identity,
  scheduler_id bigint not null references sked.scheduler (id),
  cron_id bigint not null references sked.cron (id),
  date timestamptz not null,
  created_at timestamptz not null default now(),
  unique (cron_id, date));
