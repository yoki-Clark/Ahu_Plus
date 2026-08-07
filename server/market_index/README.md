# Market index service

This service maintains a server-side ID index for the public Anhui University
circle (`school_id=10681`). It stores topic IDs and ordering metadata, plus one
latest compressed snapshot of each source list row. The snapshot keeps the
source JSON and its small `comments` preview, but does not download detail
pages, images, or avatars.

## ⚠️ Security Requirements

Before deploying to production, verify:

1. **Credential Security**
   - `MARKET_INDEX_CURSOR_SECRET`: Generate with `openssl rand -hex 32` (min 32 bytes)
   - `MARKET_SOURCE_TOKEN`: Use a **read-only** dedicated account token, NOT a personal Bearer token
   - `DATABASE_URL`: Use Docker Secrets or Kubernetes ConfigMap for password injection
   - `.env` file permissions: `chmod 600 .env` (owner read/write only)

2. **Rate Limiting**
   - `MARKET_TRUSTED_PROXY_CIDRS` **MUST ONLY** contain controlled Docker/internal networks (e.g., `172.18.0.0/16`)
   - **NEVER** use public CDN CIDRs (e.g., Cloudflare) — this allows X-Forwarded-For spoofing
   - Leave empty to safely rate-limit by direct peer IP instead of trusting forwarded headers

3. **Database Permissions**
   - Grant ONLY `SELECT`, `INSERT`, `UPDATE` to the app user
   - **DENY** `DROP`, `ALTER`, `CREATE`, `DELETE` to limit breach impact

4. **Container Security**
   - Run as non-root user (add `USER nonroot` to Dockerfile)
   - Keep `security_opt: no-new-privileges:true` in compose.yaml

5. **Log Sanitization**
   - Worker automatically redacts `Authorization` headers via httpx event hooks
   - Never log `.env` contents, database passwords, or full request/response bodies

## Local tests

```powershell
python -m unittest discover -s server/market_index/tests -p "test_*.py" -v
```

## Deployment

1. Create the external Docker network once:

   ```bash
   docker network create ahu-plus-net
   ```

2. As a database administrator, apply `migrations/001_market_readonly_index.sql`
   and `migrations/002_market_readonly_topic_archive.sql`.
3. Copy `.env.example` to an untracked `.env` and fill in the private database
   endpoint, cursor secret and manually rotated market token.
   
   **CRITICAL:** Set `MARKET_TRUSTED_PROXY_CIDRS` to the CIDR of the controlled
   Docker network containing Caddy and `market-api` (e.g., `172.18.0.0/16`).
   
   ⚠️ **DO NOT** use broad public networks, CDN CIDRs (Cloudflare, CloudFront, etc.),
   or `0.0.0.0/0` — this allows attackers to spoof X-Forwarded-For headers and
   bypass rate limiting.
   
   If uncertain about network topology, **leave it empty** — the API will safely
   rate-limit by the direct proxy peer IP instead of trusting forwarded headers.

4. Validate and start the service:

   ```bash
   docker compose -f server/market_index/compose.yaml config
   docker compose -f server/market_index/compose.yaml up -d --build
   ```

The worker starts the historical bootstrap automatically, persists its page
checkpoint, and switches to a two-minute incremental loop after reaching the
end. A 401/403 pauses collection until the token is rotated and the worker is
restarted. Never print `.env`, Authorization headers or source response bodies.

The public API is:

```text
GET /market/readonly/feed?limit=20&cursor=<opaque-cursor>
GET /market/readonly/archive/{topic_id}
```

The archive endpoint only returns a topic previously captured for the configured
school. It is intended as an explicit client fallback when the source
`topics/read_only/{id}` endpoint no longer serves a topic. Archive payloads are
stored as compact UTF-8 JSON, gzip-compressed, and SHA-256 deduplicated before
writing.
