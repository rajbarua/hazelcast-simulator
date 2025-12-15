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
import com.hazelcast.simulator.hz.HazelcastTest;
import com.hazelcast.simulator.test.BaseThreadState;
import com.hazelcast.simulator.test.annotations.Prepare;
import com.hazelcast.simulator.test.annotations.Setup;
import com.hazelcast.simulator.test.annotations.Teardown;
import com.hazelcast.simulator.test.annotations.TimeStep;
import com.hz.demo.domain.POJONoCS;
import com.hz.demo.domain.TestDtoNoCS;

public class TrainTestNoCS extends HazelcastTest {

    // properties
    public long entryCount = 100_000;


    private IMap<String, TestDtoNoCS> trainDto;
    private List<String> keys;
    private List<TestDtoNoCS> dtoPool;

    @Setup
    public void setUp() {
        trainDto = targetInstance.getMap(name);
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
    public TestDtoNoCS get(ThreadState ts) {
        return trainDto.get(ts.randomKey());
    }

    @TimeStep(prob = 0.0)
    public TestDtoNoCS put(ThreadState ts) {
        String key = ts.randomKey();
        TestDtoNoCS dto = ts.randomDto();
        return trainDto.put(key, dto);
    }

    @TimeStep(prob = 0.2)
    public void set(ThreadState ts) {
        String key = ts.randomKey();
        trainDto.set(key, ts.randomDto());
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
        public String randomKey() {
            return keys.get(randomInt(keys.size()));
        }

        public TestDtoNoCS randomDto() {
            return dtoPool.get(randomInt(dtoPool.size()));
        }
    }

    @Teardown
    public void tearDown() {
        // trainDto.destroy();
    }

    private TestDtoNoCS createRandomDto(String id) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        POJONoCS pojoNoCS = new POJONoCS("pojoNoCS-" + id, today.minusDays(rnd.nextInt(30)), "desc-" + id);
        List<Integer> ints = Arrays.asList(rnd.nextInt(1000), rnd.nextInt(1000), rnd.nextInt(1000));

        ImmutableRangeMap<LocalDate, POJONoCS> m3Val = ImmutableRangeMap.<LocalDate, POJONoCS>builder()
                .put(Range.closed(today.minusDays(1), today.plusDays(1)), pojoNoCS)
                .build();
        ImmutableRangeMap<LocalDate, List<POJONoCS>> m4Val = ImmutableRangeMap.<LocalDate, List<POJONoCS>>builder()
                .put(Range.closed(today.minusDays(2), today.minusDays(1)), Arrays.asList(pojoNoCS))
                .build();
        RangeMap<Integer, List<POJONoCS>> rm = TreeRangeMap.create();
        rm.put(Range.closed(0, 10), Arrays.asList(pojoNoCS));

        HashMultimap<String, POJONoCS> mmString = HashMultimap.create();
        mmString.put("k1", pojoNoCS);
        HashMultimap<Integer, POJONoCS> mmInt = HashMultimap.create();
        mmInt.put(1, pojoNoCS);

        Table<String, Integer, ImmutableRangeMap<LocalDate, List<String>>> table = HashBasedTable.create();
        table.put("row", 1, ImmutableRangeMap.<LocalDate, List<String>>builder()
                .put(Range.closed(today.minusDays(1), today), Arrays.asList("a", "b"))
                .build());
        Table<String, Integer, ImmutableRangeMap<LocalDate, POJONoCS>> pojoNoCSTable = HashBasedTable.create();
        pojoNoCSTable.put("row", 1, m3Val);

        TestDtoNoCS dto = new TestDtoNoCS();
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
        dto.setL2(Arrays.asList(pojoNoCS));
        dto.setIl(ImmutableList.copyOf(ints));
        dto.setTrainMasterList(new LinkedList<>(ints));
        dto.setSet(ints.stream().collect(Collectors.toSet()));

        Map<String, List<String>> m1 = new HashMap<>();
        m1.put("k1", Arrays.asList("v1", "v2"));
        dto.setM1(m1);

        Map<String, Map<String, String>> m2 = new HashMap<>();
        m2.put("m2", Map.of("a", "b"));
        dto.setM2(m2);

        Map<String, ImmutableRangeMap<LocalDate, POJONoCS>> m3 = new HashMap<>();
        m3.put("r1", m3Val);
        dto.setM3(m3);

        HashMap<String, ImmutableRangeMap<LocalDate, List<POJONoCS>>> m4 = new HashMap<>();
        m4.put("r2", m4Val);
        dto.setM4(m4);

        HashMap<Integer, HashMap<String, LinkedList<POJONoCS>>> m5 = new HashMap<>();
        HashMap<String, LinkedList<POJONoCS>> inner = new HashMap<>();
        inner.put("list", new LinkedList<>(Arrays.asList(pojoNoCS)));
        m5.put(1, inner);
        dto.setM5(m5);

        dto.setTreeMapValues(new TreeMap<>(Map.of(1, "one", 2, "two")));
        dto.setLhm(new LinkedHashMap<>(Map.of(1, pojoNoCS)));
        dto.setGm(ImmutableMap.of(1, "one", 2, "two"));
        dto.setIm(ImmutableMap.of(1, Arrays.asList(pojoNoCS)));
        dto.setR(Range.closed(today.minusDays(1), today.plusDays(1)));
        dto.setRm(rm);
        dto.setMm(mmString);
        dto.setIrm(ImmutableRangeMap.<LocalDate, String>builder()
                .put(Range.closed(today.minusDays(1), today.plusDays(1)), "window")
                .build());
        dto.setIr(ImmutableRangeMap.<LocalDate, HashMultimap<Integer, POJONoCS>>builder()
                .put(Range.closed(today.minusDays(1), today), HashMultimap.create(mmInt))
                .build());
        dto.setRangeTable(table);
        dto.setPojoRangeTable(pojoNoCSTable);

        return dto;
    }
}
