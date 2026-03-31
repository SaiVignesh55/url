# URL Scanner Phase 6 Setup (Google + VirusTotal)

This project can use two optional external providers:

- Google Safe Browsing
- VirusTotal

If keys are missing, the scanner still works with local rules.

## 1) Create Google Safe Browsing API key

1. Open Google Cloud Console.
2. Create/select a project.
3. Enable `Safe Browsing API`.
4. Open `APIs & Services` -> `Credentials`.
5. Click `Create Credentials` -> `API key`.
6. Copy the generated key.

## 2) Create VirusTotal API key

1. Open https://www.virustotal.com/ and sign in.
2. Open your profile.
3. Go to `API key` section.
4. Copy your personal API key.

## 3) Add keys in backend environment

Use the backend `.env` file (or system environment variables):

```dotenv
GOOGLE_SAFE_BROWSING_API_KEY=your_google_key
VIRUSTOTAL_API_KEY=your_virustotal_key
```

You can use `.env.example` as a template.

## 4) Where these are read in code

- `src/main/resources/application.properties`
  - `google.safe-browsing.api-key=${GOOGLE_SAFE_BROWSING_API_KEY}`
  - `virustotal.api-key=${VIRUSTOTAL_API_KEY:}`

## 5) Async API endpoints (Phase 6)

- `POST /api/scan/async` -> submit a scan job, returns `jobId`
- `GET /api/scan/async/{jobId}` -> get current status/result

## 6) Notes

- VirusTotal may return no report for unknown URLs.
- This implementation treats provider failures as non-blocking and continues with available checks.

