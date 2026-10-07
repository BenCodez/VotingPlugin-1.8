package com.bencodez.votingplugin.backendproxy.codec;

import static org.junit.jupiter.api.Assertions.*;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonEnvelopeCodecTest {
    @Test void roundTripAndNativeScalars() {
        Map<String,String> f=new LinkedHashMap<String,String>(); f.put("player","Alex"); f.put("time","12"); f.put("online","true");
        BackendEnvelope e=JsonEnvelopeCodec.decode(JsonEnvelopeCodec.encode(BackendEnvelope.of("Vote",f)));
        assertEquals("Alex",e.get("player")); assertEquals("12",e.get("time")); assertEquals("true",e.get("online"));
    }
    @Test void rejectsTrailingDuplicateAndNestedData() {
        assertThrows(IllegalArgumentException.class,()->JsonEnvelopeCodec.decode("{\"t\":\"Vote\",\"f\":{}} {}"));
        assertThrows(IllegalArgumentException.class,()->JsonEnvelopeCodec.decode("{\"t\":\"Vote\",\"t\":\"Vote\"}"));
        assertThrows(IllegalArgumentException.class,()->JsonEnvelopeCodec.decode("{\"t\":\"Vote\",\"f\":{\"x\":{}}}"));
    }
    @Test void nativeBooleansAndNumbers() {
        BackendEnvelope e=JsonEnvelopeCodec.decode("{\"t\":\"Vote\",\"v\":1,\"f\":{\"realVote\":true,\"time\":12}}");
        assertEquals("true",e.get("realVote")); assertEquals("12",e.get("time"));
    }
}
