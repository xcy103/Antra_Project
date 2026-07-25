-- Reference/demo authors so books can be created against a valid author out of the box.
-- Books are not seeded — they are created through the API.

INSERT INTO author (id, name) VALUES
    (1, 'Robert C. Martin'),
    (2, 'Eric Evans'),
    (3, 'Martin Fowler');

-- Keep the identity sequence ahead of the explicitly-seeded ids.
SELECT setval(pg_get_serial_sequence('author', 'id'), (SELECT MAX(id) FROM author));
