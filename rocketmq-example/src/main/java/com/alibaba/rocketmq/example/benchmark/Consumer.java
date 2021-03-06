/**
 * $Id: Consumer.java 1831 2013-05-16 01:39:51Z shijia.wxr $
 */
package com.alibaba.rocketmq.example.benchmark;

import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicLong;

import com.alibaba.rocketmq.client.consumer.DefaultMQPushConsumer;
import com.alibaba.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import com.alibaba.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import com.alibaba.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import com.alibaba.rocketmq.client.exception.MQClientException;
import com.alibaba.rocketmq.common.consumer.ConsumeFromWhere;
import com.alibaba.rocketmq.common.message.MessageExt;


/**
 * @author shijia.wxr<vintage.wang@gmail.com>
 * 
 */
public class Consumer {

    public static void compareAndSetMax(final AtomicLong target, final long value) {
        long prev = target.get();
        while (value > prev) {
            boolean updated = target.compareAndSet(prev, value);
            if (updated)
                break;

            prev = target.get();
        }
    }


    public static void main(String[] args) throws MQClientException {
        final StatsBenchmarkConsumer statsBenchmarkConsumer = new StatsBenchmarkConsumer();

        final Timer timer = new Timer("BenchmarkTimerThread", true);

        final LinkedList<Long[]> snapshotList = new LinkedList<Long[]>();

        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                snapshotList.addLast(statsBenchmarkConsumer.createSnapshot());
                if (snapshotList.size() > 10) {
                    snapshotList.removeFirst();
                }
            }
        }, 1000, 1000);

        timer.scheduleAtFixedRate(new TimerTask() {
            private void printStats() {
                if (snapshotList.size() >= 10) {
                    Long[] begin = snapshotList.getFirst();
                    Long[] end = snapshotList.getLast();

                    final long consumeTps =
                            (long) (((end[1] - begin[1]) / (double) (end[0] - begin[0])) * 1000L);
                    final double averageB2CRT = ((end[2] - begin[2]) / (double) (end[1] - begin[1]));
                    final double averageS2CRT = ((end[3] - begin[3]) / (double) (end[1] - begin[1]));

                    System.out.printf(
                        "Consume TPS: %d Average(B2C) RT: %7.3f Average(S2C) RT: %7.3f MAX(B2C) RT: %d MAX(S2C) RT: %d\n"//
                        , consumeTps//
                        , averageB2CRT//
                        , averageS2CRT//
                        , end[4]//
                        , end[5]//
                        );
                }
            }


            @Override
            public void run() {
                try {
                    this.printStats();
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, 10000, 10000);

        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer("benchmark_consumer");

        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_MIN_OFFSET);

        consumer.subscribe("BenchmarkTest", "*");

        consumer.registerMessageListener(new MessageListenerConcurrently() {
            @Override
            public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs,
                    ConsumeConcurrentlyContext context) {
                MessageExt msg = msgs.get(0);
                long now = System.currentTimeMillis();

                // 1
                statsBenchmarkConsumer.getReceiveMessageTotalCount().incrementAndGet();

                // 2
                long born2ConsumerRT = now - msg.getBornTimestamp();
                statsBenchmarkConsumer.getBorn2ConsumerTotalRT().addAndGet(born2ConsumerRT);

                // 3
                long store2ConsumerRT = now - msg.getStoreTimestamp();
                statsBenchmarkConsumer.getStore2ConsumerTotalRT().addAndGet(store2ConsumerRT);

                // 4
                compareAndSetMax(statsBenchmarkConsumer.getBorn2ConsumerMaxRT(), born2ConsumerRT);

                // 5
                compareAndSetMax(statsBenchmarkConsumer.getStore2ConsumerMaxRT(), store2ConsumerRT);

                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            }
        });

        consumer.start();

        System.out.println("Consumer Started.");
    }
}


class StatsBenchmarkConsumer {
    // 1
    private final AtomicLong receiveMessageTotalCount = new AtomicLong(0L);
    // 2
    private final AtomicLong born2ConsumerTotalRT = new AtomicLong(0L);
    // 3
    private final AtomicLong store2ConsumerTotalRT = new AtomicLong(0L);
    // 4
    private final AtomicLong born2ConsumerMaxRT = new AtomicLong(0L);
    // 5
    private final AtomicLong store2ConsumerMaxRT = new AtomicLong(0L);


    public Long[] createSnapshot() {
        Long[] snap = new Long[] {//
                System.currentTimeMillis(),//
                        this.receiveMessageTotalCount.get(),//
                        this.born2ConsumerTotalRT.get(),//
                        this.store2ConsumerTotalRT.get(),//
                        this.born2ConsumerMaxRT.get(),//
                        this.store2ConsumerMaxRT.get(), //
                };

        return snap;
    }


    public AtomicLong getReceiveMessageTotalCount() {
        return receiveMessageTotalCount;
    }


    public AtomicLong getBorn2ConsumerTotalRT() {
        return born2ConsumerTotalRT;
    }


    public AtomicLong getStore2ConsumerTotalRT() {
        return store2ConsumerTotalRT;
    }


    public AtomicLong getBorn2ConsumerMaxRT() {
        return born2ConsumerMaxRT;
    }


    public AtomicLong getStore2ConsumerMaxRT() {
        return store2ConsumerMaxRT;
    }
}