package com.im.njams.sdk.model.layout;

import com.im.njams.sdk.Path;
import com.im.njams.sdk.Njams;
import com.im.njams.sdk.communication.TestSender;
import com.im.njams.sdk.model.ActivityModel;
import com.im.njams.sdk.model.ProcessModel;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public class SimpleProcessModelLayouterTest {

    @Test
    public void layout_doesNotThrow() {
        Njams njams = new Njams(Path.of("TEST"), "1.0", "TEST", TestSender.getSettings());
        ProcessModel model = njams.createProcess(Path.of("proc"));
        ActivityModel a = model.createActivity("A", "A", null);
        a.setStarter(true);

        SimpleProcessModelLayouter layouter = new SimpleProcessModelLayouter();
        layouter.layout(model); // must not throw
        assertNotNull(model.getActivity("A"));
    }
}
