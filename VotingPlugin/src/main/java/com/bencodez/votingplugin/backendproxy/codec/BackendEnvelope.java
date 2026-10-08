package com.bencodez.votingplugin.backendproxy.codec;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable, bounded representation of the version-one backend envelope. */
public final class BackendEnvelope {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_FIELDS = 64;
    public static final int MAX_KEY_LENGTH = 64;
    public static final int MAX_VALUE_LENGTH = 16384;
    private final String subchannel;
    private final int schema;
    private final Map<String, String> fields;

    private BackendEnvelope(String subchannel, int schema, Map<String, String> fields) {
        this.subchannel = subchannel;
        this.schema = schema;
        this.fields = Collections.unmodifiableMap(new LinkedHashMap<String, String>(fields));
    }

    public static BackendEnvelope of(String subchannel, Map<String, String> fields) {
        return of(subchannel, SCHEMA_VERSION, fields);
    }
    public static BackendEnvelope of(String subchannel, int schema, Map<String, String> fields) {
        if (subchannel == null || subchannel.length() == 0 || subchannel.length() > MAX_KEY_LENGTH)
            throw new IllegalArgumentException("invalid subchannel");
        if (schema != SCHEMA_VERSION) throw new IllegalArgumentException("unsupported schema");
        if (fields == null || fields.size() > MAX_FIELDS) throw new IllegalArgumentException("invalid fields");
        LinkedHashMap<String, String> copy = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> e : fields.entrySet()) {
            if (e.getKey() == null || e.getKey().length() == 0 || e.getKey().length() > MAX_KEY_LENGTH || e.getValue() == null || e.getValue().length() > MAX_VALUE_LENGTH)
                throw new IllegalArgumentException("invalid field");
            if (copy.put(e.getKey(), e.getValue()) != null) throw new IllegalArgumentException("duplicate field");
        }
        return new BackendEnvelope(subchannel, schema, copy);
    }
    public static Builder builder(String subchannel) { return new Builder(subchannel); }
    public Builder toBuilder() { return builder(subchannel).schema(schema).putAll(fields); }
    public static final class Builder {
        private final String subchannel;
        private int schema = SCHEMA_VERSION;
        private final Map<String,String> fields = new LinkedHashMap<String,String>();
        private Builder(String subchannel) { this.subchannel = subchannel; }
        public Builder schema(int value) { schema = value; return this; }
        public Builder put(String key, Object value) {
            if (value == null) throw new IllegalArgumentException("null field");
            fields.put(key, String.valueOf(value)); return this;
        }
        public Builder putAll(Map<String,String> values) { fields.putAll(values); return this; }
        public BackendEnvelope build() { return BackendEnvelope.of(subchannel, schema, fields); }
    }
    public String getSubchannel() { return subchannel; }
    public String getSubChannel() { return subchannel; }
    public int getSchema() { return schema; }
    public Map<String, String> getFields() { return fields; }
    public String get(String key) { return fields.get(key); }
}
