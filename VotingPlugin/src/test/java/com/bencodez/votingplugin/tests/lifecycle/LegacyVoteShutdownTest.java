package com.bencodez.votingplugin.tests.lifecycle;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import com.bencodez.votingplugin.VotingPluginMain;

class LegacyVoteShutdownTest {
    @Test void acceptedVotesAreDrainedWithoutInterruptingTheirPhysicalWork() throws Exception {
        Fixture f=new Fixture();when(f.timer.awaitTermination(1,TimeUnit.SECONDS)).thenReturn(true);f.plugin.onPreUnLoad();
        verify(f.timer).shutdown();verify(f.timer).awaitTermination(1,TimeUnit.SECONDS);verify(f.timer,never()).shutdownNow();
    }
    @Test void unfinishedVotesReportFailureAndKeepTheirExecutorUninterrupted() throws Exception {
        Fixture f=new Fixture();assertThrows(IllegalStateException.class,f.plugin::onPreUnLoad);verify(f.timer,never()).shutdownNow();
    }
    @Test void interruptedVoteDrainPreservesInterruptAndDoesNotInterruptTheVoteWorker() throws Exception {
        Fixture f=new Fixture();when(f.timer.awaitTermination(1,TimeUnit.SECONDS)).thenThrow(new InterruptedException("fixture"));
        try {assertThrows(IllegalStateException.class,f.plugin::onPreUnLoad);assertTrue(Thread.currentThread().isInterrupted());verify(f.timer,never()).shutdownNow();}
        finally {Thread.interrupted();}
    }
    @Test void existingProxyHandlerIsStoppedEvenWhenReloadedConfigurationDisablesProxyMode() throws Exception {
        Fixture f=new Fixture();when(f.timer.awaitTermination(1,TimeUnit.SECONDS)).thenReturn(true);
        com.bencodez.votingplugin.BungeeHandler handler=mock(com.bencodez.votingplugin.BungeeHandler.class);
        java.lang.reflect.Field field=VotingPluginMain.class.getDeclaredField("bungeeHandler");field.setAccessible(true);field.set(f.plugin,handler);
        f.plugin.onPreUnLoad();verify(handler).stopAcceptingMessages();
    }
    static class Fixture {
        final VotingPluginMain plugin=mock(VotingPluginMain.class);final ScheduledExecutorService timer=mock(ScheduledExecutorService.class);
        Fixture()throws Exception {java.lang.reflect.Field field=VotingPluginMain.class.getDeclaredField("voteTimer");field.setAccessible(true);field.set(plugin,timer);doCallRealMethod().when(plugin).onPreUnLoad();}
    }
}
