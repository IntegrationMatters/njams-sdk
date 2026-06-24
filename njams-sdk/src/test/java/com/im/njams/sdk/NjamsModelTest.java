package com.im.njams.sdk;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.model.ProcessModel;

/**
 * Unit tests for {@link NjamsModel} exercised through a started {@link Njams} instance
 * ({@link AbstractTest}). The default process "PROCESSES" is created in the base-class setup.
 */
public class NjamsModelTest extends AbstractTest {

    @Test
    public void getReturnsExistingProcessModel() {
        assertSame(process, njams.model().get("PROCESSES"));
    }

    @Test
    public void getUnknownThrows() {
        try {
            njams.model().get("DOES_NOT_EXIST");
            fail("expected NjamsSdkRuntimeException for an unknown process");
        } catch (NjamsSdkRuntimeException expected) {
            // expected
        }
    }

    @Test
    public void hasReflectsRegisteredModels() {
        assertTrue(njams.model().has("PROCESSES"));
        assertFalse(njams.model().has("DOES_NOT_EXIST"));
    }

    @Test
    public void createAndGetRoundtrip() {
        ProcessModel created = njams.model().create("ANOTHER_PROCESS");
        assertTrue(njams.model().has("ANOTHER_PROCESS"));
        assertSame(created, njams.model().get("ANOTHER_PROCESS"));
    }

    @Test
    public void getAllContainsRegisteredModel() {
        assertTrue(njams.model().getAll().contains(process));
    }

    @Test
    public void createWithPathOutsideClientThrows() {
        try {
            njams.model().create(Path.resolve("OUTSIDE", "PROCESS"));
            fail("expected NjamsSdkRuntimeException for a path outside the client path");
        } catch (NjamsSdkRuntimeException expected) {
            // expected
        }
    }

    @Test
    public void addImageAfterStartThrows() {
        try {
            njams.model().addImage("key", "images/x.png");
            fail("expected NjamsSdkRuntimeException; images must be added before start");
        } catch (NjamsSdkRuntimeException expected) {
            // expected
        }
    }

    @Test
    public void setTreeElementTypeAfterStartThrows() {
        try {
            njams.model().setTreeElementType(process.getPath(), "custom.type");
            fail("expected NjamsSdkRuntimeException; tree types must be set before start");
        } catch (NjamsSdkRuntimeException expected) {
            // expected
        }
    }

    @Test
    public void getGlobalVariablesIsNotNull() {
        assertNotNull(njams.model().getGlobalVariables());
    }
}
