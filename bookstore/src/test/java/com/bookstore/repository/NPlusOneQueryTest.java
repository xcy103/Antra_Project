package com.bookstore.repository;

import com.bookstore.entity.Author;
import com.bookstore.entity.Book;
import com.bookstore.support.AbstractPostgresIT;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Demonstrates the N+1 select problem and its fetch-join fix, measured with
 * Hibernate's statistics. Naive iteration fires 1 query for the authors plus one
 * per author for their books; the fetch join collapses that to a single query.
 * The captured counts are written up in docs/02-DESIGN.md.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class NPlusOneQueryTest extends AbstractPostgresIT {

    @Autowired
    private AuthorRepository authorRepository;

    @Autowired
    private EntityManager entityManager;

    private void seedAuthorsWithBooks(int authors, int booksPerAuthor) {
        for (int a = 0; a < authors; a++) {
            Author author = authorRepository.save(new Author("Author " + a));
            for (int b = 0; b < booksPerAuthor; b++) {
                String isbn = "isbn-" + a + "-" + b;
                entityManager.persist(new Book("Title " + isbn, isbn, new BigDecimal("10.00"), 1, author));
            }
        }
        entityManager.flush();
        entityManager.clear();
    }

    private Statistics statistics() {
        return entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
    }

    @Test
    void naiveFindAll_triggersNPlusOneQueries() {
        int authors = 3;
        seedAuthorsWithBooks(authors, 2);
        Statistics stats = statistics();
        stats.clear();

        List<Author> all = authorRepository.findAll();
        all.forEach(author -> author.getBooks().size()); // touch lazy collection

        long queries = stats.getPrepareStatementCount();
        System.out.println("[N+1] naive findAll query count = " + queries + " (authors=" + authors + ")");

        // 1 query for the authors + 1 per author for its books.
        assertThat(queries).isEqualTo(authors + 1L);
    }

    @Test
    void fetchJoin_loadsEverythingInOneQuery() {
        int authors = 3;
        seedAuthorsWithBooks(authors, 2);
        Statistics stats = statistics();
        stats.clear();

        List<Author> all = authorRepository.findAllWithBooks();
        all.forEach(author -> author.getBooks().size()); // already initialized

        long queries = stats.getPrepareStatementCount();
        System.out.println("[N+1] fetch-join findAllWithBooks query count = " + queries);

        assertThat(all).hasSize(authors);
        assertThat(queries).isEqualTo(1L);
    }
}
