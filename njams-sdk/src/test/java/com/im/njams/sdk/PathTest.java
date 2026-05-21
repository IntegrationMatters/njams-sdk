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
    public void rootIsRoot() {
        assertTrue(Path.ROOT.isRoot());
    }

    @Test
    public void nonRootIsNotRoot() {
        assertFalse(Path.get("notroot").isRoot());
        assertFalse(Path.get("notroot", "deep").isRoot());
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

    // --- resolve ---

    @Test
    public void resolveWithNoArgsReturnsRoot() {
        assertSame(Path.ROOT, Path.resolve());
    }

    @Test
    public void resolveWithNullArrayReturnsRoot() {
        assertSame(Path.ROOT, Path.resolve((String[]) null));
    }

    @Test
    public void resolveWithRootStringReturnsRoot() {
        assertSame(Path.ROOT, Path.resolve(">"));
    }

    @Test
    public void resolveWithEmptyStringReturnsRoot() {
        assertSame(Path.ROOT, Path.resolve(""));
    }

    @Test
    public void resolveWithRepeatedSeparatorsReturnsRoot() {
        assertSame(Path.ROOT, Path.resolve(">>>>"));
    }

    @Test
    public void resolveSinglePathString() {
        assertEquals(">a>b>", Path.resolve("a>b").toString());
    }

    @Test
    public void resolveBracketedPathString() {
        assertEquals(">a>b>", Path.resolve(">a>b>").toString());
    }

    @Test
    public void resolveSplitsMultipleArguments() {
        assertEquals(">a>b>c>", Path.resolve("a>b>", ">c>").toString());
    }

    @Test
    public void resolveFiltersEmptySegments() {
        assertEquals(">a>b>", Path.resolve("a>>b").toString());
    }

    @Test
    public void resolveSkipsNullArguments() {
        assertEquals(">a>b>", Path.resolve("a", null, "b").toString());
    }

    @Test
    public void resolveProducesSameInstanceAsGet() {
        Path viaGet = Path.get("rsame", "x", "y");
        Path viaResolve = Path.resolve("rsame>x>y>");
        assertSame(viaGet, viaResolve);
    }

    @Test
    public void resolveWorksLikeGetForBareSegments() {
        Path viaGet = Path.get("rbare", "x", "y");
        Path viaResolve = Path.resolve("rbare", "x", "y");
        assertSame(viaGet, viaResolve);
    }

    @Test
    public void resolveIsIdempotentAcrossCalls() {
        Path first = Path.resolve("ridem>x>");
        Path second = Path.resolve("ridem>x>");
        assertSame(first, second);
    }

    @Test(expected = IllegalArgumentException.class)
    public void resolveStillValidatesBlankSegments() {
        Path.resolve("a> >b");
    }

    // --- resolveChild ---

    @Test
    public void resolveChildWithNoArgsReturnsSelf() {
        Path p = Path.get("rcSelf");
        assertSame(p, p.resolveChild());
    }

    @Test
    public void resolveChildWithNullArrayReturnsSelf() {
        Path p = Path.get("rcNullArr");
        assertSame(p, p.resolveChild((String[]) null));
    }

    @Test
    public void resolveChildSplitsPathString() {
        Path parent = Path.get("rcSplit");
        Path leaf = Path.get("rcSplit", "a", "b");
        assertSame(leaf, parent.resolveChild("a>b"));
    }

    @Test
    public void resolveChildSplitsMultipleArguments() {
        Path parent = Path.get("rcMulti");
        Path leaf = Path.get("rcMulti", "a", "b", "c");
        assertSame(leaf, parent.resolveChild("a>b>", ">c>"));
    }

    @Test
    public void resolveChildSkipsNullArguments() {
        Path parent = Path.get("rcNullArg");
        Path leaf = Path.get("rcNullArg", "a", "b");
        assertSame(leaf, parent.resolveChild("a", null, "b"));
    }

    @Test
    public void resolveChildReturnsNullWhenChainBroken() {
        Path parent = Path.get("rcBroken");
        Path.get("rcBroken", "a");
        assertNull(parent.resolveChild("a>missing"));
    }

    @Test
    public void resolveChildFiltersEmptySegments() {
        Path parent = Path.get("rcEmpty");
        Path leaf = Path.get("rcEmpty", "a", "b");
        assertSame(leaf, parent.resolveChild("a>>b"));
    }

    // --- resolveOrCreateChild ---

    @Test
    public void resolveOrCreateChildWithNoArgsReturnsSelf() {
        Path p = Path.get("rocSelf");
        assertSame(p, p.resolveOrCreateChild());
    }

    @Test
    public void resolveOrCreateChildWithNullArrayReturnsSelf() {
        Path p = Path.get("rocNullArr");
        assertSame(p, p.resolveOrCreateChild((String[]) null));
    }

    @Test
    public void resolveOrCreateChildSplitsAndCreates() {
        Path parent = Path.get("rocCreate");
        Path leaf = parent.resolveOrCreateChild("a>b>c");
        assertEquals(">rocCreate>a>b>c>", leaf.toString());
        assertSame(leaf, Path.get("rocCreate", "a", "b", "c"));
    }

    @Test
    public void resolveOrCreateChildSplitsMultipleArguments() {
        Path parent = Path.get("rocMulti");
        Path leaf = parent.resolveOrCreateChild("a>b>", ">c>");
        assertEquals(">rocMulti>a>b>c>", leaf.toString());
    }

    @Test
    public void resolveOrCreateChildSkipsNullArguments() {
        Path parent = Path.get("rocNullArg");
        Path leaf = parent.resolveOrCreateChild("a", null, "b");
        assertEquals(">rocNullArg>a>b>", leaf.toString());
    }

    @Test
    public void resolveOrCreateChildFiltersEmptySegments() {
        Path parent = Path.get("rocEmpty");
        Path leaf = parent.resolveOrCreateChild("a>>b");
        assertEquals(">rocEmpty>a>b>", leaf.toString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void resolveOrCreateChildValidatesBlankSegment() {
        Path.get("rocBlank").resolveOrCreateChild("a> >b");
    }

    // --- get(legacy Path) ---

    @Test
    @SuppressWarnings("deprecation")
    public void getFromNullLegacyReturnsRoot() {
        assertSame(Path.ROOT, Path.get((com.im.njams.sdk.common.Path) null));
    }

    @Test
    @SuppressWarnings("deprecation")
    public void getFromLegacyConvertsToNewPath() {
        com.im.njams.sdk.common.Path legacy = new com.im.njams.sdk.common.Path("legA", "legB");
        Path result = Path.get(legacy);
        assertEquals(">legA>legB>", result.toString());
        assertSame(Path.get("legA", "legB"), result);
    }

    @Test
    @SuppressWarnings("deprecation")
    public void getFromLegacySinglePathString() {
        com.im.njams.sdk.common.Path legacy = new com.im.njams.sdk.common.Path(">legX>legY>");
        Path result = Path.get(legacy);
        assertEquals(">legX>legY>", result.toString());
        assertSame(Path.get("legX", "legY"), result);
    }

    // --- multi-segment getChild ---

    @Test
    public void getChildWithNoArgsReturnsSelf() {
        Path p = Path.get("mcgcSelf");
        assertSame(p, p.getChild());
    }

    @Test
    public void getChildWithNullArrayReturnsSelf() {
        Path p = Path.get("mcgcNullArr");
        assertSame(p, p.getChild((String[]) null));
    }

    @Test
    public void getChildMultiSegmentReturnsGrandchild() {
        Path parent = Path.get("mcgcParent");
        Path grand = Path.get("mcgcParent", "a", "b");
        assertSame(grand, parent.getChild("a", "b"));
    }

    @Test
    public void getChildMultiSegmentReturnsNullWhenMissing() {
        Path parent = Path.get("mcgcMiss");
        Path.get("mcgcMiss", "a");
        assertNull(parent.getChild("a", "doesnotexist"));
    }

    // --- multi-segment hasChild ---

    @Test
    public void hasChildWithNoArgsReturnsTrue() {
        assertTrue(Path.get("mhcSelf").hasChild());
    }

    @Test
    public void hasChildWithNullArrayReturnsTrue() {
        assertTrue(Path.get("mhcNullArr").hasChild((String[]) null));
    }

    @Test
    public void hasChildMultiSegmentTrueWhenChainExists() {
        Path parent = Path.get("mhcChain");
        Path.get("mhcChain", "a", "b");
        assertTrue(parent.hasChild("a", "b"));
    }

    @Test
    public void hasChildMultiSegmentFalseWhenChainBroken() {
        Path parent = Path.get("mhcBroken");
        Path.get("mhcBroken", "a");
        assertFalse(parent.hasChild("a", "missing"));
    }

    @Test
    public void hasChildMultiSegmentFalseForNullSegment() {
        Path parent = Path.get("mhcNullSeg");
        Path.get("mhcNullSeg", "a");
        assertFalse(parent.hasChild("a", null));
    }

    // --- multi-segment getOrCreateChild ---

    @Test
    public void getOrCreateChildWithNoArgsReturnsSelf() {
        Path p = Path.get("mgocSelf");
        assertSame(p, p.getOrCreateChild());
    }

    @Test
    public void getOrCreateChildWithNullArrayReturnsSelf() {
        Path p = Path.get("mgocNullArr");
        assertSame(p, p.getOrCreateChild((String[]) null));
    }

    @Test
    public void getOrCreateChildMultiSegmentCreatesChain() {
        Path parent = Path.get("mgocChain");
        Path leaf = parent.getOrCreateChild("a", "b", "c");
        assertEquals(">mgocChain>a>b>c>", leaf.toString());
        assertSame(leaf, Path.get("mgocChain", "a", "b", "c"));
    }

    @Test
    public void getOrCreateChildMultiSegmentReturnsExistingChain() {
        Path parent = Path.get("mgocExist");
        Path pre = Path.get("mgocExist", "a", "b");
        assertSame(pre, parent.getOrCreateChild("a", "b"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void getOrCreateChildMultiSegmentRejectsNullInChain() {
        Path.get("mgocNullChain").getOrCreateChild("a", null, "b");
    }

    @Test(expected = IllegalArgumentException.class)
    public void getOrCreateChildMultiSegmentRejectsSeparatorInChain() {
        Path.get("mgocSepChain").getOrCreateChild("a", "b>c");
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
    public void hasChildReturnsFalseForNullSegment() {
        assertFalse(Path.get("hcnull").hasChild((String) null));
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
    public void getOrCreateChildThrowsForNullSegment() {
        Path.get("gocthrow").getOrCreateChild((String) null);
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
