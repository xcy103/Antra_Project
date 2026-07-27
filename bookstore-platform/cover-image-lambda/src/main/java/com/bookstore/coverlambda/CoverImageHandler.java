package com.bookstore.coverlambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.sns.SnsClient;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Triggered by an S3 upload of a book cover ({@code covers/{bookId}}). It records
 * cover metadata to DynamoDB and publishes a "cover processed" SNS notification.
 *
 * <p><b>Idempotency:</b> the item is keyed by the deterministic {@code bookId} and
 * written with a {@code attribute_not_exists(bookId)} condition. A duplicate S3
 * event therefore fails the conditional write and is skipped — no duplicate row
 * and, crucially, no duplicate email (SNS is published only after a first-time write).
 */
public class CoverImageHandler implements RequestHandler<S3Event, Void> {

    private final S3Client s3;
    private final DynamoDbClient dynamo;
    private final SnsClient sns;
    private final String tableName;
    private final String topicArn;

    /** Entry point used by the Lambda runtime. */
    public CoverImageHandler() {
        this(S3Client.create(), DynamoDbClient.create(), SnsClient.create(),
                System.getenv().getOrDefault("COVER_METADATA_TABLE", "CoverMetadata"),
                System.getenv("COVER_TOPIC_ARN"));
    }

    CoverImageHandler(S3Client s3, DynamoDbClient dynamo, SnsClient sns, String tableName, String topicArn) {
        this.s3 = s3;
        this.dynamo = dynamo;
        this.sns = sns;
        this.tableName = tableName;
        this.topicArn = topicArn;
    }

    @Override
    public Void handleRequest(S3Event event, Context context) {
        event.getRecords().forEach(record ->
                process(record.getS3().getBucket().getName(), record.getS3().getObject().getUrlDecodedKey()));
        return null;
    }

    void process(String bucket, String key) {
        Long bookId = parseBookId(key);
        if (bookId == null) {
            return; // not a cover key we understand; ignore
        }

        ResponseBytes<GetObjectResponse> object = s3.getObjectAsBytes(b -> b.bucket(bucket).key(key));
        long sizeBytes = object.response().contentLength() != null ? object.response().contentLength() : 0L;
        String contentType = object.response().contentType();
        int[] dimensions = readDimensions(object.asByteArray());

        Map<String, AttributeValue> item = new HashMap<>();
        item.put("bookId", AttributeValue.fromN(String.valueOf(bookId)));
        item.put("s3Key", AttributeValue.fromS(key));
        item.put("contentType", AttributeValue.fromS(contentType != null ? contentType : "application/octet-stream"));
        item.put("width", AttributeValue.fromN(String.valueOf(dimensions[0])));
        item.put("height", AttributeValue.fromN(String.valueOf(dimensions[1])));
        item.put("sizeBytes", AttributeValue.fromN(String.valueOf(sizeBytes)));
        item.put("processedAt", AttributeValue.fromS(Instant.now().toString()));

        try {
            dynamo.putItem(PutItemRequest.builder()
                    .tableName(tableName)
                    .item(item)
                    .conditionExpression("attribute_not_exists(bookId)")
                    .build());
        } catch (ConditionalCheckFailedException alreadyProcessed) {
            // Duplicate S3 event — cover already recorded. Idempotent: do not re-notify.
            return;
        }

        if (topicArn != null && !topicArn.isBlank()) {
            sns.publish(b -> b.topicArn(topicArn)
                    .subject("Book cover processed")
                    .message("Cover processed for book " + bookId + " (" + dimensions[0] + "x" + dimensions[1] + ")"));
        }
    }

    private Long parseBookId(String key) {
        try {
            return Long.parseLong(key.substring(key.lastIndexOf('/') + 1));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private int[] readDimensions(byte[] bytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image != null) {
                return new int[]{image.getWidth(), image.getHeight()};
            }
        } catch (Exception ignored) {
            // Not a decodable image; record zero dimensions rather than failing.
        }
        return new int[]{0, 0};
    }
}
