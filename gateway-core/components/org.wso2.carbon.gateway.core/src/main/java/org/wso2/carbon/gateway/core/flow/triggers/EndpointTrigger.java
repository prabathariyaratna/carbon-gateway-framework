package org.wso2.carbon.gateway.core.flow.triggers;

import org.wso2.carbon.messaging.CarbonMessage;

/**
 * EndpointTrigger implementation base class.
 */
public abstract class EndpointTrigger {

    private String name;

    public EndpointTrigger(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public abstract boolean matches(CarbonMessage cMsg);
}
