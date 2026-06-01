package com.im.njams.sdk.communication;

import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.faizsiegeln.njams.messageformat.v4.logmessage.LogMessage;
import com.faizsiegeln.njams.messageformat.v4.projectmessage.ProjectMessage;
import com.faizsiegeln.njams.messageformat.v4.tracemessage.TraceMessage;
import com.im.njams.sdk.NjamsSettings;
import com.im.njams.sdk.settings.ClientSettings;

public class MessageDebugDumperTest {

    @Rule
    public TemporaryFolder tmpFolder = new TemporaryFolder();

    private ClientSettings settingsWithDir(String dir) {
        Properties props = new Properties();
        props.setProperty(NjamsSettings.PROPERTY_DEBUG_MESSAGE_DIR, dir);
        return ClientSettings.from(props);
    }

    private List<Path> dumpedFiles() throws IOException {
        return Files.walk(tmpFolder.getRoot().toPath())
            .filter(Files::isRegularFile)
            .sorted()
            .collect(Collectors.toList());
    }

    @Test
    public void testDisabledWhenSettingAbsent() throws IOException {
        MessageDebugDumper dumper = new MessageDebugDumper(ClientSettings.from(new Properties()));
        LogMessage msg = new LogMessage();
        msg.setPath(">test>");
        msg.setLogId("abc");

        dumper.dump(msg, null);

        assertTrue(dumpedFiles().isEmpty());
    }

    @Test
    public void testNoArgConstructorIsDisabled() throws IOException {
        MessageDebugDumper dumper = new MessageDebugDumper();
        LogMessage msg = new LogMessage();
        msg.setPath(">test>");
        msg.setLogId("abc");

        dumper.dump(msg, null);

        assertTrue(dumpedFiles().isEmpty());
    }

    @Test
    public void testCreatesRunSubdirectory() throws IOException {
        MessageDebugDumper dumper = new MessageDebugDumper(settingsWithDir(tmpFolder.getRoot().getAbsolutePath()));
        LogMessage msg = new LogMessage();
        msg.setPath(">test>");
        msg.setLogId("abc");

        dumper.dump(msg, null);

        List<Path> subdirs = Files.list(tmpFolder.getRoot().toPath())
            .filter(Files::isDirectory)
            .collect(Collectors.toList());
        assertEquals(1, subdirs.size());
        assertTrue(subdirs.get(0).getFileName().toString().matches("\\d{8}-\\d{6}"));
    }

    @Test
    public void testLogMessageFilename() throws IOException {
        MessageDebugDumper dumper = new MessageDebugDumper(settingsWithDir(tmpFolder.getRoot().getAbsolutePath()));
        LogMessage msg = new LogMessage();
        msg.setPath(">domain>process>");
        msg.setLogId("log-id-123");

        dumper.dump(msg, null);

        String filename = dumpedFiles().get(0).getFileName().toString();
        assertEquals("00000001_event_log-id-123.json", filename);
    }

    @Test
    public void testProjectMessageFilename() throws IOException {
        MessageDebugDumper dumper = new MessageDebugDumper(settingsWithDir(tmpFolder.getRoot().getAbsolutePath()));
        ProjectMessage msg = new ProjectMessage();
        msg.setPath(">domain>MyProcess>");

        dumper.dump(msg, null);

        String filename = dumpedFiles().get(0).getFileName().toString();
        assertEquals("00000001_project_MyProcess.json", filename);
    }

    @Test
    public void testTraceMessageFilename() throws IOException {
        MessageDebugDumper dumper = new MessageDebugDumper(settingsWithDir(tmpFolder.getRoot().getAbsolutePath()));
        TraceMessage msg = new TraceMessage();
        msg.setPath(">domain>TraceProcess>");

        dumper.dump(msg, null);

        String filename = dumpedFiles().get(0).getFileName().toString();
        assertEquals("00000001_command_TraceProcess.json", filename);
    }

    @Test
    public void testSequenceNumbering() throws IOException {
        MessageDebugDumper dumper = new MessageDebugDumper(settingsWithDir(tmpFolder.getRoot().getAbsolutePath()));
        for (int i = 1; i <= 3; i++) {
            LogMessage msg = new LogMessage();
            msg.setPath(">test>");
            msg.setLogId("id-" + i);
            dumper.dump(msg, null);
        }

        List<Path> files = dumpedFiles();
        assertEquals(3, files.size());
        assertTrue(files.get(0).getFileName().toString().startsWith("00000001_"));
        assertTrue(files.get(1).getFileName().toString().startsWith("00000002_"));
        assertTrue(files.get(2).getFileName().toString().startsWith("00000003_"));
    }

    @Test
    public void testSanitizesSpecialCharsInId() throws IOException {
        MessageDebugDumper dumper = new MessageDebugDumper(settingsWithDir(tmpFolder.getRoot().getAbsolutePath()));
        LogMessage msg = new LogMessage();
        msg.setPath(">test>");
        msg.setLogId("id/with:special*chars");

        dumper.dump(msg, null);

        String filename = dumpedFiles().get(0).getFileName().toString();
        assertEquals("00000001_event_id_with_special_chars.json", filename);
    }

    @Test
    public void testFileContentHeaders() throws IOException {
        MessageDebugDumper dumper = new MessageDebugDumper(settingsWithDir(tmpFolder.getRoot().getAbsolutePath()));
        LogMessage msg = new LogMessage();
        msg.setPath(">domain>process>");
        msg.setLogId("log-999");

        dumper.dump(msg, "session-42");

        String content = Files.readString(dumpedFiles().get(0));
        assertTrue(content.contains("NJAMS_MESSAGETYPE: event\n"));
        assertTrue(content.contains("NJAMS_PATH: >domain>process>\n"));
        assertTrue(content.contains("NJAMS_LOGID: log-999\n"));
        assertTrue(content.contains("NJAMS_MESSAGEVERSION:"));
        assertTrue(content.contains("NJAMS_CLIENTSESSIONID: session-42\n"));
        assertTrue(content.contains("---\n"));
    }

    @Test
    public void testFileContentBody() throws IOException {
        MessageDebugDumper dumper = new MessageDebugDumper(settingsWithDir(tmpFolder.getRoot().getAbsolutePath()));
        LogMessage msg = new LogMessage();
        msg.setPath(">domain>process>");
        msg.setLogId("log-body-test");

        dumper.dump(msg, null);

        String content = Files.readString(dumpedFiles().get(0));
        String[] parts = content.split("---\n", 2);
        assertEquals(2, parts.length);
        String body = parts[1].trim();
        assertTrue("body should be JSON", body.startsWith("{") && body.endsWith("}"));
        assertTrue(body.contains("log-body-test"));
    }

    @Test
    public void testNoClientSessionIdOmitsHeader() throws IOException {
        MessageDebugDumper dumper = new MessageDebugDumper(settingsWithDir(tmpFolder.getRoot().getAbsolutePath()));
        LogMessage msg = new LogMessage();
        msg.setPath(">test>");
        msg.setLogId("abc");

        dumper.dump(msg, null);

        String content = Files.readString(dumpedFiles().get(0));
        assertFalse(content.contains("NJAMS_CLIENTSESSIONID"));
    }
}
