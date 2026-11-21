package com.tameio.platform.storage;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

import java.io.InputStream;

@Slf4j
@Component
@RequiredArgsConstructor
class S3FileStore implements FileStore {

    private final S3Client s3;
    private final StorageProperties properties;

    @PostConstruct
    void createBucketIfAllowed() {
        if (!properties.createBucket()) {
            return;
        }
        try {
            s3.headBucket(request -> request.bucket(properties.bucket()));
        } catch (NoSuchBucketException e) {
            create();
        }
    }

    /** Several instances starting together may all find no bucket; the one that loses the race finds it made. */
    private void create() {
        try {
            s3.createBucket(request -> request.bucket(properties.bucket()));
            log.info("Created storage bucket {}", properties.bucket());
        } catch (BucketAlreadyOwnedByYouException e) {
            log.info("Storage bucket {} was created by another instance", properties.bucket());
        }
    }

    @Override
    public void put(String key, byte[] content, String mediaType) {
        s3.putObject(request -> request.bucket(properties.bucket()).key(key).contentType(mediaType),
            RequestBody.fromBytes(content));
    }

    @Override
    public InputStream open(String key) {
        return s3.getObject(request -> request.bucket(properties.bucket()).key(key));
    }

    @Override
    public void delete(String key) {
        s3.deleteObject(request -> request.bucket(properties.bucket()).key(key));
    }
}
