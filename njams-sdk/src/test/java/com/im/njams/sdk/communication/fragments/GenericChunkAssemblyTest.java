package com.im.njams.sdk.communication.fragments;

import static com.im.njams.sdk.communication.MessageHeaders.NJAMS_CHUNKS_HEADER;
import static com.im.njams.sdk.communication.MessageHeaders.NJAMS_CHUNK_MESSAGE_KEY_HEADER;
import static com.im.njams.sdk.communication.MessageHeaders.NJAMS_CHUNK_NO_HEADER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.junit.Test;

/**
 * Unit tests for the chunk reassembly logic in {@link GenericChunkAssembly}, exercised through a
 * minimal concrete subclass over a simple fragment type.
 */
public class GenericChunkAssemblyTest {

    /** Minimal transport fragment: headers + data + optional key. */
    private static final class Frag {
        final Map<String, String> headers;
        final String data;
        final String key;

        Frag(Map<String, String> headers, String data, String key) {
            this.headers = headers;
            this.data = data;
            this.key = key;
        }
    }

    /** Test assembly that reads the key from the header (keyFunction == null). */
    private static final class HeaderKeyAssembly extends GenericChunkAssembly<Frag> {
        HeaderKeyAssembly() {
            super(null, f -> f.headers, f -> f.data, false);
        }
    }

    /** Test assembly that reads the key via a key function. */
    private static final class KeyFnAssembly extends GenericChunkAssembly<Frag> {
        KeyFnAssembly(Function<Frag, String> keyFn) {
            super(keyFn, f -> f.headers, f -> f.data, false);
        }
    }

    private static Map<String, String> chunkHeaders(int total, int no, String key) {
        Map<String, String> m = new HashMap<>();
        m.put(NJAMS_CHUNKS_HEADER, String.valueOf(total));
        m.put(NJAMS_CHUNK_NO_HEADER, String.valueOf(no));
        if (key != null) {
            m.put(NJAMS_CHUNK_MESSAGE_KEY_HEADER, key);
        }
        return m;
    }

    @Test
    public void singleMessageWithoutChunkCountIsReturnedImmediately() {
        HeaderKeyAssembly assembly = new HeaderKeyAssembly();
        Map<String, String> headers = new HashMap<>();
        headers.put("some", "header");

        RawMessage result = assembly.resolve(new Frag(headers, "payload", null));

        assertEquals("payload", result.getBody());
        assertEquals(headers, result.getHeaders());
    }

    @Test
    public void nullHeadersAreTreatedAsSingleMessage() {
        HeaderKeyAssembly assembly = new HeaderKeyAssembly();
        RawMessage result = assembly.resolve(new Frag(null, "payload", null));
        assertEquals("payload", result.getBody());
        assertNull(result.getHeaders());
    }

    @Test
    public void splitMessageIsReassembledInOrder() {
        HeaderKeyAssembly assembly = new HeaderKeyAssembly();

        assertNull(assembly.resolve(new Frag(chunkHeaders(3, 1, "k"), "Hel", null)));
        assertNull(assembly.resolve(new Frag(chunkHeaders(3, 2, "k"), "lo ", null)));
        RawMessage result = assembly.resolve(new Frag(chunkHeaders(3, 3, "k"), "World", null));

        assertEquals("Hello World", result.getBody());
    }

    @Test
    public void splitMessageIsReassembledOutOfOrder() {
        HeaderKeyAssembly assembly = new HeaderKeyAssembly();

        assertNull(assembly.resolve(new Frag(chunkHeaders(3, 3, "k"), "World", null)));
        assertNull(assembly.resolve(new Frag(chunkHeaders(3, 1, "k"), "Hel", null)));
        RawMessage result = assembly.resolve(new Frag(chunkHeaders(3, 2, "k"), "lo ", null));

        assertEquals("Hello World", result.getBody());
    }

    @Test
    public void differentKeysAreReassembledIndependently() {
        HeaderKeyAssembly assembly = new HeaderKeyAssembly();

        assertNull(assembly.resolve(new Frag(chunkHeaders(2, 1, "A"), "a1", null)));
        assertNull(assembly.resolve(new Frag(chunkHeaders(2, 1, "B"), "b1", null)));
        assertEquals("a1a2", assembly.resolve(new Frag(chunkHeaders(2, 2, "A"), "a2", null)).getBody());
        assertEquals("b1b2", assembly.resolve(new Frag(chunkHeaders(2, 2, "B"), "b2", null)).getBody());
    }

    @Test
    public void missingKeyFallsBackToSingleMessage() {
        HeaderKeyAssembly assembly = new HeaderKeyAssembly();
        // chunk headers present but no message key and no key function
        RawMessage result = assembly.resolve(new Frag(chunkHeaders(2, 1, null), "data", null));
        assertEquals("data", result.getBody());
    }

    @Test
    public void invalidChunkNumberFallsBackToSingleMessage() {
        HeaderKeyAssembly assembly = new HeaderKeyAssembly();
        // chunk no 5 of 2 -> IndexOutOfBounds internally -> treated as a single message
        RawMessage result = assembly.resolve(new Frag(chunkHeaders(2, 5, "k"), "data", null));
        assertEquals("data", result.getBody());
    }

    @Test
    public void keyFunctionIsUsedWhenProvided() {
        KeyFnAssembly assembly = new KeyFnAssembly(f -> f.key);
        // no key header at all; the key function supplies the key
        assertNull(assembly.resolve(new Frag(chunkHeaders(2, 1, null), "x", "fnKey")));
        RawMessage result = assembly.resolve(new Frag(chunkHeaders(2, 2, null), "y", "fnKey"));
        assertEquals("xy", result.getBody());
    }
}
