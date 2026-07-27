package com.bookstore.bookservice.service;

import com.bookstore.bookservice.dto.BrowsingHistoryEntry;
import com.bookstore.bookservice.entity.BrowsingHistoryItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Records and reads per-user browsing history in DynamoDB. Writes are
 * {@code @Async} and best-effort — recording a view must never slow down or fail
 * the book lookup.
 */
@Service
public class BrowsingHistoryService {

    private static final Logger log = LoggerFactory.getLogger(BrowsingHistoryService.class);

    private final DynamoDbTable<BrowsingHistoryItem> table;
    private final Duration ttl;

    public BrowsingHistoryService(
            DynamoDbEnhancedClient enhancedClient,
            @Value("${aws.dynamodb.browsing-history-table:UserBrowsingHistory}") String tableName,
            @Value("${bookstore.browsing-history.ttl-days:30}") long ttlDays) {
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(BrowsingHistoryItem.class));
        this.ttl = Duration.ofDays(ttlDays);
    }

    @Async
    public void recordView(String userId, Long bookId, String bookTitle) {
        try {
            Instant now = Instant.now();
            BrowsingHistoryItem item = new BrowsingHistoryItem();
            item.setUserId(userId);
            item.setViewedAt(now.toEpochMilli());
            item.setBookId(bookId);
            item.setBookTitle(bookTitle);
            item.setExpireAt(now.plus(ttl).getEpochSecond());
            table.putItem(item);
        } catch (Exception ex) {
            log.warn("Failed to record browsing history for user {}: {}", userId, ex.toString());
        }
    }

    public List<BrowsingHistoryEntry> getRecentlyViewed(String userId, int limit) {
        QueryConditional byUser = QueryConditional.keyEqualTo(Key.builder().partitionValue(userId).build());
        return table.query(r -> r.queryConditional(byUser)
                        .scanIndexForward(false) // most-recent first (descending sort key)
                        .limit(limit))
                .items().stream()
                .limit(limit)
                .map(i -> new BrowsingHistoryEntry(i.getBookId(), i.getBookTitle(),
                        Instant.ofEpochMilli(i.getViewedAt())))
                .toList();
    }
}
