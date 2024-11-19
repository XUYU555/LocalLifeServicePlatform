package com.hmdp.listener;

import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.impl.VoucherOrderServiceImpl;
import org.springframework.amqp.rabbit.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static com.hmdp.utils.RabbitMQConstants.*;

/**
 * @author xy
 * @date 2024-11-11 17:13
 */

@Component
public class SeckillOrderListener {

    @Autowired
    VoucherOrderServiceImpl voucherOrderService;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = SECKILL_ORDER_QUEUE, durable = "true",
                    arguments = @Argument(name = "x-queue-mode", value = "lazy")),
            exchange = @Exchange(name = SECKILL_DIRECT_EXCHANGE),
            key = SECKILL_KEY
    ))
    public void seckillOrderListener(VoucherOrder voucherOrder) {
        voucherOrderService.handlerVoucherOrder(voucherOrder);
    }

}
