package com.bencodez.votingplugin.backendproxy.codec;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import com.bencodez.votingplugin.proxy.BungeeMessageData;

class CurrentBackendMessagesTest {
    private BackendEnvelope vote() {
        return BackendEnvelope.builder("Vote").put("player","Alex")
                .put("uuid","00000000-0000-0000-0000-000000000001")
                .put("voteId","00000000-0000-0000-0000-000000000002")
                .put("service","TestSite").put("time",1800000000000L)
                .put("realVote",true).put("wasOnline",true)
                .put("totals","v2//12//5//3//1//7//4//20//6")
                .put("manageTotals",true).put("setTotals",false).put("delayValidated",true).put("queuedDelivery",true).build();
    }
    @Test void proxyTotalsOwnershipUsesManageTotalsRatherThanObsoleteSetTotals() {
        ArrayList<String> args=CurrentBackendMessages.arguments(vote());
        assertEquals("true",args.get(7));
        assertEquals("false",CurrentBackendMessages.arguments(vote().toBuilder().put("manageTotals",false).put("setTotals",true).build()).get(7));
        assertEquals("CURRENT",args.get(12)); assertEquals("true",args.get(13));
    }
    @Test void liveValidationMarkerDoesNotDisableBackendCooldown() {
        assertEquals("false",CurrentBackendMessages.arguments(vote().toBuilder().put("queuedDelivery",false).build()).get(13));
        java.util.Map<String,String> fields=new java.util.LinkedHashMap<String,String>(vote().getFields());
        fields.remove("voteId");
        assertEquals("false",CurrentBackendMessages.arguments(BackendEnvelope.of("Vote",fields)).get(13));
    }
    @Test void v2SnapshotDoesNotInventMilestoneCount() {
        BungeeMessageData totals=new BungeeMessageData(vote().get("totals"));
        assertEquals(12,totals.getAllTimeTotal()); assertEquals(7,totals.getPoints());
        assertEquals(4,totals.getVotePartyCurrent()); assertEquals(20,totals.getVotePartyRequired());
        assertEquals(6,totals.getDateMonthTotal()); assertFalse(totals.isLegacyMilestoneCount());
        assertTrue(new BungeeMessageData("12//5//3//1//7//9//4//20//6").isLegacyMilestoneCount());
        assertEquals(9,new BungeeMessageData("12//5//3//1//7//9//4//20//6").getMilestoneCount());
    }
    @Test void malformedVotesAreRejectedBeforeProcessing() {
        assertThrows(IllegalArgumentException.class,() -> CurrentBackendMessages.arguments(vote().toBuilder().put("uuid","1-1-1-1-1").build()));
        assertThrows(IllegalArgumentException.class,() -> CurrentBackendMessages.arguments(vote().toBuilder().put("manageTotals","yes").build()));
        assertThrows(IllegalArgumentException.class,() -> CurrentBackendMessages.arguments(vote().toBuilder().put("totals","v2//1").build()));
        assertThrows(IllegalArgumentException.class,() -> CurrentBackendMessages.arguments(vote().toBuilder().put("num",0).build()));
    }
    @Test void referenceProxyFramingDecodesWithoutDelimiterSplitting() throws Exception {
        String json=JsonEnvelopeCodec.encode(vote());
        ByteArrayOutputStream bytes=new ByteArrayOutputStream(); DataOutputStream out=new DataOutputStream(bytes);
        out.writeUTF("Vote"); out.writeInt(json.getBytes("UTF-8").length); out.writeUTF(json);
        assertEquals(vote().getFields(),CurrentPluginMessagePacket.decode(bytes.toByteArray(),null).getFields());
        byte[] encoded=CurrentPluginMessagePacket.encode(vote(),null);
        assertArrayEquals(bytes.toByteArray(),encoded);
    }
    @Test void legacyAesFramingKeepsReferencePlaintextLength(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        java.nio.file.Path key=directory.resolve("secretkey.key");
        java.nio.file.Files.write(key,java.util.Base64.getEncoder().encode("0123456789abcdef0123456789abcdef".getBytes("US-ASCII")));
        com.bencodez.simpleapi.encryption.EncryptionHandler cipher=new com.bencodez.simpleapi.encryption.EncryptionHandler("test",key.toFile());
        byte[] packet=CurrentPluginMessagePacket.encode(vote(),cipher);
        DataInputStream frame=new DataInputStream(new ByteArrayInputStream(packet));
        assertEquals("Vote",cipher.decrypt(frame.readUTF()));
        assertEquals(JsonEnvelopeCodec.encode(vote()).getBytes("UTF-8").length,frame.readInt());
        assertEquals(vote().getFields(),CurrentPluginMessagePacket.decode(packet,cipher).getFields());
    }
    @Test void rejectsMismatchedPacketHeaderAndTrailingBytes() throws Exception {
        byte[] encoded=CurrentPluginMessagePacket.encode(vote(),null);
        byte[] trailing=java.util.Arrays.copyOf(encoded,encoded.length+1);
        assertThrows(IOException.class,() -> CurrentPluginMessagePacket.decode(trailing,null));
        encoded[2]='X';
        assertThrows(IOException.class,() -> CurrentPluginMessagePacket.decode(encoded,null));
    }
    @Test void voteUpdateRetainsCurrentCooldownTimeAndTotals() {
        ArrayList<String> args=CurrentBackendMessages.arguments(BackendEnvelope.builder("VoteUpdate")
                .put("playerUuid","00000000-0000-0000-0000-000000000001").put("votePartyCurrent",4)
                .put("votePartyRequired",20).put("service","TestSite").put("lastVoteTime",1800000000000L)
                .put("totals","v2//12//5//3//1//7//4//20//6").build());
        assertEquals("TestSite",args.get(4)); assertEquals("1800000000000",args.get(5));
        assertEquals("v2//12//5//3//1//7//4//20//6",args.get(3));
        assertEquals("hello",CurrentBackendMessages.arguments(BackendEnvelope.builder("VotePartyBroadcast").put("broadcast","hello").build()).get(0));
    }
    @Test void broadcastAcceptsCurrentMinimumFields() {
        ArrayList<String> args=CurrentBackendMessages.arguments(BackendEnvelope.builder("VoteBroadcast")
                .put("uuid","00000000-0000-0000-0000-000000000001").put("player","Alex").put("service","TestSite").build());
        assertEquals(3,args.size());
    }
}
