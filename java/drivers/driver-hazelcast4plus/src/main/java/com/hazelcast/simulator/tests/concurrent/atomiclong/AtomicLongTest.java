/*
 * Copyright (c) 2008-2016, Hazelcast, Inc. All Rights Reserved.
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
package com.hazelcast.simulator.tests.concurrent.atomiclong;

import static com.hazelcast.simulator.tests.helpers.KeyLocality.SHARED;
import static org.junit.Assert.assertEquals;

import com.hazelcast.core.DistributedObject;
import com.hazelcast.core.IFunction;
import com.hazelcast.cp.CPSubsystem;
import com.hazelcast.cp.IAtomicLong;
import com.hazelcast.simulator.hz.HazelcastTest;
import com.hazelcast.simulator.test.BaseThreadState;
import com.hazelcast.simulator.test.annotations.AfterRun;
import com.hazelcast.simulator.test.annotations.Setup;
import com.hazelcast.simulator.test.annotations.Teardown;
import com.hazelcast.simulator.test.annotations.TimeStep;
import com.hazelcast.simulator.test.annotations.Verify;
import com.hazelcast.simulator.tests.helpers.KeyLocality;

public class AtomicLongTest extends HazelcastTest {

    // properties
    public KeyLocality keyLocality = SHARED;
    public int countersLength = 1000;
    public int cpGroupCount = 0;

    private IAtomicLong totalCounter;
    private IAtomicLong[] counters;

    @Setup
    public void setup() {
        CPSubsystem cpSubsystem = targetInstance.getCPSubsystem();
        totalCounter = getAtomicLong(name + ":TotalCounter");
        counters = new IAtomicLong[countersLength];

        for (int i = 0; i < countersLength; i++) {
            String cpGroupString = cpGroupCount == 0
                    ? ""
                    : "@" + (i % cpGroupCount);
            counters[i] = cpSubsystem.getAtomicLong("ref-"+i + cpGroupString);
        }
    }

    @TimeStep(prob = 0.5)
    public long get(ThreadState state) {
        return state.randomCounter().get();
    }

    @TimeStep(prob = 0.1)
    public long incrementAndGet(ThreadState state) {
        state.increments++;
        return state.randomCounter().incrementAndGet();
    }
    
    @TimeStep(prob = 1)
    public void set(ThreadState state) {
        state.randomCounter().set(++state.increments);
    }

    @TimeStep(prob = 0)
    public void alter(ThreadState state) {
        state.randomCounter().alter(state.identity);
    }

    @TimeStep(prob = 0)
    public boolean cas(ThreadState state) {
        return state.randomCounter().compareAndSet(state.increments, ++state.increments);
    }

    public class ThreadState extends BaseThreadState {

        private long increments;

        private IAtomicLong randomCounter() {
            int index = randomInt(counters.length);
            return counters[index];
        }
        final IFunction<Long, Long> identity = s -> s;
    }

    @AfterRun
    public void afterRun(ThreadState state) {
        totalCounter.addAndGet(state.increments);
    }

    @Verify
    public void verify() {
        String totalName = totalCounter.getName();

        long actual = 0;
        for (DistributedObject distributedObject : targetInstance.getDistributedObjects()) {
            String key = distributedObject.getName();
            if (key.startsWith(name)
                    && !key.equals(totalName)) {
                actual += getAtomicLong(key).get();
            }
        }

        assertEquals(totalCounter.get(), actual);
    }

    @Teardown
    public void teardown() {
        for (IAtomicLong counter : counters) {
            counter.destroy();
        }
        totalCounter.destroy();
    }
}
