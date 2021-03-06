package com.medusa.gruul.order.mq;

import cn.hutool.json.JSONUtil;
import com.medusa.gruul.account.api.constant.AccountExchangeConstant;
import com.medusa.gruul.order.api.constant.OrderConstant;
import com.medusa.gruul.order.api.constant.OrderQueueEnum;
import com.medusa.gruul.order.api.constant.OrderQueueNameConstant;
import com.medusa.gruul.payment.api.constant.PaymentExchangeConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.annotation.RabbitListenerConfigurer;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistrar;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.handler.annotation.support.DefaultMessageHandlerMethodFactory;
import org.springframework.messaging.handler.annotation.support.MessageHandlerMethodFactory;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * RabbitConfig.java
 *
 * @author alan
 * @date 2019/10/6 14:01
 */
@Slf4j
@Configuration
public class RabbitConfig implements RabbitListenerConfigurer {
    @Resource
    private RabbitTemplate rabbitTemplate;

    @Override
    public void configureRabbitListeners(RabbitListenerEndpointRegistrar registrar) {
        registrar.setMessageHandlerMethodFactory(messageHandlerMethodFactory());
    }

    @Bean
    MessageHandlerMethodFactory messageHandlerMethodFactory() {
        DefaultMessageHandlerMethodFactory messageHandlerMethodFactory = new DefaultMessageHandlerMethodFactory();
        messageHandlerMethodFactory.setMessageConverter(consumerJackson2MessageConverter());
        return messageHandlerMethodFactory;
    }

    @Bean
    public MappingJackson2MessageConverter consumerJackson2MessageConverter() {
        return new MappingJackson2MessageConverter();
    }

    @Bean
    public AmqpTemplate amqpTemplate() {
        // ??????jackson ???????????????
        rabbitTemplate.setMessageConverter(new Jackson2JsonMessageConverter());
        rabbitTemplate.setEncoding("UTF-8");
        // ???????????????????????????????????????yml???????????? publisher-returns: true
        rabbitTemplate.setMandatory(true);
        rabbitTemplate.setReturnCallback((message, replyCode, replyText, exchange, routingKey) -> {
            log.info("?????????{} ????????????, ????????????{} ?????????{} ?????????: {}  ?????????: {}", JSONUtil.parse(message), replyCode, replyText,
                    exchange, routingKey);
        });
        // ???????????????yml???????????? publisher-confirms: true
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (ack) {
                log.info("???????????????exchange??????,id: {}", correlationData.getId());
            } else {
                log.info("???????????????exchange??????,??????: {}", cause);
            }
        });
        return rabbitTemplate;
    }

    /**
     * ???????????????????????????????????????????????????
     */
    @Bean
    DirectExchange orderDirect() {
        return (DirectExchange) ExchangeBuilder
                .directExchange(OrderConstant.EXCHANGE_NAME)
                .durable(true)
                .build();
    }

    /**
     * ???????????????????????????????????????????????????
     */
    @Bean
    DirectExchange deliverDirect() {
        return (DirectExchange) ExchangeBuilder
                .directExchange(OrderConstant.DELIVER_EXCHANGE_NAME)
                .durable(true)
                .build();
    }

    /**
     * ?????????????????????????????????????????????
     */
    @Bean
    CustomExchange orderDelayDirect() {
        Map<String, Object> args = new HashMap<>(1);
        args.put("x-delayed-type", "direct");
        return new CustomExchange(OrderConstant.DELAY_EXCHANGE_NAME, "x-delayed-message", true, false, args);
    }

    /**
     * ???????????????????????????
     */
    @Bean
    DirectExchange paymentDirect() {
        return (DirectExchange) ExchangeBuilder
                .directExchange(PaymentExchangeConstant.PAYMENT_EXCHANGE)
                .durable(true)
                .build();
    }

    /**
     * ?????????????????????
     */
    @Bean
    DirectExchange accountDirect() {
        return (DirectExchange) ExchangeBuilder
                .directExchange(AccountExchangeConstant.ACCOUNT_EXCHANGE)
                .durable(true)
                .build();
    }

    @Bean
    public Queue orderCreateQueue() {
        return new Queue(OrderQueueNameConstant.ORDER_CREATE, true);
    }

    @Bean
    public Queue orderCreateFailQueue() {
        return new Queue(OrderQueueNameConstant.ORDER_CREATE_FAIL, true);
    }

    @Bean
    public Queue orderPayedQueue() {
        return new Queue(OrderQueueNameConstant.ORDER_PAYED, true);
    }

    @Bean
    public Queue orderReceiptQueue() {
        return new Queue(OrderQueueNameConstant.ORDER_RECEIPT, true);
    }

    @Bean
    public Queue orderAutoReceiptQueue() {
        return new Queue(OrderQueueNameConstant.ORDER_AUTO_RECEIPT, true);
    }

    @Bean
    public Queue orderAutoCompletedQueue() {
        return new Queue(OrderQueueNameConstant.ORDER_AUTO_COMPLETED, true);
    }

    @Bean
    public Queue orderCompletedQueue() {
        return new Queue(OrderQueueNameConstant.ORDER_COMPLETED, true);
    }


    @Bean
    public Queue orderCancelQueue() {
        return new Queue(OrderQueueNameConstant.ORDER_AUTO_CANCEL, true);
    }

    @Bean
    public Queue orderCloseQueue() {
        return new Queue(OrderQueueNameConstant.ORDER_CLOSE, true);
    }

    @Bean
    public Queue orderReturnQueue() {
        return new Queue(OrderQueueNameConstant.ORDER_RETURN, true);
    }


    @Bean
    public Queue orderCancelFailQueue() {
        return new Queue(OrderQueueNameConstant.ORDER_CANCEL_FAIL, true);
    }


    @Bean
    public Queue refundNotifyQueue() {
        return new Queue(OrderQueueNameConstant.REFUND_NOTIFY, true);
    }

    @Bean
    public Queue refundNotifySucceedQueue() {
        return new Queue(OrderQueueNameConstant.REFUND_NOTIFY_SUCCEED, true);
    }

    @Bean
    public Queue paymentNotifyQueue() {
        return new Queue(OrderQueueNameConstant.PAYMENT_NOTIFY, true);
    }

    @Bean
    public Queue orderDataInitQueue() {
        return new Queue(OrderQueueNameConstant.DATA_INIT, true);
    }


    /**
     * ???????????????????????????????????????
     */
    @Bean
    Binding orderCreateBinding(DirectExchange orderDirect, Queue orderCreateQueue) {
        return BindingBuilder
                .bind(orderCreateQueue)
                .to(orderDirect)
                .with(OrderQueueEnum.QUEUE_ORDER_CREATE.getRouteKey());
    }

    /**
     * ?????????????????????????????????????????????
     */
    @Bean
    Binding orderCreateFailBinding(DirectExchange orderDirect, Queue orderCreateFailQueue) {
        return BindingBuilder
                .bind(orderCreateFailQueue)
                .to(orderDirect)
                .with(OrderQueueEnum.QUEUE_ORDER_CREATE_FAIL.getRouteKey());
    }

    /**
     * ???????????????????????????????????????
     */
    @Bean
    Binding orderPayedBinding(DirectExchange orderDirect, Queue orderPayedQueue) {
        return BindingBuilder
                .bind(orderPayedQueue)
                .to(orderDirect)
                .with(OrderQueueEnum.QUEUE_ORDER_PAYED.getRouteKey());
    }

    /**
     * ???????????????????????????????????????
     */
    @Bean
    Binding orderReceiptBinding(DirectExchange orderDirect, Queue orderReceiptQueue) {
        return BindingBuilder
                .bind(orderReceiptQueue)
                .to(orderDirect)
                .with(OrderQueueEnum.QUEUE_ORDER_RECEIPT.getRouteKey());
    }

    /**
     * ???????????????????????????????????????
     */
    @Bean
    Binding orderCompletedBinding(DirectExchange orderDirect, Queue orderCompletedQueue) {
        return BindingBuilder
                .bind(orderCompletedQueue)
                .to(orderDirect)
                .with(OrderQueueEnum.QUEUE_ORDER_COMPLETED.getRouteKey());
    }

    @Bean
    Binding refundNotifySucceedBinding(DirectExchange orderDirect, Queue refundNotifySucceedQueue) {
        return BindingBuilder
                .bind(refundNotifySucceedQueue)
                .to(orderDirect)
                .with(OrderQueueEnum.QUEUE_ORDER_RETURN_SUCCEED.getRouteKey());
    }

    /**
     * ???????????????????????????????????????
     */
    @Bean
    Binding orderCloseBinding(DirectExchange orderDirect, Queue orderCloseQueue) {
        return BindingBuilder
                .bind(orderCloseQueue)
                .to(orderDirect)
                .with(OrderQueueEnum.QUEUE_ORDER_CLOSE.getRouteKey());
    }

    /**
     * ???????????????????????????????????????
     */
    @Bean
    Binding orderReturnBinding(DirectExchange orderDirect, Queue orderReturnQueue) {
        return BindingBuilder
                .bind(orderReturnQueue)
                .to(orderDirect)
                .with(OrderQueueEnum.QUEUE_ORDER_RETURN.getRouteKey());
    }

    /**
     * ?????????????????????????????????????????????
     */
    @Bean
    Binding orderAutoCompletedBinding(CustomExchange orderDelayDirect, Queue orderAutoCompletedQueue) {
        return BindingBuilder
                .bind(orderAutoCompletedQueue)
                .to(orderDelayDirect)
                .with(OrderQueueEnum.QUEUE_ORDER_AUTO_COMPLETED.getRouteKey()).noargs();
    }

    /**
     * ???????????????????????????????????????????????????
     */
    @Bean
    Binding orderAutoReceiptBinding(CustomExchange orderDelayDirect, Queue orderAutoReceiptQueue) {
        return BindingBuilder
                .bind(orderAutoReceiptQueue)
                .to(orderDelayDirect)
                .with(OrderQueueEnum.QUEUE_ORDER_AUTO_RECEIPT.getRouteKey()).noargs();
    }

    /**
     * ?????????????????????????????????????????????
     */
    @Bean
    Binding orderAutoCancelBinding(CustomExchange orderDelayDirect, Queue orderCancelQueue) {
        return BindingBuilder
                .bind(orderCancelQueue)
                .to(orderDelayDirect)
                .with(OrderQueueEnum.QUEUE_ORDER_AUTO_CANCEL.getRouteKey()).noargs();
    }

    /**
     * ?????????????????????????????????????????????
     */
    @Bean
    Binding orderCancelFailBinding(CustomExchange orderDelayDirect, Queue orderCancelFailQueue) {
        return BindingBuilder
                .bind(orderCancelFailQueue)
                .to(orderDelayDirect)
                .with(OrderQueueEnum.QUEUE_ORDER_CANCEL_FAIL.getRouteKey()).noargs();
    }


    /**
     * ???????????????????????????????????????????????????
     */
    @Bean
    Binding paymentNotifyBinding(DirectExchange paymentDirect, Queue paymentNotifyQueue) {
        return BindingBuilder
                .bind(paymentNotifyQueue)
                .to(paymentDirect)
                .with(OrderQueueEnum.QUEUE_ORDER_PAYMENT_NOTIFY.getRouteKey());
    }

    /**
     * ?????????????????????????????????????????????
     */
    @Bean
    Binding refundNotifyBinding(DirectExchange paymentDirect, Queue refundNotifyQueue) {
        return BindingBuilder
                .bind(refundNotifyQueue)
                .to(paymentDirect)
                .with(OrderQueueEnum.QUEUE_ORDER_REFUND_NOTIFY.getRouteKey());
    }


}
