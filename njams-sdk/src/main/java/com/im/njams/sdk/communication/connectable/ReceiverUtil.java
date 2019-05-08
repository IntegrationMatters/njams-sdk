package com.im.njams.sdk.communication.connectable;

import java.io.IOException;
import java.io.InputStream;

public class ReceiverUtil extends SenderUtil {

    public ReceiverUtil(){
        super();
    }

    public <T> T readJson(String json, Class<T> type) throws IOException {
        return mapper.readValue(json, type);
    }

    public <T> T readJson(InputStream inputStream, Class<T> type) throws IOException {
        return mapper.readValue(inputStream, type);
    }
}
