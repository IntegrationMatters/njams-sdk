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
 *  FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */
package com.im.njams.sdk;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import com.faizsiegeln.njams.messageformat.v4.common.TreeElement;
import com.faizsiegeln.njams.messageformat.v4.common.TreeElementType;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;

/**
 * Internal collaborator of {@link NjamsModel}: owns the client's taxonomy tree
 * ({@link TreeElement} list) and the default taxonomy type/icon mapping. Confines the
 * relocated/wire-format {@link TreeElement} type to internal code. Not part of the public API.
 */
final class TaxonomyTree {

    private static final String DEFAULT_TAXONOMY_ROOT_TYPE = "njams.taxonomy.root";
    private static final String DEFAULT_TAXONOMY_FOLDER_TYPE = "njams.taxonomy.folder";
    private static final String DEFAULT_TAXONOMY_CLIENT_TYPE = "njams.taxonomy.client";
    private static final String DEFAULT_TAXONOMY_PROCESS_TYPE = "njams.taxonomy.process";
    private static final String DEFAULT_TAXONOMY_ROOT_ICON = "images/root.png";
    private static final String DEFAULT_TAXONOMY_FOLDER_ICON = "images/folder.png";
    private static final String DEFAULT_TAXONOMY_CLIENT_ICON = "images/client.png";
    private static final String DEFAULT_TAXONOMY_PROCESS_ICON = "images/process.png";

    private final Object projectMessageLock;

    // tree representation for the client
    private final List<TreeElement> treeElements = new ArrayList<>();

    TaxonomyTree(Object projectMessageLock) {
        this.projectMessageLock = projectMessageLock;
    }

    /**
     * Create DomainObjectStructure which is the tree representation for the
     * client
     */
    void register(Path path, TreeElementType targetDomainObjectType) {
        synchronized (projectMessageLock) {
            addInto(treeElements, path, targetDomainObjectType);
        }
    }

    /** Sets the type for a TreeElement given by a path. */
    void setType(Path path, String type) {
        synchronized (projectMessageLock) {
            TreeElement dos = treeElements.stream().filter(d -> d.getPath().equals(path.toString())).findAny()
                .orElse(null);
            if (dos == null) {
                throw new NjamsSdkRuntimeException("Unable to find DomainObjectStructure for path " + path);
            }
            dos.setType(type);
        }
    }

    /** Sets the TreeElements starter flag according to the corresponding processModel. */
    void markStarters(Predicate<Path> isStarter) {
        synchronized (projectMessageLock) {
            treeElements.stream().filter(t -> t.getTreeElementType() == TreeElementType.PROCESS)
                .forEach(t -> t.setStarter(isStarter.test(Path.resolve(t.getPath()))));
        }
    }

    /** Copies the current tree elements into the given target list (for the full project message). */
    void copyInto(List<TreeElement> target) {
        synchronized (projectMessageLock) {
            target.addAll(treeElements);
        }
    }

    /**
     * Builds the tree elements for a single additional process into the given target list and sets
     * the starter flag on the process element at the given path (used for the additional project
     * message, which may carry several processes with different starter flags).
     */
    void buildInto(List<TreeElement> target, Path processPath, TreeElementType targetDomainObjectType,
        boolean isStarter) {
        addInto(target, processPath, targetDomainObjectType);
        final String processPathString = processPath.toString();
        target.stream().filter(te -> te.getTreeElementType() == TreeElementType.PROCESS
            && te.getPath().equals(processPathString)).forEach(te -> te.setStarter(isStarter));
    }

    /**
     * Returns the default type-to-icon entries for the default taxonomy types currently used by
     * the tree, in insertion order (folder, root, client, process).
     */
    Map<String, String> defaultImagesInUse() {
        final Map<String, String> result = new LinkedHashMap<>();
        synchronized (projectMessageLock) {
            putIfUsed(result, DEFAULT_TAXONOMY_FOLDER_TYPE, DEFAULT_TAXONOMY_FOLDER_ICON);
            putIfUsed(result, DEFAULT_TAXONOMY_ROOT_TYPE, DEFAULT_TAXONOMY_ROOT_ICON);
            putIfUsed(result, DEFAULT_TAXONOMY_CLIENT_TYPE, DEFAULT_TAXONOMY_CLIENT_ICON);
            putIfUsed(result, DEFAULT_TAXONOMY_PROCESS_TYPE, DEFAULT_TAXONOMY_PROCESS_ICON);
        }
        return result;
    }

    private void putIfUsed(Map<String, String> target, String type, String icon) {
        if (treeElements.stream().anyMatch(te -> te.getType().equals(type))) {
            target.put(type, icon);
        }
    }

    /** Shared tree-building loop for both the persistent tree and additional-process messages. */
    private void addInto(List<TreeElement> target, Path path, TreeElementType targetDomainObjectType) {
        final List<String> parts = path.getSegments();
        String currentPath = ">";
        for (int i = 0; i < parts.size(); i++) {
            String part = parts.get(i);
            currentPath += part + ">";
            final String finalPath = currentPath;
            boolean found = target.stream().filter(d -> d.getPath().equals(finalPath)).findAny().isPresent();
            if (!found) {
                TreeElementType domainObjectType = i == parts.size() - 1 ? targetDomainObjectType : null;
                String type = getTreeElementDefaultType(i == 0, domainObjectType);
                target.add(new TreeElement(currentPath, part, type, domainObjectType));
            }
        }
    }

    /**
     * Returns the default icon type for a TreeElement, based on the criterias
     * first and TreeElementType
     */
    private String getTreeElementDefaultType(boolean first, TreeElementType treeElmentType) {
        String type = DEFAULT_TAXONOMY_FOLDER_TYPE;
        if (first) {
            type = DEFAULT_TAXONOMY_ROOT_TYPE;
        } else if (treeElmentType == TreeElementType.CLIENT) {
            type = DEFAULT_TAXONOMY_CLIENT_TYPE;
        } else if (treeElmentType == TreeElementType.PROCESS) {
            type = DEFAULT_TAXONOMY_PROCESS_TYPE;
        }
        return type;
    }
}
