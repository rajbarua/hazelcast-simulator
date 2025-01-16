/*
    org/aexp/grt/serializer/RNN3Serializer.java
    Autogenerated by Hazelcast CLC v5.4.1
    2024-08-07T14:55:13+05:30
*/

package com.hazelcast.simulator.tests.custom;

import com.hazelcast.nio.serialization.compact.CompactReader;
import com.hazelcast.nio.serialization.compact.CompactSerializer;
import com.hazelcast.nio.serialization.compact.CompactWriter;




public class RNN3Serializer implements CompactSerializer<RNN3> {
    @Override
    public RNN3 read(CompactReader reader) {
        RNN3 o = new RNN3();
        o.setUnique_key(reader.readInt64("unique_key"));
        o.setCust_xref(reader.readInt64("cust_xref"));
        o.setAav_inp(reader.readFloat64("aav_inp"));
        o.setDisruption_inp(reader.readFloat64("disruption_inp"));
        o.setEmail_inp(reader.readFloat64("email_inp"));
        o.setFtg_inp(reader.readFloat64("ftg_inp"));
        o.setIndusamt_inp(reader.readFloat64("indusamt_inp"));
        o.setIp_inp(reader.readFloat64("ip_inp"));
        o.setLocation_inp(reader.readFloat64("location_inp"));
        o.setOnword_inp(reader.readFloat64("onword_inp"));
        o.setPhone_inp(reader.readFloat64("phone_inp"));
        o.setSeprn_inp(reader.readFloat64("seprn_inp"));
        o.setSepry_inp(reader.readFloat64("sepry_inp"));
        o.setStcd_inp(reader.readFloat64("stcd_inp"));
        o.setTxn_att_inp(reader.readFloat64("txn_att_inp"));
        o.setTopmaps_inp(reader.readFloat64("topmaps_inp"));
        o.setNgttime_inp(reader.readFloat64("ngttime_inp"));
        return o;
    }

    @Override
    public void write(CompactWriter writer, RNN3 object) {
        writer.writeInt64("unique_key", object.getUnique_key());
        writer.writeInt64("cust_xref", object.getCust_xref());
        writer.writeFloat64("aav_inp", object.getAav_inp());
        writer.writeFloat64("disruption_inp", object.getDisruption_inp());
        writer.writeFloat64("email_inp", object.getEmail_inp());
        writer.writeFloat64("ftg_inp", object.getFtg_inp());
        writer.writeFloat64("indusamt_inp", object.getIndusamt_inp());
        writer.writeFloat64("ip_inp", object.getIp_inp());
        writer.writeFloat64("location_inp", object.getLocation_inp());
        writer.writeFloat64("onword_inp", object.getOnword_inp());
        writer.writeFloat64("phone_inp", object.getPhone_inp());
        writer.writeFloat64("seprn_inp", object.getSeprn_inp());
        writer.writeFloat64("sepry_inp", object.getSepry_inp());
        writer.writeFloat64("stcd_inp", object.getStcd_inp());
        writer.writeFloat64("txn_att_inp", object.getTxn_att_inp());
        writer.writeFloat64("topmaps_inp", object.getTopmaps_inp());
        writer.writeFloat64("ngttime_inp", object.getNgttime_inp());
    }

    @Override
    public Class<RNN3> getCompactClass() {
        return RNN3.class;
    }

    @Override
    public String getTypeName() {
        return "rnn3";
    }
}
