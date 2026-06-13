# e2e-tests

End-to-end pipeline tests for the Home Energy Tracker.

## Design

The HET repo has no parent `pom.xml` — each microservice is its own Maven root.
The `e2e-tests` module is therefore a standalone Spring Boot project that depends
on the pipeline service jars (`ingestion-service`, `usage-service`,
`alert-service`) via Maven coordinates.

The E2E test starts shared infrastructure once via Testcontainers:

- Kafka
- MySQL
- InfluxDB 2.x
- GreenMail (in-JVM SMTP)
- WireMock for stubbing `device-service` and `user-service`

It then boots the three pipeline services in the **same JVM** as separate Spring
Boot child contexts using `SpringApplicationBuilder`. This avoids having three
independent `@SpringBootTest` setups and keeps lifecycle handling simple.

## Running

Docker must be running.

```bash
# Install the service jars to the local Maven repo first
(cd ../ingestion-service && ./mvnw -q -DskipTests install)
(cd ../usage-service    && ./mvnw -q -DskipTests install)
(cd ../alert-service    && ./mvnw -q -DskipTests install)

# Then run the E2E suite
./mvnw -q verify
```
