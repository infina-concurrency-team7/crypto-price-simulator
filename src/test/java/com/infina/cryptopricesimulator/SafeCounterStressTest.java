package com.infina.cryptopricesimulator.counter;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SafeCounterStressTest {

    @Test
    void shouldHandleConcurrentIncrements() throws InterruptedException {

        SafeCounter counter = new SafeCounter();

        int threadCount = 100;
        int incrementsPerThread = 10_000;

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            Thread thread = new Thread(() -> {
                try {
                    startLatch.await();

                    for (int j = 0; j < incrementsPerThread; j++) {
                        counter.increment();
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    finishLatch.countDown();
                }
            });

            thread.start();
        }

        long startTime = System.currentTimeMillis();

        startLatch.countDown();

        finishLatch.await();

        long endTime = System.currentTimeMillis();

        long expected = (long) threadCount * incrementsPerThread;

        System.out.println("Expected: " + expected);
        System.out.println("Actual: " + counter.count());
        System.out.println("Execution Time: " + (endTime - startTime) + " ms");

        assertEquals(expected, counter.count());
    }
}
