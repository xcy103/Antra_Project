package com.bookstore.bookservice.service;

import com.bookstore.bookservice.dto.CoverUploadResponse;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Presigned-URL generation is an offline (client-side signing) operation — no S3
 * call — so it can be unit-tested without any container.
 */
class CoverServiceTest {

    private CoverService coverService() {
        StaticCredentialsProvider creds = StaticCredentialsProvider.create(
                AwsBasicCredentials.create("test", "test"));
        S3Presigner presigner = S3Presigner.builder().region(Region.US_EAST_1).credentialsProvider(creds).build();
        DynamoDbEnhancedClient enhanced = DynamoDbEnhancedClient.builder()
                .dynamoDbClient(DynamoDbClient.builder().region(Region.US_EAST_1).credentialsProvider(creds).build())
                .build();
        return new CoverService(presigner, enhanced, "test-bucket", "CoverMetadata", 10L);
    }

    @Test
    void createUploadUrl_returnsPresignedPutForDeterministicKey() {
        CoverUploadResponse response = coverService().createUploadUrl(42L, "image/png");

        assertThat(response.key()).isEqualTo("covers/42");
        assertThat(response.expiresInSeconds()).isEqualTo(600L);
        assertThat(response.uploadUrl())
                .contains("test-bucket")
                .contains("covers/42")
                .contains("X-Amz-Signature");
    }
}
