-- V036: track nội dung (Java / AWS …).
-- Trang Learn gom topic theo track + cho phép nhiều lộ trình học (Java backend, AWS DVA…).
-- Docs là source of truth: importer upsert track theo `slug` (natural key) từ
-- `data-track*` trên `div.nav-group` trong docs/index.html.
create table content_tracks (
    slug          varchar(64)  primary key,
    name          varchar(200) not null,
    description   text,
    icon          varchar(64),
    display_order int          not null default 0,
    created_at    timestamptz  not null default now()
);

insert into content_tracks (slug, name, description, icon, display_order) values
    ('java', 'Java Backend Interview',
     'Lộ trình Java backend: nền tảng, concurrency, database, kiến trúc và system design.',
     'java', 1),
    ('aws',  'AWS Certified Developer - Associate (DVA-C02)',
     'DVA-C02: service cốt lõi, diagram trực quan và tình huống thi thật.',
     'aws', 2);

-- FK theo cột unique `slug` để topic luôn trỏ tới track có thật.
alter table topics add column track varchar(64) not null default 'java'
    references content_tracks (slug);
create index idx_topics_track on topics (track);
