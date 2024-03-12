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
package org.apache.rocketmq.example.lanzhiwang;

import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.remoting.common.RemotingHelper;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** producer demo 02 发送异步消息 */
public class AsyncProducer {

    public static void main(String[] args) throws Exception {
        // 实例化消息生产者 Producer
        DefaultMQProducer producer = new DefaultMQProducer("please_rename_unique_group_name");
        // 设置 NameServer 的地址
        producer.setNamesrvAddr("localhost:9876");
        // 启动 Producer 实例
        producer.start();
        /** 异步发送失败重试次数，异步重试不会选择其他 broker，仅在同一个 broker 上做重试，不保证消息不丢。 */
        producer.setRetryTimesWhenSendAsyncFailed(0);
        /**
         * 异步发送失败重试次数，异步重试不会选择其他 broker，仅在同一个 broker 上做重试，不保证消息不丢。 retryAnotherBrokerWhenNotStoreOK
         * 消息刷盘（主或备）超时或 slave 不可用（返回状态非 SEND_OK），是否尝试发送到其他 broker，默认false。
         */
        producer.setRetryAnotherBrokerWhenNotStoreOK(true);

        int messageCount = 100;
        // 根据消息数量实例化倒计时计算器
        final CountDownLatch countDownLatch = new CountDownLatch(messageCount);

        for (int i = 0; i < messageCount; i++) {
            final int index = i;
            // 创建消息，并指定 Topic，Tag 和消息体
            Message msg =
                    new Message(
                            "TopicTest",
                            "TagA",
                            "OrderID188",
                            "Hello world".getBytes(RemotingHelper.DEFAULT_CHARSET));
            // SendCallback 接收异步返回结果的回调
            producer.send(
                    msg,
                    new SendCallback() {
                        @Override
                        public void onSuccess(SendResult sendResult) {
                            countDownLatch.countDown();
                            System.out.printf("%-10d OK %s %n", index, sendResult.getMsgId());
                        }

                        @Override
                        public void onException(Throwable e) {
                            countDownLatch.countDown();
                            System.out.printf("%-10d Exception %s %n", index, e);
                            e.printStackTrace();
                        }
                    });
        }
        // 等待5s
        countDownLatch.await(5, TimeUnit.SECONDS);
        // 如果不再发送消息，关闭 Producer 实例。
        producer.shutdown();
    }
}
