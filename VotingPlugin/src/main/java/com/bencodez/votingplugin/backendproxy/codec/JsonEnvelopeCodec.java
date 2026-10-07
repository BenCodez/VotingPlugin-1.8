package com.bencodez.votingplugin.backendproxy.codec;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.Map;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

/** Strict bounded streaming codec for the canonical backend envelope. */
public final class JsonEnvelopeCodec implements BackendEnvelopeDecoder {
    public static final int MAX_JSON_CHARS = 65536;
    public static final JsonEnvelopeCodec INSTANCE = new JsonEnvelopeCodec();
    private JsonEnvelopeCodec() { }
    public static String encode(BackendEnvelope envelope) { return INSTANCE.encodeEnvelope(envelope); }
    public static BackendEnvelope decode(String json) { return INSTANCE.decodeEnvelope(json); }
    public String encodeEnvelope(BackendEnvelope e) {
        if (e == null) throw new IllegalArgumentException("null envelope");
        StringWriter out = new StringWriter();
        try {
            JsonWriter w = new JsonWriter(out); w.setHtmlSafe(false);
            w.beginObject(); w.name("t").value(e.getSubchannel()); w.name("v").value(e.getSchema()); w.name("f").beginObject();
            for (Map.Entry<String,String> x : e.getFields().entrySet()) w.name(x.getKey()).value(x.getValue());
            w.endObject(); w.endObject(); w.close();
        } catch (IOException ex) { throw new IllegalStateException(ex); }
        String result = out.toString(); if (result.length() > MAX_JSON_CHARS) throw new IllegalArgumentException("envelope too large");
        return result;
    }
    public BackendEnvelope decodeEnvelope(String json) {
        if (json == null || json.length() > MAX_JSON_CHARS) throw new IllegalArgumentException("envelope too large");
        try {
            JsonReader r = new JsonReader(new StringReader(json)); r.setLenient(false); String t = null; int v = 1; boolean vSeen=false, fSeen=false; Map<String,String> f = new LinkedHashMap<String,String>();
            r.beginObject();
            while (r.hasNext()) { String name = r.nextName();
                if ("t".equals(name)) { if (t != null || r.peek() != JsonToken.STRING) throw bad(); t = r.nextString(); }
                else if ("v".equals(name)) { if (vSeen || r.peek() != JsonToken.NUMBER) throw bad(); vSeen=true; v = Integer.parseInt(r.nextString()); }
                else if ("f".equals(name)) { if (fSeen) throw bad(); fSeen=true; r.beginObject(); while (r.hasNext()) { String k=r.nextName(); if (f.containsKey(k) || k.length()==0 || k.length()>64 || f.size()>=64) throw bad(); JsonToken tok=r.peek(); if(tok!=JsonToken.STRING&&tok!=JsonToken.NUMBER&&tok!=JsonToken.BOOLEAN) throw bad(); String val=tok==JsonToken.BOOLEAN ? Boolean.toString(r.nextBoolean()) : r.nextString(); if(val.length()>16384) throw bad(); f.put(k,val); } r.endObject(); }
                else throw bad();
            } r.endObject(); if (r.peek()!=JsonToken.END_DOCUMENT) throw bad();
            if (t == null) throw bad(); return BackendEnvelope.of(t, v, f);
        } catch (Exception ex) { if (ex instanceof IllegalArgumentException) throw (IllegalArgumentException)ex; throw new IllegalArgumentException("invalid envelope", ex); }
    }
    private static IllegalArgumentException bad() { return new IllegalArgumentException("invalid envelope"); }
}
