DROP INDEX builds_name_idx;
DROP INDEX projects_name_idx;
DROP INDEX test_name_idx;


CREATE UNIQUE INDEX builds_name_idx ON build(name);
CREATE UNIQUE INDEX projects_name_idx ON project(name);
CREATE UNIQUE INDEX test_name_idx ON test(name, build_id, project_id);
