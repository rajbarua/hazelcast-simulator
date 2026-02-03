/*
 * Copyright (c) 2008-2023, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hazelcast.simulator.tests.cp;

import com.hazelcast.cp.lock.FencedLock;
import com.hazelcast.simulator.hz.HazelcastTest;
import com.hazelcast.simulator.test.BaseThreadState;
import com.hazelcast.simulator.test.annotations.AfterRun;
import com.hazelcast.simulator.test.annotations.Setup;
import com.hazelcast.simulator.test.annotations.Teardown;
import com.hazelcast.simulator.test.annotations.TimeStep;
import com.hazelcast.simulator.test.annotations.Verify;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static com.hazelcast.simulator.utils.CommonUtils.sleepMillis;
import static org.junit.Assert.assertTrue;

/**
 * Simulates the TDRC use case: pick a key from a large shared keyspace, lock it, do dummy work, unlock.
 * Supports optional hot-key skew and CP group sharding via lock naming.
 */
public class TDRCFencedLockTest extends HazelcastTest {
    // total TDRC keyspace size (total locks)
    public long keySpaceSize = 5_000_000L;
    // fraction of keys considered "hot" for contention (0.0 disables hot keys). For example: 0.01 = 1% of keys are hot
    // meaning 50,000 in a 5,000,000 keySpaceSize
    public double hotKeyFraction = 0.0;
    // chance each operation targets the hot set instead of the cold set. With hotKeyAccessProbability = 0.8, 
    // about 80% of operations go to those 50,000 hot keys and 20% go to the remaining 4,950,000 keys.
    public double hotKeyAccessProbability = 0.0;
    // number of CP groups to shard locks across (0 => default CP group)
    public int cpGroups = 0;
    // dummy critical-section duration while holding the lock
    public int holdTimeMs = 0;
    // optionally use tryLock instead of lock
    public boolean useTryLock = false;
    // how long to wait for tryLock (0 = don't wait)
    public long tryLockTimeoutMs = 0;
    // destroy all locks for the configured key space after the test completes
    public boolean destroyLocksOnTeardown = true;

    private AtomicLong totalAcquireReleases;
    private AtomicLong totalFailedTryLocks;
    private long hotKeyCount;
    private String[] cpGroupNames;

    @Setup
    public void setup() {
        if (keySpaceSize <= 0) {
            throw new IllegalArgumentException("keySpaceSize must be > 0");
        }
        if (hotKeyFraction < 0.0 || hotKeyFraction > 1.0) {
            throw new IllegalArgumentException("hotKeyFraction must be between 0.0 and 1.0");
        }
        if (hotKeyAccessProbability < 0.0 || hotKeyAccessProbability > 1.0) {
            throw new IllegalArgumentException("hotKeyAccessProbability must be between 0.0 and 1.0");
        }

        if (hotKeyFraction > 0.0 && hotKeyAccessProbability > 0.0) {
            long candidate = (long) (keySpaceSize * hotKeyFraction);
            hotKeyCount = Math.max(1L, Math.min(candidate, keySpaceSize));
        } else {
            hotKeyCount = 0L;
        }

        cpGroupNames = createCpGroupNames();
        totalAcquireReleases = new AtomicLong();
        totalFailedTryLocks = new AtomicLong();
    }

    private String[] createCpGroupNames() {
        if (cpGroups <= 1) {
            return new String[]{"default"};
        }

        String[] names = new String[cpGroups];
        for (int i = 0; i < cpGroups; i++) {
            names[i] = "cpgroup-" + i;
        }
        return names;
    }

    private String lockNameFor(long keyIndex) {
        int groupIndex = (int) (keyIndex % cpGroupNames.length);
        return "tdrc-lock-" + keyIndex + "@" + cpGroupNames[groupIndex];
    }

    @TimeStep(prob = 1)
    public void lockThenUnlock(ThreadState state) {
        long keyIndex = state.nextKeyIndex();
        String lockName = state.lockNameFor(keyIndex);
        FencedLock lock = targetInstance.getCPSubsystem().getLock(lockName);

        boolean locked = false;
        if (useTryLock) {
            locked = state.tryLock(lock);
            if (!locked) {
                state.failedTryLocks++;
                return;
            }
        } else {
            lock.lock();
            locked = true;
        }

        try {
            if (holdTimeMs > 0) {
                sleepMillis(holdTimeMs);
            }
        } finally {
            if (locked) {
                lock.unlock();
            }
        }

        state.acquireReleases++;
    }

    public class ThreadState extends BaseThreadState {
        long acquireReleases;
        long failedTryLocks;

        private long nextKeyIndex() {
            if (hotKeyCount > 0 && randomDouble() < hotKeyAccessProbability) {
                return randomLong(hotKeyCount);
            }

            long coldCount = keySpaceSize - hotKeyCount;
            if (coldCount <= 0) {
                return randomLong(keySpaceSize);
            }
            return hotKeyCount + randomLong(coldCount);
        }

        private String lockNameFor(long keyIndex) {
            return TDRCFencedLockTest.this.lockNameFor(keyIndex);
        }

        private boolean tryLock(FencedLock lock) {
            if (tryLockTimeoutMs > 0) {
                return lock.tryLock(tryLockTimeoutMs, TimeUnit.MILLISECONDS);
            }
            return lock.tryLock();
        }
    }

    @AfterRun
    public void afterRun(ThreadState state) {
        totalAcquireReleases.addAndGet(state.acquireReleases);
        totalFailedTryLocks.addAndGet(state.failedTryLocks);
    }

    @Teardown(global = true)
    public void teardown() {
        if (!destroyLocksOnTeardown || keySpaceSize <= 0) {
            return;
        }
        if (cpGroupNames == null || cpGroupNames.length == 0) {
            cpGroupNames = createCpGroupNames();
        }
        for (long keyIndex = 0; keyIndex < keySpaceSize; keyIndex++) {
            String lockName = lockNameFor(keyIndex);
            targetInstance.getCPSubsystem().getLock(lockName).destroy();
        }
    }

    @Verify
    public void verify() {
        logger.info(name + ": totalAcquireReleases=" + totalAcquireReleases.get()
                + ", failedTryLocks=" + totalFailedTryLocks.get());
        assertTrue(totalAcquireReleases.get() > 0);
    }
}
