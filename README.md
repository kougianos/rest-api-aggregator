### How to run
**Using Maven Wrapper**, specifying application server port and queueScheduler (story 3) flag:  
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
mvnw spring-boot:build-image
```

