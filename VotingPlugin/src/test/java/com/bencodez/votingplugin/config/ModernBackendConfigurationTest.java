package com.bencodez.votingplugin.config;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Logger;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import com.google.gson.*;
import com.bencodez.votingplugin.VotingPluginMain;

class ModernBackendConfigurationTest {
    private YamlConfiguration resource(String name) throws Exception {
        YamlConfiguration y=new YamlConfiguration();
        try(InputStreamReader reader=new InputStreamReader(getClass().getResourceAsStream("/"+name),StandardCharsets.UTF_8)) {y.load(reader);}
        return y;
    }
    private Config config(YamlConfiguration y) {
        Config c=mock(Config.class,CALLS_REAL_METHODS);doReturn(y).when(c).getData();
        VotingPluginMain plugin=mock(VotingPluginMain.class);when(plugin.getLogger()).thenReturn(Logger.getLogger("config-test"));doReturn(plugin).when(c).getPlugin();return c;
    }
    @Test void bundledBackendKeysAndNonMaterialDefaultsMatchPinnedMain() throws Exception {
        JsonObject expected;
        try(InputStreamReader r=new InputStreamReader(getClass().getResourceAsStream("/pinned-main-backend-configs.json"),StandardCharsets.UTF_8)) {expected=new JsonParser().parse(r).getAsJsonObject();}
        Gson gson=new Gson();
        for(Map.Entry<String,JsonElement> file:expected.entrySet()) {
            Map<String,JsonElement> leaves=new TreeMap<String,JsonElement>();flatten(file.getValue(),"",leaves);
            YamlConfiguration y=resource(file.getKey());Map<String,JsonElement> actual=new TreeMap<String,JsonElement>();
            for(String key:y.getKeys(true)) {
                Object value=y.get(key);if(value instanceof ConfigurationSection || value==null)continue;
                if(key.endsWith(".Material")) {assertNotNull(Material.matchMaterial(String.valueOf(value)),key);continue;}
                if (key.endsWith(".Data") && y.contains(key.substring(0,key.length()-4)+"Material")) {
                    assertTrue(((Number)value).intValue() >= 0 && ((Number)value).intValue() <= 15,key);continue;
                }
                actual.put(key,gson.toJsonTree(value));
            }
            leaves.keySet().removeIf(key->key.endsWith(".Material") || (key.endsWith(".Data") && y.contains(key.substring(0,key.length()-4)+"Material")));
            assertEquals(leaves.keySet(),actual.keySet(),file.getKey());
            for(String key:leaves.keySet())assertEquals(leaves.get(key),actual.get(key),file.getKey()+":"+key);
        }
    }
    private static void flatten(JsonElement value,String prefix,Map<String,JsonElement> out) {
        if(value.isJsonObject()) {for(Map.Entry<String,JsonElement> e:value.getAsJsonObject().entrySet())flatten(e.getValue(),prefix.isEmpty()?e.getKey():prefix+"."+e.getKey(),out);}
        else if(!value.isJsonNull())out.put(prefix,value);
    }
    @Test void modernTimingAndBroadcastLoadWithoutChangingOperatorConfig() throws Exception {
        YamlConfiguration y=resource("Config.yml");String before=y.saveToString();Config c=config(y);c.loadValues();
        assertEquals(3,c.getDelayBetweenUpdates());assertTrue(c.getFormatBroadCastMsg().contains("%site%"));assertEquals(before,y.saveToString());
        y.set("DelayBetweenUpdates","30s");c.loadValues();assertEquals(1,c.getDelayBetweenUpdates());
        y.set("Format.BroadcastMsg","legacy");y.set("DelayBetweenUpdates",8);c.loadValues();assertEquals("legacy",c.getFormatBroadCastMsg());assertEquals(8,c.getDelayBetweenUpdates());
        y.set("VoteBroadcast.Type","NONE");c.loadValues();assertFalse(c.isBroadcastVotesEnabled());
        y.set("DelayBetweenUpdates","nonsense");assertThrows(IllegalArgumentException.class,c::loadValues);
    }
    @Test void modernAndLegacyVoteSiteDelayReachSameHours() {
        YamlConfiguration y=new YamlConfiguration();y.set("VoteSites.Site.VoteDelay","24h");
        ConfigVoteSites sites=mock(ConfigVoteSites.class,CALLS_REAL_METHODS);doReturn(y).when(sites).getData();
        assertEquals(24d,sites.getVoteDelay("Site"));y.set("VoteSites.Site.VoteDelay",12d);assertEquals(12d,sites.getVoteDelay("Site"));
        y.set("VoteSites.Site.VoteDelay","90m");assertEquals(1.5d,sites.getVoteDelay("Site"));
        y.set("VoteSites.Site.VoteDelay","wrong");assertThrows(IllegalArgumentException.class,()->sites.getVoteDelay("Site"));
    }
    @Test void nestedDisplayItemsWorkAndCategoryLinksCannotBecomeFreePurchases() throws Exception {
        YamlConfiguration y=resource("Shop.yml");ShopFile shop=mock(ShopFile.class,CALLS_REAL_METHODS);doReturn(y).when(shop).getData();
        assertEquals("DIAMOND",shop.getShopDisplayItemSection("Diamond").getString("Material"));
        assertTrue(shop.getShopIdentifiers().contains("Diamond"));assertFalse(shop.getShopIdentifiers().contains("Blocks"));assertEquals(3,shop.getShopIdentifierCost("Diamond"));
        y.set("Shop.Legacy.Material","STONE");assertEquals("STONE",shop.getShopDisplayItemSection("Legacy").getString("Material"));
        assertSame(y.getConfigurationSection("Shop.Diamond"),shop.getShopIdentifierSection("Diamond"));
    }
    @Test void votePartyReminderAliasesPreserveExplicitLegacyValues() {
        YamlConfiguration y=new YamlConfiguration();y.set("VoteParty.VoteReminder.Broadcast","modern");y.set("VoteParty.VoteReminder.AtVotes",Arrays.asList(3,7));
        SpecialRewardsConfig c=mock(SpecialRewardsConfig.class,CALLS_REAL_METHODS);doReturn(y).when(c).getData();c.loadValues();
        assertEquals("modern",c.getVotePartyVoteReminderBroadcast());assertEquals(Arrays.asList(3,7),c.getVotePartyVoteReminderAtVotes());
        y.set("VoteParty.VoteReminderBroadcast","legacy");y.set("VoteParty.VoteReminderAtVotes",Arrays.asList(9));c.loadValues();assertEquals("legacy",c.getVotePartyVoteReminderBroadcast());assertEquals(Arrays.asList(9),c.getVotePartyVoteReminderAtVotes());
    }
}
