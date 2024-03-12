/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.rocketmq.example.simple;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
// import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/** PushConsumer demo 05 */
public class PushConsumer {

    public static void main(String[] args) throws InterruptedException, MQClientException {

        Logger logger = LoggerFactory.getLogger(PushConsumer.class);
        logger.info("Hello PushConsumer");

        // 初始化 consumer，并设置 consumer group name
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer("my_group_10");
        // 设置 NameServer 地址
        consumer.setNamesrvAddr(
                "192.168.132.210:31580;192.168.132.208:31580;192.168.132.209:31580");
        // 订阅一个或多个 topic，并指定 tag 过滤条件，这里指定 * 表示接收所有 tag 的消息
        consumer.subscribe("TopicTest", "*");
        /**
         * Consuming point on consumer booting.
         *
         * <p>There are three consuming points:
         *
         * <p>1. CONSUME_FROM_LAST_OFFSET consumer clients pick up where it stopped previously. If
         * it were a newly booting up consumer client, according aging of the consumer group, there
         * are two cases: if the consumer group is created so recently that the earliest message
         * being subscribed has yet expired, which means the consumer group represents a lately
         * launched business, consuming will start from the very beginning;
         * 从最新的偏移量开始消费
         *
         * <p>if the earliest message being subscribed has expired, consuming will start from the
         * latest messages, meaning messages born prior to the booting timestamp would be ignored.
         *
         * <p>2. CONSUME_FROM_FIRST_OFFSET Consumer client will start from earliest messages
         * available.
         * 从头开始消费
         *
         * <p>3. CONSUME_FROM_TIMESTAMP Consumer client will start from specified timestamp, which
         * means messages born prior to {@link #consumeTimestamp} will be ignored
         * 从 Consumer 启动时间戳对应的消费进度开始消费
         *
         */
        // consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
        // consumer.setConsumeTimestamp("20181109221800");

        /** Minimum consumer thread number 消费者本地线程数默认是 20，设置为 1 方便调试 */
        consumer.setConsumeThreadMin(1);
        /** Max consumer thread number */
        consumer.setConsumeThreadMax(1);
        consumer.setPullBatchSize(1);

        // 注册回调接口来处理从 Broker 中收到的消息
        consumer.registerMessageListener(
                new MessageListenerConcurrently() {

                    @Override
                    public ConsumeConcurrentlyStatus consumeMessage(
                            List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
                        System.out.printf("message count: %d\n", msgs.size());
                        try {
                            for (MessageExt msg : msgs) {
                                System.out.printf(
                                        "%s Receive New Messages: %s %n",
                                        Thread.currentThread().getName(),
                                        new String(msg.getBody()));
                                Thread.currentThread().sleep(1000);
                            }

                        } catch (Exception e) {
                            logger.info("consumeMessage");
                            e.printStackTrace();
                        }

                        // 返回消息消费状态，ConsumeConcurrentlyStatus.CONSUME_SUCCESS 为消费成功
                        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
                    }
                });
        // 启动 Consumer
        try {
            consumer.start();

        } catch (Exception e) {
            logger.info("start");
            e.printStackTrace();
        }

        System.out.printf("Consumer Started.%n");
    }
}
