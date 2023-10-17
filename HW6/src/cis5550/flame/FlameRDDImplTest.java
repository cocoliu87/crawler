package cis5550.flame;

import cis5550.kvs.Row;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class FlameRDDImplTest {
    @Test
    public void getIntersection_hasIntersection() {
        List<Row> rowsOne = new ArrayList<>();
        rowsOne.add(createRandomRowWithOneColumn(null));
        String same = "hello world";
        rowsOne.add(createRandomRowWithOneColumn(same));

        List<Row> rowsTwo = new ArrayList<>();
        rowsTwo.add(createRandomRowWithOneColumn(null));
        rowsTwo.add(createRandomRowWithOneColumn(same));

        Set<Row> intersection = new FlameRDDImpl(null, null, null).getIntersection(rowsOne.iterator(), rowsTwo.iterator());
        Assert.assertEquals(1, intersection.size());
        String intersectedValue = "";
        for (Row r: intersection) {
            intersectedValue = r.get("values");
        }
        Assert.assertEquals("hello world", intersectedValue);
    }

    @Test
    public void getIntersection_hasNoIntersection() {
        List<Row> rowsOne = new ArrayList<>();
        rowsOne.add(createRandomRowWithOneColumn(null));
        String same = "hello world";
        rowsOne.add(createRandomRowWithOneColumn(same));

        List<Row> rowsTwo = new ArrayList<>();
        rowsTwo.add(createRandomRowWithOneColumn(null));
        rowsTwo.add(createRandomRowWithOneColumn(same + "!"));

        Set<Row> intersection = new FlameRDDImpl(null, null, null).getIntersection(rowsOne.iterator(), rowsTwo.iterator());
        Assert.assertEquals(0, intersection.size());
        String intersectedValue = "";
        for (Row r: intersection) {
            intersectedValue = r.get("values");
        }
        Assert.assertNotEquals("hello world", intersectedValue);
    }

    @Test
    public void getIntersection_hasIntersectionWithTwoColumns() {
        List<Row> rowsOne = new ArrayList<>();
        rowsOne.add(createRandomRowWithTwoColumn(null));
        String[] same = new String[]{"hello", "world"};
        rowsOne.add(createRandomRowWithTwoColumn(same));

        List<Row> rowsTwo = new ArrayList<>();
        rowsTwo.add(createRandomRowWithTwoColumn(null));
        rowsTwo.add(createRandomRowWithTwoColumn(same));

        Set<Row> intersection = new FlameRDDImpl(null, null, null).getIntersection(rowsOne.iterator(), rowsTwo.iterator());
        Assert.assertEquals(1, intersection.size());
        Row row = new Row("");
        for (Row r: intersection) {
            row = r;
        }
        Assert.assertEquals("hello world", row.get("values_0") + " " + row.get("values_1"));
    }

    private Row createRandomRowWithOneColumn(String value) {
        Row row = new Row(randomString());
        String values = value == null? randomString() : value;
        row.put("values", values);
        return row;
    }

    private Row createRandomRowWithTwoColumn(String[] value) {
        Row row = new Row(randomString());
        String[] values = value == null? new String[]{randomString(), randomString()} : value;
        row.put("values_0", values[0]);
        row.put("values_1", values[1]);
        return row;
    }

    private String randomString() {
        int randomNum = ThreadLocalRandom.current().nextInt(0, 4 + 1);
        return UUID.randomUUID().toString().split("-")[randomNum];
    }
}
