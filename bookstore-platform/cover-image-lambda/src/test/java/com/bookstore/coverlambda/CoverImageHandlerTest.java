package com.bookstore.coverlambda;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.sns.SnsClient;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

class CoverImageHandlerTest {

    private final S3Client s3 = mock(S3Client.class);
    private final DynamoDbClient dynamo = mock(DynamoDbClient.class);
    private final SnsClient sns = mock(SnsClient.class);
    private final CoverImageHandler handler =
            new CoverImageHandler(s3, dynamo, sns, "CoverMetadata", "arn:aws:sns:us-east-1:000:covers");

    private byte[] pngBytes() throws Exception {
        BufferedImage image = new BufferedImage(10, 20, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private void stubS3Object() throws Exception {
        GetObjectResponse response = GetObjectResponse.builder()
                .contentLength((long) pngBytes().length).contentType("image/png").build();
        ResponseBytes<GetObjectResponse> bytes = ResponseBytes.fromByteArray(response, pngBytes());
        when(s3.getObjectAsBytes(any(Consumer.class))).thenReturn(bytes);
    }

    @Test
    void firstEvent_writesMetadataAndPublishesSns() throws Exception {
        stubS3Object();

        handler.process("bookstore-covers", "covers/42");

        ArgumentCaptor<PutItemRequest> put = ArgumentCaptor.forClass(PutItemRequest.class);
        verify(dynamo).putItem(put.capture());
        assertThat(put.getValue().conditionExpression()).isEqualTo("attribute_not_exists(bookId)");
        assertThat(put.getValue().item().get("bookId").n()).isEqualTo("42");
        assertThat(put.getValue().item().get("width").n()).isEqualTo("10");
        assertThat(put.getValue().item().get("height").n()).isEqualTo("20");
        verify(sns).publish(any(Consumer.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void duplicateEvent_conditionalWriteFails_noSns() throws Exception {
        stubS3Object();
        when(dynamo.putItem(any(PutItemRequest.class)))
                .thenThrow(ConditionalCheckFailedException.builder().message("exists").build());

        handler.process("bookstore-covers", "covers/42");

        // Idempotent: the duplicate must NOT trigger another notification.
        verify(sns, never()).publish(any(Consumer.class));
    }

    @Test
    void nonNumericKey_isIgnored() {
        handler.process("bookstore-covers", "covers/not-a-number");

        verify(dynamo, never()).putItem(any(PutItemRequest.class));
    }
}
