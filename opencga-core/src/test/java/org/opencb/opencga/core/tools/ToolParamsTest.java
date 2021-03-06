package org.opencb.opencga.core.tools;

import org.junit.Test;
import org.opencb.commons.datastore.core.ObjectMap;

import java.io.IOException;
import java.util.*;

import static org.junit.Assert.*;

public class ToolParamsTest {

    private MyToolParams p = new MyToolParams();
    private MyToolWithDynamicParams pd = new MyToolWithDynamicParams();;

    public static class MyToolParams extends ToolParams {
        private String myKey = "asdf";
        private String myKey2;
        private boolean myBoolean;
        private boolean myBooleanTrue = true;
        private Boolean myBooleanNullable;
        private Boolean myBooleanNullableTrue = true;
        private int myInteger;
        private Integer myIntegerNullable;
        private List<String> myList;
        private List<Integer> myIntList;
        private String myPrivateString = "private!"; // Does not have any getter or setter

        public String getMyKey() {
            return myKey;
        }

        public MyToolParams setMyKey(String myKey) {
            this.myKey = myKey;
            return this;
        }

        public String getMyKey2() {
            return myKey2;
        }

        public MyToolParams setMyKey2(String myKey2) {
            this.myKey2 = myKey2;
            return this;
        }

        public boolean isMyBoolean() {
            return myBoolean;
        }

        public MyToolParams setMyBoolean(boolean myBoolean) {
            this.myBoolean = myBoolean;
            return this;
        }

        public boolean isMyBooleanTrue() {
            return myBooleanTrue;
        }

        public MyToolParams setMyBooleanTrue(boolean myBooleanTrue) {
            this.myBooleanTrue = myBooleanTrue;
            return this;
        }

        public Boolean getMyBooleanNullable() {
            return myBooleanNullable;
        }

        public MyToolParams setMyBooleanNullable(Boolean myBooleanNullable) {
            this.myBooleanNullable = myBooleanNullable;
            return this;
        }

        public Boolean getMyBooleanNullableTrue() {
            return myBooleanNullableTrue;
        }

        public MyToolParams setMyBooleanNullableTrue(Boolean myBooleanNullableTrue) {
            this.myBooleanNullableTrue = myBooleanNullableTrue;
            return this;
        }

        public int getMyInteger() {
            return myInteger;
        }

        public MyToolParams setMyInteger(int myInteger) {
            this.myInteger = myInteger;
            return this;
        }

        public Integer getMyIntegerNullable() {
            return myIntegerNullable;
        }

        public MyToolParams setMyIntegerNullable(Integer myIntegerNullable) {
            this.myIntegerNullable = myIntegerNullable;
            return this;
        }

        public List<String> getMyList() {
            return myList;
        }

        public MyToolParams setMyList(List<String> myList) {
            this.myList = myList;
            return this;
        }

        public List<Integer> getMyIntList() {
            return myIntList;
        }

        public MyToolParams setMyIntList(List<Integer> myIntList) {
            this.myIntList = myIntList;
            return this;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MyToolParams that = (MyToolParams) o;
            return myBoolean == that.myBoolean &&
                    myBooleanTrue == that.myBooleanTrue &&
                    myInteger == that.myInteger &&
                    Objects.equals(myKey, that.myKey) &&
                    Objects.equals(myKey2, that.myKey2) &&
                    Objects.equals(myBooleanNullable, that.myBooleanNullable) &&
                    Objects.equals(myBooleanNullableTrue, that.myBooleanNullableTrue) &&
                    Objects.equals(myIntegerNullable, that.myIntegerNullable) &&
                    Objects.equals(myPrivateString, that.myPrivateString);
        }

        @Override
        public int hashCode() {
            return Objects.hash(myKey, myKey2, myBoolean, myBooleanTrue, myBooleanNullable, myBooleanNullableTrue, myInteger, myIntegerNullable, myPrivateString);
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("MyToolParams{");
            sb.append("myKey='").append(myKey).append('\'');
            sb.append(", myKey2='").append(myKey2).append('\'');
            sb.append(", myBoolean=").append(myBoolean);
            sb.append(", myBooleanTrue=").append(myBooleanTrue);
            sb.append(", myBooleanNullable=").append(myBooleanNullable);
            sb.append(", myBooleanNullableTrue=").append(myBooleanNullableTrue);
            sb.append(", myInteger=").append(myInteger);
            sb.append(", myIntegerNullable=").append(myIntegerNullable);
            sb.append(", myPrivateString='").append(myPrivateString).append('\'');
            sb.append('}');
            return sb.toString();
        }
    }

    public static class MyToolWithDynamicParams extends MyToolParams {
        public Map<String, String> dynamicParams;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            if (!super.equals(o)) return false;
            MyToolWithDynamicParams that = (MyToolWithDynamicParams) o;
            return Objects.equals(dynamicParams, that.dynamicParams);
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), dynamicParams);
        }
    }

    @Test
    public void testToParams() throws IOException {
        Map<String, Object> params = p.toParams();

        assertEquals("asdf", params.get("myKey"));
        assertNull(params.get("myKey2"));
        assertEquals("", params.get("myBooleanTrue"));
        assertEquals("true", params.get("myBooleanNullableTrue"));
        assertNull(params.get("myBoolean"));
        assertNull(params.get("myBooleanNullable"));
        assertEquals("0", params.get("myInteger"));
        assertNull(params.get("myIntegerNullable"));

        assertEquals(p, ToolParams.fromParams(MyToolParams.class, params));
    }

    @Test
    public void testToParamsDynamic() throws IOException {
        pd.dynamicParams = new HashMap<>();
        pd.dynamicParams.put("otherParam", "value");
        pd.dynamicParams.put("myKey", "overwrite");
        Map<String, Object> params = pd.toParams();

        assertEquals(5, params.size());
        assertEquals("asdf", params.get("myKey"));
        assertNull(params.get("myKey2"));
        assertEquals("", params.get("myBooleanTrue"));
        assertEquals("true", params.get("myBooleanNullableTrue"));
        assertNull(params.get("myBoolean"));
        assertNull(params.get("myBooleanNullable"));
        assertEquals("0", params.get("myInteger"));
        assertNull(params.get("myIntegerNullable"));
        assertEquals(pd.dynamicParams, params.get("dynamicParams"));
        assertNotSame(pd.dynamicParams, params.get("dynamicParams"));

        assertEquals(pd, ToolParams.fromParams(MyToolWithDynamicParams.class, params));
    }

    @Test
    public void testToObjectMap() throws IOException {
        pd.dynamicParams = new HashMap<>();
        pd.dynamicParams.put("otherParam", "value");
        pd.dynamicParams.put("myKey", "overwrite");
        ObjectMap params = pd.toObjectMap();

        assertEquals(6, params.size());
        assertEquals("asdf", params.get("myKey"));
        assertNull(params.get("myKey2"));
        assertEquals(true, params.get("myBooleanTrue"));
        assertEquals(true, params.get("myBooleanNullableTrue"));
        assertEquals(false, params.get("myBoolean"));
        assertNull(params.get("myBooleanNullable"));
        assertEquals(0, params.get("myInteger"));
        assertNull(params.get("myIntegerNullable"));
        assertEquals(pd.dynamicParams, params.get("dynamicParams"));

        assertEquals(pd, ToolParams.fromParams(MyToolWithDynamicParams.class, params));
    }

    @Test
    public void testUpdateParams() {

        pd.updateParams(new ObjectMap("myKey", "KEY"));
        pd.updateParams(new ObjectMap("myBoolean", "true"));
        pd.updateParams(new ObjectMap("myInteger", "1154"));
        pd.updateParams(new ObjectMap("otherMissingParam", "1154"));
        pd.updateParams(new ObjectMap("dynamicParams", Collections.singletonMap("key", "value")));

//        System.out.println("pd = " + pd);
        assertEquals(pd.getMyKey(), "KEY");
        assertEquals(pd.isMyBoolean(), true);
        assertEquals(pd.getMyInteger(), 1154);
        assertEquals(pd.dynamicParams, Collections.singletonMap("key", "value"));

        MyToolWithDynamicParams expected = new MyToolWithDynamicParams();
        expected.setMyKey("KEY");
        expected.setMyBoolean(true);
        expected.setMyInteger(1154);
        expected.dynamicParams = Collections.singletonMap("key", "value");

        assertEquals(expected, pd);

    }

    @Test
    public void testList() {
        p.updateParams(new ObjectMap("myList", "1,2,3"));
        p.updateParams(new ObjectMap("myIntList", "1,2,3"));

        assertEquals(Arrays.asList("1", "2", "3"), p.getMyList());
        assertEquals(Arrays.asList(1, 2, 3), p.getMyIntList());
    }
}