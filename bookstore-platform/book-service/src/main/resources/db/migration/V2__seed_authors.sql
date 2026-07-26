-- Reference/demo authors so books can be created against a valid author.

INSERT INTO author (id, name) VALUES
    (1, 'Robert C. Martin'),
    (2, 'Eric Evans'),
    (3, 'Martin Fowler');

SELECT setval(pg_get_serial_sequence('author', 'id'), (SELECT MAX(id) FROM author));
