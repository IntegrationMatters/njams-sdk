package com.im.njams.sdk.common;

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
}
