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

import org.apache.rocketmq.client.consumer.DefaultLitePullConsumer;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.message.MessageQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Lite Pull Consumer Subscribe demo 06 */
public class LitePullConsumerSubscribe {

    public static volatile boolean running = true;

    public static void main(String[] args) throws Exception {
        Logger logger = LoggerFactory.getLogger(LitePullConsumerSubscribe.class);
        logger.info("Hello LitePullConsumerSubscribe");

        // 初始化 DefaultLitePullConsumer 并设置 ConsumerGroupName
        DefaultLitePullConsumer litePullConsumer =
                new DefaultLitePullConsumer("lite_pull_consumer_4");
        // 设置 NameServer 地址
        litePullConsumer.setNamesrvAddr(
                "192.168.132.210:31580;192.168.132.208:31580;192.168.132.209:31580");
        // 调用 subscribe 方法订阅 topic 并启动
        litePullConsumer.subscribe("TopicTest", "*");
        // 通过 setPullBatchSize 可以设置每一次拉取的最大消息数量
        litePullConsumer.setPullBatchSize(1);
        // // 我们采用手动提交位点的方式，因此设置 AutoCommit 为 false，然后启动 consumer。
        litePullConsumer.setAutoCommit(false);
        litePullConsumer.start();

        Collection<MessageQueue> queues = litePullConsumer.fetchMessageQueues("TopicTest");

        Map<Integer, MessageQueue> mapQueue = new HashMap<Integer, MessageQueue>();
        Map<Integer, Long> offsetMap = new HashMap<Integer, Long>();

        for (MessageQueue queue : queues) {
            System.out.printf("QueueId: %d\n", queue.getQueueId());
            // QueueId: 0
            mapQueue.put(queue.getQueueId(), queue);
            offsetMap.put(queue.getQueueId(), 0L);
        }

        try {
            while (running) {
                /**
                 * 与 Push Consumer 不同的是，LitePullConsumer 拉取消息调用的是轮询 poll 接口， 如果能拉取到消息则返回对应的消息列表，否则返回
                 * null。 如果不额外设置，LitePullConsumer 默认是自动提交位点。 在 subscribe 模式下，同一个消费组下的多个
                 * LitePullConsumer 会负载均衡消费，与P ushConsumer 一致。
                 */
                try {
                    List<MessageExt> messageExts = litePullConsumer.poll();
                    System.out.printf("message count: %d\n", messageExts.size());
                    // message count: 1

                    for (MessageExt msg : messageExts) {
                        System.out.printf(
                                "%s Receive New Messages: %s | QueueId: %d | MsgId: %s |"
                                        + " QueueOffset: %d\n",
                                Thread.currentThread().getName(),
                                new String(msg.getBody()),
                                msg.getQueueId(),
                                msg.getMsgId(),
                                msg.getQueueOffset());
                        offsetMap.put(msg.getQueueId(), msg.getQueueOffset());
                        /**
                         * 手动提交位点
                         */
                        litePullConsumer.commitSync();
                        for (Map.Entry<Integer, Long> entry : offsetMap.entrySet()) {
                            Integer queueId = entry.getKey();
                            Long offset = entry.getValue();
                            System.out.printf("QueueId: %d | QueueOffset: %d\n", queueId, offset);
                        }
                    }

                } catch (Exception e) {
                    logger.info("litePullConsumer");
                    e.printStackTrace();
                    for (Map.Entry<Integer, Long> entry : offsetMap.entrySet()) {
                        Integer queueId = entry.getKey();
                        Long offset = entry.getValue();
                        System.out.printf("QueueId: %d | QueueOffset: %d\n", queueId, offset);
                        if (offset - 1 < 0) {
                            offset = 0L;
                        } else {
                            offset = offset - 1;
                        }
                        litePullConsumer.seek(mapQueue.get(queueId), offset);
                    }
                }
                Thread.currentThread().sleep(1000);
            }
        } finally {
            litePullConsumer.shutdown();
        }
    }
}
