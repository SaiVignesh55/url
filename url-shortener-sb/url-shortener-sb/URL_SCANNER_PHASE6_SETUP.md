# URL Scanner Setup (Google Safe Browsing + urlscan.io)

This project uses two optional external providers:

- Google Safe Browsing
- urlscan.io

If keys are missing, the scanner still works with local rules.

## 1) Create Google Safe Browsing API key

1. Open Google Cloud Console.
2. Create/select a project.
3. Enable `Safe Browsing API`.
4. Open `APIs & Services` -> `Credentials`.
5. Click `Create Credentials` -> `API key`.
6. Copy the generated key.

## 2) Create urlscan.io API key

1. Open https://urlscan.io/ and sign in.
2. Open your account settings.
3. Copy your API key.

## 3) Add keys in backend environment

Use the backend `.env` file (or system environment variables):

```dotenv
GOOGLE_SAFE_BROWSING_API_KEY=your_google_key
URLSCAN_API_KEY=your_urlscan_key
```

You can use `.env.example` as a template.

## 4) Where these are read in code

- `src/main/resources/application.properties`
  - `google.safe-browsing.api-key=${GOOGLE_SAFE_BROWSING_API_KEY}`
  - `urlscan.api-key=${URLSCAN_API_KEY}`

## 5) urlscan polling behavior

- `urlscan.submit-url` -> submit scan request
- `urlscan.result-url-template` -> fetch result by UUID
- `urlscan.poll.max-attempts` and `urlscan.poll.delay-ms` control timeout behavior

## 6) Notes

- urlscan may take time to complete; scanner polls for a limited number of attempts.
- Provider failures are non-blocking and scanner continues with available checks.
