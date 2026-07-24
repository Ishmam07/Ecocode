# EcoCode Scheduler — Hackathon Build

A trimmed-down, single-module Spring Boot implementation of the EcoCode Scheduler
blueprint. No database, no Spring Security, no DTO/mapper layers — just the
core idea, working end to end.

## 1. Setup

Requires **JDK 21** and a running **MySQL** server (this was written/reviewed
without internet access to Maven Central, so **do a test build on your own
machine before the demo**).

```bash
# 1. Create the database (Hibernate will create the "tasks" table itself)
mysql -u root -p -e "CREATE DATABASE ecocode;"

# 2. Set environment variables
export GROQ_API_KEY=gsk_...              # required for Layer 2 (code generation)
export OPENAQ_API_KEY=your_openaq_key    # optional - falls back to a mock AQI reading if unset
export DB_PASSWORD=your_mysql_password   # defaults to "root" if not set - see application.properties

# 3. Build and run
cd ecocode-scheduler
mvn clean package -DskipTests
java -jar target/scheduler-0.0.1-SNAPSHOT.jar
```

The app starts on **http://localhost:8080**. On first run, Hibernate
(`spring.jpa.hibernate.ddl-auto=update`) auto-creates the `tasks` table in
the `ecocode` database - no manual schema/migration needed.

If your MySQL username/host/port differ from the defaults, edit
`spring.datasource.url` and `spring.datasource.username` in
`application.properties`.

## 2. Test it step by step in Postman

### Step 1 — Check the GPU cluster is alive (no LLM key needed)
```
GET http://localhost:8080/api/v1/cluster/nodes
```
You should see 3 nodes (A/B/C) with different load% and energy multipliers.

### Step 2 — Check AQI data (works even without an OpenAQ key — returns a mock reading)
```
GET http://localhost:8080/api/v1/aqi/latest?city=Dhaka
```

### Step 3 — Submit a task (this is the full pipeline: LLM → analyse → dispatch → execute)
```
POST http://localhost:8080/api/v1/tasks/submit
Content-Type: application/json

{
  "description": "Predict asthma risk from PM2.5 data"
}
```
Response includes `taskId`, `assignedNode`, `estimatedKwh`, `greenScore`, and the
execution result.

### Step 4 — Poll / inspect the task
```
GET http://localhost:8080/api/v1/tasks/{taskId}
GET http://localhost:8080/api/v1/tasks/{taskId}/code
GET http://localhost:8080/api/v1/tasks/{taskId}/result
```

### Step 5 — List every task submitted so far
```
GET http://localhost:8080/api/v1/tasks
```

### Step 6 — Error handling check
```
GET http://localhost:8080/api/v1/tasks/does-not-exist
```
→ returns a clean 404 JSON error instead of a stack trace.

```
POST http://localhost:8080/api/v1/tasks/submit
Content-Type: application/json

{ "description": "" }
```
→ returns a clean 400 JSON error (validation).

## 3. What's intentionally left out (by design, for a 4-day hackathon scope)

- Database: MySQL via standard Spring Data JPA (`TaskRepository extends
  JpaRepository`) - tasks persist across restarts now.
- No Spring Security / login — this is a demo API, not a production service.
- No DTO/mapper layers — `TaskRecord` is the JPA entity AND the JSON returned
  to the client, no separate layers.
- No real GPU cluster or Kubernetes — 3 in-memory `GpuNode` objects simulate it.
- No WebSocket dashboard — poll the `/tasks/{id}` endpoint instead.

## 4. If the LLM call fails or `python3` isn't available

- `CodexClient` throws a clean `LlmGenerationException` → 502 JSON error, so a
  missing/invalid `GROQ_API_KEY` won't crash the app.
- `PipelineRunner` catches execution failures/timeouts and returns a
  clearly-labelled mock result (`"mocked": true`) instead of failing the whole
  request — keeps the demo safe even if a generated script is occasionally broken.
