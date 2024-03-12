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

import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.remoting.common.RemotingHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** producer demo 03 发送异步消息 */
public class AsyncProducer {
    public static void main(String[] args)
            throws MQClientException, InterruptedException, UnsupportedEncodingException {

        Logger logger = LoggerFactory.getLogger(AsyncProducer.class);
        logger.info("Hello AsyncProducer");

        DefaultMQProducer producer = new DefaultMQProducer("ProducerGroupName");
        /**
         * 设置 NameServer 的地址。Apache RocketMQ 很多方式设置 NameServer 地址(客户端配置中有介绍)，这里是在代码中调用 producer 的
         * API setNamesrvAddr 进行设置，如果有多个 NameServer，中间以分号隔开，比如 "127.0.0.2:9876;127.0.0.3:9876"。
         */
        producer.setNamesrvAddr(
                "192.168.132.210:31580;192.168.132.208:31580;192.168.132.209:31580");
        /**
         * Maximum number of retry to perform internally before claiming sending failure in
         * asynchronous mode. This may potentially cause message duplication which is up to
         * application developers to resolve.
         */
        producer.setRetryTimesWhenSendAsyncFailed(2);
        /** Indicate whether to retry another broker on sending failure internally. */
        producer.setRetryAnotherBrokerWhenNotStoreOK(true);
        producer.start();

        int messageCount = 100;
        final CountDownLatch countDownLatch = new CountDownLatch(messageCount);
        for (int i = 0; i < messageCount; i++) {
            try {
                final int index = i;
                String body = "Hello RocketMQ " + i;
                logger.info(body);
                Message msg =
                        new Message(
                                "TopicTest",
                                "TagA",
                                "TestKey " + i,
                                body.getBytes(RemotingHelper.DEFAULT_CHARSET));
                producer.send(
                        msg,
                        new SendCallback() {
                            @Override
                            public void onSuccess(SendResult sendResult) {
                                countDownLatch.countDown();
                                System.out.printf("%s %s\n", body, "onSuccess");
                                System.out.printf("%s%n\n", sendResult);
                                System.out.printf("%-10d OK %s %n\n", index, sendResult.getMsgId());
                            }

                            @Override
                            public void onException(Throwable e) {
                                countDownLatch.countDown();
                                System.out.printf("%s %s\n", body, "onException");
                                System.out.printf("%n\n", e);
                                System.out.printf("%-10d Exception %s %n\n", index, e);
                                // e.printStackTrace();
                            }
                        });
                Thread.currentThread().sleep(1000);
            } catch (Exception e) {
                logger.info("生产者本身的逻辑异常");
                e.printStackTrace();
            }
        }
        countDownLatch.await(5, TimeUnit.SECONDS);
        producer.shutdown();
    }
}
