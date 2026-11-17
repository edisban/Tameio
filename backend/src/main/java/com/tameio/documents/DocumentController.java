package com.tameio.documents;

import com.tameio.platform.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Tag(name = "Documents")
@RestController
@RequestMapping("/api/v1/businesses/{businessId}/documents")
@PreAuthorize("hasRole('BUSINESS')")
@RequiredArgsConstructor
class DocumentController {

    private final DocumentService service;

    @Operation(summary = "Upload a receipt, invoice or statement (PDF, JPEG or PNG, up to 10 MB)")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<DocumentView> upload(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID businessId,
                                        @RequestPart("file") MultipartFile file) throws IOException {
        // The multipart size limit in application.yml bounds how much this reads into memory
        DocumentView created = service.upload(CurrentUser.from(jwt), businessId, file.getOriginalFilename(), file.getBytes());
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}/content").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @Operation(summary = "The business's documents, newest first")
    @GetMapping
    Page<DocumentView> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID businessId,
                            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.documentsOf(CurrentUser.from(jwt), businessId, pageable);
    }

    @Operation(summary = "The document's file, streamed from storage")
    @GetMapping("/{documentId}/content")
    ResponseEntity<InputStreamResource> content(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID businessId,
                                                @PathVariable UUID documentId) {
        DocumentContent content = service.content(CurrentUser.from(jwt), businessId, documentId);
        DocumentView document = content.document();
        // inline: the browser shows PDFs and images itself; the UTF-8 name keeps Greek file names intact
        var disposition = ContentDisposition.inline().filename(document.filename(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(document.mediaType()))
            .contentLength(document.sizeBytes())
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
            .body(new InputStreamResource(content.stream()));
    }
}
