package com.im.njams.sdk;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class PathTest {

    // --- ROOT ---

    @Test
    public void rootExists() {
        assertNotNull(Path.ROOT);
        assertEquals(">", Path.ROOT.toString());
    }

    @Test
    public void rootHasNullParent() {
        assertNull(Path.ROOT.getParent());
    }

    @Test
    public void rootChildrenIsEmptyCollection() {
        assertNotNull(Path.ROOT.getChildren());
    }

    // --- Factory: no-arg / null / root string ---

    @Test
    public void getWithNoSegmentsReturnsRoot() {
        assertSame(Path.ROOT, Path.get());
    }

    @Test
    public void getWithNullArrayReturnsRoot() {
        assertSame(Path.ROOT, Path.get((String[]) null));
    }

    @Test
    public void getWithRootStringReturnsRoot() {
        assertSame(Path.ROOT, Path.get(">"));
    }

    // --- Path string representation ---

    @Test
    public void singleSegmentPathString() {
        assertEquals(">seg1>", Path.get("seg1").toString());
    }

    @Test
    public void twoSegmentPathString() {
        assertEquals(">alpha>beta>", Path.get("alpha", "beta").toString());
    }

    @Test
    public void threeSegmentPathString() {
        assertEquals(">a>b>c>", Path.get("a", "b", "c").toString());
    }

    // --- Singleton guarantee ---

    @Test
    public void sameSegmentsReturnSameInstance() {
        Path p1 = Path.get("singleton", "test");
        Path p2 = Path.get("singleton", "test");
        assertSame(p1, p2);
    }

    @Test
    public void intermediateNodeIsShared() {
        Path child = Path.get("shared", "child");
        Path parent = Path.get("shared");
        assertSame(parent, child.getParent());
    }

    // --- Navigation: getParent ---

    @Test
    public void parentOfRootChildIsRoot() {
        assertSame(Path.ROOT, Path.get("rootchild").getParent());
    }

    @Test
    public void parentAtDepthTwo() {
        Path parent = Path.get("depth", "one");
        Path child = Path.get("depth", "one", "two");
        assertSame(parent, child.getParent());
    }

    // --- Navigation: getChild ---

    @Test
    public void getChildReturnsKnownChild() {
        Path parent = Path.get("nav", "parent");
        Path child = Path.get("nav", "parent", "kid");
        assertSame(child, parent.getChild("kid"));
    }

    @Test
    public void getChildReturnsNullForUnknown() {
        Path p = Path.get("unknownchild");
        assertNull(p.getChild("doesnotexist"));
    }

    // --- Navigation: getChildren ---

    @Test
    public void getChildrenContainsCreatedChildren() {
        Path parent = Path.get("multi");
        Path c1 = Path.get("multi", "first");
        Path c2 = Path.get("multi", "second");
        Collection<Path> children = parent.getChildren();
        assertTrue(children.contains(c1));
        assertTrue(children.contains(c2));
    }

    @Test
    public void getChildrenIsUnmodifiable() {
        Path p = Path.get("immutablechildren");
        assertThrows(UnsupportedOperationException.class,
                () -> p.getChildren().add(Path.get("illegal")));
    }

    // --- equals and hashCode ---

    @Test
    public void equalsIsTrueForSameInstance() {
        Path p = Path.get("eqtest");
        assertTrue(p.equals(p));
    }

    @Test
    public void equalsIsFalseForDifferentInstance() {
        Path p1 = Path.get("eq", "a");
        Path p2 = Path.get("eq", "b");
        assertFalse(p1.equals(p2));
    }

    @Test
    public void equalsIsFalseForNull() {
        assertFalse(Path.get("eqnull").equals(null));
    }

    @Test
    public void equalsIsFalseForOtherType() {
        assertFalse(Path.get("eqtype").equals(">eqtype>"));
    }

    @Test
    public void hashCodeIsStable() {
        Path p = Path.get("hashstable");
        assertEquals(p.hashCode(), p.hashCode());
    }

    @Test
    public void equalInstancesHaveSameHashCode() {
        Path p1 = Path.get("hashequal");
        Path p2 = Path.get("hashequal");
        assertSame(p1, p2);
        assertEquals(p1.hashCode(), p2.hashCode());
    }

    // --- Validation ---

    @Test(expected = IllegalArgumentException.class)
    public void nullSegmentThrows() {
        Path.get("valid", null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptySegmentThrows() {
        Path.get("");
    }

    @Test(expected = IllegalArgumentException.class)
    public void blankSegmentThrows() {
        Path.get("   ");
    }

    @Test(expected = IllegalArgumentException.class)
    public void segmentContainingSeparatorThrows() {
        Path.get("a>b");
    }

    @Test
    public void invalidSegmentIsRejectedOnRepeat() {
        try {
            Path.get("bad>repeat");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
        assertThrows(IllegalArgumentException.class, () -> Path.get("bad>repeat"));
    }

    // --- getSegmentName ---

    @Test
    public void segmentNameOfRootIsNull() {
        assertNull(Path.ROOT.getSegmentName());
    }

    @Test
    public void segmentNameOfRootChild() {
        assertEquals("seg", Path.get("seg").getSegmentName());
    }

    @Test
    public void segmentNameAtDepthTwo() {
        assertEquals("leaf", Path.get("branch", "leaf").getSegmentName());
    }

    // --- hasChild ---

    @Test
    public void hasChildReturnsFalseForUnknown() {
        assertFalse(Path.get("hcparent").hasChild("nope"));
    }

    @Test
    public void hasChildReturnsTrueForKnown() {
        Path parent = Path.get("hcparent2");
        Path.get("hcparent2", "kid");
        assertTrue(parent.hasChild("kid"));
    }

    @Test
    public void hasChildReturnsFalseForNull() {
        assertFalse(Path.get("hcnull").hasChild(null));
    }

    // --- getOrCreateChild ---

    @Test
    public void getOrCreateChildReturnsExistingChild() {
        Path parent = Path.get("gocparent");
        Path existing = Path.get("gocparent", "child");
        assertSame(existing, parent.getOrCreateChild("child"));
    }

    @Test
    public void getOrCreateChildCreatesNewChild() {
        Path parent = Path.get("gocnew");
        Path created = parent.getOrCreateChild("freshchild");
        assertEquals(">gocnew>freshchild>", created.toString());
        assertSame(parent, created.getParent());
        assertSame(created, Path.get("gocnew", "freshchild"));
    }

    @Test
    public void getOrCreateChildIsIdempotent() {
        Path parent = Path.get("gocidem");
        Path a = parent.getOrCreateChild("c");
        Path b = parent.getOrCreateChild("c");
        assertSame(a, b);
    }

    @Test(expected = IllegalArgumentException.class)
    public void getOrCreateChildThrowsForNull() {
        Path.get("gocthrow").getOrCreateChild(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void getOrCreateChildThrowsForBlank() {
        Path.get("gocthrow2").getOrCreateChild("   ");
    }

    @Test(expected = IllegalArgumentException.class)
    public void getOrCreateChildThrowsForSeparator() {
        Path.get("gocthrow3").getOrCreateChild("a>b");
    }

    // --- Thread safety ---

    @Test
    public void concurrentCreationReturnsSameInstance() throws Exception {
        int threads = 20;
        CyclicBarrier barrier = new CyclicBarrier(threads);
        List<AtomicReference<Path>> results = new ArrayList<>(threads);
        List<Thread> threadList = new ArrayList<>(threads);

        for (int i = 0; i < threads; i++) {
            AtomicReference<Path> ref = new AtomicReference<>();
            results.add(ref);
            threadList.add(new Thread(() -> {
                try {
                    barrier.await();
                    ref.set(Path.get("concurrent", "singleton", "test"));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }));
        }

        threadList.forEach(Thread::start);
        for (Thread t : threadList) {
            t.join(5000);
        }

        Path first = results.get(0).get();
        assertNotNull(first);
        for (AtomicReference<Path> ref : results) {
            assertSame("All threads must return the same instance", first, ref.get());
        }
    }
}
