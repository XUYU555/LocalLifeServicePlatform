package com.hmdp.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;

/**
 * @author xy
 * @date 2024-11-12 17:11
 */
@Configuration
public class ErrorMessageConfig {

    @Resource
    RabbitTemplate rabbitTemplate;

    private final static String ERROR_QUEUE = "error.queue";
    private final static String ERROR_EXCHANGE = "error.direct";
    private final static String ROUTING_KEY = "error";

    @Bean
    public Queue errorQueue() {
        return QueueBuilder.durable(ERROR_QUEUE).lazy().build();
    }

    @Bean
    public DirectExchange errorExchange() {
        return new DirectExchange(ERROR_EXCHANGE);
    }

    @Bean
    public Binding errorBinding(Queue errorQueue, DirectExchange errorExchange) {
        return BindingBuilder.bind(errorQueue).to(errorExchange).with(ROUTING_KEY);
    }

    @Bean
    // 修改MessageRecover重试失败后，将消息发送到指定交换机
    public MessageRecoverer republishMessageRecoverer() {
        return new RepublishMessageRecoverer(rabbitTemplate, ERROR_EXCHANGE, ROUTING_KEY);
    }
}
