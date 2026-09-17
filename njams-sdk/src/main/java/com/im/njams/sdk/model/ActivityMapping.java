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
package com.im.njams.sdk.model;

import java.util.Iterator;
import java.util.Map;
import java.util.function.Consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.im.njams.sdk.common.JsonSerializerFactory;
import com.im.njams.sdk.utils.JsonUtils;

/**
 * Produces the tree-viewer JSON structure that the nJAMS UI renders for an
 * {@link ActivityModel#setMapping(ActivityMapping) activity mapping}.
 *
 * <p>The structure is a root object carrying a {@code name} and an {@code entries} array, where each entry is either a
 * <em>leaf</em> ({@code name} + {@code value}) or a <em>branch</em> ({@code name} + nested {@code entries}). This class
 * lets callers obtain that JSON without knowing the exact format or handling JSON serialization themselves.
 *
 * <p>There are two ways to create an instance:
 * <ul>
 * <li>{@link #builder(String)} — construct the tree programmatically, node by node, to arbitrary depth:
 * <pre>
 * String json = ActivityMapping.builder("stylesheet")
 *     .leaf("version", "2.0")
 *     .branch("template", t -&gt; t
 *         .leaf("match", "/")
 *         .branch("ActivityInput", a -&gt; a.leaf("message", "...")))
 *     .toJson();
 * </pre></li>
 * <li>{@link #fromObject(String, Object)} — derive the tree automatically from an arbitrary object: scalar properties
 * become leaves, nested objects and collections become branches, recursively.</li>
 * </ul>
 *
 * <p>Pass the result straight to {@link ActivityModel#setMapping(ActivityMapping)}, or obtain the raw string via
 * {@link #toJson()} and use {@link ActivityModel#setMapping(String)}.
 *
 * @author cwinkler
 */
public final class ActivityMapping {

    private final JsonNode root;

    private ActivityMapping(final JsonNode root) {
        this.root = root;
    }

    /**
     * Starts building a mapping tree programmatically. The returned builder represents the root branch with the given
     * label; add leaves and branches to it and call {@link Builder#build()} to obtain the {@link ActivityMapping}.
     *
     * @param rootName label of the root node
     * @return a new {@link Builder}
     */
    public static Builder builder(final String rootName) {
        return new Builder(rootName);
    }

    /**
     * Derives a mapping tree from an arbitrary object, analogous to serializing it to JSON. Scalar values become leaf
     * nodes ({@code name} + {@code value}), nested objects and collections become branch nodes ({@code name} +
     * {@code entries}), recursively to arbitrary depth. Collection elements are named by their position
     * ({@code "0"}, {@code "1"}, …). A {@code null} value yields a leaf with an empty-string value. If {@code source}
     * itself is a scalar, it is wrapped as a single leaf so that the root remains a branch.
     *
     * @param rootName label of the root node
     * @param source   the object to derive the tree from; may be {@code null}
     * @return a new {@link ActivityMapping}
     */
    public static ActivityMapping fromObject(final String rootName, final Object source) {
        final ObjectMapper mapper = mapper();
        final JsonNode src = mapper.valueToTree(source);
        final ObjectNode rootNode = mapper.createObjectNode();
        rootNode.put("name", rootName);
        final ArrayNode entries = rootNode.putArray("entries");
        if (src != null && (src.isObject() || src.isArray())) {
            appendEntries(src, entries, mapper);
        } else {
            entries.add(leafNode("value", src, mapper));
        }
        return new ActivityMapping(rootNode);
    }

    /**
     * Derives a mapping tree from an arbitrary JSON string. This behaves like {@link #fromObject(String, Object)} with
     * the object parsed from the given JSON: JSON objects become branch nodes, JSON arrays become branch nodes whose
     * entries are named by their position ({@code "0"}, {@code "1"}, …), and scalar values become leaf nodes. A JSON
     * {@code null} yields a leaf with an empty-string value. If the JSON is a scalar at the top level, it is wrapped as
     * a single leaf so that the root remains a branch.
     *
     * @param rootName label of the root node
     * @param json     the JSON string to derive the tree from
     * @return a new {@link ActivityMapping}
     * @throws com.im.njams.sdk.common.NjamsSdkRuntimeException if the given string is not valid JSON
     */
    public static ActivityMapping fromPlainJson(final String rootName, final String json) {
        return fromObject(rootName, JsonUtils.parse(json, JsonNode.class));
    }

    /**
     * Returns the tree-viewer JSON string represented by this mapping. The string can be passed to
     * {@link ActivityModel#setMapping(String)}.
     *
     * @return the JSON string
     */
    public String toJson() {
        return JsonUtils.serialize(root);
    }

    /**
     * Returns the same JSON string as {@link #toJson()}.
     *
     * @return the JSON string
     */
    @Override
    public String toString() {
        return toJson();
    }

    private static void appendEntries(final JsonNode source, final ArrayNode targetEntries, final ObjectMapper mapper) {
        if (source.isObject()) {
            final Iterator<Map.Entry<String, JsonNode>> fields = source.fields();
            while (fields.hasNext()) {
                final Map.Entry<String, JsonNode> field = fields.next();
                targetEntries.add(toNode(field.getKey(), field.getValue(), mapper));
            }
        } else if (source.isArray()) {
            int index = 0;
            for (final JsonNode element : source) {
                targetEntries.add(toNode(String.valueOf(index++), element, mapper));
            }
        }
    }

    private static ObjectNode toNode(final String name, final JsonNode value, final ObjectMapper mapper) {
        if (value != null && (value.isObject() || value.isArray())) {
            final ObjectNode branch = mapper.createObjectNode();
            branch.put("name", name);
            appendEntries(value, branch.putArray("entries"), mapper);
            return branch;
        }
        return leafNode(name, value, mapper);
    }

    private static ObjectNode leafNode(final String name, final JsonNode value, final ObjectMapper mapper) {
        final ObjectNode leaf = mapper.createObjectNode();
        leaf.put("name", name);
        leaf.put("value", scalarText(value));
        return leaf;
    }

    private static String scalarText(final JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode()) {
            return "";
        }
        return value.asText();
    }

    /**
     * Returns the shared mapper used to assemble the node tree. It is configured to include {@code null} values so that
     * {@code null} object properties surface as empty-string leaves rather than being dropped.
     */
    @SuppressWarnings("deprecation")
    private static ObjectMapper mapper() {
        return InclusiveMapperHolder.MAPPER;
    }

    private static final class InclusiveMapperHolder {
        @SuppressWarnings("deprecation")
        private static final ObjectMapper MAPPER = JsonSerializerFactory.getMapper(false, false);
    }

    /**
     * Fluent builder for constructing a mapping tree node by node. Sibling nodes are added by chaining
     * {@link #leaf(String, Object)} and {@link #branch(String, Consumer)} on the same builder; a branch's children are
     * populated through the {@link Consumer} passed to {@link #branch(String, Consumer)}.
     */
    public static final class Builder {

        private final ObjectMapper mapper;
        private final ObjectNode node;
        private final ArrayNode entries;

        private Builder(final String name) {
            mapper = mapper();
            node = mapper.createObjectNode();
            node.put("name", name);
            entries = node.putArray("entries");
        }

        /**
         * Adds a leaf node (a {@code name} with a {@code value}) to the current level. The value is rendered as its
         * string representation; {@code null} becomes an empty string.
         *
         * @param name  the leaf label
         * @param value the leaf value; may be {@code null}
         * @return this builder, for chaining further siblings
         */
        public Builder leaf(final String name, final Object value) {
            final ObjectNode leaf = mapper.createObjectNode();
            leaf.put("name", name);
            leaf.put("value", value == null ? "" : String.valueOf(value));
            entries.add(leaf);
            return this;
        }

        /**
         * Adds a branch node (a {@code name} with its own nested {@code entries}) to the current level. The given
         * {@link Consumer} receives a fresh builder for the branch and should populate its children; branches may be
         * nested to arbitrary depth.
         *
         * @param name     the branch label
         * @param children callback that populates the branch's children
         * @return this builder, for chaining further siblings
         */
        public Builder branch(final String name, final Consumer<Builder> children) {
            final Builder child = new Builder(name);
            children.accept(child);
            entries.add(child.node);
            return this;
        }

        /**
         * Builds the {@link ActivityMapping} from the nodes added so far.
         *
         * @return the resulting {@link ActivityMapping}
         */
        public ActivityMapping build() {
            return new ActivityMapping(node);
        }

        /**
         * Convenience for {@code build().toJson()}: builds the mapping and returns its tree-viewer JSON string.
         *
         * @return the JSON string
         */
        public String toJson() {
            return build().toJson();
        }
    }
}
