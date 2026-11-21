package com.tameio.platform.storage;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

import java.net.URI;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class S3FileStoreTest {

    private static final StorageProperties LOCAL =
        new StorageProperties(URI.create("http://storage:8333"), "us-east-1", "documents", "key", "secret", true);

    @Test
    @SuppressWarnings("unchecked")
    void anInstanceThatLosesTheRaceToCreateTheBucketStillStarts() {
        S3Client s3 = mock(S3Client.class);
        when(s3.headBucket(any(Consumer.class))).thenThrow(NoSuchBucketException.builder().build());
        when(s3.createBucket(any(Consumer.class))).thenThrow(BucketAlreadyOwnedByYouException.builder().build());

        assertThatCode(() -> new S3FileStore(s3, LOCAL).createBucketIfAllowed()).doesNotThrowAnyException();
    }
}
