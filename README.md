### Introduction
This project was implemented in the context of technical assessment during an interview process with a multinational company.  
Detailed requirements can be found in assessment_backend_java.pdf file.  
In short, it is a Spring boot service with the following functionalities:
1. Integrate with 3 REST APIs and dynamically combine the responses into 1, depending on the client's requested parameters.
2. Implement request throttling and batching of individual requests, using BlockingQueues (more details below).
3. Create a scheduler that periodically checks items left in queues, and returns a response to the caller, guaranteeing a 10-second SLA

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

As far as data transfer objects are concerned, a `GenericMap extends LinkedHashMap<String, Object>` has been chosen for simplicity reasons, to map both the responses from the External API to our service, and to create the aggregated response to the end user. The reason behind this is that there isn't any transformation logic in the layers of the application:
The aggregated response is basically a merge of all the responses from the External API, with key=apiName and value=response from External API.

##### Technologies used:
- Spring reactive / webflux has been used to have a complete asynhcronous reactive chain throughout the complete flow of the service.
- Lombok is used for improved readability.
- MockWebServer, WebTestClient and Mockito used in automated testing.

##### AS-1: As COMPANY, I want to be able to query all services in a single network call to optimise network traffic.
Straightforward requirement, the service dynamically creates 1-3 Mono<Response> objects (depending on the requested parameters) and calls the External API asynchronously, handling any errors. The WebClient has been configured with a readTimeout=5 seconds because the SLA of the BE service is 5 seconds.  
<br>
As mentioned earlier, the service performs a number of requests to the External API, equal to the total number of the comma separated values in the original request. For example, for the call 
`GET /aggregation?pricing=NL,CN,CH,GB,DE&track=1,2,3,4,5&shipments=1,2,3,4,5` **a total of 15 calls will be sent to the External API and this is by design.**


##### AS-2: as COMPANY, I want service calls to be throttled and bulked into consolidated requests per respective API to prevent services from being overloaded.
A combination of `LinkedBlockingQueue<String>` and concurrent maps has been used to accommodate this task. For every REST call our service receives, it populates the required queues (depending on the request) and blocks the thread if any of the queues does not reach size >= 5. At this point a second REST call should be triggered, to populate any remaining queues and unblock the first REST call as well.  

Several logs are in place that show the queue state during the time of requests, and every 4 seconds.  
Also, the responses from the External API are also logged. This is useful because it shows that for multiple /aggregation calls, only 1 call is sent to the External API (per apiName).

So, as described in the requirements, in the following scenario:
- This request only triggers a call to the Pricing API /aggregation?pricing=NL,CN,CH,GB,DE&track=117347282,109347263,123456891
- As soon as this request arrives, a call to the Track API is triggered as well: /aggregation?track=219389201,813434811

Of course, there are more complex scenarios and edge cases that were manually tested, and the overall solution suffers under certain circumstances.  
Example:
`/aggregation?pricing=NL,CN,CH,GB,DE&track=1,2,3&shipments=1,2,3,4`  
In the above call, 2/3 queues are size <= 5. And the thread will block in one of the two queues (usually track)

A next call comes:
`/aggregation?shipments=5,6,7`  
which unblocks queue shipments, but thread A is not notified because it is blocked on queue track. This is handled as an edge case, and when Thread A eventually gets unblocked, it will perform an extra API call to the Track API, because it was blocked at the time the original Track API call was made, and the responses are only cached for 2 seconds in ExternalApiClient.

##### AS-3: as COMPANY, I want service calls to be scheduled periodically even if the queue is not full to prevent overly-long response times.
A configurable QueueScheduler bean has been created for this user story, which can be enabled/disabled through application properties.  
Also a custom `FedexQueue extends LinkedBlockingQueue<String>` has replaced the queues. This custom queue has an extra field 
```java
private Instant oldestElementInsertTimestamp;
```
which is used in the scheduled task that runs every 1 second and fills up any remaining queues with dummy values, notifying all waiting threads.

The functionality is also tested in `SchedulerEnabledIT` to make sure our application meets the 10-second SLA for requests to the aggregation service.
