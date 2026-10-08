package com.bencodez.votingplugin.votesites;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import com.bencodez.votingplugin.VotingPluginMain;
import com.bencodez.votingplugin.config.Config;
import com.bencodez.votingplugin.config.ConfigVoteSites;
import com.bencodez.votingplugin.objects.VoteSite;

class LegacyVoteSiteResolutionTest {
    @Test void rawNamesIncludeIncompleteDisabledSectionsWithoutCreatingAnything() throws Exception {
        Fixture f = new Fixture();
        f.yaml.set("VoteSites.InvalidEntry", "scalar");
        assertEquals(Collections.singletonList("Disabled_Site"), f.sites.getRawVoteSiteNames());
        verify(f.sites, never()).generateVoteSite(anyString());
    }
    @Test void disabledSiteIsRecognizedByServiceDisplayAndNormalizedKey() throws Exception {
        Fixture f = new Fixture();
        assertEquals("Disabled_Site", f.plugin.getVoteSiteName(false, "external-service.example"));
        assertTrue(f.plugin.hasConfiguredVoteSite("external-service.example"));
        assertTrue(f.plugin.hasConfiguredVoteSite("Disabled display"));
        assertTrue(f.plugin.hasConfiguredVoteSite("Disabled.Site"));
        assertFalse(f.plugin.hasConfiguredVoteSite("unknown-service"));
        assertFalse(f.plugin.hasConfiguredVoteSite((String[]) null));
        verify(f.sites, never()).generateVoteSite(anyString());
    }
    @Test void enabledOnlyLookupCannotReturnOrAutoEnableDisabledSite() throws Exception {
        Fixture f = new Fixture();
        VoteSite disabled = mock(VoteSite.class);
        when(disabled.getKey()).thenReturn("Disabled_Site");
        when(disabled.getDisplayName()).thenReturn("Disabled display");
        when(f.plugin.getVoteSites()).thenReturn(Collections.singletonList(disabled));
        assertNull(f.plugin.getVoteSite("external-service.example", true));
        assertNull(f.plugin.getVoteSite("Disabled_Site", true));
        assertSame(disabled, f.plugin.getVoteSite("external-service.example", false));
        verify(f.sites, never()).generateVoteSite(anyString());
        assertFalse(f.yaml.getBoolean("VoteSites.Disabled_Site.Enabled"));
    }
    @Test void missingSiteRemainsMissingWhenAutoCreationIsDisabled() throws Exception {
        Fixture f = new Fixture();
        when(f.config.isAutoCreateVoteSites()).thenReturn(false);
        assertNull(f.plugin.getVoteSite("unknown.example", true));
        verify(f.sites, never()).generateVoteSite(anyString());
    }
    private static class Fixture {
        final VotingPluginMain plugin = mock(VotingPluginMain.class, CALLS_REAL_METHODS);
        final Config config = mock(Config.class);
        final ConfigVoteSites sites = mock(ConfigVoteSites.class, CALLS_REAL_METHODS);
        final YamlConfiguration yaml = new YamlConfiguration();
        Fixture() throws Exception {
            yaml.set("VoteSites.Disabled_Site.Enabled", false);
            yaml.set("VoteSites.Disabled_Site.ServiceSite", "external-service.example");
            yaml.set("VoteSites.Disabled_Site.Name", "Disabled display");
            when(sites.getData()).thenReturn(yaml);
            doReturn(new ArrayList<String>()).when(sites).getVoteSitesNames(anyBoolean());
            when(config.isAutoCreateVoteSites()).thenReturn(true);
            when(plugin.getConfigVoteSites()).thenReturn(sites);
            when(plugin.getVoteSites()).thenReturn(Collections.emptyList());
            for (String name : Arrays.asList("configFile", "configVoteSites")) {
                Field field = VotingPluginMain.class.getDeclaredField(name);
                field.setAccessible(true);
                field.set(plugin, name.equals("configFile") ? config : sites);
            }
        }
    }
}
