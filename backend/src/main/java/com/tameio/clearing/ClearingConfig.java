package com.tameio.clearing;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The clearing house's queues: transfers to clear, directory lookups, and returns from receiving banks. */
@Configuration(proxyBeanMethods = false)
class ClearingConfig {

    @Bean
    Declarables clearingTopology() {
        TopicExchange clearing = new TopicExchange(ClearingMessages.CLEARING_EXCHANGE);
        TopicExchange bank = new TopicExchange(ClearingMessages.BANK_EXCHANGE);
        Queue submitted = QueueBuilder.durable(ClearingMessages.SUBMITTED_QUEUE).quorum().build();
        // Lookups are answered at once or not at all: nothing to keep across a restart
        Queue vop = QueueBuilder.nonDurable(ClearingMessages.VOP_QUEUE).ttl(5_000).build();
        Queue returns = QueueBuilder.durable(ClearingMessages.RETURNS_QUEUE).quorum().build();
        return new Declarables(clearing, bank, submitted, vop, returns,
            BindingBuilder.bind(submitted).to(clearing).with("sct.submitted"),
            BindingBuilder.bind(vop).to(clearing).with("vop.request"),
            BindingBuilder.bind(returns).to(clearing).with("sct.return"));
    }
}
