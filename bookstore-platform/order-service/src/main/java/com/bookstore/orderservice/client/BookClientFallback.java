package com.bookstore.orderservice.client;

import com.bookstore.common.exception.ResourceNotFoundException;
import com.bookstore.orderservice.exception.CatalogUnavailableException;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * Fallback for {@link BookClient}. Distinguishes "book genuinely not found" (a 404
 * from book-service → {@link ResourceNotFoundException} → 404) from "book-service
 * unavailable / timed out / circuit open" ({@link CatalogUnavailableException} →
 * 503). This is what makes a place-order request degrade gracefully when
 * book-service is down instead of cascading timeouts.
 */
@Component
public class BookClientFallback implements FallbackFactory<BookClient> {

    private static final Logger log = LoggerFactory.getLogger(BookClientFallback.class);

    @Override
    public BookClient create(Throwable cause) {
        return bookId -> {
            if (cause instanceof FeignException.NotFound) {
                throw new ResourceNotFoundException("Book not found with id " + bookId);
            }
            log.warn("book-service call failed for bookId={}, degrading gracefully: {}",
                    bookId, cause.toString());
            throw new CatalogUnavailableException("Book catalog is temporarily unavailable, please retry");
        };
    }
}
