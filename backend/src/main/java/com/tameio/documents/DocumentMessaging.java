package com.tameio.documents;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;

/**
 * The RabbitMQ topology for reading documents, and the document AI client.
 *
 * <pre>
 *   tameio.documents (topic) --document.uploaded-------> tameio.documents.read --> DocumentReader
 *                            --document.read-requested--^           |
 *                                                          after the last retry
 *                                                                   v
 *   tameio.documents.dlx (direct) -----------------------> tameio.documents.read.dead
 * </pre>
 *
 * Quorum queues are replicated across RabbitMQ nodes and survive a broker restart. The dead-letter
 * queue keeps every job that failed for good, so it can be inspected and replayed.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DocumentAiProperties.class)
class DocumentMessaging {

    static final String EXCHANGE = "tameio.documents";
    static final String UPLOADED_KEY = "document.uploaded";
    static final String READ_REQUESTED_KEY = "document.read-requested";
    static final String READ_QUEUE = "tameio.documents.read";
    static final String DEAD_LETTER_EXCHANGE = "tameio.documents.dlx";
    static final String DEAD_LETTER_QUEUE = "tameio.documents.read.dead";

    @Bean
    Declarables documentTopology() {
        TopicExchange exchange = new TopicExchange(EXCHANGE);
        DirectExchange deadLetters = new DirectExchange(DEAD_LETTER_EXCHANGE);
        Queue read = QueueBuilder.durable(READ_QUEUE)
            .quorum()
            .deadLetterExchange(DEAD_LETTER_EXCHANGE)
            .deadLetterRoutingKey(READ_QUEUE)
            .build();
        Queue dead = QueueBuilder.durable(DEAD_LETTER_QUEUE).quorum().build();
        return new Declarables(exchange, deadLetters, read, dead,
            BindingBuilder.bind(read).to(exchange).with(UPLOADED_KEY),
            BindingBuilder.bind(read).to(exchange).with(READ_REQUESTED_KEY),
            BindingBuilder.bind(dead).to(deadLetters).with(READ_QUEUE));
    }

    /** JSON messages, with the same Jackson settings as the REST API. */
    @Bean
    MessageConverter messageConverter(JsonMapper json) {
        return new JacksonJsonMessageConverter(json);
    }

    /**
     * Runs once a job has used all its retries: marks the document as failed so the person sees it,
     * then rejects the message, which RabbitMQ moves to the dead-letter queue.
     */
    @Bean
    MessageRecoverer documentReadRecoverer(DocumentReader reader, JsonMapper json) {
        var reject = new RejectAndDontRequeueRecoverer();
        return (message, cause) -> {
            if (READ_QUEUE.equals(message.getMessageProperties().getConsumerQueue())) {
                DocumentReader.ReadJob job = json.readValue(message.getBody(), DocumentReader.ReadJob.class);
                reader.giveUp(job.documentId(), cause);
            }
            reject.recover(message, cause);
        };
    }

    @Bean
    DocumentAi documentAi(DocumentAiProperties properties, JsonMapper json) {
        var client = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
        var requests = new JdkClientHttpRequestFactory(client);
        // OCR of a multi-page scan takes a while; the timeout still bounds how long a consumer waits
        requests.setReadTimeout(properties.readTimeout());
        return new HttpDocumentAi(RestClient.builder().baseUrl(properties.url()).requestFactory(requests), json);
    }
}
