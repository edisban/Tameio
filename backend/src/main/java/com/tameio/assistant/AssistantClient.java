package com.tameio.assistant;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Calls {@code POST /v1/chat} of the assistant service over HTTP. */
@Component
@EnableConfigurationProperties(AssistantProperties.class)
class AssistantClient {

    /** One message of the conversation, as the service takes it. */
    record Message(String role, String content) {
    }

    /** The assistant's answer, which tools it used and which documents it drew on. */
    record Reply(String reply, List<String> toolsUsed, List<Map<String, Object>> sources) {
    }

    private record Request(UUID businessId, List<Message> messages) {
    }

    /** The service could not answer: not running, no Claude API key, or an error on its side. */
    static class Unavailable extends RuntimeException {
        Unavailable(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private final RestClient http;

    AssistantClient(AssistantProperties properties) {
        var client = HttpClient.newBuilder()
            .connectTimeout(properties.connectTimeout())
            // uvicorn refuses the JDK client's default upgrade to HTTP/2 over plain HTTP
            .version(HttpClient.Version.HTTP_1_1)
            .build();
        var requests = new JdkClientHttpRequestFactory(client);
        requests.setReadTimeout(properties.readTimeout());
        this.http = RestClient.builder().baseUrl(properties.url()).requestFactory(requests).build();
    }

    Reply ask(UUID businessId, List<Message> messages) {
        try {
            Reply reply = http.post().uri("/v1/chat")
                .body(new Request(businessId, messages))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    throw new Unavailable("The assistant answered " + response.getStatusCode().value(), null);
                })
                .body(Reply.class);
            if (reply == null) {
                throw new Unavailable("The assistant sent an empty answer", null);
            }
            return reply;
        } catch (Unavailable e) {
            throw e;
        } catch (RestClientException e) {
            throw new Unavailable("The assistant could not be reached: " + e.getMessage(), e);
        }
    }
}
