package com.bencodez.votingplugin.proxy.bungee;

import static org.mockito.Mockito.*;
import java.lang.reflect.Field;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import com.bencodez.votingplugin.proxy.VotingPluginProxy;
import com.bencodez.votingplugin.proxy.ProxyMysqlUserTable;
import net.md_5.bungee.api.connection.ProxiedPlayer;

class NonVotedPlayersCachePlatformTest {
    @Test void bungeePlayerUsesBungeeNameAndUuidWithoutVelocityApi() throws Exception { check(false); }
    @Test void existingVoterIsNotReaddedToNonVotedCache() throws Exception { check(true); }
    private void check(boolean exists) throws Exception {
        VotingPluginBungee plugin = mock(VotingPluginBungee.class);
        VotingPluginProxy proxy = mock(VotingPluginProxy.class);
        ProxyMysqlUserTable storage = mock(ProxyMysqlUserTable.class);
        UUID uuid = UUID.fromString("12345678-1234-1234-1234-123456789abc");
        when(plugin.getVotingPluginProxy()).thenReturn(proxy);
        when(proxy.getProxyMySQL()).thenReturn(storage);
        when(storage.containsKeyQuery(uuid.toString())).thenReturn(exists);
        ProxiedPlayer player = mock(ProxiedPlayer.class);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getName()).thenReturn("LegacyPlayer");
        NonVotedPlayersCache cache = mock(NonVotedPlayersCache.class, CALLS_REAL_METHODS);
        Field owner = NonVotedPlayersCache.class.getDeclaredField("plugin");
        owner.setAccessible(true);
        owner.set(cache, plugin);
        doNothing().when(cache).addPlayer(anyString(), anyString());
        cache.addPlayer(player);
        verify(storage).containsKeyQuery(uuid.toString());
        if (exists) verify(cache, never()).addPlayer(anyString(), anyString());
        else verify(cache).addPlayer(uuid.toString(), "LegacyPlayer");
    }
}
