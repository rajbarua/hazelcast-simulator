package com.hazelcast.simulator.tests.custom;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableRangeMap;
import com.google.common.collect.Range;
import com.google.common.collect.RangeMap;
import com.google.common.collect.Table;
import com.google.common.collect.TreeRangeMap;
import com.hazelcast.map.IMap;
import com.hazelcast.spi.exception.TargetDisconnectedException;
import com.hazelcast.query.Predicates;
import com.hazelcast.simulator.hz.HazelcastTest;
import com.hazelcast.simulator.test.BaseThreadState;
import com.hazelcast.simulator.test.annotations.AfterRun;
import com.hazelcast.simulator.test.annotations.BeforeRun;
import com.hazelcast.simulator.test.annotations.Prepare;
import com.hazelcast.simulator.test.annotations.Setup;
import com.hazelcast.simulator.test.annotations.Teardown;
import com.hazelcast.simulator.test.annotations.TimeStep;
import com.hazelcast.simulator.test.annotations.Verify;
import com.hz.demo.domain.POJO;
import com.hz.demo.domain.TestDto;
import com.hazelcast.core.HazelcastInstanceNotActiveException;

import static org.junit.Assert.assertEquals;

public class TrainTestCS extends HazelcastTest {

    // properties
    public long entryCount = 100_000;

    private static final AtomicLong THREAD_PREFIX_SEQ = new AtomicLong();

    private IMap<String, TestDto> trainDto;
    private List<String> keys;
    private List<TestDto> dtoPool;
    private String setNewKeyPrefixBase;
    private final LongAdder setNewOps = new LongAdder();

    @Setup
    public void setUp() {
        trainDto = targetInstance.getMap(name);
        setNewKeyPrefixBase = testContext.getTestId() + "-" + resolveWorkerId() + "-setnew-";
    }

    @Prepare(global = false)
    public void prepare() {
        keys = ThreadLocalRandom.current()
                .longs(entryCount)
                .mapToObj(id -> "dto-" + id)
                .collect(Collectors.toList());

        dtoPool = keys.parallelStream()
                .map(this::createRandomDto)
                .collect(Collectors.toList());

        for (int i = 0; i < keys.size(); i++) {
            trainDto.set(keys.get(i), dtoPool.get(i));
        }
    }

    @TimeStep(prob = 0.8)
    public TestDto get(ThreadState ts) {
        try {
            return trainDto.get(ts.randomKey());
        } catch (RuntimeException e) {
            if (shouldIgnore(e)) {
                return null;
            }
            throw e;
        }
    }

    @TimeStep(prob = 0.0)
    public TestDto put(ThreadState ts) {
        String key = ts.randomKey();
        TestDto dto = ts.randomDto();
        return trainDto.put(key, dto);
    }

    @TimeStep(prob = 0.2)
    public void set(ThreadState ts) {
        String key = ts.randomKey();
        try {
            trainDto.set(key, ts.randomDto());
        } catch (RuntimeException e) {
            if (!shouldIgnore(e)) {
                throw e;
            }
        }
    }

    @TimeStep(prob = 0.0)
    public void setNew(ThreadState ts) {
        String key = ts.currentSetNewKey();
        try {
            trainDto.set(key, ts.nextSetNewDto());
            ts.onSetNewSuccess();
        } catch (RuntimeException e) {
            if (!shouldIgnore(e)) {
                throw e;
            }
        }
    }

    @TimeStep(prob = 0.0)
    public void delete(ThreadState ts) {
        trainDto.delete(ts.randomKey());
    }

    @TimeStep(prob = 0.0)
    public void sizeLog() {
        logger.info("current size of {}: {}", trainDto.getName(), trainDto.size());
    }

    public class ThreadState extends BaseThreadState {
        private String setNewKeyPrefix;
        private long setNewSequence;
        private long setNewThreadId;
        private int setNewDtoIndex;

        public String randomKey() {
            return keys.get(randomInt(keys.size()));
        }

        public TestDto randomDto() {
            return dtoPool.get(randomInt(dtoPool.size()));
        }

        private String currentSetNewKey() {
            return setNewKeyPrefix + setNewSequence;
        }

        private TestDto nextSetNewDto() {
            TestDto dto = dtoPool.get(setNewDtoIndex);
            setNewDtoIndex++;
            if (setNewDtoIndex == dtoPool.size()) {
                setNewDtoIndex = 0;
            }
            return dto;
        }

        private void onSetNewSuccess() {
            setNewSequence++;
        }
    }

    @Teardown
    public void tearDown() {
        // trainDto.destroy();
    }

    private TestDto createRandomDto(String id) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        POJO pojo = new POJO("pojo-" + id, today.minusDays(rnd.nextInt(30)), "desc-" + id);
        List<Integer> ints = Arrays.asList(rnd.nextInt(1000), rnd.nextInt(1000), rnd.nextInt(1000));

        ImmutableRangeMap<LocalDate, POJO> m3Val = ImmutableRangeMap.<LocalDate, POJO>builder()
                .put(Range.closed(today.minusDays(1), today.plusDays(1)), pojo)
                .build();
        ImmutableRangeMap<LocalDate, List<POJO>> m4Val = ImmutableRangeMap.<LocalDate, List<POJO>>builder()
                .put(Range.closed(today.minusDays(2), today.minusDays(1)), Arrays.asList(pojo))
                .build();
        RangeMap<Integer, List<POJO>> rm = TreeRangeMap.create();
        rm.put(Range.closed(0, 10), Arrays.asList(pojo));

        HashMultimap<String, POJO> mmString = HashMultimap.create();
        mmString.put("k1", pojo);
        HashMultimap<Integer, POJO> mmInt = HashMultimap.create();
        mmInt.put(1, pojo);

        Table<String, Integer, ImmutableRangeMap<LocalDate, List<String>>> table = HashBasedTable.create();
        table.put("row", 1, ImmutableRangeMap.<LocalDate, List<String>>builder()
                .put(Range.closed(today.minusDays(1), today), Arrays.asList("a", "b"))
                .build());
        Table<String, Integer, ImmutableRangeMap<LocalDate, POJO>> pojoTable = HashBasedTable.create();
        pojoTable.put("row", 1, m3Val);

        TestDto dto = new TestDto();
        dto.setId(id);
        dto.setA(rnd.nextBoolean());
        dto.setB(rnd.nextInt(10_000));
        dto.setC("val-" + rnd.nextInt(10_000));
        dto.setD((short) rnd.nextInt(Short.MAX_VALUE));
        dto.setE(rnd.nextLong());
        dto.setF(rnd.nextDouble());
        dto.setDate(today.minusDays(rnd.nextInt(10)));
        dto.setDateTime(now.minusMinutes(rnd.nextInt(60)));
        dto.setL1(ints);
        dto.setL2(Arrays.asList(pojo));
        dto.setIl(ImmutableList.copyOf(ints));
        dto.setTrainMasterList(new LinkedList<>(ints));
        dto.setSet(ints.stream().collect(Collectors.toSet()));

        Map<String, List<String>> m1 = new HashMap<>();
        m1.put("k1", Arrays.asList("v1", "v2"));
        dto.setM1(m1);

        Map<String, Map<String, String>> m2 = new HashMap<>();
        m2.put("m2", Map.of("a", "b"));
        dto.setM2(m2);

        Map<String, ImmutableRangeMap<LocalDate, POJO>> m3 = new HashMap<>();
        m3.put("r1", m3Val);
        dto.setM3(m3);

        HashMap<String, ImmutableRangeMap<LocalDate, List<POJO>>> m4 = new HashMap<>();
        m4.put("r2", m4Val);
        dto.setM4(m4);

        HashMap<Integer, HashMap<String, LinkedList<POJO>>> m5 = new HashMap<>();
        HashMap<String, LinkedList<POJO>> inner = new HashMap<>();
        inner.put("list", new LinkedList<>(Arrays.asList(pojo)));
        m5.put(1, inner);
        dto.setM5(m5);

        dto.setTreeMapValues(new TreeMap<>(Map.of(1, "one", 2, "two")));
        dto.setLhm(new LinkedHashMap<>(Map.of(1, pojo)));
        dto.setGm(ImmutableMap.of(1, "one", 2, "two"));
        dto.setIm(ImmutableMap.of(1, Arrays.asList(pojo)));
        dto.setR(Range.closed(today.minusDays(1), today.plusDays(1)));
        dto.setRm(rm);
        dto.setMm(mmString);
        dto.setIrm(ImmutableRangeMap.<LocalDate, String>builder()
                .put(Range.closed(today.minusDays(1), today.plusDays(1)), "window")
                .build());
        dto.setIr(ImmutableRangeMap.<LocalDate, HashMultimap<Integer, POJO>>builder()
                .put(Range.closed(today.minusDays(1), today), HashMultimap.create(mmInt))
                .build());
        dto.setRangeTable(table);
        dto.setPojoRangeTable(pojoTable);

        return dto;
    }

    private boolean shouldIgnore(Throwable t) {
        Throwable current = t;
        while (current != null) {
            if (current instanceof TargetDisconnectedException) {
                return true;
            }
            if (current instanceof HazelcastInstanceNotActiveException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.contains("NO_MIGRATION")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    @BeforeRun
    public void beforeRun(ThreadState state) {
        state.setNewThreadId = THREAD_PREFIX_SEQ.incrementAndGet();
        state.setNewKeyPrefix = setNewKeyPrefixBase + state.setNewThreadId + "-" + System.nanoTime() + "-";
        state.setNewSequence = 0;
        state.setNewDtoIndex = state.randomInt(dtoPool.size());
    }

    @AfterRun
    public void afterRun(ThreadState state) {
        if (state.setNewSequence > 0) {
            setNewOps.add(state.setNewSequence);
        }
        state.setNewKeyPrefix = null;
    }

    @Verify(global = false)
    public void verifySetNew() {
        long expected = setNewOps.sum();
        int actual = trainDto.keySet(Predicates.like("__key", setNewKeyPrefixBase + "%")).size();
        assertEquals("setNew key count mismatch for " + setNewKeyPrefixBase, expected, actual);
    }

    private String resolveWorkerId() {
        String workerAddress = System.getenv("WORKER_ADDRESS");
        if (workerAddress != null && !workerAddress.trim().isEmpty()) {
            return workerAddress;
        }
        String workerName = System.getenv("WORKER_NAME");
        if (workerName != null && !workerName.trim().isEmpty()) {
            return workerName;
        }
        String workerIndex = System.getenv("WORKER_INDEX");
        if (workerIndex != null && !workerIndex.trim().isEmpty()) {
            return "W" + workerIndex;
        }
        return "unknown";
    }
}
