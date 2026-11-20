package com.tameio.documents;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/** Calls the {@code POST /v1/read} endpoint of document AI over HTTP. */
class HttpDocumentAi implements DocumentAi {

    private static final int UNPROCESSABLE_CONTENT = 422;

    private final RestClient http;
    private final JsonMapper json;

    HttpDocumentAi(RestClient.Builder builder, JsonMapper json) {
        this.http = builder.build();
        this.json = json;
    }

    /** The part of an RFC 9457 problem this client uses. */
    private record Problem(String detail) {
    }

    @Override
    public Result read(byte[] content, String mediaType) {
        try {
            Result result = http.post().uri("/v1/read")
                .contentType(MediaType.parseMediaType(mediaType))
                .body(content)
                .retrieve()
                // 4xx: the service looked at the file and said no, which will not change on a retry
                .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                    String detail = detailOf(response.getBody().readAllBytes());
                    ReadFailure failure = response.getStatusCode().value() == UNPROCESSABLE_CONTENT
                        ? ReadFailure.UNREADABLE : ReadFailure.REJECTED;
                    throw new Unreadable(failure, detail);
                })
                .onStatus(HttpStatusCode::is5xxServerError, (request, response) -> {
                    throw new Unavailable("Document AI answered " + response.getStatusCode().value(), null);
                })
                .body(Result.class);
            if (result == null) {
                throw new Unavailable("Document AI sent an empty answer", null);
            }
            return result;
        } catch (Unreadable | Unavailable e) {
            throw e;
        } catch (ResourceAccessException e) {
            throw new Unavailable("Document AI could not be reached: " + e.getMessage(), e);
        } catch (RestClientException e) {
            throw new Unavailable("Document AI sent an answer that could not be read: " + e.getMessage(), e);
        }
    }

    private String detailOf(byte[] body) {
        try {
            Problem problem = json.readValue(body, Problem.class);
            if (problem.detail() != null && !problem.detail().isBlank()) {
                return problem.detail();
            }
        } catch (JacksonException e) {
            // Not a problem document; fall through to a generic detail
        }
        return "Document AI refused the file.";
    }
}
