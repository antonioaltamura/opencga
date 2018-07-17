package org.opencb.opencga.storage.hadoop.variant.index.phoenix;

import org.apache.hadoop.hbase.io.ImmutableBytesWritable;
import org.apache.hadoop.hbase.util.ByteStringer;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.phoenix.coprocessor.generated.PTableProtos;
import org.apache.phoenix.schema.*;
import org.junit.Test;
import org.opencb.biodata.models.variant.Variant;

import java.sql.SQLException;
import java.util.*;

import static org.junit.Assert.*;
import static org.opencb.opencga.storage.hadoop.variant.index.phoenix.VariantPhoenixHelper.OPTIONAL_PRIMARY_KEY;

/**
 * Created on 25/06/18.
 *
 * @author Jacobo Coll &lt;jacobo167@gmail.com&gt;
 */
public class VariantPhoenixKeyFactoryTest {

    @Test
    public void testVariantRowKey() throws Exception {
        checkVariantRowKeyGeneration(new Variant("5", 21648, "A", "T"));
        checkVariantRowKeyGeneration(new Variant("5", 21648, "AAAAAA", "T"));
        checkVariantRowKeyGeneration(new Variant("5", 21648, "A", ""));
        checkVariantRowKeyGeneration(new Variant("5", 21648, "AAT", "TTT"));
        checkVariantRowKeyGeneration(new Variant("X", 21648, "", "TTT"));
        checkVariantRowKeyGeneration(new Variant("MT", 21648, "", ""));
    }

    @Test
    public void testStructuralVariantRowKey() throws Exception {
        checkVariantRowKeyGeneration(new Variant("5:110-510:-:<DEL>"));
        checkVariantRowKeyGeneration(new Variant("5:100<110<120-500<510<520:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA:-"));
        checkVariantRowKeyGeneration(new Variant("5:100<110<120-500<510<520:A:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"));
        checkVariantRowKeyGeneration(new Variant("5:100<110<120-500<510<520:A:<DEL>"));
        checkVariantRowKeyGeneration(new Variant("5:100<110<120-500<510<520:-:<DEL>"));
        checkVariantRowKeyGeneration(new Variant("5:100<110<120-500<510<520:A:<CN5>"));
        checkVariantRowKeyGeneration(new Variant("5:100<110<120-500<510<520:-:<CN5>"));
        checkVariantRowKeyGeneration(new Variant("5:100:A:A]:chr5:234]"));
    }

    public void checkVariantRowKeyGeneration(Variant variant) {
        byte[] phoenixRowKey = generateVariantRowKeyPhoenix(variant);

//        System.out.println("expected = " + Bytes.toStringBinary(phoenixRowKey));

        byte[] variantRowkey = VariantPhoenixKeyFactory.generateVariantRowKey(variant);
//        System.out.println("actual   = " + Bytes.toStringBinary(variantRowkey));
        Variant generatedVariant = VariantPhoenixKeyFactory.extractVariantFromVariantRowKey(variantRowkey);

        assertArrayEquals(variant.toString(), phoenixRowKey, variantRowkey);
        assertEquals(variant, generatedVariant);
    }

    public byte[] generateVariantRowKeyPhoenix(Variant variant) {

        Set<VariantPhoenixHelper.VariantColumn> nullableColumn = new HashSet<>(Arrays.asList(
                VariantPhoenixHelper.VariantColumn.REFERENCE,
                VariantPhoenixHelper.VariantColumn.ALTERNATE
//                VariantPhoenixHelper.VariantColumn.SV_END,
//                VariantPhoenixHelper.VariantColumn.CI_START_L,
//                VariantPhoenixHelper.VariantColumn.CI_START_R,
//                VariantPhoenixHelper.VariantColumn.CI_END_L,
//                VariantPhoenixHelper.VariantColumn.CI_END_R
        ));

        PTableImpl table;
        try {
            List<PColumn> columns = new ArrayList<>();
            for (PhoenixHelper.Column column : VariantPhoenixHelper.PRIMARY_KEY) {
                if (!variant.isSV() && OPTIONAL_PRIMARY_KEY.contains(column)) {
                    break;
                }
                columns.add(PColumnImpl.createFromProto(PTableProtos.PColumn.newBuilder()
                        .setColumnNameBytes(ByteStringer.wrap(PNameFactory.newName(column.column()).getBytes()))
                        .setDataType(column.getPDataType().getSqlTypeName())
                        .setPosition(columns.size())
                        .setNullable(nullableColumn.contains(column))
                        .setSortOrder(SortOrder.ASC.getSystemValue()).build()));
            }

            table = PTableImpl.makePTable(new PTableImpl(), columns);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        ImmutableBytesWritable key = new ImmutableBytesWritable();
        if (variant.isSV()) {
            table.newKey(key, new byte[][]{
                    Bytes.toBytes(variant.getChromosome()),
                    Bytes.toBytes(variant.getStart()),
                    Bytes.toBytes(variant.getReference()),
                    Bytes.toBytes(variant.getAlternate()),
                    Bytes.toBytes(variant.getEnd()),
                    Bytes.toBytes(variant.getSv()==null||variant.getSv().getCiStartLeft() == null ? 0 : variant.getSv().getCiStartLeft()),
                    Bytes.toBytes(variant.getSv()==null||variant.getSv().getCiStartRight() == null ? 0 : variant.getSv().getCiStartRight()),
                    Bytes.toBytes(variant.getSv()==null||variant.getSv().getCiEndLeft() == null ? 0 : variant.getSv().getCiEndLeft()),
                    Bytes.toBytes(variant.getSv()==null||variant.getSv().getCiEndRight() == null ? 0 : variant.getSv().getCiEndRight()),
                    Bytes.toBytes(variant.getSv()==null||variant.getSv().getCopyNumber() == null ? 0 : variant.getSv().getCopyNumber())
            });
        } else {
            table.newKey(key, new byte[][]{
                    Bytes.toBytes(variant.getChromosome()),
                    Bytes.toBytes(variant.getStart()),
                    Bytes.toBytes(variant.getReference()),
                    Bytes.toBytes(variant.getAlternate())
            });
        }

        if (key.getLength() == key.get().length) {
            return key.get();
        } else {
            return Arrays.copyOf(key.get(), key.getLength());
        }
    }
}