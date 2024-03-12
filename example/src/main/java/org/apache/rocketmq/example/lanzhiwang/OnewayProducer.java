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
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.remoting.common.RemotingHelper;

/** producer demo 04 单向发送消息 */
public class OnewayProducer {

    public static void main(String[] args) throws Exception {
        // 实例化消息生产者 Producer
        DefaultMQProducer producer = new DefaultMQProducer("please_rename_unique_group_name");
        // 设置 NameServer 的地址
        producer.setNamesrvAddr("localhost:9876");
        // 启动 Producer 实例
        producer.start();
        for (int i = 0; i < 100; i++) {
            // 创建消息，并指定 Topic，Tag 和消息体
            Message msg =
                    new Message(
                            "TopicTest" /* Topic */,
                            "TagA" /* Tag */,
                            ("Hello RocketMQ " + i)
                                    .getBytes(RemotingHelper.DEFAULT_CHARSET) /* Message body */);
            // 发送单向消息，没有任何返回结果
            producer.sendOneway(msg);
        }
        // 如果不再发送消息，关闭 Producer 实例。
        producer.shutdown();
    }
}
