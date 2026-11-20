package com.tameio.documents;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Where document AI runs and how long to wait for it.
 *
 * @param url            base URL of the service
 * @param connectTimeout how long to wait for a connection
 * @param readTimeout    how long to wait for an answer; OCR of a long scan takes tens of seconds
 */
@Validated
@ConfigurationProperties("tameio.document-ai")
record DocumentAiProperties(@NotBlank String url, @NotNull Duration connectTimeout, @NotNull Duration readTimeout) {
}
