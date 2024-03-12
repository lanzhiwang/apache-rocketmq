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
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.message.MessageQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Lite Pull Consumer Assign demo 07 */
public class LitePullConsumerAssign {

    public static volatile boolean running = true;

    public static void main(String[] args) throws Exception {
        Logger logger = LoggerFactory.getLogger(LitePullConsumerAssign.class);
        logger.info("Hello LitePullConsumerAssign");

        DefaultLitePullConsumer litePullConsumer =
                new DefaultLitePullConsumer("lite_pull_consumer_2");
        // 设置 NameServer 地址
        litePullConsumer.setNamesrvAddr(
                "192.168.132.210:30731;192.168.132.208:30731;192.168.132.209:30731");
        // 我们采用手动提交位点的方式，因此设置 AutoCommit 为 false，然后启动 consumer。
        litePullConsumer.setAutoCommit(false);
        litePullConsumer.start();

        /**
         * 与 Subscribe 模式不同的是，Assign 模式下没有自动的负载均衡机制，需要用户自行指定需要拉取的队列， 因此在例子中，先用 fetchMessageQueues
         * 获取了 Topic 下的队列，再取前面的一半队列进行拉取， 示例中还调用了 seek 方法，将第一个队列拉取的位点设置从 10 开始。
         */
        Collection<MessageQueue> mqSet = litePullConsumer.fetchMessageQueues("TopicTest");
        List<MessageQueue> list = new ArrayList<>(mqSet);
        List<MessageQueue> assignList = new ArrayList<>();
        for (int i = 0; i < list.size() / 2; i++) {
            assignList.add(list.get(i));
        }
        litePullConsumer.assign(assignList);
        try {
            litePullConsumer.seek(assignList.get(0), 10);
        } catch (MQClientException e) {
            e.printStackTrace();
            litePullConsumer.seek(assignList.get(0), 0);
        }

        try {
            /** 进入循环不停地调用 poll 方法拉取消息，拉取到消息后调用 commitSync 方法手动提交位点。 */
            while (running) {
                List<MessageExt> messageExts = litePullConsumer.poll();
                // System.out.printf("%s %n", messageExts);
                for (MessageExt msg : messageExts) {
                    System.out.printf(
                            "%s Receive New Messages: %s %n",
                            Thread.currentThread().getName(), new String(msg.getBody()));
                }
                litePullConsumer.commitSync();
            }
        } finally {
            litePullConsumer.shutdown();
        }
    }
}
