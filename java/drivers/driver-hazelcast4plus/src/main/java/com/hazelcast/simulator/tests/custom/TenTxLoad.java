package com.hazelcast.simulator.tests.custom;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import com.hazelcast.map.IMap;
import com.hazelcast.query.Predicates;
import com.hazelcast.simulator.hz.HazelcastTest;
import com.hazelcast.simulator.test.BaseThreadState;
import com.hazelcast.simulator.test.annotations.Prepare;
import com.hazelcast.simulator.test.annotations.Setup;
import com.hazelcast.simulator.test.annotations.Teardown;
import com.hazelcast.simulator.test.annotations.TimeStep;

public class TenTxLoad extends HazelcastTest {

    // properties
    public String filePathAndName = "~/data/RNN3_SampleData10M.csv";
    public String customersFilePath = "~/data/custIds.txt";
    public boolean partitioned = false;
    public long entryCount = 10_000_000;
    
    
    private List<Long> custIds;
    private int custIdsLength;
    private IMap<Long, RNN3> unpartitionedMap;
    private IMap<RNN3Key, RNN3> partitionedMap;

    @Setup
    public void setUp() throws Exception{
        if(partitioned){
            partitionedMap = targetInstance.getMap(name);
        }else{
            unpartitionedMap = targetInstance.getMap(name);
        }
        custIds = Files.lines(Path.of(customersFilePath)).map(Long::valueOf).collect(Collectors.toList());
        custIdsLength = custIds.size();
    }

    /**
     * If global is set to true, only one test instance will do the data load. This can take a while to load the data.
     * If global is set to false and we have more than one instance specified in the test configuration, the data load will be loaded by each instance. 
     * This can improve data load.
     */
    @Prepare(global = false)
    public void prepare() throws Exception {
        
        String[] header = Files.lines(Paths.get(filePathAndName)).findFirst().get().split(",", 0);


        if(partitioned){
            partitionedMap = targetInstance.getMap(name);
        }else{
            unpartitionedMap = targetInstance.getMap(name);
        }

        System.out.println(System.nanoTime() + " - Starting to Load Data");


        long start = System.nanoTime();
        // int partitionedMapSize = partitionedMap.size();
        // int unpartitionedMapSize = unpartitionedMap.size();
        // if(partitionedMapSize > 0 || unpartitionedMapSize > 0){
        //     System.out.println("Data already loaded. Skipping data load.");
        //     return;
        // }
        Files.lines(Paths.get(filePathAndName))
                                //skip the header row
                                .skip(1)
                                .limit(entryCount)
                                .parallel()
                                // Parallel and batching can be done
                                .map(line -> line.split(",", 0))
                                .map(row -> (RNN3)Util.createCCTrans(row, RNN3.class, header))
                                .forEach(rnn3 -> {
                                    if(partitioned){
                                        partitionedMap.set(new RNN3Key(rnn3.getUnique_key(), rnn3.getCust_xref()), rnn3);
                                    }else{
                                        unpartitionedMap.set(rnn3.getUnique_key(), rnn3);
                                    }
                                });
        Long totalTime = System.nanoTime() - start;

        System.out.println(System.nanoTime() + " - Completed Data Load for RNN3");

        System.out.println(
                String.format("Data loaded in %d nanosecs - %f ms",
                        totalTime, ((double)totalTime)/1_000_000.0));
        if(partitioned){
            System.out.println("Loaded Partitioned Map Size: " + partitionedMap.size());
        }else{
            System.out.println("Loaded Unpartitioned Map Size: " + unpartitionedMap.size());
        }

    }

    @TimeStep
    public void predicate(ThreadState ts) throws Exception {
        Long custId = custIds.get(ts.randomInt(custIdsLength-1));
        if(partitioned){
            // PartitionPredicate<RNN3Key, RNN3> partitionPredicate 
            //     = Predicates.partitionPredicate(custId, Predicates.equal("cust_xref", custId));
            // Set<Entry<RNN3Key, RNN3>> partitionedEntries = partitionedMap.entrySet(partitionPredicate);
            Set<Entry<RNN3Key, RNN3>> partitionedEntries = partitionedMap.entrySet(Predicates.equal("cust_xref", custId));
            if (partitionedEntries.size() != 10) {
                System.out.println(String.format("custId = %d, partitionedEntries.size() = %d", custId, partitionedEntries.size()));
                // throw new Exception("wrong entry count");
            }
        }else{
            Set<Entry<Long, RNN3>> unpartitionedEntries 
                = unpartitionedMap.entrySet(Predicates.equal("cust_xref", custId));
                if (unpartitionedEntries.size() != 10) {
                    System.out.println(String.format("custId = %d, unpartitionedEntries.size() = %d", custId, unpartitionedEntries.size()));
                    // throw new Exception("wrong entry count");
                }
            }
    }
    @TimeStep
    public void insert(ThreadState ts) throws Exception {
        Double randomVal = ts.randomDouble();
        RNN3 rnn3 = new RNN3();
        rnn3.setUnique_key(ts.randomLong());
        rnn3.setCust_xref(custIds.get(ts.randomInt(custIdsLength-1)));
        rnn3.setAav_inp(randomVal);
        rnn3.setDisruption_inp(randomVal);
        rnn3.setEmail_inp(randomVal);
        rnn3.setFtg_inp(randomVal);
        rnn3.setIndusamt_inp(randomVal);
        rnn3.setIp_inp(randomVal);
        rnn3.setLocation_inp(randomVal);
        rnn3.setOnword_inp(randomVal);
        rnn3.setPhone_inp(randomVal);
        rnn3.setSeprn_inp(randomVal);
        rnn3.setSepry_inp(randomVal);
        rnn3.setStcd_inp(randomVal);
        rnn3.setTxn_att_inp(randomVal);
        rnn3.setTopmaps_inp(randomVal);
        rnn3.setNgttime_inp(randomVal);
        if(partitioned){
            partitionedMap.set(new RNN3Key(rnn3.getUnique_key(), rnn3.getCust_xref()), rnn3);
        }else{
            unpartitionedMap.set(rnn3.getUnique_key(), rnn3);
        }
    }
    @TimeStep
    public void searchAndDelete(ThreadState ts) throws Exception {
        Long custId = custIds.get(ts.randomInt(custIdsLength-1));
        if(partitioned){
            Set<Entry<RNN3Key, RNN3>> partitionedEntries = partitionedMap.entrySet(Predicates.equal("cust_xref", custId));
            if (partitionedEntries.size() > 0) {
                partitionedMap.deleteAsync(partitionedEntries.iterator().next().getKey());
            }
        }else{
            Set<Entry<Long, RNN3>> unpartitionedEntries 
                = unpartitionedMap.entrySet(Predicates.equal("cust_xref", custId));
            if (unpartitionedEntries.size() > 0) {
                unpartitionedMap.deleteAsync(unpartitionedEntries.iterator().next().getKey());
            }
        }
    }

    public class ThreadState extends BaseThreadState {
    }

    @Teardown
    public void tearDown() {
        if(partitioned){
            partitionedMap.destroy();
        }else{  
            unpartitionedMap.destroy();
        }
    }
}
