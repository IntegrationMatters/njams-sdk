package com.im.njams.sdk.service.factories;

import com.im.njams.sdk.service.NjamsService;

import java.util.Properties;

public class ConnectableFactory extends ServiceFactory {

    public static final String COMMUNICATION = "njams.sdk.communication";

    protected final Properties properties;

    public ConnectableFactory(Properties properties, Class<? extends NjamsService> classOfServiceToCreate) {
        super(properties.getProperty(COMMUNICATION), classOfServiceToCreate);
        this.properties = properties;
    }
}
