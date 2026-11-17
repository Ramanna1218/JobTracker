# Run JobTracker on your Windows PC

## One-time setup
1. Unzip this folder (jobtracker-v3-local) somewhere easy, e.g. `C:\Users\manoh\jobtracker-v3-local`.
2. Copy `.env.example` to `.env` (same folder) and fill in your keys.
   Notepad: File → Save As → File name `.env`, "Save as type: All Files".
3. Open `register.json`, change `"password"` to a password you choose.
   The daily alerts go to the `"email"` in this file.
4. Make sure Docker Desktop is running (whale icon in the taskbar).

## Start it (PowerShell, inside the jobtracker-v3-local folder)
```powershell
cd C:\Users\manoh\jobtracker-v3-local
docker compose up -d --build        # first build takes ~5 min
docker compose logs -f app          # wait for "Started JobTrackerApplication", then Ctrl+C
```

## Register yourself (only once)
```powershell
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/users/register" -ContentType "application/json; charset=utf-8" -InFile register.json
```
Note the `id` it returns (usually 1).

## Test it now (don't wait for 7 AM)
```powershell
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/users/1/run-now"
```
Check your inbox, then open http://localhost:8080/dashboard.html?userId=1

## Daily use
- Runs automatically every day at 7:00 AM Eastern while Docker Desktop is running.
- Docker Desktop → Settings → General → "Start Docker Desktop when you sign in" = on.
- Stop: `docker compose down`   ·   Start again: `docker compose up -d`
- Your saved matches survive restarts (stored in a Docker volume).

## Costs
- JSearch: 3 keywords x 1 search/day = ~90 of your 200 free requests a month.
- Claude (claude-haiku-5-5): a fraction of a cent per job match.
