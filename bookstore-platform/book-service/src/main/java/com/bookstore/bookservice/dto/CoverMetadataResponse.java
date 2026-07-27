package com.bookstore.bookservice.dto;

import com.bookstore.bookservice.entity.CoverMetadataItem;

import java.time.Instant;

/**
 * Cover metadata (produced by the cover Lambda) returned to clients.
 */
public record CoverMetadataResponse(
        Long bookId,
        String s3Key,
        String contentType,
        Integer width,
        Integer height,
        Long sizeBytes,
        Instant processedAt
) {
    public static CoverMetadataResponse from(CoverMetadataItem item) {
        return new CoverMetadataResponse(
                item.getBookId(), item.getS3Key(), item.getContentType(),
                item.getWidth(), item.getHeight(), item.getSizeBytes(), item.getProcessedAt());
    }
}
