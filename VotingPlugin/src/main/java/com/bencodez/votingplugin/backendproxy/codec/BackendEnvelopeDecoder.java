package com.bencodez.votingplugin.backendproxy.codec;

/** Small seam allowing transport handlers to depend on decoding without Gson. */
public interface BackendEnvelopeDecoder {
    BackendEnvelope decodeEnvelope(String json);
}
