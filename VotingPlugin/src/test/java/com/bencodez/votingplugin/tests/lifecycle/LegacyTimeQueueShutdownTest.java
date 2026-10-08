package com.bencodez.votingplugin.tests.lifecycle;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Server;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import com.bencodez.votingplugin.VotingPluginMain;
import com.bencodez.votingplugin.data.ServerData;
import com.bencodez.votingplugin.events.PlayerVoteEvent;
import com.bencodez.votingplugin.timequeue.TimeQueueHandler;
import com.bencodez.votingplugin.timequeue.VoteTimeQueue;

class LegacyTimeQueueShutdownTest {
    @Test void nativePreUnloadCancelsStartupWakeupButDrainsAcceptedImmediateWorkAndRetainsVotes() throws Exception {
        ScheduledThreadPoolExecutor timer=new ScheduledThreadPoolExecutor(1);
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        ExecutorService shutdown=Executors.newSingleThreadExecutor();
        try {
            VotingPluginMain plugin=mock(VotingPluginMain.class);
            ServerData data=mock(ServerData.class);
            when(plugin.getVoteTimer()).thenReturn(timer);when(plugin.getServerData()).thenReturn(data);
            when(data.getTimedVoteCacheKeys()).thenReturn(Collections.emptySet());
            TimeQueueHandler queue=new TimeQueueHandler(plugin);
            VoteTimeQueue retained=new VoteTimeQueue("RetainedVote","service.example",123456789L);
            queue.getTimeChangeQueue().add(retained);
            field(plugin,"voteTimer",timer);field(plugin,"timeQueueHandler",queue);
            doCallRealMethod().when(plugin).onPreUnLoad();
            Future<?> first=timer.submit(() -> {entered.countDown();await(release);});
            assertTrue(entered.await(2,TimeUnit.SECONDS));
            AtomicBoolean secondRan=new AtomicBoolean();Future<?> second=timer.submit(() -> secondRan.set(true));
            Future<?> stopping=shutdown.submit(plugin::onPreUnLoad);
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
            while(!timer.isShutdown() && System.nanoTime()<deadline) Thread.yield();
            assertTrue(timer.isShutdown());assertFalse(stopping.isDone());assertFalse(timer.isTerminated());
            release.countDown();stopping.get(2,TimeUnit.SECONDS);first.get();second.get();
            assertTrue(secondRan.get());assertTrue(timer.isTerminated());
            assertSame(retained,queue.getTimeChangeQueue().peek());
            queue.save();verify(data).addTimeVoted(0,retained);assertTrue(queue.getTimeChangeQueue().isEmpty());
            assertEquals("RetainedVote",retained.getName());assertEquals("service.example",retained.getService());assertEquals(123456789L,retained.getTime());
        } finally {release.countDown();timer.shutdownNow();shutdown.shutdownNow();assertTrue(timer.awaitTermination(2,TimeUnit.SECONDS));assertTrue(shutdown.awaitTermination(2,TimeUnit.SECONDS));}
    }

    @Test void cancellingAnAlreadyRunningQueueWakeupDoesNotAcknowledgeItsPhysicalCompletion() throws Exception {
        ScheduledThreadPoolExecutor timer=new ScheduledThreadPoolExecutor(1);
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        try {
            VotingPluginMain plugin=mock(VotingPluginMain.class);ServerData data=mock(ServerData.class);
            ScheduledExecutorService scheduling=mock(ScheduledExecutorService.class);
            when(plugin.getVoteTimer()).thenReturn(scheduling);when(plugin.getServerData()).thenReturn(data);
            when(data.getTimedVoteCacheKeys()).thenReturn(Collections.emptySet());
            // Preserve the real 120-second startup wakeup, but run the date-change wakeup immediately.
            when(scheduling.schedule(any(Runnable.class),anyLong(),eq(TimeUnit.SECONDS))).thenAnswer(call -> {
                long delay=call.getArgument(1);return timer.schedule((Runnable)call.getArgument(0),delay==5?0:delay,TimeUnit.SECONDS);
            });
            Server server=mock(Server.class);PluginManager manager=mock(PluginManager.class);
            when(plugin.getServer()).thenReturn(server);when(server.getPluginManager()).thenReturn(manager);
            doAnswer(call -> {entered.countDown();await(release);return null;}).when(manager).callEvent(any(PlayerVoteEvent.class));
            TimeQueueHandler queue=new TimeQueueHandler(plugin);queue.addVote("RunningVote","service.example");queue.postTimeChange(null);
            assertTrue(entered.await(2,TimeUnit.SECONDS));
            queue.stopScheduledChecks();timer.shutdown();
            assertFalse(timer.awaitTermination(20,TimeUnit.MILLISECONDS));
            release.countDown();assertTrue(timer.awaitTermination(2,TimeUnit.SECONDS));
            verify(manager,times(1)).callEvent(any(PlayerVoteEvent.class));
        } finally {release.countDown();timer.shutdownNow();assertTrue(timer.awaitTermination(2,TimeUnit.SECONDS));}
    }

    @Test void lateDateChangeCannotScheduleAfterQueueWakeupsAreRetired() throws Exception {
        ScheduledThreadPoolExecutor timer=new ScheduledThreadPoolExecutor(1);
        try {
            VotingPluginMain plugin=mock(VotingPluginMain.class);ServerData data=mock(ServerData.class);
            when(plugin.getVoteTimer()).thenReturn(timer);when(plugin.getServerData()).thenReturn(data);
            when(data.getTimedVoteCacheKeys()).thenReturn(Collections.emptySet());
            TimeQueueHandler queue=new TimeQueueHandler(plugin);queue.stopScheduledChecks();timer.shutdown();
            assertDoesNotThrow(() -> queue.postTimeChange(null));assertTrue(timer.awaitTermination(2,TimeUnit.SECONDS));
        } finally {timer.shutdownNow();assertTrue(timer.awaitTermination(2,TimeUnit.SECONDS));}
    }

    private static void field(Object target,String name,Object value) throws Exception {
        java.lang.reflect.Field field=VotingPluginMain.class.getDeclaredField(name);field.setAccessible(true);field.set(target,value);
    }
    private static void await(CountDownLatch latch) {
        try {if(!latch.await(3,TimeUnit.SECONDS))throw new AssertionError("Fixture release deadline expired");}
        catch(InterruptedException interrupted) {Thread.currentThread().interrupt();throw new AssertionError("Accepted body was interrupted",interrupted);}
    }
}
