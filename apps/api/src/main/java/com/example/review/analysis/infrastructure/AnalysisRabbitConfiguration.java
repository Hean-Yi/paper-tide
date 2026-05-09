package com.example.review.analysis.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "review.analysis.broker-enabled", havingValue = "true")
public class AnalysisRabbitConfiguration {
    @Bean
    AmqpAdmin analysisAmqpAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }

    @Bean
    MessageConverter analysisRabbitMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    TopicExchange analysisExchange(@Value("${review.analysis.broker-exchange:review.analysis.exchange}") String exchange) {
        return new TopicExchange(exchange, true, false);
    }

    @Bean
    Queue analysisRequestedQueue(@Value("${review.analysis.request-queue:analysis.requested.agent}") String queue) {
        return new Queue(queue, true);
    }

    @Bean
    Binding analysisRequestedBinding(
            Queue analysisRequestedQueue,
            TopicExchange analysisExchange,
            @Value("${review.analysis.request-routing-key:analysis.requested}") String routingKey
    ) {
        return BindingBuilder.bind(analysisRequestedQueue).to(analysisExchange).with(routingKey);
    }

    @Bean
    Queue analysisCompletedQueue(@Value("${review.analysis.completion-queue:analysis.completed.api}") String queue) {
        return new Queue(queue, true);
    }

    @Bean
    Binding analysisCompletedBinding(
            Queue analysisCompletedQueue,
            TopicExchange analysisExchange,
            @Value("${review.analysis.completion-routing-key:analysis.completed}") String routingKey
    ) {
        return BindingBuilder.bind(analysisCompletedQueue).to(analysisExchange).with(routingKey);
    }
}
