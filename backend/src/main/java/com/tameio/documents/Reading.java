package com.tameio.documents;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * What document AI read from a document: the fields with how sure it was of each, the suggested expense
 * category and the full text. A field it did not find is null.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class Reading {

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "issuer_afm", length = 9)
    private String issuerAfm;

    @Column(name = "issuer_afm_confidence")
    private Double issuerAfmConfidence;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "issue_date_confidence")
    private Double issueDateConfidence;

    @Column(name = "net_amount", precision = 14, scale = 2)
    private BigDecimal netAmount;

    @Column(name = "net_amount_confidence")
    private Double netAmountConfidence;

    @Column(name = "vat_amount", precision = 14, scale = 2)
    private BigDecimal vatAmount;

    @Column(name = "vat_amount_confidence")
    private Double vatAmountConfidence;

    @Column(name = "total_amount", precision = 14, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "total_amount_confidence")
    private Double totalAmountConfidence;

    @Column(name = "suggested_category", length = 40)
    private String suggestedCategory;

    @Column(name = "suggested_category_confidence")
    private Double suggestedCategoryConfidence;

    @Column(name = "model_version", length = 100)
    private String modelVersion;

    @Column(name = "page_count")
    private Integer pageCount;

    @Column(name = "ocr_method", length = 20)
    private String ocrMethod;

    @Column(name = "ocr_text", columnDefinition = "text")
    private String text;

    static Reading of(DocumentAi.Result result, Instant readAt) {
        var reading = new Reading();
        reading.readAt = readAt;
        DocumentAi.Fields fields = result.fields();
        if (fields.issuerAfm() != null) {
            reading.issuerAfm = fields.issuerAfm().value();
            reading.issuerAfmConfidence = fields.issuerAfm().confidence();
        }
        if (fields.issueDate() != null) {
            reading.issueDate = fields.issueDate().value();
            reading.issueDateConfidence = fields.issueDate().confidence();
        }
        if (fields.netAmount() != null) {
            reading.netAmount = new BigDecimal(fields.netAmount().value());
            reading.netAmountConfidence = fields.netAmount().confidence();
        }
        if (fields.vatAmount() != null) {
            reading.vatAmount = new BigDecimal(fields.vatAmount().value());
            reading.vatAmountConfidence = fields.vatAmount().confidence();
        }
        if (fields.totalAmount() != null) {
            reading.totalAmount = new BigDecimal(fields.totalAmount().value());
            reading.totalAmountConfidence = fields.totalAmount().confidence();
        }
        reading.suggestedCategory = result.category().best().category();
        reading.suggestedCategoryConfidence = result.category().best().confidence();
        reading.modelVersion = result.category().modelVersion();
        reading.pageCount = result.pages();
        reading.ocrMethod = result.method();
        reading.text = result.text();
        return reading;
    }
}
