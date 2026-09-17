package com.im.njams.sdk;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Test;

import com.im.njams.sdk.Njams.Feature;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;

/**
 * Unit tests for {@link NjamsFeatures}: the optional-feature list, inherent-feature protection,
 * container-mode and the started-state guards.
 */
public class NjamsFeaturesTest {

    private LifecycleState lifecycle;
    private NjamsFeatures features;

    @Before
    public void setUp() {
        lifecycle = new LifecycleState();
        features = new NjamsFeatures(lifecycle);
    }

    @Test
    public void inherentFeaturesPresentByDefault() {
        assertTrue(features.has(Feature.EXPRESSION_TEST));
        assertTrue(features.has(Feature.PING));
        assertTrue(features.has(Feature.COMMANDS_SPLIT));
        // an optional feature is not present until added
        assertFalse(features.has(Feature.REPLAY));
    }

    @Test
    public void addFeatureIsIdempotent() {
        features.add(Feature.REPLAY);
        features.add(Feature.REPLAY);
        long count = features.list().stream().filter(f -> f == Feature.REPLAY).count();
        assertTrue("feature must appear at most once", count == 1);
        assertTrue(features.has(Feature.REPLAY));
    }

    @Test
    public void removeOptionalFeature() {
        features.add(Feature.REPLAY);
        features.remove(Feature.REPLAY);
        assertFalse(features.has(Feature.REPLAY));
    }

    @Test
    public void removeInherentFeatureThrows() {
        try {
            features.remove(Feature.PING);
            fail("inherent features must not be removable");
        } catch (NjamsSdkRuntimeException expected) {
            // expected
        }
    }

    @Test
    public void addAfterStartThrows() {
        lifecycle.setStarted(true);
        try {
            features.add(Feature.REPLAY);
            fail("expected NjamsSdkRuntimeException after start");
        } catch (NjamsSdkRuntimeException expected) {
            // expected
        }
    }

    @Test
    public void removeAfterStartThrows() {
        features.add(Feature.REPLAY);
        lifecycle.setStarted(true);
        try {
            features.remove(Feature.REPLAY);
            fail("expected NjamsSdkRuntimeException after start");
        } catch (NjamsSdkRuntimeException expected) {
            // expected
        }
    }

    @Test
    public void containerModeEnabledByDefault() {
        assertTrue(features.isContainerMode());
    }

    @Test
    public void setContainerModeTrueAddsFeature() {
        features.setContainerMode(true);
        assertTrue(features.isContainerMode());
        assertTrue(features.has(Feature.CONTAINER_MODE));
    }

    @Test
    public void setContainerModeFalseRemovesFeature() {
        features.setContainerMode(true);
        features.setContainerMode(false);
        assertFalse(features.isContainerMode());
        assertFalse(features.has(Feature.CONTAINER_MODE));
    }

    @Test
    public void setContainerModeAfterStartThrows() {
        lifecycle.setStarted(true);
        try {
            features.setContainerMode(false);
            fail("expected NjamsSdkRuntimeException after start");
        } catch (NjamsSdkRuntimeException expected) {
            // expected
        }
    }
}
