# Echo Reactive REST API

This is a simple message echoing REST service, includes the following points:
1. Demotrate the basic usage of Spring WebFlux.
1. Show case the usage of service and log tracing by utilizing Spring Cloud Sleuth.
1. Experiment on logging the full inbound and outbound HTTP request & response, including HTTP start/status line, headers, and full body.

# HOW-TOs
1. To launch the application as a standalone service (at http://localhost:8080), use the `sole` profile, for example:
   ```
   java -jar echo-rx-rest-api-1.0.0-SNAPSHOT.jar -Dspring.profiles.active=sole
   ```
   OR
   ```
   mvn spring-boot:run -Dspring-boot.run.profiles=sole
   ```
1. To launch the application instances to form a simplified/minified microservices example, use the `main` and `sub` profile, for example:

* For the main service (at http://localhost:8080):
   ```
   java -jar echo-rx-rest-api-1.0.0-SNAPSHOT.jar -Dspring.profiles.active=main
   ```
   OR
   ```
   mvn spring-boot:run -Dspring-boot.run.profiles=main
   ```

* For the sub service (at http://localhost:8081):
   ```
   java -jar echo-rx-rest-api-1.0.0-SNAPSHOT.jar -Dspring.profiles.active=sub
   ```
   OR
   ```
   mvn spring-boot:run -Dspring-boot.run.profiles=sub
   ```

# TODOs
1. Add response header X-B3-TraceId
