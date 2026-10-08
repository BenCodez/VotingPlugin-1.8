package com.bencodez.votingplugin.proxy.bungee;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.lang.reflect.Field;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import com.bencodez.votingplugin.proxy.VotingPluginProxy;
import com.bencodez.votingplugin.proxy.ProxyMysqlUserTable;

class LegacyPartialStartupShutdownTest {
    @Test void hostShutsDownProxyEvenWhenMethodAndCachesNeverInitialized() throws Exception {
        VotingPluginBungee host = mock(VotingPluginBungee.class,CALLS_REAL_METHODS);
        VotingPluginProxy proxy = mock(VotingPluginProxy.class);
        Field field = VotingPluginBungee.class.getDeclaredField("votingPluginProxy");
        field.setAccessible(true);field.set(host,proxy);
        when(host.getLogger()).thenReturn(Logger.getAnonymousLogger());
        assertDoesNotThrow(host::onDisable);
        verify(proxy).onDisable();
    }
    @Test void hostCanStopBeforeProxyConstruction() {
        VotingPluginBungee host = mock(VotingPluginBungee.class,CALLS_REAL_METHODS);
        assertDoesNotThrow(host::onDisable);
    }
    @Test void partiallyInitializedProxyClosesDatabaseWithoutTimeChecker() throws Exception {
        VotingPluginProxy proxy = mock(VotingPluginProxy.class,CALLS_REAL_METHODS);
        ProxyMysqlUserTable storage = mock(ProxyMysqlUserTable.class);
        when(proxy.getProxyMySQL()).thenReturn(storage);
        Field enabled = VotingPluginProxy.class.getDeclaredField("enabled");
        enabled.setAccessible(true); enabled.setBoolean(proxy,true);
        assertDoesNotThrow(proxy::onDisable);
        verify(storage).shutdown();
        assertFalse(enabled.getBoolean(proxy));
    }
}
