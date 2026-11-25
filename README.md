# CodePath

An AI-powered adaptive DSA learning and placement preparation platform.

**Academic context:** B.Tech CSE Major Project-1, Medicaps University, Indore.

CodePath combines a diagnostic assessment, a structured Java question bank, an isolated code judge (Judge0), a transparent rule-based mastery engine, and safeguarded AI hints that are displayed separately from verified judge verdicts.

Mastery scores are **rule-based estimates** based on learner submissions, not measurements of ability. AI output is **unverified guidance**; only the Judge0 verdict is authoritative for code execution.

## Technology stack

| Part | Technology |
| --- | --- |
| Frontend | React 18 (JSX), Vite 5, Tailwind CSS 3, React Router 6, Axios, Monaco Editor, Recharts, Lucide |
| Backend | Java 17+, Spring Boot 3.5 (modular monolith), Spring Security + JWT |
| Database | PostgreSQL 15 (Spring Data JPA / Hibernate) |
| Code execution | Judge0 CE 1.13.1, a separate service called by the API over HTTP |
| AI | Gemini API, called from the backend `ai` module |

## Features

- User registration, authentication, and onboarding
- 10-question diagnostic assessment
- DSA problem bank with public and hidden test cases
- Java coding workspace powered by Monaco Editor
- Code execution and submission through Judge0
- Rule-based mastery tracking and personalized recommendations
- Prerequisite-aware learning roadmap and revision queue
- Progressive AI hints (Levels 1–4) and complexity review
- Problem discussions and learning analytics

## Prerequisites

- JDK 17 or newer
- Maven
- Node.js 20+ (Node.js 22 LTS recommended) and npm
- Docker with Docker Compose (for the PostgreSQL database)
- Optional: a Gemini API key from Google AI Studio for live AI hints
- Optional: a compatible Linux/Docker environment for Judge0 CE 1.13.1

## Installation and setup

Run commands from the project root unless a step says otherwise.

### 1. Get the project

```bash
git clone https://github.com/galectussensei-max/CodePath.git
cd CodePath
```

Replace galectussensei-max with the GitHub account that hosts the repository.

### 2. Start PostgreSQL

```bash
docker compose up -d postgres
```

The application database is exposed on host port **5433**. The local Docker Compose defaults are:

- Database: `codepath`
- Username: `codepath`
- Password: `codepath`

These are local-development defaults. Change them before using the application in a shared or production environment, and update the backend datasource configuration accordingly.

### 3. Configure optional environment variables

The backend can start with its development defaults, but you should set your own JWT secret and any API keys in your local environment. Do not commit real `.env` files, API keys, passwords, or production secrets.

Backend environment variables:

| Variable | Purpose | Default / behavior |
| --- | --- | --- |
| `CODEPATH_JWT_SECRET` | JWT signing secret; use a strong secret of at least 32 characters | Development fallback in `application.yml`; replace it outside local development |
| `CODEPATH_AI_API_KEY` | Gemini API key | Empty; AI endpoints return HTTP 503 without a key |
| `CODEPATH_AI_MODEL` | Gemini model name | `gemini-2.0-flash` |
| `JUDGE0_BASE_URL` | Judge0 service address | `http://localhost:2358` |
| `CODEPATH_CORS_ALLOWED_ORIGINS` | Allowed browser origins | `http://localhost:5173,http://localhost:3000` |
| `CODEPATH_SEED_ENABLED` | Enable initial seed data | `true` |

The frontend includes `frontend/.env.example`. If the backend is not running at `http://localhost:8080`, copy that file to `frontend/.env` and set `VITE_API_BASE_URL` to your backend URL.

For Windows Command Prompt:

```cmd
copy frontend\.env.example frontend\.env
```

For Git Bash / macOS / Linux:

```bash
cp frontend/.env.example frontend/.env
```

The Gemini key belongs only in the backend environment. It must never be placed in a `VITE_` variable or exposed to the browser.

### 4. Start the backend

Open a terminal:

```bash
cd backend
mvn clean install
mvn spring-boot:run
```

The API runs at `http://localhost:8080`.

On the first start with an empty database, the application seeds 5 topics with prerequisites, 10 concepts, 15 Java problems (including public and hidden test cases), and 10 diagnostic questions.

### 5. Start the frontend

Open a second terminal:

```bash
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173`.

To create a production frontend build:

```bash
npm run build
```

The generated production files are written to `frontend/dist/`. This folder is build output and is not committed to Git; regenerate it with the command above when needed.

## Judge0 code execution (optional)

CodePath's learning features can be used without Judge0, but **Run** and **Submit** code-execution features require a working Judge0 service. Without Judge0, those endpoints return HTTP 503 rather than inventing a verdict.

The repository includes `judge0.conf.example`, not the active local configuration. Before starting the Judge0 services, create your local `judge0.conf` from the example.

Windows Command Prompt:

```cmd
copy judge0.conf.example judge0.conf
```

Git Bash / macOS / Linux:

```bash
cp judge0.conf.example judge0.conf
```

Then start the Judge0 services:

```bash
docker compose up -d db redis judge0 judge0-worker
```

Judge0 CE 1.13.1 requires **cgroup v1** for its isolate sandbox. On cgroup v2 hosts (including many current Linux and Docker Desktop setups), submissions may fail with status 13. The original development setup used a cgroup v1 Ubuntu 22.04 VM and forwarded port 2358 to the host. The backend expects Judge0 at `http://localhost:2358`; override this with `JUDGE0_BASE_URL` if needed.

To diagnose a running Judge0 instance:

```bash
bash docs/diagnose-judge0.sh
```

Expected checks include `GET /languages/62 -> HTTP 200` and a Java hello-world submission with status 3 (Accepted).

The `judge0.conf.example` values are local Docker-network development credentials only. Replace them for any non-local deployment. Never commit your active `judge0.conf`.

## Demo walkthrough

1. Register a new account, then set your goal and daily study time during onboarding.
2. Take the 10-question diagnostic. The mastery reveal shows one estimate per topic.
3. Open the Dashboard. Recommended problems include a "Why this" reason, and topics whose prerequisites are not met are listed with the reason they were held back.
4. Open Problems, filter by topic, concept, or difficulty, and open a problem.
5. In the Workspace, write Java in Monaco. **Run** uses public sample cases only; **Submit** also runs hidden cases (never shown to the browser) and updates mastery.
6. Review the result in the Verified Execution Verdict area. Ask for AI hints Levels 1–4 in the separate AI Assistant (Unverified Guidance) area. Levels must be requested in order; Levels 1–3 do not contain code. After an Accepted submission, use Analyze Accepted Solution for a complexity review.
7. Check the Roadmap (prerequisite-ordered and advisory), the Revision queue on the Dashboard, and the Analytics charts.

## Testing

### Backend unit tests

JUnit 5 + Mockito tests:

```bash
cd backend
mvn test
```

### API flow integration test

This test starts a real Spring context with security filters and PostgreSQL. It does not require Judge0 or an AI key. Start PostgreSQL first, then run:

```bash
cd backend
mvn -Dtest=ApiFlowIT test
```

The test is named `*IT`, so the normal `mvn test` command skips it. Each run registers a throw-away learner with a unique email in the development database.

### Scripted checks

Run these from Git Bash at the project root:

```bash
bash docs/verify-phase1.sh   # auth, schema, seed data
bash docs/verify-phase2.sh   # phase 2 checks
bash docs/verify-phase2a.sh  # phase 2a checks
bash docs/verify-phase3.sh   # judge verdicts (requires Judge0)
bash docs/verify-phase4.sh   # recommendations, roadmap, revision, analytics
bash docs/verify-phase5.sh   # AI hints and discussions (live-LLM checks skip without a key)
```

## Project structure

```text
CodePath/
├── backend/                 Spring Boot application and tests
├── frontend/                React + Vite application
├── docs/                    Verification and diagnostic scripts
├── docker-compose.yml       PostgreSQL and optional Judge0 services
├── judge0.conf.example      Example local Judge0 configuration
├── .gitignore
└── README.md
```

The generated folders `frontend/node_modules/`, `frontend/dist/`, and `backend/target/` are intentionally excluded from version control. Install dependencies and regenerate build output using the commands in this README.

## Security and configuration notes

- Never commit `.env`, `frontend/.env`, `judge0.conf`, API keys, JWT secrets, or production credentials.
- The checked-in `application.yml` contains local-development database settings and a development JWT-secret fallback. Set secure values through environment variables before any shared or production deployment.
- Keep the Gemini API key on the backend only.
- The sample database and Judge0 credentials are for local development, not production use.

## Known limitations

- Discussion upvotes are a plain counter; repeat upvotes are not de-duplicated.
- AI calls do not currently have a per-user rate limit.
- A submission containing an infinite loop may take a while because each test case can run up to its time limit.
- Judge0 CE 1.13.1's cgroup v1 requirement limits where its sandbox can run.

## Academic context

B.Tech CSE Major Project-1, Medicaps University, Indore.
