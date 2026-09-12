# Fraud Detection System

A transaction fraud detection service: rule-based risk scoring, alert generation,
and an analyst review workflow. Built as a portfolio project to practise
production-shaped design decisions rather than feature count.

**Stack:** Java 21 · Spring Boot 3.5 · PostgreSQL · Kafka · React 19 · TypeScript · Vite

---

## What it does

Transactions are ingested, scored against a set of configurable rules, and
assigned a decision (`APPROVE` / `REVIEW` / `BLOCK`). Anything scored above the
review threshold raises an alert. Analysts work through open alerts in the web
UI and mark each one `CONFIRMED` or `FALSE_POSITIVE`.

| Layer | What lives there |
|---|---|
| Ingestion | REST endpoint + Kafka consumer for transaction events |
| Scoring | Rule engine producing a risk score and a decision |
| Alerting | Alert creation for flagged transactions, plus the review workflow |
| API | Paginated transaction list, alert list, alert review |
| UI | Two pages — transaction browser and alert review queue |

---

## Running locally
**Prerequisites:** JDK 21, Maven 3.9+, Node 20+, Docker.
Three processes, started in this order.

### 1. Database

```bash
docker compose up -d
```

Postgres listens on `5432`. To open a SQL shell against it:

```bash
docker compose exec postgres psql -U fraud -d frauddb
```

SQL statements only work inside that shell — typing them straight into your
terminal will not work.

### 2. Backend

```bash
mvn spring-boot:run
```

Serves on `8080`. Verify it is up before starting the frontend:

```bash
curl -i "http://localhost:8080/api/v1/alerts?status=OPEN"
```

A `200` means the API is reachable. If the frontend later shows
"Cannot reach the server", this is the thing to check.

### 3. Frontend

```bash
cd frontend
npm install
npm run dev
```

Open **http://localhost:5173**. The Vite dev server proxies `/api` to `8080`,
so there is no CORS configuration to maintain and hot reload still works.

---

## Production build

The frontend compiles into the backend's static resources and ships as a single
jar — same origin, no separate web server, no CORS.

```bash
cd frontend
npm run build                   # outputs to src/main/resources/static

cd ..
mvn package
java -jar target/*.jar
```

Open **http://localhost:8080** — one port, everything served from it.

The build output is generated, not committed, so `npm run build` must run before
`mvn package`.

---

## Design decisions

The reasoning behind the choices that aren't obvious from the code.

### Money is a string end to end

`BigDecimal` serialises to a JSON string, not a JSON number, and the frontend
types it as `string` to match. Parsing it into a JavaScript `number` for display
would reintroduce exactly the float precision problem `BigDecimal` exists to
avoid, and nothing in the UI does arithmetic on it.

### No router for two pages

Tab state is a single `useState`. Neither page needs a shareable URL, browser
history, or nested routes. The trade-off is real and accepted: the back button
does not move between tabs, because the URL never changes. A router earns its
place when links need to be shareable — not before.

### Selected tabs use `aria-selected`, not `disabled`

`disabled` stops the browser dispatching click events at all, so using it to
mark the current tab made half of every click silently vanish. Selection is a
state; `disabled` means an action is unavailable. Pagination buttons still use
`disabled`, because "Next" on the last page genuinely is unavailable.

### Pessimistic updates on review

After submitting a review, the alert list is refetched rather than patched
locally. The server is the only authority on which alerts are still open, and
hand-patching risks drifting from it. The cost is one extra round trip; the
benefit is that the UI can never claim an alert was handled when it wasn't.

### Render states are mutually exclusive

Loading, error, empty, and data are ordered early returns, not independent
conditions. Rendered as separate blocks, a failed request produced an error
message *and* an empty table at the same time — two contradictory claims about
one request.

### Failures are classified, not flattened

The axios client sorts failures into network / timeout / client / server and the
UI phrases each differently. "Cannot reach the server — check the backend is
running" tells the user what to do; a generic "Failed to load" does not.

---

## Project layout

```
src/main/java/…          Spring Boot application
src/main/resources/      Config, migrations, and the built frontend (generated)
frontend/src/
  api/                   axios client and typed endpoint wrappers
  types/                 TypeScript interfaces mirroring backend DTOs
  pages/                 TransactionPage, AlertPage
docker-compose.yml       Postgres and Kafka
```
