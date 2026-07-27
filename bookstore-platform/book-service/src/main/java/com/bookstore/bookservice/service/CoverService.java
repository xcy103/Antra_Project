package com.bookstore.bookservice.service;

import com.bookstore.bookservice.dto.CoverMetadataResponse;
import com.bookstore.bookservice.dto.CoverUploadResponse;
import com.bookstore.bookservice.entity.CoverMetadataItem;
import com.bookstore.common.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import java.time.Duration;

/**
 * Cover uploads/metadata. Uploads use a presigned S3 PUT URL (client uploads
 * directly to S3; a Lambda then processes the S3 event and writes CoverMetadata).
 * The S3 key is deterministic per book ({@code covers/{bookId}}), which is what
 * lets the Lambda's DynamoDB write be idempotent.
 */
@Service
public class CoverService {

    private final S3Presigner presigner;
    private final DynamoDbTable<CoverMetadataItem> table;
    private final String bucket;
    private final Duration uploadUrlTtl;

    public CoverService(
            S3Presigner presigner,
            DynamoDbEnhancedClient enhancedClient,
            @Value("${aws.s3.bucket:bookstore-covers}") String bucket,
            @Value("${aws.dynamodb.cover-metadata-table:CoverMetadata}") String tableName,
            @Value("${aws.s3.upload-url-ttl-minutes:10}") long uploadUrlTtlMinutes) {
        this.presigner = presigner;
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(CoverMetadataItem.class));
        this.bucket = bucket;
        this.uploadUrlTtl = Duration.ofMinutes(uploadUrlTtlMinutes);
    }

    public static String coverKey(Long bookId) {
        return "covers/" + bookId;
    }

    public CoverUploadResponse createUploadUrl(Long bookId, String contentType) {
        String key = coverKey(bookId);
        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType != null ? contentType : "image/jpeg")
                .build();
        PresignedPutObjectRequest presigned = presigner.presignPutObject(b -> b
                .signatureDuration(uploadUrlTtl)
                .putObjectRequest(objectRequest));
        return new CoverUploadResponse(presigned.url().toString(), key, uploadUrlTtl.toSeconds());
    }

    public CoverMetadataResponse getCoverMetadata(Long bookId) {
        CoverMetadataItem item = table.getItem(Key.builder().partitionValue(bookId).build());
        if (item == null) {
            throw new ResourceNotFoundException("No cover has been processed for book " + bookId);
        }
        return CoverMetadataResponse.from(item);
    }
}
