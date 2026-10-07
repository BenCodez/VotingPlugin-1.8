package com.bencodez.votingplugin.backendproxy.codec;

import java.io.*;
import java.nio.charset.StandardCharsets;
import com.bencodez.simpleapi.encryption.EncryptionHandler;

/** Current SimpleAPI plugin-message framing; legacy AES is a separate optional layer. */
public final class CurrentPluginMessagePacket {
    private CurrentPluginMessagePacket() { }
    public static byte[] encode(BackendEnvelope envelope, EncryptionHandler encryption) throws IOException {
        String payload=JsonEnvelopeCodec.encode(envelope);
        String header=envelope.getSubChannel();
        int payloadLength=payload.getBytes(StandardCharsets.UTF_8).length;
        if(encryption!=null) { header=encryption.encrypt(header); payload=encryption.encrypt(payload); }
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        DataOutputStream out=new DataOutputStream(bytes);
        out.writeUTF(header); out.writeInt(payloadLength); out.writeUTF(payload);
        out.flush();
        if(bytes.size()>32767) throw new IOException("Plugin message exceeds carrier packet bound");
        return bytes.toByteArray();
    }
    public static BackendEnvelope decode(byte[] packet, EncryptionHandler encryption) throws IOException {
        if(packet==null || packet.length>32767) throw new IOException("Invalid plugin message size");
        DataInputStream in=new DataInputStream(new ByteArrayInputStream(packet));
        String header=in.readUTF(); int length=in.readInt(); String payload=in.readUTF();
        if(in.available()!=0 || length<0 || length>65536) throw new IOException("Invalid plugin message framing");
        if(encryption!=null) { header=encryption.decrypt(header); payload=encryption.decrypt(payload); }
        if(length!=payload.getBytes(StandardCharsets.UTF_8).length) throw new IOException("Invalid plugin message length");
        BackendEnvelope envelope=JsonEnvelopeCodec.decode(payload);
        if(!header.equals(envelope.getSubChannel())) throw new IOException("Plugin message channel mismatch");
        return envelope;
    }
}
