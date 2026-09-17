package com.im.njams.sdk.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.im.njams.sdk.utils.JsonUtils;

/**
 * Tests for {@link ActivityMapping} and the {@link ActivityModel#setMapping(ActivityMapping)} overload.
 */
public class ActivityMappingTest {

    private static JsonNode parse(String json) {
        return JsonUtils.parse(json, JsonNode.class);
    }

    /** Returns the child entry with the given name, or null. */
    private static JsonNode entry(JsonNode parent, String name) {
        for (JsonNode e : parent.get("entries")) {
            if (name.equals(e.get("name").asText())) {
                return e;
            }
        }
        return null;
    }

    private static boolean isLeaf(JsonNode node) {
        return node.has("value") && !node.has("entries");
    }

    private static boolean isBranch(JsonNode node) {
        return node.has("entries") && !node.has("value");
    }

    @Test
    public void builderProducesTreeViewerStructure() {
        String json = ActivityMapping.builder("stylesheet")
            .leaf("version", "2.0")
            .branch("template", t -> t
                .leaf("match", "/")
                .branch("ActivityInput", a -> a.leaf("message", "hello")))
            .toJson();

        JsonNode root = parse(json);
        assertEquals("stylesheet", root.get("name").asText());
        assertTrue(isBranch(root));

        JsonNode version = entry(root, "version");
        assertTrue(isLeaf(version));
        assertEquals("2.0", version.get("value").asText());

        JsonNode template = entry(root, "template");
        assertTrue(isBranch(template));
        assertEquals("/", entry(template, "match").get("value").asText());

        JsonNode input = entry(template, "ActivityInput");
        assertTrue(isBranch(input));
        assertEquals("hello", entry(input, "message").get("value").asText());
    }

    @Test
    public void builderKeepsSiblingOrderAndSupportsDepth() {
        String json = ActivityMapping.builder("root")
            .leaf("a", "1")
            .leaf("b", "2")
            .branch("c", c -> c.branch("d", d -> d.leaf("e", "deep")))
            .toJson();

        JsonNode root = parse(json);
        JsonNode entries = root.get("entries");
        assertEquals(3, entries.size());
        assertEquals("a", entries.get(0).get("name").asText());
        assertEquals("b", entries.get(1).get("name").asText());
        assertEquals("c", entries.get(2).get("name").asText());
        assertEquals("deep", entry(entry(entry(root, "c"), "d"), "e").get("value").asText());
    }

    @Test
    public void builderNullLeafValueBecomesEmptyString() {
        String json = ActivityMapping.builder("root").leaf("n", null).toJson();
        assertEquals("", entry(parse(json), "n").get("value").asText());
    }

    @Test
    public void builderRendersNonStringLeafValuesAsText() {
        String json = ActivityMapping.builder("root")
            .leaf("number", 42)
            .leaf("flag", true)
            .toJson();
        JsonNode root = parse(json);
        assertEquals("42", entry(root, "number").get("value").asText());
        assertEquals("true", entry(root, "flag").get("value").asText());
    }

    @Test
    public void fromObjectDerivesNestedBranchesAndLeaves() {
        Map<String, Object> child = new LinkedHashMap<>();
        child.put("city", "Berlin");
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("name", "Alice");
        source.put("address", child);

        JsonNode root = parse(ActivityMapping.fromObject("person", source).toJson());
        assertEquals("person", root.get("name").asText());
        assertEquals("Alice", entry(root, "name").get("value").asText());

        JsonNode address = entry(root, "address");
        assertTrue(isBranch(address));
        assertEquals("Berlin", entry(address, "city").get("value").asText());
    }

    @Test
    public void fromObjectNamesCollectionElementsByIndex() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("tags", Arrays.asList("x", "y", "z"));

        JsonNode tags = entry(parse(ActivityMapping.fromObject("root", source).toJson()), "tags");
        assertTrue(isBranch(tags));
        assertEquals("x", entry(tags, "0").get("value").asText());
        assertEquals("y", entry(tags, "1").get("value").asText());
        assertEquals("z", entry(tags, "2").get("value").asText());
    }

    @Test
    public void fromObjectRendersNonStringScalars() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("count", 7);
        source.put("active", false);

        JsonNode root = parse(ActivityMapping.fromObject("root", source).toJson());
        assertEquals("7", entry(root, "count").get("value").asText());
        assertEquals("false", entry(root, "active").get("value").asText());
    }

    @Test
    public void fromObjectNullPropertyBecomesEmptyLeaf() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("missing", null);

        JsonNode missing = entry(parse(ActivityMapping.fromObject("root", source).toJson()), "missing");
        assertTrue(isLeaf(missing));
        assertEquals("", missing.get("value").asText());
    }

    @Test
    public void fromObjectScalarTopLevelIsWrappedAsLeaf() {
        JsonNode root = parse(ActivityMapping.fromObject("greeting", "hello").toJson());
        assertTrue(isBranch(root));
        assertEquals(1, root.get("entries").size());
        assertEquals("hello", entry(root, "value").get("value").asText());
    }

    @Test
    public void fromObjectEmptyObjectProducesEmptyBranch() {
        JsonNode root = parse(ActivityMapping.fromObject("root", new LinkedHashMap<>()).toJson());
        assertTrue(isBranch(root));
        assertEquals(0, root.get("entries").size());
    }

    @Test
    public void fromPlainJsonDerivesNestedBranchesAndLeaves() {
        String json = "{\"name\":\"Alice\",\"address\":{\"city\":\"Berlin\"},\"tags\":[\"x\",\"y\"]}";
        JsonNode root = parse(ActivityMapping.fromPlainJson("person", json).toJson());

        assertEquals("person", root.get("name").asText());
        assertEquals("Alice", entry(root, "name").get("value").asText());

        JsonNode address = entry(root, "address");
        assertTrue(isBranch(address));
        assertEquals("Berlin", entry(address, "city").get("value").asText());

        JsonNode tags = entry(root, "tags");
        assertTrue(isBranch(tags));
        assertEquals("x", entry(tags, "0").get("value").asText());
        assertEquals("y", entry(tags, "1").get("value").asText());
    }

    @Test
    public void fromPlainJsonRendersNonStringScalarsAndNulls() {
        String json = "{\"count\":7,\"active\":false,\"missing\":null}";
        JsonNode root = parse(ActivityMapping.fromPlainJson("root", json).toJson());
        assertEquals("7", entry(root, "count").get("value").asText());
        assertEquals("false", entry(root, "active").get("value").asText());
        JsonNode missing = entry(root, "missing");
        assertTrue(isLeaf(missing));
        assertEquals("", missing.get("value").asText());
    }

    @Test
    public void fromPlainJsonScalarTopLevelIsWrappedAsLeaf() {
        JsonNode root = parse(ActivityMapping.fromPlainJson("greeting", "\"hello\"").toJson());
        assertTrue(isBranch(root));
        assertEquals(1, root.get("entries").size());
        assertEquals("hello", entry(root, "value").get("value").asText());
    }

    @Test(expected = com.im.njams.sdk.common.NjamsSdkRuntimeException.class)
    public void fromPlainJsonRejectsInvalidJson() {
        ActivityMapping.fromPlainJson("root", "{not valid json");
    }

    @Test
    public void toStringEqualsToJson() {
        ActivityMapping mapping = ActivityMapping.builder("root").leaf("a", "1").build();
        assertEquals(mapping.toJson(), mapping.toString());
    }

    @Test
    public void setMappingWithActivityMappingDelegatesToStringSetter() {
        ActivityModel activity = new ActivityModel(null, "id", "name", "type");
        ActivityMapping mapping = ActivityMapping.builder("root").leaf("a", "1").build();
        activity.setMapping(mapping);
        assertEquals(mapping.toJson(), activity.getMapping());
    }

    @Test
    public void setMappingWithNullActivityMappingClearsMapping() {
        ActivityModel activity = new ActivityModel(null, "id", "name", "type");
        activity.setMapping("existing");
        activity.setMapping((ActivityMapping) null);
        assertNull(activity.getMapping());
    }

    @Test
    public void leafIsNotMistakenForBranch() {
        JsonNode root = parse(ActivityMapping.builder("root").leaf("a", "1").toJson());
        assertFalse(root.get("entries").get(0).has("entries"));
        assertNotNull(root.get("entries").get(0).get("value"));
    }
}
