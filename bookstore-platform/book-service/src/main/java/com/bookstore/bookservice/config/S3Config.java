package com.bookstore.bookservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * S3 presigner for generating cover-upload URLs. Real AWS uses the SDK default
 * credential chain; a set {@code aws.s3.endpoint} (dev) switches to static creds.
 * book-service never streams image bytes — clients PUT directly to S3 with the
 * presigned URL, and a Lambda reacts to the S3 event.
 */
@Configuration
public class S3Config {

    @Bean
    public S3Presigner s3Presigner(@Value("${aws.region:us-east-1}") String region,
                                   @Value("${aws.s3.endpoint:}") String endpoint) {
        S3Presigner.Builder builder = S3Presigner.builder().region(Region.of(region));
        if (StringUtils.hasText(endpoint)) {
            builder = builder
                    .endpointOverride(URI.create(endpoint))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create("test", "test")));
        }
        return builder.build();
    }
}
