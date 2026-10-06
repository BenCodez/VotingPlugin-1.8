package com.bencodez.votingplugin.proxy.bungee;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;

class LegacyOfflineUUIDTest {
    @Test void connectedPlayerUsesCanonicalSpellingForOfflineIdentity() {
        VotingPluginBungee plugin = fixture();
        ProxiedPlayer player = mock(ProxiedPlayer.class);
        when(plugin.getProxy().getPlayer("legacyplayer")).thenReturn(player);
        when(player.isConnected()).thenReturn(true);
        when(player.getName()).thenReturn("LegacyPlayer");
        assertEquals(offline("LegacyPlayer"), plugin.getOfflineUUID("legacyplayer"));
        assertNotEquals(offline("legacyplayer"), plugin.getOfflineUUID("legacyplayer"));
        verify(player, never()).getUniqueId();
    }
    @Test void disconnectedPlayerDoesNotReplaceTheSuppliedName() {
        VotingPluginBungee plugin = fixture();
        ProxiedPlayer player = mock(ProxiedPlayer.class);
        when(plugin.getProxy().getPlayer("legacyplayer")).thenReturn(player);
        when(player.isConnected()).thenReturn(false);
        assertEquals(offline("legacyplayer"), plugin.getOfflineUUID("legacyplayer"));
        verify(player, never()).getName();
    }
    @Test void absentPlayerRetainsTheExistingUtf8OfflineAlgorithm() {
        VotingPluginBungee plugin = fixture();
        assertEquals(offline("LegacyPlayer"), plugin.getOfflineUUID("LegacyPlayer"));
        assertEquals(offline("Player\u00e9"), plugin.getOfflineUUID("Player\u00e9"));
    }
    private VotingPluginBungee fixture() {
        VotingPluginBungee plugin = mock(VotingPluginBungee.class, CALLS_REAL_METHODS);
        doReturn(mock(ProxyServer.class)).when(plugin).getProxy();
        return plugin;
    }
    private String offline(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)).toString();
    }
}
