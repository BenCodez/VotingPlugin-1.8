package com.bencodez.votingplugin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import com.bencodez.votingplugin.proxy.BungeeMethod;

class LegacyProxyVotePartyStartupTest {
    @ParameterizedTest @EnumSource(BungeeMethod.class)
    void interruptedTransportStartupDoesNotOverwritePersistedVotePartyCounts(BungeeMethod method) {
        VotingPluginMain plugin=mock(VotingPluginMain.class,RETURNS_DEEP_STUBS);
        when(plugin.getBungeeSettings().getBungeeMethod()).thenReturn(method.name());
        when(plugin.getBungeeSettings().getData().getString("ProxyProtocol")).thenReturn("LEGACY");
        when(plugin.getServerData().getBungeeVotePartyCurrent()).thenReturn(17);
        when(plugin.getServerData().getBungeeVotePartyRequired()).thenReturn(50);
        BungeeHandler handler=new BungeeHandler(plugin) {
            @Override public void loadGlobalMysql() {throw new StartupInterrupted();}
        };
        assertThrows(StartupInterrupted.class,handler::load);
        assertEquals(17,handler.getBungeeVotePartyCurrent());
        assertEquals(50,handler.getBungeeVotePartyRequired());
        handler.close();
        verify(plugin.getServerData()).setBungeeVotePartyCurrent(17);
        verify(plugin.getServerData()).setBungeeVotePartyRequired(50);
    }
    @org.junit.jupiter.api.Test
    void unsupportedCurrentTransportCannotResetStoredPartyState() {
        VotingPluginMain plugin=mock(VotingPluginMain.class,RETURNS_DEEP_STUBS);
        when(plugin.getBungeeSettings().getBungeeMethod()).thenReturn("HTTP");
        when(plugin.getBungeeSettings().getData().getString("ProxyProtocol")).thenReturn("CURRENT");
        when(plugin.getServerData().getBungeeVotePartyCurrent()).thenReturn(17);
        when(plugin.getServerData().getBungeeVotePartyRequired()).thenReturn(50);
        BungeeHandler handler=new BungeeHandler(plugin);
        assertThrows(IllegalArgumentException.class,handler::load);
        handler.close();
        verify(plugin.getServerData()).setBungeeVotePartyCurrent(17);
        verify(plugin.getServerData()).setBungeeVotePartyRequired(50);
    }
    private static class StartupInterrupted extends RuntimeException {}
}
