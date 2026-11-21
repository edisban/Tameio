package com.tameio.banking;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The bank's side of the clearing house protocol (ADR 0010). The bank publishes to the clearing
 * house's exchange and consumes from its own; the clearing house does the reverse. Each side declares
 * what it uses, so neither depends on the other being started first.
 *
 * <pre>
 *   tameio.clearing  --sct.submitted-->  (clearing house)
 *                    --vop.request--->   (clearing house, replies directly)
 *   tameio.banking   --sct.report---->   tameio.banking.sct-reports   --> PaymentEvents
 *                    --sct.incoming-->   tameio.banking.sct-incoming  --> PaymentEvents
 * </pre>
 */
@Configuration(proxyBeanMethods = false)
class BankingMessaging {

    static final String CLEARING_EXCHANGE = "tameio.clearing";
    static final String SUBMITTED_KEY = "sct.submitted";
    static final String VOP_KEY = "vop.request";

    static final String BANKING_EXCHANGE = "tameio.banking";
    static final String REPORTS_QUEUE = "tameio.banking.sct-reports";
    static final String INCOMING_QUEUE = "tameio.banking.sct-incoming";
    static final String DEAD_LETTERS = "tameio.banking.dlx";
    static final String DEAD_QUEUE = "tameio.banking.dead";

    @Bean
    Declarables bankingTopology() {
        TopicExchange clearing = new TopicExchange(CLEARING_EXCHANGE);
        TopicExchange banking = new TopicExchange(BANKING_EXCHANGE);
        DirectExchange deadLetters = new DirectExchange(DEAD_LETTERS);
        Queue reports = QueueBuilder.durable(REPORTS_QUEUE).quorum().deadLetterExchange(DEAD_LETTERS).deadLetterRoutingKey(DEAD_QUEUE).build();
        Queue incoming = QueueBuilder.durable(INCOMING_QUEUE).quorum().deadLetterExchange(DEAD_LETTERS).deadLetterRoutingKey(DEAD_QUEUE).build();
        Queue dead = QueueBuilder.durable(DEAD_QUEUE).quorum().build();
        return new Declarables(clearing, banking, deadLetters, reports, incoming, dead,
            BindingBuilder.bind(reports).to(banking).with("sct.report"),
            BindingBuilder.bind(incoming).to(banking).with("sct.incoming"),
            BindingBuilder.bind(dead).to(deadLetters).with(DEAD_QUEUE));
    }
}
