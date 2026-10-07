package com.bencodez.votingplugin.backendproxy.codec;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;
import com.bencodez.votingplugin.proxy.BungeeMessageData;

/** Validates current named fields before entering the retained backend processing API. */
public final class CurrentBackendMessages {
    private CurrentBackendMessages() { }
    public static ArrayList<String> arguments(BackendEnvelope envelope) {
        String sub = envelope.getSubChannel();
        if ("Vote".equals(sub) || "VoteOnline".equals(sub)) {
            String player = text(envelope, "player", 16);
            String uuid = uuid(envelope, "uuid");
            String service = text(envelope, "service", 256);
            String time = number(envelope, "time", 1, Long.MAX_VALUE);
            String totals = text(envelope, "totals", 1024);
            new BungeeMessageData(totals); // Parse completely before any user/reward mutation.
            boolean identified=envelope.get("voteId") != null && !envelope.get("voteId").isEmpty();
            if (identified) uuid(envelope, "voteId");
            boolean recorded=identified && Boolean.parseBoolean(bool(envelope,"queuedDelivery",false));
            bool(envelope,"delayValidated",false);
            return list(player, uuid, service, time, bool(envelope,"wasOnline",false),
                    bool(envelope,"realVote",false), totals, bool(envelope,"manageTotals",false),
                    "1", bool(envelope,"bungeeBroadcast",false), positive(envelope,"num"),
                    positive(envelope,"numberOfVotes"), "CURRENT", Boolean.toString(recorded));
        }
        if ("VoteUpdate".equals(sub)) {
            String totals=envelope.get("totals");
            if(totals!=null && !totals.isEmpty()) new BungeeMessageData(totals);
            String service=envelope.get("service");
            if(service!=null && !service.isEmpty()) text(envelope,"service",256);
            String time=envelope.get("lastVoteTime")!=null ? number(envelope,"lastVoteTime",0,Long.MAX_VALUE)
                    : envelope.get("time")!=null ? number(envelope,"time",0,Long.MAX_VALUE) : "0";
            return list(uuid(envelope,"playerUuid"), number(envelope,"votePartyCurrent",0,Integer.MAX_VALUE),
                    number(envelope,"votePartyRequired",0,Integer.MAX_VALUE), totals==null?"":totals,
                    service==null?"":service,time);
        }
        if ("VoteBroadcast".equals(sub)) return list(uuid(envelope,"uuid"),text(envelope,"player",16),text(envelope,"service",256));
        if ("VoteBroadcastOffline".equals(sub)) return list(uuid(envelope,"uuid"),text(envelope,"player",16),positive(envelope,"numberOfVotes"));
        if ("ServerName".equals(sub)) return list(text(envelope,"server",128));
        if ("VotePartyBroadcast".equals(sub)) return list(text(envelope,"broadcast",2048));
        if ("VotePartyBungee".equals(sub) || "BungeeTimeChange".equals(sub)) return list();
        throw new IllegalArgumentException("Unsupported current backend message");
    }
    private static ArrayList<String> list(String... values) { return new ArrayList<String>(Arrays.asList(values)); }
    private static String text(BackendEnvelope e, String key, int max) {
        String value=e.get(key);
        if (value==null || value.isEmpty() || value.length()>max || value.indexOf('\n')>=0 || value.indexOf('\r')>=0)
            throw new IllegalArgumentException("Invalid current message field: "+key);
        return value;
    }
    private static String uuid(BackendEnvelope e, String key) {
        String value=text(e,key,36);
        if (!UUID.fromString(value).toString().equalsIgnoreCase(value)) throw new IllegalArgumentException("Invalid UUID field");
        return value;
    }
    private static String number(BackendEnvelope e, String key, long min, long max) {
        String value=text(e,key,20); long n=Long.parseLong(value);
        if(n<min || n>max) throw new IllegalArgumentException("Invalid current message number");
        return value;
    }
    private static String positive(BackendEnvelope e,String key) { return e.get(key)==null ? "1" : number(e,key,1,Integer.MAX_VALUE); }
    private static String bool(BackendEnvelope e,String key,boolean fallback) {
        String value=e.get(key);
        if(value==null) return Boolean.toString(fallback);
        if(!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) throw new IllegalArgumentException("Invalid current message boolean");
        return value.toLowerCase(java.util.Locale.ROOT);
    }
}
