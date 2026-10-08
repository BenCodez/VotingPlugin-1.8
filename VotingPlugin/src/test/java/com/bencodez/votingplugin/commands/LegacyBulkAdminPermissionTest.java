package com.bencodez.votingplugin.commands;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Collections;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;
import com.bencodez.advancedcore.api.command.PlayerCommandHandler;
import com.bencodez.votingplugin.VotingPluginMain;

class LegacyBulkAdminPermissionTest {
    @Test void fullAdministratorRetainsBulkAccessWithoutSeparateAllNode() {
        PlayerCommandHandler handler = handler();
        CommandLoader.configureBulkPermissions(Collections.singletonList(handler), "VotingPlugin.Admin");
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("VotingPlugin.Admin")).thenReturn(true);
        assertTrue(handler.hasAllPermission(sender));
    }
    @Test void granularPermissionStillNeedsItsMatchingBulkNode() {
        PlayerCommandHandler handler = handler();
        CommandLoader.configureBulkPermissions(Collections.singletonList(handler), "VotingPlugin.Admin");
        CommandSender sender = mock(CommandSender.class);
        when(sender.hasPermission("VotingPlugin.Commands.AdminVote.SetPoints")).thenReturn(true);
        assertFalse(handler.hasAllPermission(sender));
        when(sender.hasPermission("VotingPlugin.Commands.AdminVote.SetPoints.All")).thenReturn(true);
        assertTrue(handler.hasAllPermission(sender));
    }
    private PlayerCommandHandler handler() {
        VotingPluginMain plugin = mock(VotingPluginMain.class, RETURNS_DEEP_STUBS);
        when(plugin.getOptions().isMultiplePermissionChecks()).thenReturn(true);
        return new PlayerCommandHandler(plugin,new String[]{"User","(player)","SetPoints","(number)"},
                "VotingPlugin.Commands.AdminVote.SetPoints|VotingPlugin.Admin","fixture") {
            public void executeAll(CommandSender sender,String[] args) {}
            public void executeSinglePlayer(CommandSender sender,String[] args) {}
        };
    }
}
