package com.bookstore.bookservice.service;

import com.bookstore.bookservice.dto.BrowsingHistoryEntry;
import com.bookstore.bookservice.entity.BrowsingHistoryItem;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the DynamoDB browsing-history integration against LocalStack: writes
 * are queried back most-recent-first and are isolated per user.
 */
@Testcontainers
class BrowsingHistoryIntegrationTest {

    @Container
    static final LocalStackContainer LOCALSTACK = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:3.8"))
            .withServices(LocalStackContainer.Service.DYNAMODB);

    private static BrowsingHistoryService service;

    @BeforeAll
    static void setUp() {
        DynamoDbClient client = DynamoDbClient.builder()
                .endpointOverride(LOCALSTACK.getEndpointOverride(LocalStackContainer.Service.DYNAMODB))
                .region(Region.of(LOCALSTACK.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(LOCALSTACK.getAccessKey(), LOCALSTACK.getSecretKey())))
                .build();

        DynamoDbEnhancedClient enhanced = DynamoDbEnhancedClient.builder().dynamoDbClient(client).build();
        DynamoDbTable<BrowsingHistoryItem> table =
                enhanced.table("UserBrowsingHistory", TableSchema.fromBean(BrowsingHistoryItem.class));
        table.createTable();
        client.waiter().waitUntilTableExists(b -> b.tableName("UserBrowsingHistory"));

        service = new BrowsingHistoryService(enhanced, "UserBrowsingHistory", 30L);
    }

    @Test
    void recordsAndReturnsMostRecentFirst() throws InterruptedException {
        service.recordView("alice", 1L, "Book One");
        Thread.sleep(5); // ensure a distinct viewedAt (sort key) millisecond
        service.recordView("alice", 2L, "Book Two");

        List<BrowsingHistoryEntry> history = service.getRecentlyViewed("alice", 10);

        assertThat(history).hasSize(2);
        assertThat(history.get(0).bookTitle()).isEqualTo("Book Two"); // most recent first
        assertThat(history.get(1).bookTitle()).isEqualTo("Book One");
    }

    @Test
    void isolatesHistoryByUser() {
        service.recordView("bob", 99L, "Bob's Only Book");

        List<BrowsingHistoryEntry> aliceHistory = service.getRecentlyViewed("alice", 10);

        assertThat(aliceHistory).extracting(BrowsingHistoryEntry::bookId).doesNotContain(99L);
    }
}
