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
        assertFalse(Path.of("notroot").isRoot());
        assertFalse(Path.of("notroot", "deep").isRoot());
    }

    @Test
    public void rootChildrenIsEmptyCollection() {
        assertNotNull(Path.ROOT.getChildren());
    }

    // --- Factory: no-arg / null / root string ---

    @Test
    public void ofWithNoSegmentsReturnsRoot() {
        assertSame(Path.ROOT, Path.of());
    }

    @Test
    public void ofWithNullArrayReturnsRoot() {
        assertSame(Path.ROOT, Path.of((String[]) null));
    }

    @Test
    public void ofWithRootStringReturnsRoot() {
        assertSame(Path.ROOT, Path.of(">"));
    }

    // --- Path string representation ---

    @Test
    public void singleSegmentPathString() {
        assertEquals(">seg1>", Path.of("seg1").toString());
    }

    @Test
    public void twoSegmentPathString() {
        assertEquals(">alpha>beta>", Path.of("alpha", "beta").toString());
    }

    @Test
    public void threeSegmentPathString() {
        assertEquals(">a>b>c>", Path.of("a", "b", "c").toString());
    }

    // --- Singleton guarantee ---

    @Test
    public void sameSegmentsReturnSameInstance() {
        Path p1 = Path.of("singleton", "test");
        Path p2 = Path.of("singleton", "test");
        assertSame(p1, p2);
    }

    @Test
    public void intermediateNodeIsShared() {
        Path child = Path.of("shared", "child");
        Path parent = Path.of("shared");
        assertSame(parent, child.getParent());
    }

    // --- Navigation: getParent ---

    @Test
    public void parentOfRootChildIsRoot() {
        assertSame(Path.ROOT, Path.of("rootchild").getParent());
    }

    @Test
    public void parentAtDepthTwo() {
        Path parent = Path.of("depth", "one");
        Path child = Path.of("depth", "one", "two");
        assertSame(parent, child.getParent());
    }

    // --- Navigation: getChild ---

    @Test
    public void getChildReturnsKnownChild() {
        Path parent = Path.of("nav", "parent");
        Path child = Path.of("nav", "parent", "kid");
        assertSame(child, parent.getChild("kid"));
    }

    @Test
    public void getChildReturnsNullForUnknown() {
        Path p = Path.of("unknownchild");
        assertNull(p.getChild("doesnotexist"));
    }

    // --- Navigation: getChildren ---

    @Test
    public void getChildrenContainsCreatedChildren() {
        Path parent = Path.of("multi");
        Path c1 = Path.of("multi", "first");
        Path c2 = Path.of("multi", "second");
        Collection<Path> children = parent.getChildren();
        assertTrue(children.contains(c1));
        assertTrue(children.contains(c2));
    }

    @Test
    public void getChildrenIsUnmodifiable() {
        Path p = Path.of("immutablechildren");
        assertThrows(UnsupportedOperationException.class,
                () -> p.getChildren().add(Path.of("illegal")));
    }

    // --- equals and hashCode ---

    @Test
    public void equalsIsTrueForSameInstance() {
        Path p = Path.of("eqtest");
        assertTrue(p.equals(p));
    }

    @Test
    public void equalsIsFalseForDifferentInstance() {
        Path p1 = Path.of("eq", "a");
        Path p2 = Path.of("eq", "b");
        assertFalse(p1.equals(p2));
    }

    @Test
    public void equalsIsFalseForNull() {
        assertFalse(Path.of("eqnull").equals(null));
    }

    @Test
    public void equalsIsFalseForOtherType() {
        assertFalse(Path.of("eqtype").equals(">eqtype>"));
    }

    @Test
    public void hashCodeIsStable() {
        Path p = Path.of("hashstable");
        assertEquals(p.hashCode(), p.hashCode());
    }

    @Test
    public void equalInstancesHaveSameHashCode() {
        Path p1 = Path.of("hashequal");
        Path p2 = Path.of("hashequal");
        assertSame(p1, p2);
        assertEquals(p1.hashCode(), p2.hashCode());
    }

    // --- Validation ---

    @Test(expected = IllegalArgumentException.class)
    public void nullSegmentThrows() {
        Path.of("valid", null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptySegmentThrows() {
        Path.of("");
    }

    @Test(expected = IllegalArgumentException.class)
    public void blankSegmentThrows() {
        Path.of("   ");
    }

    @Test(expected = IllegalArgumentException.class)
    public void segmentContainingSeparatorThrows() {
        Path.of("a>b");
    }

    @Test
    public void invalidSegmentIsRejectedOnRepeat() {
        try {
            Path.of("bad>repeat");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
        assertThrows(IllegalArgumentException.class, () -> Path.of("bad>repeat"));
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
        Path viaGet = Path.of("rsame", "x", "y");
        Path viaResolve = Path.resolve("rsame>x>y>");
        assertSame(viaGet, viaResolve);
    }

    @Test
    public void resolveWorksLikeGetForBareSegments() {
        Path viaGet = Path.of("rbare", "x", "y");
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
        Path p = Path.of("rcSelf");
        assertSame(p, p.resolveChild());
    }

    @Test
    public void resolveChildWithNullArrayReturnsSelf() {
        Path p = Path.of("rcNullArr");
        assertSame(p, p.resolveChild((String[]) null));
    }

    @Test
    public void resolveChildSplitsPathString() {
        Path parent = Path.of("rcSplit");
        Path leaf = Path.of("rcSplit", "a", "b");
        assertSame(leaf, parent.resolveChild("a>b"));
    }

    @Test
    public void resolveChildSplitsMultipleArguments() {
        Path parent = Path.of("rcMulti");
        Path leaf = Path.of("rcMulti", "a", "b", "c");
        assertSame(leaf, parent.resolveChild("a>b>", ">c>"));
    }

    @Test
    public void resolveChildSkipsNullArguments() {
        Path parent = Path.of("rcNullArg");
        Path leaf = Path.of("rcNullArg", "a", "b");
        assertSame(leaf, parent.resolveChild("a", null, "b"));
    }

    @Test
    public void resolveChildReturnsNullWhenChainBroken() {
        Path parent = Path.of("rcBroken");
        Path.of("rcBroken", "a");
        assertNull(parent.resolveChild("a>missing"));
    }

    @Test
    public void resolveChildFiltersEmptySegments() {
        Path parent = Path.of("rcEmpty");
        Path leaf = Path.of("rcEmpty", "a", "b");
        assertSame(leaf, parent.resolveChild("a>>b"));
    }

    // --- resolvesChild ---

    @Test
    public void resolvesChildWithNoArgsReturnsTrue() {
        assertTrue(Path.of("rscSelf").resolvesChild());
    }

    @Test
    public void resolvesChildWithNullArrayReturnsTrue() {
        assertTrue(Path.of("rscNullArr").resolvesChild((String[]) null));
    }

    @Test
    public void resolvesChildTrueWhenChainExists() {
        Path parent = Path.of("rscExists");
        Path.of("rscExists", "a", "b");
        assertTrue(parent.resolvesChild("a>b"));
    }

    @Test
    public void resolvesChildFalseWhenChainBroken() {
        Path parent = Path.of("rscBroken");
        Path.of("rscBroken", "a");
        assertFalse(parent.resolvesChild("a>missing"));
    }

    @Test
    public void resolvesChildSplitsMultipleArguments() {
        Path parent = Path.of("rscMulti");
        Path.of("rscMulti", "a", "b", "c");
        assertTrue(parent.resolvesChild("a>b>", ">c>"));
    }

    @Test
    public void resolvesChildSkipsNullArguments() {
        Path parent = Path.of("rscNullArg");
        Path.of("rscNullArg", "a", "b");
        assertTrue(parent.resolvesChild("a", null, "b"));
    }

    @Test
    public void resolvesChildFiltersEmptySegments() {
        Path parent = Path.of("rscEmpty");
        Path.of("rscEmpty", "a", "b");
        assertTrue(parent.resolvesChild("a>>b"));
    }

    // --- resolveOrCreateChild ---

    @Test
    public void resolveOrCreateChildWithNoArgsReturnsSelf() {
        Path p = Path.of("rocSelf");
        assertSame(p, p.resolveOrCreateChild());
    }

    @Test
    public void resolveOrCreateChildWithNullArrayReturnsSelf() {
        Path p = Path.of("rocNullArr");
        assertSame(p, p.resolveOrCreateChild((String[]) null));
    }

    @Test
    public void resolveOrCreateChildSplitsAndCreates() {
        Path parent = Path.of("rocCreate");
        Path leaf = parent.resolveOrCreateChild("a>b>c");
        assertEquals(">rocCreate>a>b>c>", leaf.toString());
        assertSame(leaf, Path.of("rocCreate", "a", "b", "c"));
    }

    @Test
    public void resolveOrCreateChildSplitsMultipleArguments() {
        Path parent = Path.of("rocMulti");
        Path leaf = parent.resolveOrCreateChild("a>b>", ">c>");
        assertEquals(">rocMulti>a>b>c>", leaf.toString());
    }

    @Test
    public void resolveOrCreateChildSkipsNullArguments() {
        Path parent = Path.of("rocNullArg");
        Path leaf = parent.resolveOrCreateChild("a", null, "b");
        assertEquals(">rocNullArg>a>b>", leaf.toString());
    }

    @Test
    public void resolveOrCreateChildFiltersEmptySegments() {
        Path parent = Path.of("rocEmpty");
        Path leaf = parent.resolveOrCreateChild("a>>b");
        assertEquals(">rocEmpty>a>b>", leaf.toString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void resolveOrCreateChildValidatesBlankSegment() {
        Path.of("rocBlank").resolveOrCreateChild("a> >b");
    }

    // --- get(legacy Path) ---

    @Test
    @SuppressWarnings("deprecation")
    public void ofFromNullLegacyReturnsRoot() {
        assertSame(Path.ROOT, Path.of((com.im.njams.sdk.common.Path) null));
    }

    @Test
    @SuppressWarnings("deprecation")
    public void ofFromLegacyConvertsToNewPath() {
        com.im.njams.sdk.common.Path legacy = new com.im.njams.sdk.common.Path("legA", "legB");
        Path result = Path.of(legacy);
        assertEquals(">legA>legB>", result.toString());
        assertSame(Path.of("legA", "legB"), result);
    }

    @Test
    @SuppressWarnings("deprecation")
    public void ofFromLegacySinglePathString() {
        com.im.njams.sdk.common.Path legacy = new com.im.njams.sdk.common.Path(">legX>legY>");
        Path result = Path.of(legacy);
        assertEquals(">legX>legY>", result.toString());
        assertSame(Path.of("legX", "legY"), result);
    }

    // --- toLegacyPath ---

    @Test
    @SuppressWarnings("deprecation")
    public void toLegacyPathFromRoot() {
        com.im.njams.sdk.common.Path legacy = Path.ROOT.toLegacyPath();
        assertEquals(">", legacy.toString());
    }

    @Test
    @SuppressWarnings("deprecation")
    public void toLegacyPathPreservesPathString() {
        Path neu = Path.of("tlpA", "tlpB", "tlpC");
        com.im.njams.sdk.common.Path legacy = neu.toLegacyPath();
        assertEquals(">tlpA>tlpB>tlpC>", legacy.toString());
        assertEquals(java.util.Arrays.asList("tlpA", "tlpB", "tlpC"), legacy.getParts());
    }

    @Test
    @SuppressWarnings("deprecation")
    public void toLegacyPathRoundTripsViaGet() {
        Path neu = Path.of("rtripA", "rtripB");
        com.im.njams.sdk.common.Path legacy = neu.toLegacyPath();
        assertSame(neu, Path.of(legacy));
    }

    // --- multi-segment getChild ---

    @Test
    public void getChildWithNoArgsReturnsSelf() {
        Path p = Path.of("mcgcSelf");
        assertSame(p, p.getChild());
    }

    @Test
    public void getChildWithNullArrayReturnsSelf() {
        Path p = Path.of("mcgcNullArr");
        assertSame(p, p.getChild((String[]) null));
    }

    @Test
    public void getChildMultiSegmentReturnsGrandchild() {
        Path parent = Path.of("mcgcParent");
        Path grand = Path.of("mcgcParent", "a", "b");
        assertSame(grand, parent.getChild("a", "b"));
    }

    @Test
    public void getChildMultiSegmentReturnsNullWhenMissing() {
        Path parent = Path.of("mcgcMiss");
        Path.of("mcgcMiss", "a");
        assertNull(parent.getChild("a", "doesnotexist"));
    }

    // --- multi-segment hasChild ---

    @Test
    public void hasChildWithNoArgsReturnsTrue() {
        assertTrue(Path.of("mhcSelf").hasChild());
    }

    @Test
    public void hasChildWithNullArrayReturnsTrue() {
        assertTrue(Path.of("mhcNullArr").hasChild((String[]) null));
    }

    @Test
    public void hasChildMultiSegmentTrueWhenChainExists() {
        Path parent = Path.of("mhcChain");
        Path.of("mhcChain", "a", "b");
        assertTrue(parent.hasChild("a", "b"));
    }

    @Test
    public void hasChildMultiSegmentFalseWhenChainBroken() {
        Path parent = Path.of("mhcBroken");
        Path.of("mhcBroken", "a");
        assertFalse(parent.hasChild("a", "missing"));
    }

    @Test
    public void hasChildMultiSegmentFalseForNullSegment() {
        Path parent = Path.of("mhcNullSeg");
        Path.of("mhcNullSeg", "a");
        assertFalse(parent.hasChild("a", null));
    }

    // --- multi-segment getOrCreateChild ---

    @Test
    public void getOrCreateChildWithNoArgsReturnsSelf() {
        Path p = Path.of("mgocSelf");
        assertSame(p, p.getOrCreateChild());
    }

    @Test
    public void getOrCreateChildWithNullArrayReturnsSelf() {
        Path p = Path.of("mgocNullArr");
        assertSame(p, p.getOrCreateChild((String[]) null));
    }

    @Test
    public void getOrCreateChildMultiSegmentCreatesChain() {
        Path parent = Path.of("mgocChain");
        Path leaf = parent.getOrCreateChild("a", "b", "c");
        assertEquals(">mgocChain>a>b>c>", leaf.toString());
        assertSame(leaf, Path.of("mgocChain", "a", "b", "c"));
    }

    @Test
    public void getOrCreateChildMultiSegmentReturnsExistingChain() {
        Path parent = Path.of("mgocExist");
        Path pre = Path.of("mgocExist", "a", "b");
        assertSame(pre, parent.getOrCreateChild("a", "b"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void getOrCreateChildMultiSegmentRejectsNullInChain() {
        Path.of("mgocNullChain").getOrCreateChild("a", null, "b");
    }

    @Test(expected = IllegalArgumentException.class)
    public void getOrCreateChildMultiSegmentRejectsSeparatorInChain() {
        Path.of("mgocSepChain").getOrCreateChild("a", "b>c");
    }

    // --- getSegments ---

    @Test
    public void segmentsOfRootIsEmpty() {
        assertTrue(Path.ROOT.getSegments().isEmpty());
    }

    @Test
    public void segmentsOfSingleNode() {
        assertEquals(java.util.Arrays.asList("seg1"), Path.of("seg1").getSegments());
    }

    @Test
    public void segmentsOfNestedNode() {
        assertEquals(java.util.Arrays.asList("a", "b", "c"), Path.of("a", "b", "c").getSegments());
    }

    // --- getName ---

    @Test
    public void nameOfRootIsNull() {
        assertNull(Path.ROOT.getName());
    }

    @Test
    public void nameOfRootChild() {
        assertEquals("seg", Path.of("seg").getName());
    }

    @Test
    public void nameAtDepthTwo() {
        assertEquals("leaf", Path.of("branch", "leaf").getName());
    }

    // --- hasChild ---

    @Test
    public void hasChildReturnsFalseForUnknown() {
        assertFalse(Path.of("hcparent").hasChild("nope"));
    }

    @Test
    public void hasChildReturnsTrueForKnown() {
        Path parent = Path.of("hcparent2");
        Path.of("hcparent2", "kid");
        assertTrue(parent.hasChild("kid"));
    }

    @Test
    public void hasChildReturnsFalseForNullSegment() {
        assertFalse(Path.of("hcnull").hasChild((String) null));
    }

    // --- getOrCreateChild ---

    @Test
    public void getOrCreateChildReturnsExistingChild() {
        Path parent = Path.of("gocparent");
        Path existing = Path.of("gocparent", "child");
        assertSame(existing, parent.getOrCreateChild("child"));
    }

    @Test
    public void getOrCreateChildCreatesNewChild() {
        Path parent = Path.of("gocnew");
        Path created = parent.getOrCreateChild("freshchild");
        assertEquals(">gocnew>freshchild>", created.toString());
        assertSame(parent, created.getParent());
        assertSame(created, Path.of("gocnew", "freshchild"));
    }

    @Test
    public void getOrCreateChildIsIdempotent() {
        Path parent = Path.of("gocidem");
        Path a = parent.getOrCreateChild("c");
        Path b = parent.getOrCreateChild("c");
        assertSame(a, b);
    }

    @Test(expected = IllegalArgumentException.class)
    public void getOrCreateChildThrowsForNullSegment() {
        Path.of("gocthrow").getOrCreateChild((String) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void getOrCreateChildThrowsForBlank() {
        Path.of("gocthrow2").getOrCreateChild("   ");
    }

    @Test(expected = IllegalArgumentException.class)
    public void getOrCreateChildThrowsForSeparator() {
        Path.of("gocthrow3").getOrCreateChild("a>b");
    }

    // --- List overloads ---

    @Test
    public void ofListEquivalentToVarargs() {
        assertSame(Path.of("listOf", "a", "b"), Path.of(java.util.Arrays.asList("listOf", "a", "b")));
    }

    @Test
    public void ofListEmptyReturnsRoot() {
        assertSame(Path.ROOT, Path.of(java.util.Collections.emptyList()));
    }

    @Test
    public void ofListNullReturnsRoot() {
        assertSame(Path.ROOT, Path.of((List<String>) null));
    }

    @Test
    public void resolveListEquivalentToVarargs() {
        assertSame(Path.resolve("resolveList>a>b"),
            Path.resolve(java.util.Arrays.asList("resolveList>a>b")));
    }

    @Test
    public void resolveListNullReturnsRoot() {
        assertSame(Path.ROOT, Path.resolve((List<String>) null));
    }

    @Test
    public void getChildListEquivalentToVarargs() {
        Path parent = Path.of("gcListParent");
        Path grand = Path.of("gcListParent", "a", "b");
        assertSame(grand, parent.getChild(java.util.Arrays.asList("a", "b")));
    }

    @Test
    public void getChildListNullReturnsSelf() {
        Path p = Path.of("gcListNull");
        assertSame(p, p.getChild((List<String>) null));
    }

    @Test
    public void resolveChildListEquivalentToVarargs() {
        Path parent = Path.of("rcListParent");
        Path leaf = Path.of("rcListParent", "a", "b");
        assertSame(leaf, parent.resolveChild(java.util.Arrays.asList("a>b>")));
    }

    @Test
    public void resolveChildListNullReturnsSelf() {
        Path p = Path.of("rcListNull");
        assertSame(p, p.resolveChild((List<String>) null));
    }

    @Test
    public void hasChildListEquivalentToVarargs() {
        Path parent = Path.of("hcListParent");
        Path.of("hcListParent", "a", "b");
        assertTrue(parent.hasChild(java.util.Arrays.asList("a", "b")));
        assertFalse(parent.hasChild(java.util.Arrays.asList("a", "missing")));
    }

    @Test
    public void hasChildListNullReturnsTrue() {
        assertTrue(Path.of("hcListNull").hasChild((List<String>) null));
    }

    @Test
    public void resolvesChildListEquivalentToVarargs() {
        Path parent = Path.of("rscListParent");
        Path.of("rscListParent", "a", "b");
        assertTrue(parent.resolvesChild(java.util.Arrays.asList("a>b>")));
    }

    @Test
    public void resolvesChildListNullReturnsTrue() {
        assertTrue(Path.of("rscListNull").resolvesChild((List<String>) null));
    }

    @Test
    public void resolveOrCreateChildListEquivalentToVarargs() {
        Path parent = Path.of("rocListParent");
        Path leaf = parent.resolveOrCreateChild(java.util.Arrays.asList("a>b>c"));
        assertEquals(">rocListParent>a>b>c>", leaf.toString());
        assertSame(leaf, Path.of("rocListParent", "a", "b", "c"));
    }

    @Test
    public void resolveOrCreateChildListNullReturnsSelf() {
        Path p = Path.of("rocListNull");
        assertSame(p, p.resolveOrCreateChild((List<String>) null));
    }

    @Test
    public void getOrCreateChildListEquivalentToVarargs() {
        Path parent = Path.of("gocListParent");
        Path leaf = parent.getOrCreateChild(java.util.Arrays.asList("a", "b", "c"));
        assertEquals(">gocListParent>a>b>c>", leaf.toString());
        assertSame(leaf, Path.of("gocListParent", "a", "b", "c"));
    }

    @Test
    public void getOrCreateChildListNullReturnsSelf() {
        Path p = Path.of("gocListNull");
        assertSame(p, p.getOrCreateChild((List<String>) null));
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
                    ref.set(Path.of("concurrent", "singleton", "test"));
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

    // --- startsWith ---

    @Test
    public void startsWithSelfIsTrue() {
        Path p = Path.of("a", "b");
        assertTrue(p.startsWith(p));
    }

    @Test
    public void startsWithAncestorIsTrue() {
        Path child = Path.of("a", "b", "c");
        assertTrue(child.startsWith(Path.of("a")));
        assertTrue(child.startsWith(Path.of("a", "b")));
    }

    @Test
    public void startsWithRootIsTrue() {
        assertTrue(Path.of("a", "b").startsWith(Path.ROOT));
        assertTrue(Path.ROOT.startsWith(Path.ROOT));
    }

    @Test
    public void startsWithNonAncestorIsFalse() {
        assertFalse(Path.of("a", "b").startsWith(Path.of("a", "x")));
        assertFalse(Path.of("a").startsWith(Path.of("a", "b")));
        assertFalse(Path.of("a", "b").startsWith(Path.of("x")));
    }

    @Test
    public void startsWithNullIsFalse() {
        assertFalse(Path.of("a").startsWith(null));
    }

    // --- depth ---

    @Test
    public void rootDepthIsZero() {
        assertEquals(0, Path.ROOT.getDepth());
    }

    @Test
    public void childDepthIsParentDepthPlusOne() {
        assertEquals(1, Path.of("a").getDepth());
        assertEquals(2, Path.of("a", "b").getDepth());
        assertEquals(3, Path.of("a", "b", "c").getDepth());
    }

    @Test
    public void depthEqualsSegmentCount() {
        Path p = Path.of("x", "y", "z");
        assertEquals(p.getSegments().size(), p.getDepth());
    }

    // --- print ---

    @Test
    public void printNullReturnsEmptyString() {
        assertEquals("", Path.print(null));
    }

    @Test
    public void printLeafRendersSingleLine() {
        assertEquals("printSolo\n", Path.print(Path.of("printSolo")));
    }

    @Test
    public void printRendersSortedAsciiTree() {
        Path root = Path.of("printRoot");
        root.getOrCreateChild("a", "b");
        root.getOrCreateChild("a", "c");
        root.getOrCreateChild("d");
        String expected =
            "printRoot\n"
            + "+- a\n"
            + "|  +- b\n"
            + "|  `- c\n"
            + "`- d\n";
        assertEquals(expected, Path.print(root));
    }

    @Test
    public void printRootStartsWithRootMarker() {
        assertTrue(Path.print(Path.ROOT).startsWith(">\n"));
    }
}
