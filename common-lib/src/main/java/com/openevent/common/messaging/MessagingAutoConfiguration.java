package com.openevent.common.messaging;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.DefaultJacksonJavaTypeMapper;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

/** Kích hoạt ở service có spring-boot-starter-amqp: exchange chung và JSON cho message. */
@AutoConfiguration(beforeName = "org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration")
@ConditionalOnClass(TopicExchange.class)
public class MessagingAutoConfiguration {

    @Bean
    TopicExchange openEventExchange() {
        return new TopicExchange(Messaging.EXCHANGE);
    }

    @Bean
    MessageConverter openEventMessageConverter() {
        DefaultJacksonJavaTypeMapper typeMapper = new DefaultJacksonJavaTypeMapper();
        typeMapper.setTrustedPackages("com.openevent.common.messaging.events");
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }
}
