package com.tameio.platform.storage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;

/**
 * Connection to an S3-compatible store: SeaweedFS locally, AWS S3 or any other S3 service elsewhere.
 * Validated at startup, so a missing setting stops the app instead of failing on the first upload.
 *
 * @param createBucket create the bucket at startup if it is missing; for local development only,
 *                     since in production the bucket is provisioned with the rest of the infrastructure
 */
@Validated
@ConfigurationProperties("tameio.storage")
public record StorageProperties(
    @NotNull URI endpoint,
    @NotBlank String region,
    @NotBlank String bucket,
    @NotBlank String accessKey,
    @NotBlank String secretKey,
    boolean createBucket) {
}
