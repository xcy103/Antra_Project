package com.bookstore.bookservice.dto;

import java.time.Instant;

/**
 * One entry in a user's recently-viewed list.
 */
public record BrowsingHistoryEntry(
        Long bookId,
        String bookTitle,
        Instant viewedAt
) {
}
