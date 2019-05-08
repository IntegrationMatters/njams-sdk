/*
 */

package com.im.njams.sdk.communication.http.connector;

import com.im.njams.sdk.common.NjamsSdkRuntimeException;
import com.im.njams.sdk.communication.connector.AbstractConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public abstract class HttpConnector extends AbstractConnector {

    private static final Logger LOG = LoggerFactory.getLogger(HttpConnector.class);

    public HttpConnector(Properties properties, String name) {
        super(properties, name);
    }

    @Override
    public final void close() {
        List<Exception> exceptions = new ArrayList<>();
        exceptions.addAll(extClose());
        if (!exceptions.isEmpty()) {
            exceptions.forEach(exception -> LOG.error(exception.getMessage()));
            throw new NjamsSdkRuntimeException("Unable to close http connector");
        } else {
            LOG.info("HttpConnector has been closed.");
        }
    }

    protected abstract List<Exception> extClose();
}
