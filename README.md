### How to run
**Using Maven Wrapper**, specifying application server port and Queue Scheduler (story 3) flag:  
Windows:
```bash
mvnw.cmd spring-boot:run -Dspring-boot.run.arguments="--server.port=8083 --app.enable-queue-scheduler=true --app.external-api.url=http://localhost:9999"
```
Linux / MacOS:
```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments="--server.port=8083 --app.enable-queue-scheduler=true --app.external-api.url=http://localhost:9999"
```

where:
- server.port: the port of the aggregator service
- app.enable-queue-scheduler: true|false
- app.external-api.url: the URL of the BACKEND service that has the 3 API calls (Track, Pricing, Shipments)

**To build docker image**, run
```bash
./mvnw spring-boot:build-image
```

**The docker image is not configurable, the recommended way to run the application using command line parameters is Maven Wrapper**

### Design decisions, comments, task analysis

##### Workflow:
- 3 branches and 3 corresponding pull requests have been created, each one of them addressing the requirements described in the 3 user stories
- Reviewers are welcome to check out to individual branches, see the commit history, and review the pull requests to easily identify the changes made from one story to another.
- The end result is available on `main` branch. Automated testing has been done in the last branch for the whole application.

##### Notice:
The Intro mentions "We advise to develop the stories in order,
since they build on each other. Consequently, later stories should not break the
functionality already implemented in the earlier stories."  
however, there is a difference in the requirements of the service between story 1 and story 2:

The API Aggregation Service Contract  has this request/response example:
![image](https://github.com/kougianos/fedex-aggregator/assets/23719920/bb363f46-4479-4c12-b690-b9453acee8a5)

Which suggests that individual API calls should be sent for every comma separated value in the parameters. Thus, in this particular example **a total of 6 API calls are sent to the External API** (2 calls for pricing, 2 for track, 2 for shipments) Otherwise it wouldn't be possible to have one value populated and one value null, like it is shown in the example.

**This functionality is implemented in story 1 (branch `1-query-all-services-in-a-single-network`) but has been removed in the next pull requests where batching is in place.** Which means it is not entirely possible to build on top of each pull request as the response transformation logic had to be slightly refactored.

##### Project structure:
The project has a relatively flat structure with a few indicative packages.  
![image](https://github.com/kougianos/fedex-aggregator/assets/23719920/d46a987e-f57f-4b9a-a47c-65d8e9a1e0cd)

As far as data transfer objects are concerned, a GenericMap (which extends LinkedHashMap<String, Object>) has been chosen for simplicity reasons, to map both the responses from the External API to our service, and to create the aggregated response to the end user. The reason behind this is that there isn't any transformation logic in the layers of the application:
The aggregated response is basically a merge of all the responses from the External API, with key=apiName and value=response from External API.

##### Technologies used:
- Spring reactive / webflux has been used to have a complete asynhcronous reactive chain throughout the complete flow of the service.
- Lombok is used for improved readability.
- MockWebServer, WebTestClient and Mockito used in automated testing.
