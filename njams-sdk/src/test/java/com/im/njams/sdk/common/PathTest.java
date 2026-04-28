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
package com.im.njams.sdk.common;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Assert;
import org.junit.Test;

public class PathTest {

    @Test
    public void testGetObjectName() {
        final Path path = new Path("foo", "bar");
        Assert.assertEquals("bar", path.getObjectName());
    }

    @Test
    public void testAddBase() {
        final Path path = new Path("foo");
        Assert.assertEquals(">bar>foo>", path.addBase(new Path("bar")).toString());
    }

    @Test
    public void testAdd() {
        final Path path = new Path("foo");
        Assert.assertEquals(">foo>bar>", path.add("bar").toString());
    }

    @Test
    public void testAddPath() {
        final Path path = new Path("foo");
        Assert.assertEquals(">foo>bar>", path.add(new Path("bar")).toString());
    }

    @Test
    public void testAddPostfix() {
        final Path path = new Path("foo");
        Assert.assertEquals(">foo>bar>", path.add(new Path("bar")).toString());
    }

    @Test
    public void testGetParent() {
        Assert.assertNull(new Path(">foo").getParent());

        final Path path = new Path("foo", "bar");
        Assert.assertEquals(">foo>", path.getParent().toString());
    }

    @Test
    public void testHashCode() {
        final Path path = new Path("foo");
        Assert.assertEquals(60407837, path.hashCode());
    }

    @Test
    public void testEquals() {
        final Path path = new Path("foo");
        Assert.assertTrue(path.equals(path));
        Assert.assertFalse(path.equals(null));
        Assert.assertFalse(path.equals("bar"));
        Assert.assertFalse(path.equals(new Path("bar")));
    }

    @Test
    public void testCompareTo() {
        final Path path = new Path("foo");
        Assert.assertEquals(0, path.compareTo(new Path("foo")));
    }

    @Test
    public void testGetAllPaths() {
        Path path = new Path(">a>b>c>d>");
        List<Path> paths = path.getAllPaths();
        System.out.println(paths);
        assertEquals(new Path(">a>"), paths.get(0));
        assertEquals(new Path(">a>b>"), paths.get(1));
        assertEquals(new Path(">a>b>c>"), paths.get(2));
        assertEquals(new Path(">a>b>c>d>"), paths.get(3));
    }
}
