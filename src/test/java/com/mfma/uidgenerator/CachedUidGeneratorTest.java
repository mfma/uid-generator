package com.mfma.uidgenerator;

import com.mfma.uidgenerator.impl.CachedUidGenerator;
import org.apache.commons.lang.StringUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test for {@link CachedUidGenerator}
 *
 * @author yutianbao
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"classpath:uid/cached-uid-spring.xml"})
public class CachedUidGeneratorTest {

    private static Logger logger = LoggerFactory.getLogger(CachedUidGeneratorTest.class);

    private static final int SIZE = 7000000; // 700w
    private static final boolean VERBOSE = false;
    private static final int THREADS = Runtime.getRuntime().availableProcessors() << 1;

    @Resource
    private UidGenerator uidGenerator;

    /**
     * Test for serially generate
     *
     */
    @Test
    public void testSerialGenerate() {
        // Generate UID serially
        Set<Long> uidSet = new HashSet<>(SIZE);
        for (int i = 0; i < SIZE; i++) {
            doGenerate(uidSet, i);
        }

        // Check UIDs are all unique
        checkUniqueID(uidSet);
    }

    /**
     * Test for parallel generate
     *
     * @throws InterruptedException
     */
    @Test
    public void testParallelGenerate(){
        try {
            AtomicInteger control = new AtomicInteger(-1);
            Set<Long> uidSet = new ConcurrentSkipListSet<>();

            // Initialize threads
            List<Thread> threadList = new ArrayList<>(THREADS);
            for (int i = 0; i < THREADS; i++) {
                Thread thread = new Thread(() -> workerRun(uidSet, control));
                thread.setName("UID-generator-" + i);

                threadList.add(thread);
                thread.start();
            }

            // Wait for worker done
            for (Thread thread : threadList) {
                thread.join();
            }

            // Check generate 700w times
            Assert.assertEquals(SIZE, control.get());

            // Check UIDs are all unique
            checkUniqueID(uidSet);
        }catch (InterruptedException e){
            logger.error(e.getMessage());
        }
    }

    /**
     * Woker run
     */
    private void workerRun(Set<Long> uidSet, AtomicInteger control) {
        for (; ; ) {
            int myPosition = control.updateAndGet(old -> (old == SIZE ? SIZE : old + 1));
            if (myPosition == SIZE) {
                return;
            }

            doGenerate(uidSet, myPosition);
        }
    }

    /**
     * Do generating
     */
    private void doGenerate(Set<Long> uidSet, int index) {
        long uid = uidGenerator.getUid();
        String parsedInfo = uidGenerator.parseUid(uid);
        boolean existed = !uidSet.add(uid);
        if (existed) {
            logger.info("Found duplicate UID " + uid);
        }

        // Check UID is positive, and can be parsed
        Assert.assertTrue(uid > 0L);
        Assert.assertTrue(StringUtils.isNotBlank(parsedInfo));

        if (VERBOSE) {
            logger.info(Thread.currentThread().getName() + " No." + index + " >>> " + parsedInfo);
        }
    }

    /**
     * Check UIDs are all unique
     */
    private void checkUniqueID(Set<Long> uidSet) {
        int size = uidSet.size();
        logger.info("size=={}",size);
        Assert.assertEquals(SIZE, uidSet.size());
    }

}
