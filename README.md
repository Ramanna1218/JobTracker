# JobTracker

A Spring Boot service that finds new job postings every morning, scores each one against your resume with Claude, writes tailored resume bullets for it, and emails you a digest with a link to a review dashboard.

## How it works

1. **Search.** Every day at 7:00 AM (or on demand) it queries the JSearch API, which aggregates LinkedIn, Indeed, Glassdoor, ZipRecruiter and Google Jobs, for each of your keywords. Only postings from the last 24 hours are fetched.
2. **Deduplicate.** Jobs already seen for a user are skipped, so each run only processes new postings.
3. **Score and tailor.** Each new job is sent to the Anthropic Messages API with your resume. Claude returns a 0-100 match score and 3-5 resume bullets rewritten for that job, using only your real experience.
4. **Notify.** A digest email (Gmail SMTP) lists the new matches with their scores.
5. **Review.** A dashboard shows the matches sorted by score, with Approve (opens the apply link) and Reject (hides the job).

## Tech stack

- **Java 17, Spring Boot 3.3:** Web, Data JPA, Security, Mail, Scheduling
- **PostgreSQL 16** with Hibernate
- **External APIs:** JSearch (RapidAPI), Anthropic Claude
- **Docker and Docker Compose** for local runs
- **Kubernetes manifests** (`k8s/`) for a k3s deployment on AWS EC2 (planned; see `DEPLOYMENT.md`)

## Engineering notes

- Free-text fields (job descriptions, apply URLs, tailored bullets) are stored as PostgreSQL `TEXT`. Real job-board data regularly exceeds `VARCHAR(255)`.
- NUL bytes found in some postings are stripped before insert, and a single bad posting is logged and skipped instead of failing the whole run.
- Claude's reply is parsed defensively: the JSON object is extracted even if extra text surrounds it, and API errors are logged with their response bodies.
- API secrets come only from environment variables (`.env`, which is gitignored). The password hash is excluded from all API responses.
- The app is bound to `127.0.0.1` because the API has no authentication yet.

## Run it locally

Requirements: Docker Desktop, a JSearch API key, an Anthropic API key, and a Gmail app password.

```bash
cp .env.example .env              # fill in your keys
cp register.example.json register.json   # fill in your details and resume text
docker compose up -d --build
```

Register yourself, then trigger a run:

```bash
curl -X POST http://localhost:8080/api/users/register \
     -H "Content-Type: application/json" -d @register.json
curl -X POST http://localhost:8080/api/users/1/run-now
```

Open the dashboard at `http://localhost:8080/dashboard.html?userId=1`.

Windows (PowerShell) instructions are in `START-WINDOWS.md`.

## API

| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/api/users/register` | Create a user with resume text, keywords and location |
| POST | `/api/users/{id}/run-now` | Run the search, scoring and email for one user immediately |
| GET | `/api/users/{id}/matches/pending` | List pending matches, highest score first |
| POST | `/api/matches/{id}/approve` | Mark a match approved |
| POST | `/api/matches/{id}/reject` | Dismiss a match |

## Configuration

| Variable | Purpose |
|---|---|
| `JSEARCH_API_KEY` | RapidAPI key for JSearch |
| `ANTHROPIC_API_KEY` | Anthropic API key |
| `ANTHROPIC_MODEL` | Claude model (default `claude-haiku-5-5`) |
| `GMAIL_USERNAME`, `GMAIL_APP_PASSWORD` | Account the digest is sent from |
| `DB_PASSWORD` | PostgreSQL password |

## Roadmap

- JWT authentication so the service can be shared safely
- Resume upload (PDF/DOCX) instead of plain text
- Deployment to AWS EC2 with k3s (manifests in `k8s/`)
