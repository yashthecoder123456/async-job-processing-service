package com.example.asyncjobs.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitConfig {

    public static final String EXECUTION_EXCHANGE = "job.execution.exchange";
    public static final String EXECUTION_ROUTING_KEY = "job.execution";

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public DirectExchange executionExchange() {
        return new DirectExchange(EXECUTION_EXCHANGE, true, false);
    }

    @Bean
    public Queue executionDlq(AppProperties appProperties) {
        return QueueBuilder.durable(appProperties.rabbitmq().executionDlq()).build();
    }

    @Bean
    public Queue executionQueue(AppProperties appProperties) {
        Map<String, Object> args = new HashMap<>();
        args.put("x-max-priority", appProperties.rabbitmq().maxPriority());
        args.put("x-dead-letter-exchange", "");
        args.put("x-dead-letter-routing-key", appProperties.rabbitmq().executionDlq());
        return QueueBuilder.durable(appProperties.rabbitmq().executionQueue())
                .withArguments(args)
                .build();
    }

    @Bean
    public Binding executionBinding(Queue executionQueue, DirectExchange executionExchange) {
        return BindingBuilder.bind(executionQueue).to(executionExchange).with(EXECUTION_ROUTING_KEY);
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter jsonMessageConverter,
            AppProperties appProperties) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        factory.setAcknowledgeMode(org.springframework.amqp.core.AcknowledgeMode.MANUAL);
        factory.setPrefetchCount(1);
        int concurrency = Math.max(1, appProperties.worker().concurrency());
        factory.setConcurrentConsumers(concurrency);
        factory.setMaxConcurrentConsumers(concurrency);
        return factory;
    }
}
