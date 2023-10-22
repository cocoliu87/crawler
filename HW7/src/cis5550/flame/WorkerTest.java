package cis5550.flame;

import cis5550.kvs.Row;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class WorkerTest {
    @Test
    public void joinRows_Success() {
        String k = "key_1";
        Row r1 = new Row(k), r2 = new Row(k), expected = new Row(k);
        String cKey1 = UUID.randomUUID().toString().split("-")[0], cKey2 = UUID.randomUUID().toString().split("-")[0];
        r1.put(cKey1, "hello");
        r2.put(cKey2, "world");
        expected.put(cKey1+Worker.Delimiter+cKey2, "hello,world");

        Row joined = Worker.joinRows(r1, r2);
        Assert.assertNotNull(joined);
        Assert.assertEquals(expected.toString(), joined.toString());
    }

    @Test
    public void joinRows_Null() {
        String k = "key_1";
        Row r1 = new Row(k), r2 = new Row(k+"a");
        String cKey1 = UUID.randomUUID().toString().split("-")[0], cKey2 = UUID.randomUUID().toString().split("-")[0];
        r1.put(cKey1, "hello");
        r2.put(cKey2, "world");

        Row joined = Worker.joinRows(r1, r2);
        Assert.assertNull(joined);
    }

    @Test
    public void groupRows_Success() {
        String k = "key_1";
        Row r1 = new Row(k), r2 = new Row(k);
        r1.put("r1_c_1", "apple");
        r1.put("r1_c_2", "banana");
        r2.put("r2_c_1", "cherry");
        r2.put("r2_c_2", "date");
        r2.put("r2_c_3", "fig");

        FlamePair grouped = Worker.groupRows(r1, r2);
        Assert.assertNotNull(grouped);

        String[] strs = grouped.b.replace("[", "").replace("]", "").split(",");
        Assert.assertEquals(5, strs.length);
        Set<String> set = new HashSet<>(Arrays.asList(strs));
        Assert.assertEquals(5, set.size());
        for (String s: new String[]{"apple", "banana", "cherry", "date", "fig"}) {
            Assert.assertTrue(set.contains(s));
        }
    }
}
