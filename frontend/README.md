# EGS Composer Frontend

This is a minimal React + TypeScript frontend scaffold for the Composer (BFF / Wallet). It assumes Composer runs on http://localhost:5001 and Vite dev server will proxy `/v1` to Composer.

Quick start

```bash
cd frontend
npm install
npm run dev
```

Environment
- `VITE_COMPOSER_BASE` (optional): base URL of Composer (default `http://localhost:5001`)
- `VITE_LOGIN_URL` / `VITE_SIGNUP_URL` (optional): URL to redirect user for login/signup (Composer or Keycloak)

Notes
- The scaffold uses a dev proxy configured in `vite.config.ts` so fetch('/v1/...') is forwarded to Composer.
- Login flow currently expects a redirect-based OIDC flow; set `VITE_LOGIN_URL` to the appropriate redirect URL (Composer or Keycloak).
