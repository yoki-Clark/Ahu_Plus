# Market index service

This service maintains a server-side ID index for the public Anhui University
circle (`school_id=10681`). It stores topic IDs and ordering metadata, plus one
latest compressed snapshot of each source list row. The snapshot keeps the
source JSON and its small `comments` preview, but does not download detail
pages, images, or avatars.

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
   endpoint, cursor secret and manually rotated market token. Set
   `MARKET_TRUSTED_PROXY_CIDRS` to the CIDR of the controlled Docker network
   containing Caddy and `market-api`; do not use a broad public network. If it
   is empty, the API safely rate-limits by the direct proxy peer instead of
   trusting forwarded client headers.
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
