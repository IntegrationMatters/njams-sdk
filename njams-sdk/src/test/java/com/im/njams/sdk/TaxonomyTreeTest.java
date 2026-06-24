package com.im.njams.sdk;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.faizsiegeln.njams.messageformat.v4.common.TreeElement;
import com.faizsiegeln.njams.messageformat.v4.common.TreeElementType;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;

/**
 * Unit tests for {@link TaxonomyTree}: tree element construction, type assignment, default-image
 * detection and the additional-process build path.
 */
public class TaxonomyTreeTest {

    private TaxonomyTree taxonomy;

    @Before
    public void setUp() {
        taxonomy = new TaxonomyTree(new Object());
    }

    private List<TreeElement> copy() {
        List<TreeElement> list = new ArrayList<>();
        taxonomy.copyInto(list);
        return list;
    }

    @Test
    public void registerBuildsPathElementsWithDefaultTypes() {
        taxonomy.register(Path.resolve("A", "B"), TreeElementType.PROCESS);

        List<TreeElement> elements = copy();
        assertEquals(2, elements.size());
        assertEquals(">A>", elements.get(0).getPath());
        assertEquals(">A>B>", elements.get(1).getPath());
        // first element is the root, last is the process
        assertEquals("njams.taxonomy.root", elements.get(0).getType());
        assertEquals("njams.taxonomy.process", elements.get(1).getType());
        assertEquals(TreeElementType.PROCESS, elements.get(1).getTreeElementType());
        assertNull("intermediate element has no domain object type", elements.get(0).getTreeElementType());
    }

    @Test
    public void registerDeduplicatesSharedPrefixes() {
        taxonomy.register(Path.resolve("A", "B"), TreeElementType.PROCESS);
        taxonomy.register(Path.resolve("A", "C"), TreeElementType.PROCESS);
        // shared root A must only appear once -> A, B, C = 3 elements
        assertEquals(3, copy().size());
    }

    @Test
    public void setTypeOverridesElementType() {
        taxonomy.register(Path.resolve("A", "B"), TreeElementType.PROCESS);
        taxonomy.setType(Path.resolve("A", "B"), "custom.type");

        TreeElement leaf = copy().stream().filter(e -> e.getPath().equals(">A>B>")).findFirst().orElseThrow(
                () -> new AssertionError("leaf not found"));
        assertEquals("custom.type", leaf.getType());
    }

    @Test
    public void setTypeForUnknownPathThrows() {
        try {
            taxonomy.setType(Path.resolve("does", "not", "exist"), "x");
            fail("expected NjamsSdkRuntimeException for unknown path");
        } catch (NjamsSdkRuntimeException expected) {
            // expected
        }
    }

    @Test
    public void defaultImagesInUseReflectsUsedTypes() {
        // A=root, B=folder, C=process
        taxonomy.register(Path.resolve("A", "B", "C"), TreeElementType.PROCESS);

        Map<String, String> images = taxonomy.defaultImagesInUse();
        assertEquals("images/root.png", images.get("njams.taxonomy.root"));
        assertEquals("images/folder.png", images.get("njams.taxonomy.folder"));
        assertEquals("images/process.png", images.get("njams.taxonomy.process"));
        // no client element was registered
        assertNull(images.get("njams.taxonomy.client"));
    }

    @Test
    public void markStartersRunsOverProcessElements() {
        taxonomy.register(Path.resolve("A", "B"), TreeElementType.PROCESS);
        // must not throw and must leave the process element in place
        taxonomy.markStarters(p -> true);
        assertTrue(copy().stream().anyMatch(e -> e.getTreeElementType() == TreeElementType.PROCESS));
    }

    @Test
    public void buildIntoCreatesElementsForAdditionalProcess() {
        List<TreeElement> target = new ArrayList<>();
        taxonomy.buildInto(target, Path.resolve("X", "Y"), TreeElementType.PROCESS, true);

        assertEquals(2, target.size());
        assertEquals(">X>Y>", target.get(1).getPath());
        assertEquals(TreeElementType.PROCESS, target.get(1).getTreeElementType());
    }
}
