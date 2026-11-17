package com.tameio.documents;

import com.tameio.identity.BusinessService;
import com.tameio.platform.security.CurrentUser;
import com.tameio.platform.storage.FileStore;
import com.tameio.platform.web.BadRequestException;
import com.tameio.platform.web.ConflictException;
import com.tameio.platform.web.NotFoundException;
import com.tameio.platform.web.UnsupportedMediaTypeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/** Uploading, listing and reading a business's documents. Every method first checks that the user belongs to the business. */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class DocumentService {

    private static final int MAX_FILENAME_LENGTH = 255;

    private final DocumentRepository documents;
    private final BusinessService businesses;
    private final FileStore files;
    private final ApplicationEventPublisher events;

    /**
     * Stores the file, then records it. If recording fails, the stored file is removed again, so storage
     * does not fill up with files the database knows nothing about.
     */
    @Transactional
    public DocumentView upload(CurrentUser user, UUID businessId, String originalFilename, byte[] content) {
        businesses.checkAccess(user, businessId);
        if (content.length == 0) {
            throw new BadRequestException("The file is empty.");
        }
        FileType type = FileType.detect(content)
            .orElseThrow(() -> new UnsupportedMediaTypeException("Upload a PDF, JPEG or PNG file."));
        String sha256 = sha256(content);
        // A friendly answer for the common case; the unique constraint still covers two uploads at once
        if (documents.existsByBusinessIdAndSha256(businessId, sha256)) {
            throw new ConflictException("This document has already been uploaded.");
        }

        String storageKey = "businesses/%s/documents/%s".formatted(businessId, UUID.randomUUID());
        files.put(storageKey, content, type.mediaType());
        Document document;
        try {
            // Flush now so a constraint violation surfaces here, while the stored file can still be removed
            document = documents.saveAndFlush(
                Document.uploaded(businessId, cleanFilename(originalFilename, type), type, content.length, sha256, storageKey));
        } catch (RuntimeException e) {
            files.delete(storageKey);
            throw e;
        }

        events.publishEvent(new DocumentUploaded(document.getId(), businessId, type.mediaType()));
        log.info("Document {} uploaded to business {} by user {}", document.getId(), businessId, user.id());
        return DocumentView.of(document);
    }

    public Page<DocumentView> documentsOf(CurrentUser user, UUID businessId, Pageable pageable) {
        businesses.checkAccess(user, businessId);
        return documents.findByBusinessId(businessId, pageable).map(DocumentView::of);
    }

    public DocumentContent content(CurrentUser user, UUID businessId, UUID documentId) {
        businesses.checkAccess(user, businessId);
        Document document = documents.findByIdAndBusinessId(documentId, businessId)
            .orElseThrow(() -> new NotFoundException("Document not found."));
        return new DocumentContent(DocumentView.of(document), files.open(document.getStorageKey()));
    }

    /** Keeps only the name the user gave the file, without any folder path, and never an empty name. */
    private static String cleanFilename(String originalFilename, FileType type) {
        String name = StringUtils.getFilename(StringUtils.cleanPath(originalFilename == null ? "" : originalFilename.strip()));
        if (!StringUtils.hasText(name)) {
            return "document." + type.extension();
        }
        // Keep the end of an overlong name, where the extension is
        return name.length() > MAX_FILENAME_LENGTH ? name.substring(name.length() - MAX_FILENAME_LENGTH) : name;
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every Java runtime provides SHA-256", e);
        }
    }
}
