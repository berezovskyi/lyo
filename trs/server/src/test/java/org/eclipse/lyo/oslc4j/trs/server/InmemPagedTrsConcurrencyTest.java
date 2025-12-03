package org.eclipse.lyo.oslc4j.trs.server;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.lyo.core.trs.ChangeLog;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class InmemPagedTrsConcurrencyTest {

    @Test
    public void testConcurrentHistoryData() throws InterruptedException {
        final int changelogPageLimit = 10;
        final int threadCount = 10;
        final int eventsPerThread = 100;
        final int totalEvents = threadCount * eventsPerThread;

        // Create InmemPagedTrs with small page limit to force frequent page creation
        final InmemPagedTrs pagedTrs = new InmemPagedTrs(10, changelogPageLimit, URI.create("http://localhost:1337/trs/"), Collections.emptySet());

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger exceptionCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < eventsPerThread; j++) {
                        pagedTrs.onHistoryData(TRSTestUtil.createHistory());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    exceptionCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(exceptionCount.get()).as("Exceptions occurred during concurrent execution").isEqualTo(0);

        int actualTotalEvents = 0;
        int pageCount = pagedTrs.changelogPageCount();
        for (int i = 1; i <= pageCount; i++) {
            ChangeLog changeLog = pagedTrs.getChangeLog(i);
            actualTotalEvents += changeLog.getChange().size();
        }

        assertThat(actualTotalEvents).as("Total events recorded").isEqualTo(totalEvents);
    }
}
