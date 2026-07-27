package com.bookstore.bookservice.service;

import com.bookstore.bookservice.dto.CoverMetadataResponse;
import com.bookstore.bookservice.entity.CoverMetadataItem;
import com.bookstore.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Reads {@code CoverMetadata} (as written by the cover Lambda) via dynamodb-local.
 */
@Testcontainers
class CoverMetadataIntegrationTest {

    @Container
    static final GenericContainer<?> DYNAMODB = new GenericContainer<>(
            DockerImageName.parse("amazon/dynamodb-local:2.5.2")).withExposedPorts(8000);

    private static CoverService coverService;
    private static DynamoDbTable<CoverMetadataItem> table;

    @BeforeAll
    static void setUp() {
        StaticCredentialsProvider creds = StaticCredentialsProvider.create(
                AwsBasicCredentials.create("test", "test"));
        DynamoDbClient client = DynamoDbClient.builder()
                .endpointOverride(URI.create("http://" + DYNAMODB.getHost() + ":" + DYNAMODB.getMappedPort(8000)))
                .region(Region.US_EAST_1)
                .credentialsProvider(creds)
                .build();
        DynamoDbEnhancedClient enhanced = DynamoDbEnhancedClient.builder().dynamoDbClient(client).build();
        table = enhanced.table("CoverMetadata", TableSchema.fromBean(CoverMetadataItem.class));
        table.createTable();
        client.waiter().waitUntilTableExists(b -> b.tableName("CoverMetadata"));

        S3Presigner presigner = S3Presigner.builder().region(Region.US_EAST_1).credentialsProvider(creds).build();
        coverService = new CoverService(presigner, enhanced, "test-bucket", "CoverMetadata", 10L);
    }

    @Test
    void getCoverMetadata_returnsWhatLambdaWrote() {
        CoverMetadataItem item = new CoverMetadataItem();
        item.setBookId(7L);
        item.setS3Key("covers/7");
        item.setContentType("image/png");
        item.setWidth(800);
        item.setHeight(1200);
        item.setSizeBytes(45678L);
        item.setProcessedAt(Instant.now());
        table.putItem(item);

        CoverMetadataResponse response = coverService.getCoverMetadata(7L);

        assertThat(response.bookId()).isEqualTo(7L);
        assertThat(response.width()).isEqualTo(800);
        assertThat(response.s3Key()).isEqualTo("covers/7");
    }

    @Test
    void getCoverMetadata_missing_throwsNotFound() {
        assertThatThrownBy(() -> coverService.getCoverMetadata(999L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
