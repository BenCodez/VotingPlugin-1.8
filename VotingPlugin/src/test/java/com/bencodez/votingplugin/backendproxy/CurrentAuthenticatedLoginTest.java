package com.bencodez.votingplugin.backendproxy;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import com.bencodez.votingplugin.VotingPluginMain;
import com.bencodez.votingplugin.BungeeHandler;
import com.bencodez.votingplugin.backendproxy.codec.CurrentPluginMessagePacket;
import com.bencodez.votingplugin.proxy.security.TransportEnvelopeEncryption;

class CurrentAuthenticatedLoginTest {
    @Test void rawJoinCannotPublishUnauthenticatedPresence() {
        VotingPluginMain plugin=plugin(); Player player=player();
        CurrentPluginMessaging transport=new CurrentPluginMessaging(plugin,mock(BungeeHandler.class));
        transport.join(new PlayerJoinEvent(player,""));
        verifyNoInteractions(plugin.getBukkitScheduler());
        verify(player,never()).sendPluginMessage(any(),anyString(),any());
    }
    @Test void authenticatedLoginPublishesStatusAndCurrentIdentity() throws Exception { check(true,false,false); }
    @Test void replacedPhysicalSessionCannotPublishOldAuthentication() throws Exception { check(false,false,false); }
    @Test void wrongLogicalIdentityCannotPublishPresence() throws Exception { check(true,true,false); }
    @Test void authenticatedLoginWaitsForCarrierChannelRegistration() throws Exception { check(true,false,true); }
    private void check(boolean sameSession,boolean wrongIdentity,boolean deferred) throws Exception {
        VotingPluginMain plugin=plugin(); Player player=player();
        CurrentPluginMessaging transport=new CurrentPluginMessaging(plugin,mock(BungeeHandler.class));
        java.lang.reflect.Field field=CurrentPluginMessaging.class.getDeclaredField("encryption");field.setAccessible(true);
        field.set(transport,TransportEnvelopeEncryption.disabled(TransportEnvelopeEncryption.Domain.PROXY_BACKEND));
        transport.join(new PlayerJoinEvent(player,""));
        String logical=UUID.nameUUIDFromBytes("OfflinePlayer:alex".getBytes(StandardCharsets.UTF_8)).toString();
        transport.authenticatedLogin(player,wrongIdentity?UUID.randomUUID().toString():logical);
        ArgumentCaptor<Runnable> work=ArgumentCaptor.forClass(Runnable.class);
        verify(plugin.getBukkitScheduler()).runTask(eq(plugin),work.capture());
        try(MockedStatic<Bukkit> bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(()->Bukkit.getPlayer(player.getUniqueId())).thenReturn(sameSession?player:mock(Player.class));
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(Collections.singleton(player));
            if(deferred) when(player.getListeningPluginChannels()).thenReturn(Collections.emptySet());
            work.getValue().run();
            if(deferred) {
                verify(player,never()).sendPluginMessage(any(),anyString(),any());
                when(player.getListeningPluginChannels()).thenReturn(Collections.singleton("vp:vp"));
                transport.channelRegistered(new org.bukkit.event.player.PlayerRegisterChannelEvent(player,"vp:vp"));
            }
        }
        if(!sameSession || wrongIdentity) { verify(player,never()).sendPluginMessage(any(),anyString(),any());return; }
        ArgumentCaptor<byte[]> packets=ArgumentCaptor.forClass(byte[].class);
        verify(player,times(2)).sendPluginMessage(eq(plugin),eq("vp:vp"),packets.capture());
        assertEquals("statusokay",CurrentPluginMessagePacket.decode(packets.getAllValues().get(0),null).getSubChannel());
        assertEquals(logical,CurrentPluginMessagePacket.decode(packets.getAllValues().get(1),null).get("uuid"));
    }
    private VotingPluginMain plugin() {
        VotingPluginMain plugin=mock(VotingPluginMain.class,RETURNS_DEEP_STUBS);
        when(plugin.getBungeeSettings().getServer()).thenReturn("backend-test");
        when(plugin.getBungeeSettings().getPluginMessagingChannel()).thenReturn("vp:vp");
        return plugin;
    }
    private Player player() {
        Player player=mock(Player.class);when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getListeningPluginChannels()).thenReturn(Collections.singleton("vp:vp"));
        when(player.getName()).thenReturn("Alex");when(player.isOnline()).thenReturn(true);return player;
    }
}
