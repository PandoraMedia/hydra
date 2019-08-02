## Hydra Client ##

Library used to communicate with `hydra-server`

### Setup

The `hydra-client` is configured via environment variables: 

- `VM_HOSTNAME` the name of the individual node that is running the client
- `HYDRA_SERVER` the uri of the hydra server
- `HYDRA_HTTPS` should https be used to talk to the hydra server
- `HYDRA_CLIENT_TIMEOUT` timeout for calls to the hydra server
- `HYDRA_HOST_LIST` a comma separated list of nodes that will run tests
- `JOB_NAME` name of CI build (exported by jenkins)
- `BUILD_TAG` a unique name associated with an individual build (exported by jenkins)
- `HYDRA_CLIENT_ATTEMPTS` # of times the client should attempt network requests before giving up
 
 ### Direct Usage
 
 The easiest way to use `hydra-`client is via the Gradle plugin, but it can also be directly used inside a Gradle build script:
 
 
```
// the map passed into Configuration.newConfigurationFromEnv is used to override environment variables 
HydraClient client = new HydraClient(Configuration.newConfigurationFromEnv(Collections.emptyMap()))
 
// returns a set of fully qualified test names that should NOT be run
Set<String> testBlacklist = client.getExcludes()
 
// sends test results to the hydra server. TestSuites need to be manually created from test reports, or some other method
client.postTestRuntime(List<TestSuite>)
```







 

