package com.bencodez.votingplugin.backendproxy;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitTask;
import com.bencodez.simpleapi.encryption.EncryptionHandler;
import com.bencodez.votingplugin.BungeeHandler;
import com.bencodez.votingplugin.VotingPluginMain;
import com.bencodez.votingplugin.backendproxy.codec.*;
import com.bencodez.votingplugin.proxy.security.TransportEnvelopeEncryption;

/** Current-proxy compatibility lane. All player/presence reads run on the Bukkit owner thread. */
public final class CurrentPluginMessaging implements PluginMessageListener, Listener, AutoCloseable {
    private final VotingPluginMain plugin;
    private final BungeeHandler handler;
    private final String channel;
    private final String server;
    private final Map<UUID,UUID> connections = new HashMap<UUID,UUID>();
    private final Map<UUID,Player> authenticated = new HashMap<UUID,Player>();
    private final ThreadPoolExecutor incoming = new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,
            new ArrayBlockingQueue<Runnable>(128), runnable -> {
                Thread thread=new Thread(runnable,"VotingPlugin-current-backend"); thread.setDaemon(true); return thread;
            },new ThreadPoolExecutor.AbortPolicy());
    private final LinkedHashMap<String,Boolean> completed = new LinkedHashMap<String,Boolean>();
    private EncryptionHandler legacyEncryption;
    private TransportEnvelopeEncryption encryption;
    private BukkitTask heartbeat;
    private volatile boolean closed;
    public CurrentPluginMessaging(VotingPluginMain plugin, BungeeHandler handler) {
        this.plugin=plugin; this.handler=handler;
        channel=plugin.getBungeeSettings().getPluginMessagingChannel();
        server=plugin.getBungeeSettings().getServer();
        if(server.trim().isEmpty() || "PleaseSet".equalsIgnoreCase(server)) throw new IllegalArgumentException("Set backend Server before enabling proxy mode");
    }
    public void start() {
        try {
            File key=new File(plugin.getDataFolder(),"secretkey.key");
            if(plugin.getBungeeSettings().isPluginMessageEncryption()) legacyEncryption=new EncryptionHandler(plugin.getName(),key);
            encryption=TransportEnvelopeEncryption.load(key.toPath(),TransportEnvelopeEncryption.Domain.PROXY_BACKEND,
                    plugin.getBungeeSettings().getData().getBoolean("CommunicationEncryption",false));
        } catch(IOException failure) { incoming.shutdownNow(); throw new IllegalStateException("Cannot initialize current transport encryption",failure); }
        plugin.registerBungeeChannels(channel);
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin,channel,plugin.getPluginMessaging());
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin,channel,this);
        plugin.registerEvents(this);
        for(Player player:Bukkit.getOnlinePlayers()) connections.put(player.getUniqueId(),UUID.randomUUID());
        heartbeat=Bukkit.getScheduler().runTaskTimer(plugin,() -> status(null),20L,600L);
        status(null);
    }
    @Override public void onPluginMessageReceived(String received,Player carrier,byte[] packet) {
        if(closed || !channel.equals(received)) return;
        try {
            BackendEnvelope envelope=CurrentPluginMessagePacket.decode(packet,legacyEncryption);
            TransportEnvelopeEncryption.Decryption result=encryption.decrypt(envelope);
            if(!result.accepted()) { plugin.getLogger().warning("Rejected current proxy encryption envelope"); return; }
            envelope=result.envelope();
            final BackendEnvelope message=envelope;
            String sub=message.getSubChannel();
            if("Status".equals(sub)) { status(message.get("requestId")); return; }
            final ArrayList<String> args=CurrentBackendMessages.arguments(message);
            if("VotePartyBungee".equals(sub)) {
                Bukkit.getScheduler().runTask(plugin,() -> { if(!closed) handler.getGlobalMessageHandler().onMessage(sub,args); });
                return;
            }
            incoming.execute(() -> {
                String voteId=message.get("voteId");
                boolean vote="Vote".equals(sub) || "VoteOnline".equals(sub);
                if(vote && voteId!=null && !voteId.isEmpty() && completed.containsKey(voteId)) return;
                try {
                    handler.getGlobalMessageHandler().onMessage(sub,args);
                    if(vote && voteId!=null && !voteId.isEmpty()) {
                        completed.put(voteId,Boolean.TRUE);
                        if(completed.size()>4096) completed.remove(completed.keySet().iterator().next());
                    }
                } catch(RuntimeException failure) { plugin.getLogger().warning("Current proxy message processing failed: "+sub); }
            });
        } catch(IOException | IllegalArgumentException | RejectedExecutionException failure) {
            plugin.getLogger().warning("Rejected current proxy message or exhausted backend admission capacity");
        }
    }
    public void sendLegacy(String sub,String... args) {
        if(closed) return;
        if("Login".equals(sub)) {
            throw new IllegalArgumentException("Current login requires an authenticated player session");
        }
        BackendEnvelope.Builder builder=BackendEnvelope.builder(sub);
        if("statusokay".equals(sub)) { status(null); return; }
        if("TimeChangeFinished".equals(sub)) builder.put("server",server);
        else if("voteupdate".equals(sub) && args.length>0) builder.put("playerUuid",args[0]);
        else { plugin.getLogger().warning("Unsupported outbound current proxy message: "+sub); return; }
        send(builder.build());
    }
    /** Called by the existing authenticated, storage-ready VotingPlugin login handler. */
    public void authenticatedLogin(final Player player, String logicalUuid) {
        final UUID logical=UUID.fromString(logicalUuid);
        plugin.getBukkitScheduler().runTask(plugin,() -> {
            if(closed || !player.isOnline() || Bukkit.getPlayer(player.getUniqueId())!=player
                    || !connections.containsKey(player.getUniqueId()) || !identity(player).equals(logical)) return;
            authenticated.put(player.getUniqueId(),player);
            publishAuthenticatedLogin(player);

        });
    }
    private void publishAuthenticatedLogin(Player player) {
        if(closed || authenticated.get(player.getUniqueId())!=player || !player.isOnline()
                || Bukkit.getPlayer(player.getUniqueId())!=player
                || !player.getListeningPluginChannels().contains(channel)) return;
        status(null);
        send(BackendEnvelope.builder("login").put("server",server)
                .put("player",player.getName()).put("uuid",identity(player)).build());
    }
    @EventHandler public void channelRegistered(PlayerRegisterChannelEvent event) {
        if(channel.equals(event.getChannel())) publishAuthenticatedLogin(event.getPlayer());
    }
    private void status(String requestId) {
        BackendEnvelope.Builder builder=BackendEnvelope.builder("statusokay").put("server",server)
                .put("voteDeliveryAckVersion",0).put("voteDelayRejectionAckVersion",0);
        if(requestId!=null && !requestId.isEmpty()) { UUID.fromString(requestId); builder.put("requestId",requestId); }
        send(builder.build());
    }
    private UUID identity(Player player) {
        if(plugin.getOptions().isOnlineMode()) return player.getUniqueId();
        return UUID.nameUUIDFromBytes(("OfflinePlayer:"+player.getName().toLowerCase(Locale.ROOT).trim())
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    @EventHandler public void join(PlayerJoinEvent event) {
        if(closed) return;
        Player player=event.getPlayer(); UUID connection=UUID.randomUUID(); connections.put(player.getUniqueId(),connection);
        authenticated.remove(player.getUniqueId());

    }
    @EventHandler public void quit(PlayerQuitEvent event) {
        if(closed) return;
        Player player=event.getPlayer(); connections.remove(player.getUniqueId());
        authenticated.remove(player.getUniqueId());
        // The player-facing proxy observes logout directly for plugin messaging.
    }
    private void send(BackendEnvelope envelope) {
        if(closed) return;
        if(!Bukkit.isPrimaryThread()) { plugin.getBukkitScheduler().runTask(plugin,() -> send(envelope)); return; }
        // With no carrier the periodic owner-thread heartbeat will retry; no failure/spam or vote synthesis.
        Player carrier=null; for(Player player:Bukkit.getOnlinePlayers()) {
            if(player.getListeningPluginChannels().contains(channel)) { carrier=player; break; }
        }
        if(carrier==null) return;
        try { carrier.sendPluginMessage(plugin,channel,CurrentPluginMessagePacket.encode(encryption.encrypt(envelope),legacyEncryption)); }
        catch(IOException | IllegalArgumentException failure) { plugin.getLogger().warning("Current proxy message exceeds packet bounds"); }
    }
    @Override public void close() {
        if(!closed) {
            closed=true;
            if(heartbeat!=null) heartbeat.cancel();
            plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin,channel,this);
            HandlerList.unregisterAll(this);
            incoming.shutdown();
        }
        // A previous timeout must not allow a later teardown to skip unfinished work.
        try {
            if(!incoming.awaitTermination(5,TimeUnit.SECONDS)) {
                throw new IllegalStateException("Current backend message processing did not settle");
            }
        } catch(InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted draining current backend messages",interrupted);
        }
    }
}
