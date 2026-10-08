package com.bencodez.votingplugin.user;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import com.bencodez.votingplugin.VotingPluginMain;
import com.bencodez.votingplugin.objects.VoteSite;

class LegacyFractionalVoteDelayTest {
    private final LocalDateTime last = LocalDateTime.of(2026, 10, 6, 12, 0);
    private final VotingPluginMain plugin = mock(VotingPluginMain.class, RETURNS_DEEP_STUBS);
    private final VoteSite site = mock(VoteSite.class);
    private final VotingPluginUser user = mock(VotingPluginUser.class, CALLS_REAL_METHODS);

    private void setup(double hours, double minutes, LocalDateTime now) throws Exception {
        Field field = VotingPluginUser.class.getDeclaredField("plugin");
        field.setAccessible(true); field.set(user, plugin);
        when(plugin.getTimeChecker().getTime()).thenReturn(now);
        when(plugin.getOptions().getTimeHourOffSet()).thenReturn(0);
        when(site.getVoteDelay()).thenReturn(hours);
        when(site.getVoteDelayMin()).thenReturn(minutes);
        doReturn(last.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()).when(user).getTime(site);
    }

    @Test void halfHourDelayStaysUnavailableAndReportsRemainingTime() throws Exception {
        setup(0.5, 0, last.plusMinutes(5));
        assertFalse(user.canVoteSite(site));
        assertEquals(1500, user.voteNextDurationTime(site, user.getTime(site)));
        when(plugin.getTimeChecker().getTime()).thenReturn(last.plusMinutes(30).plusSeconds(1));
        assertTrue(user.canVoteSite(site));
        assertEquals(0, user.voteNextDurationTime(site, user.getTime(site)));
    }

    @Test void fractionalHoursAndLegacyMinutesAreBothApplied() throws Exception {
        setup(1.5, 0.5, last.plusHours(1));
        assertFalse(user.canVoteSite(site));
        assertEquals(1830, user.voteNextDurationTime(site, user.getTime(site)));
    }

    @Test void subSecondDelayDoesNotRoundDownToImmediateEligibility() throws Exception {
        setup(0.5 / 3600, 0, last.plusNanos(250000000));
        assertFalse(user.canVoteSite(site));
        when(plugin.getTimeChecker().getTime()).thenReturn(last.plusSeconds(1));
        assertTrue(user.canVoteSite(site));
    }

    @Test void zeroDelayAndNeverVotedKeepExistingSemantics() throws Exception {
        setup(0, 0, last.plusMinutes(1));
        assertFalse(user.canVoteSite(site));
        assertEquals(0, user.voteNextDurationTime(site, user.getTime(site)));
        doReturn(0L).when(user).getTime(site);
        assertTrue(user.canVoteSite(site));
        assertEquals(0, user.voteNextDurationTime(site, 0L));
    }

    @Test void serverTimeOffsetAppliesToFractionalDelay() throws Exception {
        setup(0.5, 0, last.plusHours(2).plusMinutes(5));
        when(plugin.getOptions().getTimeHourOffSet()).thenReturn(2);
        assertFalse(user.canVoteSite(site));
        assertEquals(1500, user.voteNextDurationTime(site, user.getTime(site)));
    }

    @Test void integerHoursAndDailyResetKeepExistingSemantics() throws Exception {
        setup(24, 0, last.plusHours(1));
        assertFalse(user.canVoteSite(site));
        assertEquals(23 * 3600, user.voteNextDurationTime(site, user.getTime(site)));
        when(site.isVoteDelayDaily()).thenReturn(true);
        when(site.getVoteDelayDailyHour()).thenReturn(14);
        assertFalse(user.canVoteSite(site));
        assertEquals(3600, user.voteNextDurationTime(site, user.getTime(site)));
        when(plugin.getTimeChecker().getTime()).thenReturn(last.plusHours(3));
        assertTrue(user.canVoteSite(site));
    }
}
