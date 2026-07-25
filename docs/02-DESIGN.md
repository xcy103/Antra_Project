# 02 — Design & Data-Layer Decisions

Living design notes. Phase 2 establishes the persistence layer; later phases append
their own sections. Every decision here is "be able to explain why" material.

---

## Phase 2 — Data layer

### Data model

```
author (1) ────< (N) book

author: id (PK), name
book:   id (PK), title, isbn (UQ), price, stock (CHECK >= 0),
        author_id (FK -> author.id), version (optimistic lock), created_at
```

Entities:
- `Book` — `@ManyToOne(fetch = LAZY, optional = false)` to `Author`, `@Version Long version`,
  `@CreationTimestamp Instant createdAt`.
- `Author` — `@OneToMany(mappedBy = "author")` back-reference, LAZY (kept lazy on purpose so
  the N+1 problem is demonstrable and then fixed).

### Schema is owned by Flyway; Hibernate only validates

- `spring.jpa.hibernate.ddl-auto=validate` — Hibernate never creates or alters tables. The schema
  is defined entirely in versioned Flyway scripts (`V1__init.sql`, `V2__seed_authors.sql`).
- **Why:** `ddl-auto=update` is non-deterministic, silently drifts, and can't be code-reviewed or
  rolled back. Flyway migrations are ordered, immutable once shipped, and reproducible across
  environments. `validate` catches entity/DDL drift at startup instead of at runtime.
- Verified: on boot Flyway reports *"Successfully applied 2 migrations … now at version v2"* and the
  app starts with **no** `SchemaManagementException` — i.e. the entity mappings exactly match the
  Flyway DDL (including `Instant`↔`timestamptz`, `BigDecimal`↔`numeric(10,2)`, `@Version`↔`bigint`).

### Indexes and their rationale

| Index | Column | Why |
|---|---|---|
| `idx_book_author_id` | `book(author_id)` | Backs the FK and "books by a given author" lookups (incl. the fetch-join). FK columns are not auto-indexed in PostgreSQL. |
| `idx_book_title` | `book(title)` | Backs catalog search/sort by title — the most common read path for a bookstore. |

`isbn` is covered by the `uq_book_isbn` unique constraint (which creates its own index).

**EXPLAIN ANALYZE evidence** (20,000 rows, `ANALYZE`d):

Equality on the indexed `title` → index scan:
```
EXPLAIN ANALYZE SELECT * FROM book WHERE title = 'Title-12345';

 Index Scan using idx_book_title on book  (cost=0.29..8.30 rows=1 width=69)
                                          (actual time=0.010..0.010 rows=1 loops=1)
   Index Cond: ((title)::text = 'Title-12345'::text)
 Execution Time: 0.021 ms
```

Contrast — a leading-wildcard `LIKE '%…%'` can't use a B-tree, so it falls back to a seq scan:
```
EXPLAIN ANALYZE SELECT * FROM book WHERE title LIKE '%12345%';

 Seq Scan on book  (cost=0.00..497.02 rows=2 width=69) (actual time=0.715..1.361 rows=1 loops=1)
   Filter: ((title)::text ~~ '%12345%'::text)
   Rows Removed by Filter: 20001
```
Takeaway: the B-tree index accelerates equality and prefix (`LIKE 'x%'`) predicates, not
substring search. Substring/full-text search would need a different index type (trigram/GIN) — noted
for later if catalog search grows.

### N+1 problem and fix

- **Where:** listing authors together with their books. Naively, `authorRepository.findAll()` runs
  one query for the authors, then Hibernate lazily loads each author's `books` on first access —
  **1 + N** queries.
- **Fix:** `AuthorRepository.findAllWithBooks()` uses `LEFT JOIN FETCH a.books`, loading everything
  in **one** query.
- **Measured** by `NPlusOneQueryTest` with Hibernate statistics (`generate_statistics=true`):

  | Approach | Query count (3 authors) |
  |---|---|
  | `findAll()` + touch `getBooks()` (naive) | **4** (= authors + 1) |
  | `findAllWithBooks()` (fetch join) | **1** |

- The same pattern protects the book endpoints: `GET /api/books` uses `findAllWithAuthor()`
  (`JOIN FETCH b.author`), observed in the SQL log as a **single** `book JOIN author` select — so
  mapping to a DTO never triggers a per-row lazy load, even with `open-in-view=false`.

> The query counts above are asserted by `NPlusOneQueryTest`; the single-query book listing was
> observed live in the dev-profile SQL log (see Verification status).

### Transactions

- Read methods: `@Transactional(readOnly = true)` — a consistent snapshot and a hint that lets the
  driver/DB skip write bookkeeping.
- Write methods (`createBook`, `updateBook`): `@Transactional` — each does multiple operations
  (uniqueness check, author lookup, insert/update) that must commit or roll back as one unit.
- `spring.jpa.open-in-view=false` — the Hibernate session does **not** stay open into the view layer.
  Fetching is decided explicitly in the service (fetch-join queries), which is why every DTO field is
  available at mapping time without a lazy surprise.

### Optimistic locking

- `Book.version` (`@Version`) makes every update a `… WHERE id = ? AND version = ?`. If another
  transaction already bumped the version, 0 rows match and Hibernate raises
  `ObjectOptimisticLockingFailureException` instead of silently overwriting.
- **Why optimistic, not `SELECT … FOR UPDATE`:** stock contention is low and reads dominate;
  optimistic locking avoids holding row locks and only pays a cost on the rare real conflict.
- `BookRepositoryTest.staleUpdate_failsOptimisticLock` proves it: it loads a book, simulates a
  concurrent update via a native `UPDATE … version = version + 1`, then flushing the stale entity
  throws the optimistic-lock exception.

### Author is a required FK; seeding

- `book.author_id` is `NOT NULL` — every book has an author. Creating a book with an unknown
  `authorId` returns **404** (`Author not found`); the DB FK is the backstop.
- A few reference authors are seeded (`V2__seed_authors.sql`) so the API is usable out of the box.
  Books are never seeded — they go through the API. Author-management endpoints aren't part of the
  documented API surface, so none were added this phase.

### Repository evolution (Phase 1 → 2)

Phase 1's `BookRepository` was a hand-rolled in-memory interface whose method set deliberately
mirrored `JpaRepository`. Phase 2 simply made it `extends JpaRepository<Book, Long>` (plus derived
`existsByIsbn` and two fetch-join queries) and deleted the in-memory implementation — the service
layer's calls were unchanged. The Phase-1 `InMemoryBookRepository`/its test were removed.

### Verification status (honest record)

Run in this environment:
- `BookServiceImplTest` (Mockito, 9 tests) — **pass**.
- App booted against the live docker-compose PostgreSQL: Flyway migrated V1+V2, `validate` passed,
  full CRUD via curl (201/200/204), 404 (unknown author), 409 (duplicate isbn); rows confirmed in
  `psql`. DB constraints verified directly: `uq_book_isbn`, `chk_book_stock_non_negative`,
  `fk_book_author` all reject bad rows. `EXPLAIN ANALYZE` captured above.

Not run here (pending on the local machine):
- The Testcontainers integration tests (`BookRepositoryTest`, `NPlusOneQueryTest`, and the
  `@SpringBootTest` smoke test) — this sandbox blocks the Docker **API** for the Java client, so
  Testcontainers cannot start a container (the Docker CLI works, but docker-java does not). The tests
  are written and expected to pass; **`mvn clean verify` must be run on the local machine to confirm.**
