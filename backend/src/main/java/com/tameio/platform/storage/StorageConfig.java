package com.tameio.platform.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
class StorageConfig {

    @Bean(destroyMethod = "close")
    S3Client s3Client(StorageProperties properties) {
        return S3Client.builder()
            .endpointOverride(properties.endpoint())
            .region(Region.of(properties.region()))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())))
            // host/bucket/key rather than bucket.host/key: works with any S3-compatible server without DNS setup
            .forcePathStyle(true)
            .build();
    }
}
