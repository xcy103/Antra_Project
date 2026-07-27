# Bookstore — Web Client

A single-file demo frontend for the Bookstore microservices platform. It talks **only** to the
API Gateway (`http://localhost:8080` by default), which routes to the user / book / order / payment
services and enforces JWT auth at the edge.

It is intentionally build-free: [`index.html`](index.html) loads React + Babel from a CDN and
transpiles the JSX in the browser. This keeps the capstone's focus on the backend while still giving
a real UI that exercises the documented API end to end.

## What it demonstrates

- **Register / log in** → `POST /api/auth/register`, `POST /api/auth/login`; the JWT is stored in
  `localStorage` and sent as a `Bearer` token on every subsequent call.
- **Browse the catalog** → `GET /api/books`; "View" a book → `GET /api/books/{id}` (records a
  browsing-history event when signed in).
- **Place an order** from the cart → `POST /api/orders`.
- **Pay / cancel an order** → `POST /api/payments`, `PUT /api/orders/{id}/cancel`; order and payment
  status render as badges.
- **Recently viewed** → `GET /api/books/me/history` (best-effort; needs the DynamoDB-backed history
  feature running).

## Run it

1. Start the platform so the gateway is up on `:8080`. Either:
   - `cd bookstore-platform && docker compose up` (full stack), or
   - run `config-server`, `api-gateway`, and the services from your IDE.
2. Open `frontend/index.html` in a browser (double-click, or serve it — e.g.
   `python3 -m http.server 5500` then visit `http://localhost:5500/frontend/`).
3. Register a user, browse the catalog, place and pay an order.

The gateway URL is editable in the header if you run it on a different host/port. CORS is already
open on the gateway for local dev (`allowedOrigins: "*"`).

## Admin actions

Creating/editing books (`POST/PUT /api/books`) requires the `ADMIN` role. New registrations get
`USER`; promote an account by seeding an `ADMIN` row in the user-service database (or via a Flyway
seed) — see the backend docs.

> This is a demo client, not a production frontend: no bundler, no tests, tokens in `localStorage`.
> Its job is to make the documented API tangible for the capstone demo.
