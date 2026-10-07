package com.bencodez.votingplugin.backendproxy;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ProxyProtocolTest {
    @Test void missingSettingUsesCurrentCarrierProtocol() {
        assertEquals("CURRENT",ProxyProtocol.resolve(null,"PLUGINMESSAGING"));
        assertEquals("CURRENT",ProxyProtocol.resolve(null,"HTTP"));
    }
    @Test void missingSettingPreservesExistingDedicatedLegacyTransports() {
        for(String method:new String[]{"MYSQL","REDIS","MQTT","SOCKETS"})
            assertEquals("LEGACY",ProxyProtocol.resolve(null,method));
    }
    @Test void explicitCurrentSelectionIsNotSilentlyDowngraded() {
        assertEquals("CURRENT",ProxyProtocol.resolve("current","REDIS"));
        assertEquals("LEGACY",ProxyProtocol.resolve("LEGACY","PLUGINMESSAGING"));
    }
    @Test void invalidExplicitProtocolIsRejected() {
        assertThrows(IllegalArgumentException.class,()->ProxyProtocol.resolve("OTHER","PLUGINMESSAGING"));
        assertThrows(IllegalArgumentException.class,()->ProxyProtocol.resolve("","PLUGINMESSAGING"));
    }
}
