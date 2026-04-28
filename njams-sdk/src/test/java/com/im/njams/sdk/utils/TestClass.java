/*
 * Copyright (c) 2026 Salesfive Integration Services GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * The Software shall be used for Good, not Evil.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO
 * THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE
 * FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
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
