## Pandora Hydra server ##

Hydra's Spring Boot backend that is responsible for persisting test results and partitioning tests into equally sized chunks. The Hydra server
is *stateful* so only one instance should be run per testing environment.


### Persistence ###

Hydra can be configured to use two different persistence mechanisms: file based or SQL based.

#### File Based Persistence
This mechanism simply maintains a flat file on the file system that stores test times. To use file backed storage start
Hydra server with the Spring profile `file_store` enabled (`spring.profiles.active=file_store`). The persistence directory can be configured by changing the property
`hydra.repo` (it defaults to server-root-folder/.repo)

 
### SQL Based Persistence
SQL is used as the default persistence mechanism. We've tested with PostgreSQL, but other flavors of SQL should also work.

First create the hydra db
```
createdb hydra
```

Connect to the database, create a new role, and change DB ownership to the new user. The username and password is set in `application.yml` and should 
correspond to the owner of the DB

```
psql
CREATE ROLE hydra LOGIN;
ALTER DATABASE hydra OWNER TO hydra;
\q
```

Flyway is used to manage the schemas.

### Balancing Strategies
There are three different strategies available creating equally sized test partitions

- `greedy`
- `greedy_with_failures`
- `affinity`

The `greedy` strategy calculates fresh test partitions for every test run. The most recent test time data is used to calculate the partitions.

The `greedy_with_failures` strategy calculates fresh test partitions for each test run, but will always put tests that failed on the same host

The `affinity` strategy tries to keep tests on the same host across test runs. It will rebalance tests if the expected test times across hosts starts to vary too much

If your test cluster consists of a fixed set of servers then `affinity` is the best strategy. The `affinity` strategy will eventually lead to 
the most similarly sized test partitions, because it will eventually account for differences between different nodes on the cluster.

`greedy_with_failures` can be useful if you have flakey tests that only fail when run in a specific order. 

The strategy can be configured with the argument `hydra.partition.strategy` in `application.yml` or by passing the argument in as a command line argument, e.g. 
`--hydra.partition.strategy=greedy`


### Deployment ###
There are two possible options to deploy the application -- Standalone or Docker container.

#### Standalone app ####
The `hydra-server` is a Spring Boot application, so it does not need a separate container.
 
 + Build the server with command ./gradlew build (from root folder)
 + Run the server with command `java -jar hydra.war` (war file is in build/libs)
 + To use different profiles or partition strategies `java -jar hydra.war --spring.profiles.active=file_repo --hydra.partition.strategy=greedy`


#### Docker container ####
`Dockerfile` and `entrypoint.sh` scripts are shipped along with the source. Please modify `application.yml` and set the correct DB host/port/dbname for the `docker` profile.
Then switch to the `hydra-server` folder and run:

```
docker build --build-arg VERSION=X.X.X -f ./deployment/docker/Dockerfile .
```
to create a container, where `X.X.X` is the current version of the app.