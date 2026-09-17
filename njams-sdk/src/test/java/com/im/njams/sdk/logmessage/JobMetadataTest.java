package com.im.njams.sdk.logmessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.Before;
import org.junit.Test;

import com.im.njams.sdk.Path;
import com.im.njams.sdk.common.NjamsSdkRuntimeException;

/**
 * Unit tests for {@link JobMetadata}: the descriptive, wire-transmitted metadata of a job. The
 * owning {@link JobImpl} is mocked so the facet is exercised in isolation.
 */
public class JobMetadataTest {

    private JobImpl job;
    private JobMetadata metadata;

    @Before
    public void setUp() {
        job = mock(JobImpl.class);
        metadata = new JobMetadata(job, "initial-correlation");
    }

    @Test
    public void initialCorrelationLogIdComesFromConstructor() {
        assertEquals("initial-correlation", metadata.getCorrelationLogId());
    }

    @Test
    public void settersAreFluentAndStoreValues() {
        assertSame(metadata, metadata.setCorrelationLogId("corr"));
        assertSame(metadata, metadata.setParentLogId("parent"));
        assertSame(metadata, metadata.setExternalLogId("ext"));

        assertEquals("corr", metadata.getCorrelationLogId());
        assertEquals("parent", metadata.getParentLogId());
        assertEquals("ext", metadata.getExternalLogId());
    }

    @Test
    public void businessServiceAndObjectAcceptStrings() {
        metadata.setBusinessService("svc");
        metadata.setBusinessObject("obj");
        assertEquals(Path.resolve("svc").toString(), metadata.getBusinessService());
        assertEquals(Path.resolve("obj").toString(), metadata.getBusinessObject());
    }

    @Test
    public void businessServiceAndObjectAcceptPaths() {
        Path svc = Path.resolve("a", "b");
        metadata.setBusinessService(svc);
        assertEquals(svc.toString(), metadata.getBusinessService());
    }

    @Test
    public void nullPathBusinessServiceIsIgnored() {
        metadata.setBusinessService((Path) null);
        assertNull(metadata.getBusinessService());
    }

    @Test
    public void nullPathBusinessObjectIsIgnored() {
        metadata.setBusinessObject((Path) null);
        assertNull(metadata.getBusinessObject());
    }

    @Test
    public void businessTimestampsAreStored() {
        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 8, 0);
        LocalDateTime end = LocalDateTime.of(2026, 1, 1, 9, 0);
        assertSame(metadata, metadata.setBusinessStart(start));
        assertSame(metadata, metadata.setBusinessEnd(end));
        assertEquals(start, metadata.getBusinessStart());
        assertEquals(end, metadata.getBusinessEnd());
    }

    @Test
    public void jobIdAndLogIdDelegateToJob() {
        when(job.getJobId()).thenReturn("job-1");
        when(job.getLogId()).thenReturn("log-1");
        assertEquals("job-1", metadata.getJobId());
        assertEquals("log-1", metadata.getLogId());
    }

    @Test
    public void correlationLogIdIsLengthLimited() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < JobImpl.MAX_VALUE_LIMIT + 10; i++) {
            sb.append('x');
        }
        metadata.setCorrelationLogId(sb.toString());
        assertTrue("value must be limited to the max value length",
                metadata.getCorrelationLogId().length() < JobImpl.MAX_VALUE_LIMIT);
    }

    @Test(expected = NjamsSdkRuntimeException.class)
    public void setterAfterEndThrows() {
        doThrow(new NjamsSdkRuntimeException("finished")).when(job).requireNotFinished(any());
        metadata.setCorrelationLogId("x");
    }
}
