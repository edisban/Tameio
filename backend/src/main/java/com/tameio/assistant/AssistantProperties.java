package com.tameio.assistant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Where the assistant service runs and how long to wait for it.
 *
 * @param url            base URL of the service
 * @param connectTimeout how long to wait for a connection
 * @param readTimeout    how long to wait for an answer; a few calls to Claude and the tools take seconds
 */
@Validated
@ConfigurationProperties("tameio.assistant")
record AssistantProperties(@NotBlank String url, @NotNull Duration connectTimeout, @NotNull Duration readTimeout) {
}
