#!/usr/bin/env bash
#
# Seed demo data into the running docker-compose stack so the store is usable.
#   - Inserts a handful of demo books (idempotent; references the seeded authors).
#   - Optionally promotes an existing user to ADMIN:  ./seed-demo.sh <username>
#
# Requires the stack to be up (docker compose up) — it writes straight into the
# Postgres container using the local-dev superuser. Data only, no schema changes.
#
set -euo pipefail

PG=bookstore-platform-postgres

if ! docker ps --format '{{.Names}}' | grep -q "^${PG}$"; then
  echo "✗ Postgres container '${PG}' is not running. Start the stack first:"
  echo "    docker compose up -d --build"
  exit 1
fi

echo "→ Seeding demo books into bookdb…"
docker exec -i "$PG" psql -U postgres -d bookdb -v ON_ERROR_STOP=1 <<'SQL'
INSERT INTO book (title, isbn, price, stock, author_id) VALUES
  ('Clean Code',                                    '978-0132350884', 39.99, 20, 1),
  ('Clean Architecture',                            '978-0134494166', 34.99, 15, 1),
  ('The Clean Coder',                               '978-0137081073', 29.99, 12, 1),
  ('Domain-Driven Design',                          '978-0321125217', 49.99, 10, 2),
  ('Refactoring',                                   '978-0134757599', 44.99, 18, 3),
  ('Patterns of Enterprise Application Architecture','978-0321127426', 54.99,  8, 3)
ON CONFLICT (isbn) DO NOTHING;
SQL

count=$(docker exec -i "$PG" psql -U postgres -d bookdb -tAc "SELECT count(*) FROM book;")
echo "✓ bookdb now has ${count} book(s)."

if [ "${1:-}" != "" ]; then
  user="$1"
  echo "→ Promoting user '${user}' to ADMIN…"
  updated=$(docker exec -i "$PG" psql -U postgres -d userdb -tAc \
    "UPDATE users SET role='ADMIN' WHERE username='${user}'; SELECT count(*) FROM users WHERE username='${user}' AND role='ADMIN';")
  if [ "$updated" = "1" ]; then
    echo "✓ '${user}' is now ADMIN. Log out and back in to get a token with the ADMIN role."
  else
    echo "✗ No user named '${user}'. Register it first in the web client, then re-run:"
    echo "    ./scripts/seed-demo.sh ${user}"
    exit 1
  fi
fi

echo
echo "Done. Open frontend/index.html, register a user, and start shopping."
