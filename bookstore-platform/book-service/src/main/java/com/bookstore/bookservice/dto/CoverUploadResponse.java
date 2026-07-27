package com.bookstore.bookservice.dto;

/**
 * A presigned S3 upload target: the client PUTs the cover image bytes to
 * {@code uploadUrl} within {@code expiresInSeconds}.
 */
public record CoverUploadResponse(
        String uploadUrl,
        String key,
        long expiresInSeconds
) {
}
