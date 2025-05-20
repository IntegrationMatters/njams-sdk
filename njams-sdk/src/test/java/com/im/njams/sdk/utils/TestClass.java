package com.im.njams.sdk.utils;

public class TestClass {

    private int primitiveInt = 0;
    private Integer wrappedInt = 0;
    private boolean primitiveBoolean = true;
    private Boolean wrappedBoolean = true;
    private String string = null;
    private TestClass other = null;

    public TestClass() {

    }

    public TestClass(int primitiveInt, Integer wrappedInt, boolean primitiveBoolean, Boolean wrappedBoolean,
            String string) {
        this.primitiveInt = primitiveInt;
        this.wrappedInt = wrappedInt;
        this.primitiveBoolean = primitiveBoolean;
        this.wrappedBoolean = wrappedBoolean;
        this.string = string;
    }

    public int getPrimitiveInt() {
        return primitiveInt;
    }

    public void setPrimitiveInt(int primitiveInt) {
        this.primitiveInt = primitiveInt;
    }

    public Integer getWrappedInt() {
        return wrappedInt;
    }

    public void setWrappedInt(Integer wrappedInt) {
        this.wrappedInt = wrappedInt;
    }

    public boolean isPrimitiveBoolean() {
        return primitiveBoolean;
    }

    public void setPrimitiveBoolean(boolean primitiveBoolean) {
        this.primitiveBoolean = primitiveBoolean;
    }

    public Boolean getWrappedBoolean() {
        return wrappedBoolean;
    }

    public void setWrappedBoolean(Boolean wrappedBoolean) {
        this.wrappedBoolean = wrappedBoolean;
    }

    public String getString() {
        return string;
    }

    public void setString(String string) {
        this.string = string;
    }

    public TestClass getOther() {
        return other;
    }

    public void setOther(TestClass other) {
        this.other = other;
    }

    public String setAndGet(String text, int primitiveInt) {
        this.primitiveInt = primitiveInt;
        string = text;
        return string + this.primitiveInt;
    }
}
