package com.im.njams.sdk.communication.connectable.sender;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.im.njams.sdk.common.JsonSerializerFactory;

import java.io.IOException;
import java.io.OutputStream;

public class SenderUtil {

    protected ObjectMapper mapper;

    public SenderUtil(){
        this.mapper = JsonSerializerFactory.getDefaultMapper();
    }

    public String writeJson(Object object) throws JsonProcessingException {
        return mapper.writeValueAsString(object);
    }

    public void writeJson(OutputStream outputStream, Object object) throws IOException {
        mapper.writeValue(outputStream, object);
    }
}
