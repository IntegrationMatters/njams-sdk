package com.im.njams.sdk.logmessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Test;

import com.faizsiegeln.njams.messageformat.v4.common.SubProcess;
import com.im.njams.sdk.AbstractTest;
import com.im.njams.sdk.Path;
import com.im.njams.sdk.model.ProcessModel;
import com.im.njams.sdk.model.SubProcessActivityModel;

/**
 * Unit tests for {@link SubProcessActivityImpl}: the subprocess reference setters and the
 * one-time estimated-size accounting they perform.
 */
public class SubProcessActivityImplTest extends AbstractTest {

    private SubProcessActivityModel subProcessModel(String id) {
        return process.createActivity(id + "Start", "Start", null)
                .transitionToSubProcess(id, "Sub", "stepType");
    }

    @Test
    public void stringOverloadStoresAllFields() {
        JobImpl job = createDefaultStartedJob();
        SubProcessActivityImpl sp = new SubProcessActivityImpl(job, subProcessModel("spStr"));

        sp.setSubProcess("SubName", "PROCESSES>SubProcess", "log-123");

        SubProcess ref = sp.getSubProcess();
        assertNotNull(ref);
        assertEquals("SubName", ref.getName());
        assertEquals("PROCESSES>SubProcess", ref.getSubProcessPath());
        assertEquals("log-123", ref.getLogId());
    }

    @Test
    public void processModelOverloadUsesNameAndPath() {
        JobImpl job = createDefaultStartedJob();
        SubProcessActivityImpl sp = new SubProcessActivityImpl(job, subProcessModel("spPm"));

        ProcessModel subModel = mock(ProcessModel.class);
        when(subModel.getName()).thenReturn("SubName");
        when(subModel.getPath()).thenReturn(Path.resolve("SUB"));

        sp.setSubProcess(subModel);

        SubProcess ref = sp.getSubProcess();
        assertEquals("SubName", ref.getName());
        assertEquals(Path.resolve("SUB").toString(), ref.getSubProcessPath());
    }

    @Test
    public void processModelConstructorSetsSubProcess() {
        JobImpl job = createDefaultStartedJob();
        ProcessModel subModel = mock(ProcessModel.class);
        when(subModel.getName()).thenReturn("CtorSub");
        when(subModel.getPath()).thenReturn(Path.resolve("CTOR"));

        SubProcessActivityImpl sp =
                new SubProcessActivityImpl(subModel, job, subProcessModel("spCtor"));

        assertEquals("CtorSub", sp.getSubProcess().getName());
    }

    @Test
    public void subProcessConstantIsCountedExactlyOnce() {
        JobImpl job = createDefaultStartedJob();
        SubProcessActivityImpl sp = new SubProcessActivityImpl(job, subProcessModel("spSize"));

        long before = job.getEstimatedSize();
        sp.setSubProcess("n", "p", "l");
        assertEquals(before + SubProcessActivityImpl.SUBPROCESS_ESTIMATED_SIZE, job.getEstimatedSize());

        // re-setting must not count the constant again
        sp.setSubProcess("other", "otherPath", "otherLog");
        assertEquals(before + SubProcessActivityImpl.SUBPROCESS_ESTIMATED_SIZE, job.getEstimatedSize());
    }
}
