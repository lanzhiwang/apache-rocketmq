/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.rocketmq.example.simple;

import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.remoting.common.RemotingHelper;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** producer demo 01 同步发送 */
public class Producer {
    public static void main(String[] args) throws MQClientException, InterruptedException {

        Logger logger = LoggerFactory.getLogger(Producer.class);
        logger.info("Hello SyncProducer");

        /**
         * 首先会创建一个 producer。普通消息可以创建 DefaultMQProducer，创建时需要填写生产组的名称，生产者组是指同一类 Producer 的集合，这类
         * Producer 发送同一类消息且发送逻辑一致。
         */
        DefaultMQProducer producer = new DefaultMQProducer("ProducerGroupName");
        /**
         * 设置 NameServer 的地址。Apache RocketMQ 很多方式设置 NameServer 地址(客户端配置中有介绍)，这里是在代码中调用 producer 的
         * API setNamesrvAddr 进行设置，如果有多个 NameServer，中间以分号隔开，比如 "127.0.0.2:9876;127.0.0.3:9876"。
         */
        producer.setNamesrvAddr(
                "192.168.132.210:31580;192.168.132.208:31580;192.168.132.209:31580");
        /**
         * 同步发送失败重投次数，默认为 2，因此生产者会最多尝试发送 retryTimesWhenSendFailed + 1 次。 不会选择上次失败的 broker，尝试向其他
         * broker 发送，最大程度保证消息不丢。 超过重投次数，抛出异常，由客户端保证消息不丢。 当出现 RemotingException、MQClientException 和部分
         * MQBrokerException 时会重投。
         */
        producer.setRetryTimesWhenSendFailed(4);

        // 启动 producer
        producer.start();

        for (int i = 0; i < 200; i++) {
            String body = "Hello RocketMQ " + i;
            logger.info(body);
            try {
                /**
                 * 第三步是构建消息。指定 topic、tag、body 等信息，tag 可以理解成标签，对消息进行再归类，RocketMQ 可以在消费端对 tag 进行过滤。
                 */
                Message msg =
                        new Message(
                                "TopicTest",
                                "TagA",
                                "TestKey " + i,
                                body.getBytes(RemotingHelper.DEFAULT_CHARSET));

                /**
                 * 最后调用 send 接口将消息发送出去。同步发送等待结果最后返回 SendResult，SendResut 包含实际发送状态还包括 SEND_OK（发送成功）,
                 * FLUSH_DISK_TIMEOUT（刷盘超时）, FLUSH_SLAVE_TIMEOUT（同步到备超时）, SLAVE_NOT_AVAILABLE（备不可用），
                 * 如果发送失败会抛出异常。
                 */
                SendResult sendResult = producer.send(msg);
                System.out.printf("%s%n", sendResult);
                Thread.currentThread().sleep(1000);

                // } catch (RemotingCommandException e) {
                //     logger.info(
                //             "RemotingCommandException RemotingCommandException
                // RemotingCommandException");
                //     e.printStackTrace();
                // } catch (RemotingConnectException e) {
                //     logger.info(
                //             "RemotingConnectException RemotingConnectException
                // RemotingConnectException");
                //     e.printStackTrace();
                // } catch (RemotingSendRequestException e) {
                //     logger.info(
                //             "RemotingSendRequestException RemotingSendRequestException
                // RemotingSendRequestException");
                //     e.printStackTrace();
                // } catch (RemotingTimeoutException e) {
                //     logger.info(
                //             "RemotingTimeoutException RemotingTimeoutException
                // RemotingTimeoutException");
                //     e.printStackTrace();
                // } catch (RemotingTooMuchRequestException e) {
                //     logger.info(
                //             "RemotingTooMuchRequestException RemotingTooMuchRequestException
                // RemotingTooMuchRequestException");
                //     e.printStackTrace();
            } catch (RemotingException e) {
                logger.info(
                        "RemotingException RemotingException RemotingException RemotingException");
                logger.info(body + " retry");
                e.printStackTrace();
            } catch (MQClientException e) {
                logger.info(
                        "MQClientException MQClientException MQClientException MQClientException");
                logger.info(body + " retry");
                e.printStackTrace();
            } catch (Exception e) {
                logger.info("Exception Exception Exception Exception Exception Exception");
                logger.info(body + " retry");
                e.printStackTrace();
            }
        }

        // 一旦 producer 不再使用，关闭 producer
        producer.shutdown();
    }
}
