package com.bencodez.votingplugin.backendproxy;

/** Select the current carrier lane without silently changing an existing dedicated transport. */
public final class ProxyProtocol {
    private ProxyProtocol() { }
    public static String resolve(String configured, String method) {
        if (configured == null) {
            if ("MYSQL".equalsIgnoreCase(method) || "REDIS".equalsIgnoreCase(method)
                    || "MQTT".equalsIgnoreCase(method) || "SOCKETS".equalsIgnoreCase(method)) return "LEGACY";
            return "CURRENT";
        }
        if ("CURRENT".equalsIgnoreCase(configured)) return "CURRENT";
        if ("LEGACY".equalsIgnoreCase(configured)) return "LEGACY";
        throw new IllegalArgumentException("ProxyProtocol must be CURRENT or LEGACY");
    }
}
