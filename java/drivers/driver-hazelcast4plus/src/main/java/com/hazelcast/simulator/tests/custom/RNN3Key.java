package com.hazelcast.simulator.tests.custom;

import java.io.Serializable;

import com.hazelcast.partition.PartitionAware;

public class RNN3Key implements PartitionAware<Long>, Serializable {
    private final Long unique_key;
    private final Long cust_xref;

    public RNN3Key(Long uniqueKey, Long custXref) {
        unique_key = uniqueKey;
        cust_xref = custXref;
    }

    public Long getUnique_key() {
        return unique_key;
    }

    public Long getCust_xref() {
        return cust_xref;
    }

    @Override
    public Long getPartitionKey() {
        return cust_xref;
    }
}
