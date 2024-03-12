#  最佳实践

## 1 生产者

### 1.1 发送消息注意事项

#### 1 Tags 的使用

一个应用尽可能用一个 Topic，而消息子类型则可以用 tags 来标识。tags 可以由应用自由设置，只有生产者在发送消息设置了 tags，消费方在订阅消息时才可以利用 tags 通过 broker 做消息过滤：`message.setTags("TagA")`。

#### 2 Keys 的使用

每个消息在业务层面的唯一标识码要设置到 keys 字段，方便将来定位消息丢失问题。服务器会为每个消息创建索引（哈希索引），应用可以通过 topic、key 来查询这条消息内容，以及消息被谁消费。由于是哈希索引，请务必保证 key 尽可能唯一，这样可以避免潜在的哈希冲突。

```java
// 订单 Id
String orderId = "20034568923546";
message.setKeys(orderId);
```

#### 3 日志的打印

​消息发送成功或者失败要打印消息日志，务必要打印 SendResult 和 key 字段。send 消息方法只要不抛异常，就代表发送成功。发送成功会有多个状态，在 sendResult 里定义。以下对每个状态进行说明：

- SEND_OK
  消息发送成功。要注意的是消息发送成功也不意味着它是可靠的。要确保不会丢失任何消息，还应启用同步 Master 服务器或同步刷盘，即 SYNC_MASTER 或 SYNC_FLUSH。

- FLUSH_DISK_TIMEOUT
  消息发送成功但是服务器刷盘超时。此时消息已经进入服务器队列（内存），只有服务器宕机，消息才会丢失。
  消息存储配置参数中可以设置刷盘方式和同步刷盘时间长度，如果 Broker 服务器设置了刷盘方式为同步刷盘，即 FlushDiskType=SYNC_FLUSH（默认为异步刷盘方式），当 Broker 服务器未在同步刷盘时间内（默认为 5s）完成刷盘，则将返回该状态——刷盘超时。

- FLUSH_SLAVE_TIMEOUT
  消息发送成功，但是服务器同步到 Slave 时超时。此时消息已经进入服务器队列，只有服务器宕机，消息才会丢失。如果 Broker 服务器的角色是同步Master，即 SYNC_MASTER（默认是异步 Master 即 ASYNC_MASTER），并且从 Broke r服务器未在同步刷盘时间（默认为 5 秒）内完成与主服务器的同步，则将返回该状态——数据同步到 Slave 服务器超时。

- SLAVE_NOT_AVAILABLE
  消息发送成功，但是此时 Slave 不可用。如果 Broker 服务器的角色是同步 Master，即 SYNC_MASTER（默认是异步 Master 服务器即 ASYNC_MASTER），但没有配置 slave Broker 服务器，则将返回该状态——无 Slave 服务器可用。

### 1.2 消息发送失败处理方式

Producer 的 send 方法本身支持内部重试，重试逻辑如下：

- 至多重试 2 次。
- 如果同步模式发送失败，则轮转到下一个 Broker，如果异步模式发送失败，则只会在当前 Broker 进行重试。
  这个方法的总耗时时间不超过 sendMsgTimeout 设置的值，默认 10s。
- 如果本身向 broker 发送消息产生超时异常，就不会再重试。

以上策略也是在一定程度上保证了消息可以发送成功。如果业务对消息可靠性要求比较高，建议应用增加相应的重试逻辑：比如调用 send 同步方法发送失败时，则尝试将消息存储到 db，然后由后台线程定时重试，确保消息一定到达 Broker。

上述 db 重试方式为什么没有集成到 MQ 客户端内部做，而是要求应用自己去完成，主要基于以下几点考虑：
首先，MQ 的客户端设计为无状态模式，方便任意的水平扩展，且对机器资源的消耗仅仅是 cpu、内存、网络。
其次，如果 MQ 客户端内部集成一个 KV 存储模块，那么数据只有同步落盘才能较可靠，而同步落盘本身性能开销较大，所以通常会采用异步落盘，又由于应用关闭过程不受 MQ 运维人员控制，可能经常会发生 kill -9 这样暴力方式关闭，造成数据没有及时落盘而丢失。
第三，Producer 所在机器的可靠性较低，一般为虚拟机，不适合存储重要数据。
综上，建议重试过程交由应用来控制。

### 1.3 选择 oneway 形式发送

通常消息的发送是这样一个过程：

- 客户端发送请求到服务器
- 服务器处理请求
- 服务器向客户端返回应答

所以，一次消息发送的耗时时间是上述三个步骤的总和，而某些场景要求耗时非常短，但是对可靠性要求并不高，例如日志收集类应用，此类应用可以采用 oneway 形式调用，oneway 形式只发送请求不等待应答，而发送请求在客户端实现层面仅仅是一个操作系统系统调用的开销，即将数据写入客户端的 socket 缓冲区，此过程耗时通常在微秒级。

## 2 消费者

### 2.1 消费过程幂等

RocketMQ 无法避免消息重复（Exactly-Once），所以如果业务对消费重复非常敏感，务必要在业务层面进行去重处理。可以借助关系数据库进行去重。
首先需要确定消息的唯一键，可以是 msgId，也可以是消息内容中的唯一标识字段，例如订单 Id 等。在消费之前判断唯一键是否在关系数据库中存在。如果不存在则插入，并消费，否则跳过。（实际过程要考虑原子性问题，判断是否存在可以尝试插入，如果报主键冲突，则插入失败，直接跳过）

msgId 一定是全局唯一标识符，但是实际使用中，可能会存在相同的消息有两个不同 msgId 的情况（消费者主动重发、因客户端重投机制导致的重复等），这种情况就需要使业务字段进行重复消费。

### 2.2 消费速度慢的处理方式

#### 1 提高消费并行度

绝大部分消息消费行为都属于 IO 密集型，即可能是操作数据库，或者调用 RPC，这类消费行为的消费速度在于后端数据库或者外系统的吞吐量，通过增加消费并行度，可以提高总的消费吞吐量，但是并行度增加到一定程度，反而会下降。所以，应用必须要设置合理的并行度。 如下有几种修改消费并行度的方法：

- 同一个 ConsumerGroup 下，通过增加 Consumer 实例数量来提高并行度（需要注意的是超过订阅队列数的 Consumer 实例无效）。可以通过加机器，或者在已有机器启动多个进程的方式。
- 提高单个 Consumer 的消费并行线程，通过修改参数 consumeThreadMin、consumeThreadMax 实现。

#### 2 批量方式消费

某些业务流程如果支持批量方式消费，则可以很大程度上提高消费吞吐量，例如订单扣款类应用，一次处理一个订单耗时 1 s，一次处理 10 个订单可能也只耗时 2 s，这样即可大幅度提高消费的吞吐量，通过设置 consumer的 consumeMessageBatchMaxSize 返个参数，默认是 1，即一次只消费一条消息，例如设置为 N，那么每次消费的消息数小于等于 N。

#### 3 跳过非重要消息

发生消息堆积时，如果消费速度一直追不上发送速度，如果业务对数据要求不高的话，可以选择丢弃不重要的消息。例如，当某个队列的消息数堆积到 100000 条以上，则尝试丢弃部分或全部消息，这样就可以快速追上发送消息的速度。示例代码如下：

```java
public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
    long offset = msgs.get(0).getQueueOffset();
    String maxOffset = msgs.get(0).getProperty(Message.PROPERTY_MAX_OFFSET);
    long diff = Long.parseLong(maxOffset) - offset;
    if (diff > 100000) {
        // TODO 消息堆积情况的特殊处理
        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
    }
    // TODO 正常消费过程
    return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
}
```

#### 4 优化每条消息消费过程

举例如下，某条消息的消费过程如下：

- 根据消息从 DB 查询【数据 1】
- 根据消息从 DB 查询【数据 2】
- 复杂的业务计算
- 向 DB 插入【数据 3】
- 向 DB 插入【数据 4】

这条消息的消费过程中有 4 次与 DB 的交互，如果按照每次 5ms 计算，那么总共耗时 20ms，假设业务计算耗时 5ms，那么总过耗时 25ms，所以如果能把 4 次 DB 交互优化为 2 次，那么总耗时就可以优化到 15ms，即总体性能提高了 40%。所以应用如果对时延敏感的话，可以把 DB 部署在 SSD 硬盘，相比于 SCSI 磁盘，前者的 RT 会小很多。

### 2.3 消费打印日志

如果消息量较少，建议在消费入口方法打印消息，消费耗时等，方便后续排查问题。

```java
public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
    log.info("RECEIVE_MSG_BEGIN: " + msgs.toString());
    // TODO 正常消费过程
    return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
}
```

如果能打印每条消息消费耗时，那么在排查消费慢等线上问题时，会更方便。

### 2.4 其他消费建议

#### 1 关于消费者和订阅

​第一件需要注意的事情是，不同的消费者组可以独立的消费一些 topic，并且每个消费者组都有自己的消费偏移量，请确保同一组内的每个消费者订阅信息保持一致。

#### 2 关于有序消息

消费者将锁定每个消息队列，以确保他们被逐个消费，虽然这将会导致性能下降，但是当你关心消息顺序的时候会很有用。我们不建议抛出异常，你可以返回 ConsumeOrderlyStatus.SUSPEND_CURRENT_QUEUE_A_MOMENT 作为替代。

#### 3 关于并发消费

顾名思义，消费者将并发消费这些消息，建议你使用它来获得良好性能，我们不建议抛出异常，你可以返回 ConsumeConcurrentlyStatus.RECONSUME_LATER 作为替代。

#### 4 关于消费状态 Consume Status

对于并发的消费监听器，你可以返回 RECONSUME_LATER 来通知消费者现在不能消费这条消息，并且希望可以稍后重新消费它。然后，你可以继续消费其他消息。对于有序的消息监听器，因为你关心它的顺序，所以不能跳过消息，但是你可以返回SUSPEND_CURRENT_QUEUE_A_MOMENT 告诉消费者等待片刻。

#### 5 关于 Blocking

不建议阻塞监听器，因为它会阻塞线程池，并最终可能会终止消费进程

#### 6 关于线程数设置

消费者使用 ThreadPoolExecutor 在内部对消息进行消费，所以你可以通过设置 setConsumeThreadMin 或 setConsumeThreadMax 来改变它。

#### 7 关于消费位点

当建立一个新的消费者组时，需要决定是否需要消费已经存在于 Broker 中的历史消息 CONSUME_FROM_LAST_OFFSET 将会忽略历史消息，并消费之后生成的任何消息。CONSUME_FROM_FIRST_OFFSET 将会消费每个存在于 Broker 中的信息。你也可以使用 CONSUME_FROM_TIMESTAMP 来消费在指定时间戳后产生的消息。

## 3 Broker

### 3.1 Broker 角色

​Broker 角色分为 ASYNC_MASTER（异步主机）、SYNC_MASTER（同步主机）以及 SLAVE（从机）。如果对消息的可靠性要求比较严格，可以采用 SYNC_MASTER 加 SLAVE 的部署方式。如果对消息可靠性要求不高，可以采用 ASYNC_MASTER 加 SLAVE 的部署方式。如果只是测试方便，则可以选择仅 ASYNC_MASTER 或仅 SYNC_MASTER 的部署方式。

### 3.2 FlushDiskType

​SYNC_FLUSH（同步刷新）相比于 ASYNC_FLUSH（异步处理）会损失很多性能，但是也更可靠，所以需要根据实际的业务场景做好权衡。

### 3.3 Broker 配置

| 参数名                   | 默认值                  | 说明                  |
| ----------------------- | ---------------------- | -------------------- |
| listenPort              | 10911                  | 接受客户端连接的监听端口 |
| namesrvAddr             | null                   | nameServer 地址 |
| brokerIP1               | 网卡的 InetAddress      | 当前 broker 监听的 IP |
| brokerIP2               | 跟 brokerIP1 一样       | 存在主从 broker 时，如果在 broker 主节点上配置了 brokerIP2 属性，broker 从节点会连接主节点配置的 brokerIP2 进行同步 |
| brokerName              | null                   | broker 的名称 |
| brokerClusterName       | DefaultCluster         | 本 broker 所属的 Cluser 名称 |
| brokerId                | 0                      | broker id, 0 表示 master, 其他的正整数表示 slave |
| storePathRootDir        | $HOME/store/           | 存储根路径 |
| storePathCommitLog      | $HOME/store/commitlog/ | 存储 commit log 的路径 |
| mappedFileSizeCommitLog | 1024 * 1024 * 1024(1G) | commit log 的映射文件大小 |​
| deleteWhen              | 04                     | 在每天的什么时间删除已经超过文件保留时间的 commit log |​
| fileReservedTime        | 72                     | 以小时计算的文件保留时间 |​
| brokerRole              | ASYNC_MASTER           | SYNC_MASTER / ASYNC_MASTER / SLAVE |​
| flushDiskType           | ASYNC_FLUSH            | SYNC_FLUSH / ASYNC_FLUSH SYNC_FLUSH 模式下的 broker 保证在收到确认生产者之前将消息刷盘。ASYNC_FLUSH 模式下的 broker 则利用刷盘一组消息的模式，可以取得更好的性能。 |​

## 4  NameServer

​RocketMQ 中，Name Servers 被设计用来做简单的路由管理。其职责包括：

- Brokers 定期向每个名称服务器注册路由数据。
- 名称服务器为客户端，包括生产者，消费者和命令行客户端提供最新的路由信息。
​
## 5 客户端配置

相对于 RocketMQ 的 Broker 集群，生产者和消费者都是客户端。本小节主要描述生产者和消费者公共的行为配置。

### 5.1 客户端寻址方式

RocketMQ 可以令客户端找到 Name Server, 然后通过 Name Server 再找到 Broker。如下所示有多种配置方式，优先级由高到低，高优先级会覆盖低优先级。

- 代码中指定 Name Server 地址，多个 namesrv 地址之间用分号分割

```java
producer.setNamesrvAddr("192.168.0.1:9876;192.168.0.2:9876");

consumer.setNamesrvAddr("192.168.0.1:9876;192.168.0.2:9876");
```

- Java 启动参数中指定 Name Server 地址

```bash
-Drocketmq.namesrv.addr=192.168.0.1:9876;192.168.0.2:9876
```

- 环境变量指定Name Server地址

```bash
export NAMESRV_ADDR=192.168.0.1:9876;192.168.0.2:9876
```

- HTTP 静态服务器寻址（默认）

客户端启动后，会定时访问一个静态 HTTP 服务器，地址如下：`<http://jmenv.tbsite.net:8080/rocketmq/nsaddr>`，这个 URL 的返回内容如下：

```
192.168.0.1:9876;192.168.0.2:9876
```

客户端默认每隔 2 分钟访问一次这个 HTTP 服务器，并更新本地的 Name Server 地址。URL 已经在代码中硬编码，可通过修改 /etc/hosts 文件来改变要访问的服务器，例如在 /etc/hosts 增加如下配置：

```bash
10.232.22.67    jmenv.tbsite.net
```

推荐使用 HTTP 静态服务器寻址方式，好处是客户端部署简单，且 Name Server 集群可以热升级。

### 5.2 客户端配置

`DefaultMQProducer`、`TransactionMQProducer`、`DefaultMQPushConsumer`、`DefaultMQPullConsumer` 都继承于 `ClientConfig` 类，ClientConfig 为客户端的公共配置类。客户端的配置都是 `get`、`set` 形式，每个参数都可以用 spring 来配置，也可以在代码中配置，例如 namesrvAddr 这个参数可以这样配置，producer.setNamesrvAddr("192.168.0.1:9876")，其他参数同理。

#### 1 客户端的公共配置

| 参数名                         | 默认值   | 说明                                          |
| ----------------------------- | ------- | -------------------------------------------- |
| namesrvAddr                   |         | Name Server 地址列表，多个 NameServer 地址用分号隔开 |
| clientIP                      | 本机IP   | 客户端本机 IP 地址，某些机器会发生无法识别客户端 IP 地址情况，需要应用在代码中强制指定 |
| instanceName                  | DEFAULT | 客户端实例名称，客户端创建的多个 Producer、Consumer 实际是共用一个内部实例（这个实例包含网络连接、线程资源等） |
| clientCallbackExecutorThreads | 4       | 通信层异步回调线程数 |
| pollNameServerInteval         | 30000   | 轮询 Name Server 间隔时间，单位毫秒 |
| heartbeatBrokerInterval       | 30000   | 向 Broker 发送心跳间隔时间，单位毫秒 |
| persistConsumerOffsetInterval | 5000    | 持久化 Consumer 消费进度间隔时间，单位毫秒 |

#### 2 Producer 配置

| 参数名                           | 默认值           | 说明                                                         |
| -------------------------------- | ---------------- | ------------------------------------------------------------ |
| producerGroup                    | DEFAULT_PRODUCER | Producer 组名，多个 Producer 如果属于一个应用，发送同样的消息，则应该将它们归为同一组 |
| createTopicKey                   | TBW102           | 在发送消息时，自动创建服务器不存在的 topic，需要指定 Key，该 Key 可用于配置发送消息所在 topic 的默认路由。 |
| defaultTopicQueueNums            | 4                | 在发送消息，自动创建服务器不存在的 topic 时，默认创建的队列数  |
| sendMsgTimeout                   | 3000             | 发送消息超时时间，单位毫秒                                   |
| compressMsgBodyOverHowmuch       | 4096             | 消息 Body 超过多大开始压缩（Consumer 收到消息会自动解压缩），单位字节 |
| retryAnotherBrokerWhenNotStoreOK | FALSE            | 如果发送消息返回 sendResult，但是 sendStatus != SEND_OK，是否重试发送 |
| retryTimesWhenSendFailed         | 2                | 如果消息发送失败，最大重试次数，该参数只对同步发送模式起作用 |
| maxMessageSize                   | 4MB              | 客户端限制的消息大小，超过报错，同时服务端也会限制，所以需要跟服务端配合使用。 |
| transactionCheckListener         |                  | 事务消息回查监听器，如果发送事务消息，必须设置               |
| checkThreadPoolMinSize           | 1                | Broker 回查 Producer 事务状态时，线程池最小线程数                     |
| checkThreadPoolMaxSize           | 1                | Broker 回查 Producer 事务状态时，线程池最大线程数                     |
| checkRequestHoldMax              | 2000             | Broker 回查 Producer 事务状态时，Producer 本地缓冲请求队列大小   |
| RPCHook                          | null             | 该参数是在 Producer 创建时传入的，包含消息发送前的预处理和消息响应后的处理两个接口，用户可以在第一个接口中做一些安全控制或者其他操作。 |

#### 3  PushConsumer 配置

| 参数名                       | 默认值                        | 说明                                                         |
| ---------------------------- | ----------------------------- | ------------------------------------------------------------ |
| consumerGroup                | DEFAULT_CONSUMER              | Consumer 组名，多个 Consumer 如果属于一个应用，订阅同样的消息，且消费逻辑一致，则应该将它们归为同一组 |
| messageModel                 | CLUSTERING                    | 消费模型支持集群消费和广播消费两种                           |
| consumeFromWhere             | CONSUME_FROM_LAST_OFFSET      | Consumer 启动后，默认从上次消费的位置开始消费，这包含两种情况：一种是上次消费的位置未过期，则消费从上次中止的位置进行；一种是上次消费位置已经过期，则从当前队列第一条消息开始消费 |
| consumeTimestamp             | 半个小时前                    | 只有当 consumeFromWhere 值为 CONSUME_FROM_TIMESTAMP 时才起作用。 |
| allocateMessageQueueStrategy | AllocateMessageQueueAveragely | Rebalance 算法实现策略                                        |
| subscription                 |                               | 订阅关系                                                     |
| messageListener              |                               | 消息监听器                                                   |
| offsetStore                  |                               | 消费进度存储                                                 |
| consumeThreadMin             | 20                            | 消费线程池最小线程数                                               |
| consumeThreadMax             | 20                            | 消费线程池最大线程数                                               |
| consumeConcurrentlyMaxSpan   | 2000                          | 单队列并行消费允许的最大跨度                                 |
| pullThresholdForQueue        | 1000                          | 拉消息本地队列缓存消息最大数                                 |
| pullInterval                 | 0                             | 拉消息间隔，由于是长轮询，所以为0，但是如果应用为了流控，也可以设置大于0的值，单位毫秒 |
| consumeMessageBatchMaxSize   | 1                             | 批量消费，一次消费多少条消息                                 |
| pullBatchSize                | 32                            | 批量拉消息，一次最多拉多少条                                 |

#### 4  PullConsumer配置

| 参数名                           | 默认值                        | 说明                                                         |
| -------------------------------- | ----------------------------- | ------------------------------------------------------------ |
| consumerGroup                    | DEFAULT_CONSUMER              | Consumer 组名，多个 Consumer 如果属于一个应用，订阅同样的消息，且消费逻辑一致，则应该将它们归为同一组 |
| brokerSuspendMaxTimeMillis       | 20000                         | 长轮询，Consumer 拉消息请求在Broker 挂起最长时间，单位毫秒     |
| consumerTimeoutMillisWhenSuspend | 30000                         | 长轮询，Consumer 拉消息请求在 Broker 挂起超过指定时间，客户端认为超时，单位毫秒 |
| consumerPullTimeoutMillis        | 10000                         | 非长轮询，拉消息超时时间，单位毫秒                           |
| messageModel                     | BROADCASTING                  | 消息支持两种模式：集群消费和广播消费           |
| messageQueueListener             |                               | 监听队列变化                                                 |
| offsetStore                      |                               | 消费进度存储                                                 |
| registerTopics                   |                               | 注册的 topic 集合                                              |
| allocateMessageQueueStrategy     | AllocateMessageQueueAveragely | Rebalance 算法实现策略                                        |

#### 5  Message数据结构

| 字段名         | 默认值 | 说明                                                         |
| -------------- | ------ | ------------------------------------------------------------ |
| Topic          | null   | 必填，消息所属 topic 的名称                                        |
| Body           | null   | 必填，消息体                                                 |
| Tags           | null   | 选填，消息标签，方便服务器过滤使用。目前只支持每个消息设置一个 tag |
| Keys           | null   | 选填，代表这条消息的业务关键词，服务器会根据 keys 创建哈希索引，设置后，可以在 Console 系统根据 Topic、Keys 来查询消息，由于是哈希索引，请尽可能保证 key 唯一，例如订单号，商品 Id 等。 |
| Flag           | 0      | 选填，完全由应用来设置，RocketMQ 不做干预                     |
| DelayTimeLevel | 0      | 选填，消息延时级别，0 表示不延时，大于 0 会延时特定的时间才会被消费 |
| WaitStoreMsgOK | TRUE   | 选填，表示消息是否在服务器落盘后才返回应答。                 |

## 6  系统配置

本小节主要介绍系统（JVM/OS）相关的配置。

### 6.1 JVM 选项

推荐使用最新发布的 JDK 1.8 版本。通过设置相同的 Xms 和 Xmx 值来防止 JVM 调整堆大小以获得更好的性能。简单的 JVM 配置如下所示：
​
```bash​
​-server -Xms8g -Xmx8g -Xmn4g
```

如果您不关心 RocketMQ Broker 的启动时间，还有一种更好的选择，就是通过“预触摸” Java 堆以确保在 JVM 初始化期间每个页面都将被分配。那些不关心启动时间的人可以启用它：

```bash
-XX:+AlwaysPreTouch
```

禁用偏置锁定可能会减少 JVM 暂停，

```bash
-XX:-UseBiasedLocking
```

至于垃圾回收，建议使用带 JDK 1.8 的 G1 收集器。

```bash
-XX:+UseG1GC -XX:G1HeapRegionSize=16m
-XX:G1ReservePercent=25
-XX:InitiatingHeapOccupancyPercent=30
```

这些 GC 选项看起来有点激进，但事实证明它在我们的生产环境中具有良好的性能。另外不要把 `-XX:MaxGCPauseMillis` 的值设置太小，否则 JVM 将使用一个小的年轻代来实现这个目标，这将导致非常频繁的 minor GC，所以建议使用 rolling GC 日志文件：

```bash
-XX:+UseGCLogFileRotation
-XX:NumberOfGCLogFiles=5
-XX:GCLogFileSize=30m
```

如果写入 GC 文件会增加代理的延迟，可以考虑将 GC 日志文件重定向到内存文件系统：

```bash
-Xloggc:/dev/shm/mq_gc_%p.log123
```

### 6.2 Linux 内核参数

`os.sh` 脚本在 bin 文件夹中列出了许多内核参数，可以进行微小的更改然后用于生产用途。下面的参数需要注意，更多细节请参考 `/proc/sys/vm/*` 的[文档](https://www.kernel.org/doc/Documentation/sysctl/vm.txt)

- `vm.extra_free_kbytes`，告诉 VM 在后台回收（kswapd）启动的阈值与直接回收（通过分配进程）的阈值之间保留额外的可用内存。RocketMQ 使用此参数来避免内存分配中的长延迟。（与具体内核版本相关）

- `vm.min_free_kbytes`，如果将其设置为低于 1024KB，将会巧妙的将系统破坏，并且系统在高负载下容易出现死锁。

- `vm.max_map_count`，限制一个进程可能具有的最大内存映射区域数。RocketMQ 将使用 mmap 加载 CommitLog 和 ConsumeQueue，因此建议将为此参数设置较大的值。（agressiveness --> aggressiveness）

- `vm.swappiness`，定义内核交换内存页面的积极程度。较高的值会增加攻击性，较低的值会减少交换量。建议将值设置为 10 来避免交换延迟。

- `File descriptor limits`，RocketMQ 需要为文件（CommitLog 和 ConsumeQueue）和网络连接打开文件描述符。我们建议设置文件描述符的值为 655350。

- [Disk scheduler](https://access.redhat.com/documentation/en-US/Red_Hat_Enterprise_Linux/6/html/Performance_Tuning_Guide/ch06s04s02.html)， RocketMQ 建议使用 I/O 截止时间调度器，它试图为请求提供有保证的延迟。

