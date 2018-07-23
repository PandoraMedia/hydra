CREATE TABLE build (
  id   BIGSERIAL PRIMARY KEY,
  name VARCHAR
);
CREATE INDEX builds_name_idx
  ON build (name);

CREATE TABLE project (
  id   BIGSERIAL PRIMARY KEY,
  name VARCHAR
);
CREATE INDEX projects_name_idx
  ON project (name);

CREATE TABLE test (
  id           BIGSERIAL PRIMARY KEY,
  name         VARCHAR,
  time         BIGINT,
  failed       BOOLEAN,
  hostname     VARCHAR,
  last_updated TIMESTAMP,
  build_id     BIGINT,
  project_id   BIGINT
);
CREATE INDEX test_name_idx
  ON test (name);