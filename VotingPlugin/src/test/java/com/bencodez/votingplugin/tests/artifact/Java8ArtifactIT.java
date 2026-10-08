package com.bencodez.votingplugin.tests.artifact;

import static org.junit.jupiter.api.Assertions.*;
import java.io.DataInputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;

class Java8ArtifactIT {
    @Test void exactShadedBackendRetainsLegacyApisAndOnlyJava8Classes() throws Exception {
        Path artifact = Paths.get(System.getProperty("votingplugin.jar"));
        assertTrue(Files.isRegularFile(artifact));
        int classCount = 0;
        HashSet<String> names = new HashSet<>();
        try (JarFile jar = new JarFile(artifact.toFile())) {
            assertNotNull(jar.getJarEntry("plugin.yml"));
            assertNull(jar.getJarEntry("bungee.yml"));
            assertNotNull(jar.getJarEntry("com/bencodez/votingplugin/VotingPluginMain.class"));
            assertNull(jar.getJarEntry("com/bencodez/votingplugin/proxy/bungee/VotingPluginBungee.class"));
            assertNotNull(jar.getJarEntry("com/bencodez/votingplugin/backendproxy/CurrentPluginMessaging.class"));
            assertNull(jar.getJarEntry("velocity-plugin.json"));
            assertNotNull(jar.getJarEntry("com/bencodez/votingplugin/simpleapi/folialib/impl/LegacySpigotImplementation.class"));
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                assertTrue(names.add(name), "Duplicate entry: " + name);
                assertFalse(name.matches("META-INF/[^/]+\\.(SF|RSA|DSA)"), "Stale signature: " + name);
                assertFalse(name.startsWith("com/bencodez/votingplugin/proxy/velocity/"), name);
                if (!name.endsWith(".class") || name.startsWith("META-INF/versions/")) continue;
                try (DataInputStream in = new DataInputStream(jar.getInputStream(entry))) {
                    assertEquals(0xCAFEBABE, in.readInt(), name);
                    in.readUnsignedShort();
                    int major = in.readUnsignedShort();
                    assertTrue(major <= 52, name + " requires class version " + major);
                    classCount++;
                }
            }
        }
        assertTrue(classCount > 1000, "Inspect the actual self-contained shaded plugin");
        try (URLClassLoader loader = new URLClassLoader(new URL[] {artifact.toUri().toURL()}, null)) {
            Class<?> legacy = Class.forName("com.bencodez.votingplugin.simpleapi.servercomm.global.GlobalMessageProxyHandler", false, loader);
            assertNotNull(legacy.getMethod("sendMessage", String.class, String.class, String[].class));
            Class<?> config = Class.forName("com.bencodez.votingplugin.advancedcore.hikari.HikariConfig", true, loader);
            Object pool = config.getConstructor().newInstance();
            config.getMethod("setMaximumPoolSize", int.class).invoke(pool, 2);
            assertEquals(2, config.getMethod("getMaximumPoolSize").invoke(pool));
        }
    }
}
