package com.bencodez.votingplugin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.HashMap;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import com.bencodez.advancedcore.bungeeapi.globaldata.GlobalDataHandler;
import com.bencodez.votingplugin.config.BungeeSettings;
import com.bencodez.votingplugin.data.ServerData;

class LegacyGlobalProviderLifecycleTest {
    @Test void nativeCloseWaitsForADirectProtocolPollRatherThanOnlyTheScheduledTimer() throws Exception {
        VotingPluginMain plugin=mock(VotingPluginMain.class);
        BungeeSettings settings=mock(BungeeSettings.class);ServerData data=mock(ServerData.class);
        when(plugin.getBungeeSettings()).thenReturn(settings);when(settings.getServer()).thenReturn("backend-a");when(plugin.getServerData()).thenReturn(data);
        BungeeHandler handler=new BungeeHandler(plugin);GlobalDataHandler global=mock(GlobalDataHandler.class);
        when(global.getGlobalMysql()).thenReturn(mock(com.bencodez.advancedcore.bungeeapi.globaldata.GlobalMySQL.class));
        field(handler,"globalDataHandler",global);
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);ExecutorService workers=Executors.newFixedThreadPool(2);
        when(global.getExact("backend-a")).thenAnswer(call -> {entered.countDown();assertTrue(release.await(3,TimeUnit.SECONDS));return new HashMap<>();});
        try {
            Future<?> polling=workers.submit(handler::checkGlobalData);assertTrue(entered.await(2,TimeUnit.SECONDS));
            Future<?> closing=workers.submit(handler::close);
            assertThrows(TimeoutException.class,() -> closing.get(50,TimeUnit.MILLISECONDS));
            release.countDown();polling.get(2,TimeUnit.SECONDS);closing.get(2,TimeUnit.SECONDS);
        } finally {release.countDown();workers.shutdownNow();assertTrue(workers.awaitTermination(2,TimeUnit.SECONDS));}
    }
    @Test void borrowedMainPoolRemainsOpenEvenAfterUseMainConfigurationChanges() throws Exception {
        Fixture f=new Fixture();when(f.settings.isGloblalDataUseMainMySQL()).thenReturn(true);when(f.plugin.getStorageType()).thenReturn(com.bencodez.advancedcore.api.user.UserStorage.MYSQL);
        f.handler.loadGlobalMysql();verify(f.handler).createGlobalDataHandler(true);
        when(f.settings.isGloblalDataUseMainMySQL()).thenReturn(false);
        f.handler.close();f.handler.close();verify(f.pool,never()).close();assertNull(value(f.handler,"globalDataHandler"));
    }
    @Test void independentPoolClosesEvenWhenCurrentConfigurationNowRequestsBorrowing() throws Exception {
        Fixture f=new Fixture();f.handler.loadGlobalMysql();when(f.settings.isGloblalDataUseMainMySQL()).thenReturn(true);
        f.handler.close();f.handler.close();verify(f.pool,times(1)).close();
    }
    @Test void reloadDrainsAndClosesOldOwnedProviderBeforeConstructingBorrowedReplacement() throws Exception {
        Fixture f=new Fixture();f.handler.loadGlobalMysql();
        GlobalDataHandler next=mock(GlobalDataHandler.class);com.bencodez.advancedcore.bungeeapi.globaldata.GlobalMySQL pool=mock(com.bencodez.advancedcore.bungeeapi.globaldata.GlobalMySQL.class);when(next.getGlobalMysql()).thenReturn(pool);
        when(f.settings.isGloblalDataUseMainMySQL()).thenReturn(true);when(f.plugin.getStorageType()).thenReturn(com.bencodez.advancedcore.api.user.UserStorage.MYSQL);
        doAnswer(call -> {verify(f.timer).shutdown();verify(f.pool).close();return next;}).when(f.handler).createGlobalDataHandler(true);
        f.handler.loadGlobalMysql();assertSame(next,value(f.handler,"globalDataHandler"));f.handler.close();verify(pool,never()).close();
    }
    @Test void unsettledTimerPreventsCloseReplacementAndNewPollAdmissionUntilExplicitRetry() throws Exception {
        Fixture f=new Fixture();f.handler.loadGlobalMysql();clearInvocations(f.handler,f.global);
        when(f.timer.awaitTermination(anyLong(),eq(TimeUnit.NANOSECONDS))).thenReturn(false);
        assertThrows(IllegalStateException.class,f.handler::loadGlobalMysql);assertSame(f.global,value(f.handler,"globalDataHandler"));
        f.handler.checkGlobalData();verify(f.global,never()).getExact(anyString());verify(f.pool,never()).close();verify(f.handler,never()).createGlobalDataHandler(anyBoolean());verify(f.timer,never()).shutdownNow();
        when(f.timer.awaitTermination(anyLong(),eq(TimeUnit.NANOSECONDS))).thenReturn(true);f.handler.loadGlobalMysql();verify(f.pool,times(1)).close();f.handler.close();
    }
    @Test void interruptedReloadRetainsProviderAndPreservesInterruptWithoutForceStoppingItsWorker() throws Exception {
        Fixture f=new Fixture();f.handler.loadGlobalMysql();clearInvocations(f.handler);
        when(f.timer.awaitTermination(anyLong(),eq(TimeUnit.NANOSECONDS))).thenThrow(new InterruptedException("fixture"));
        try {assertThrows(IllegalStateException.class,f.handler::loadGlobalMysql);assertTrue(Thread.currentThread().isInterrupted());assertSame(f.global,value(f.handler,"globalDataHandler"));verify(f.pool,never()).close();verify(f.handler,never()).createGlobalDataHandler(anyBoolean());verify(f.timer,never()).shutdownNow();}
        finally {Thread.interrupted();}
    }
    @Test void failedProviderCloseRetainsOwnershipAndCannotPublishReplacement() throws Exception {
        Fixture f=new Fixture();f.handler.loadGlobalMysql();clearInvocations(f.handler);
        IllegalStateException failure=new IllegalStateException("close not acknowledged");doThrow(failure).when(f.pool).close();
        assertSame(failure,assertThrows(IllegalStateException.class,f.handler::loadGlobalMysql));assertSame(f.global,value(f.handler,"globalDataHandler"));verify(f.handler,never()).createGlobalDataHandler(anyBoolean());
        doNothing().when(f.pool).close();f.handler.close();assertNull(value(f.handler,"globalDataHandler"));
    }
    @Test void failedReplacementPreparationStaysVisibleAndDoesNotStartAnotherTimer() throws Exception {
        Fixture f=new Fixture();f.handler.loadGlobalMysql();clearInvocations(f.handler);
        IllegalStateException failure=new IllegalStateException("preparation failed");doThrow(failure).when(f.handler).createGlobalDataHandler(anyBoolean());
        assertSame(failure,assertThrows(IllegalStateException.class,f.handler::loadGlobalMysql));verify(f.pool).close();assertNull(value(f.handler,"globalDataHandler"));verify(f.handler,never()).createGlobalDataTimer();
        doReturn(f.global).when(f.handler).createGlobalDataHandler(anyBoolean());f.handler.loadGlobalMysql();f.handler.close();
    }
    @Test void candidateInitializationFailureRetainsOwnedCandidateSealedForExplicitCleanup() throws Exception {
        Fixture f=new Fixture();IllegalStateException failure=new IllegalStateException("column setup failed");doThrow(failure).when(f.pool).alterColumnType("MONTH","VARCHAR(5)");
        assertSame(failure,assertThrows(IllegalStateException.class,f.handler::loadGlobalMysql));assertSame(f.global,value(f.handler,"globalDataHandler"));
        verify(f.handler,never()).createGlobalDataTimer();f.handler.checkGlobalData();verify(f.global,never()).getExact(anyString());f.handler.close();verify(f.pool).close();
    }
    @Test void disablingGlobalDataStopsPollingAndRestoresLocalTimeOwnership() throws Exception {
        Fixture f=new Fixture();f.handler.loadGlobalMysql();when(f.settings.isGloblalDataEnabled()).thenReturn(false);
        f.handler.loadGlobalMysql();verify(f.pool).close();verify(f.time).setProcessingEnabled(true);assertNull(value(f.handler,"globalDataHandler"));
    }
    @Test void directPeriodProcessingKeepsCapturedProviderUntilItsPhysicalBodyFinishes() throws Exception {
        Fixture f=new Fixture();f.handler.loadGlobalMysql();
        GlobalDataHandler next=mock(GlobalDataHandler.class);com.bencodez.advancedcore.bungeeapi.globaldata.GlobalMySQL pool=mock(com.bencodez.advancedcore.bungeeapi.globaldata.GlobalMySQL.class);when(next.getGlobalMysql()).thenReturn(pool);doReturn(next).when(f.handler).createGlobalDataHandler(anyBoolean());
        com.bencodez.simpleapi.servercomm.global.GlobalMessageHandler messages=mock(com.bencodez.simpleapi.servercomm.global.GlobalMessageHandler.class);field(f.handler,"globalMessageHandler",messages);
        HashMap<String,com.bencodez.simpleapi.sql.data.DataValue> data=new HashMap<>();data.put("DAY",new com.bencodez.simpleapi.sql.data.DataValueBoolean(true));data.put("LastUpdated",new com.bencodez.simpleapi.sql.data.DataValueString(""+System.currentTimeMillis()));
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);ExecutorService workers=Executors.newFixedThreadPool(2);
        doAnswer(call -> {entered.countDown();assertTrue(release.await(3,TimeUnit.SECONDS));return null;}).when(f.time).forceChanged(com.bencodez.advancedcore.api.time.TimeType.DAY,false,true,true);
        try {
            Future<Boolean> processing=workers.submit(() -> f.handler.checkGlobalDataTime(com.bencodez.advancedcore.api.time.TimeType.DAY,data));assertTrue(entered.await(2,TimeUnit.SECONDS));
            Future<?> reloading=workers.submit(f.handler::loadGlobalMysql);assertThrows(TimeoutException.class,() -> reloading.get(50,TimeUnit.MILLISECONDS));verify(f.pool,never()).close();
            release.countDown();assertTrue(processing.get(2,TimeUnit.SECONDS));reloading.get(2,TimeUnit.SECONDS);
            verify(f.global).setBoolean("backend-a","DAY",false);verify(next,never()).setBoolean(anyString(),anyString(),anyBoolean());verify(f.pool).close();f.handler.close();
        } finally {release.countDown();workers.shutdownNow();assertTrue(workers.awaitTermination(2,TimeUnit.SECONDS));}
    }
    @Test void callbackCannotWaitOnItsOwnGlobalAdmissionOrCloseItsProvider() throws Exception {
        Fixture f=new Fixture();f.handler.loadGlobalMysql();clearInvocations(f.timer);
        when(f.global.getExact("backend-a")).thenAnswer(call -> {assertThrows(IllegalStateException.class,f.handler::close);verify(f.timer,never()).shutdown();return new HashMap<>();});
        f.handler.checkGlobalData();verify(f.pool,never()).close();f.handler.close();verify(f.pool).close();
    }
    @Test void concurrentReplacementCannotOverwriteAnOwnedLifecycleTransition() throws Exception {
        Fixture f=new Fixture();f.handler.loadGlobalMysql();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);ExecutorService worker=Executors.newSingleThreadExecutor();
        doAnswer(call -> {entered.countDown();assertTrue(release.await(3,TimeUnit.SECONDS));return f.global;}).when(f.handler).createGlobalDataHandler(anyBoolean());
        try {Future<?> replacing=worker.submit(f.handler::loadGlobalMysql);assertTrue(entered.await(2,TimeUnit.SECONDS));assertThrows(IllegalStateException.class,f.handler::loadGlobalMysql);release.countDown();replacing.get(2,TimeUnit.SECONDS);f.handler.close();}
        finally {release.countDown();worker.shutdownNow();assertTrue(worker.awaitTermination(2,TimeUnit.SECONDS));}
    }
    @Test void nativeProviderConstructionCompletesBeforePollingTasksArePublished() throws Exception {
        Fixture f=new Fixture();doAnswer(call -> {verify(f.pool).alterColumnType("ForceUpdate","VARCHAR(5)");verify(f.time).setProcessingEnabled(false);return f.timer;}).when(f.handler).createGlobalDataTimer();
        f.handler.loadGlobalMysql();verify(f.timer).scheduleWithFixedDelay(any(Runnable.class),eq(60L),eq(10L),eq(TimeUnit.SECONDS));verify(f.timer).scheduleWithFixedDelay(any(Runnable.class),eq(1L),eq(60L),eq(TimeUnit.MINUTES));f.handler.close();
    }
    @Test void storageReloadSealsGlobalReadersWithoutClosingBorrowedPoolOrStoppingTransportIngress() throws Exception {
        Fixture f=new Fixture();when(f.settings.isGloblalDataUseMainMySQL()).thenReturn(true);when(f.plugin.getStorageType()).thenReturn(com.bencodez.advancedcore.api.user.UserStorage.MYSQL);f.handler.loadGlobalMysql();
        com.bencodez.simpleapi.servercomm.sockets.ClientHandler client=mock(com.bencodez.simpleapi.servercomm.sockets.ClientHandler.class);field(f.handler,"clientHandler",client);
        f.handler.stopGlobalDataForStorageReload();f.handler.checkGlobalData();verify(f.global,never()).getExact(anyString());verify(f.pool,never()).close();verifyNoInteractions(client);
        f.handler.closeGlobalDataForStorageReload();verify(f.pool,never()).close();assertNull(value(f.handler,"globalDataHandler"));verify(f.time).setProcessingEnabled(true);verifyNoInteractions(client);
    }
    @Test void pollingPreservesPublicPeriodOverridesAndNestedAcceptedContinuationAfterSealing() throws Exception {
        Fixture f=new Fixture();f.handler.loadGlobalMysql();CountDownLatch sealed=new CountDownLatch(1);ExecutorService worker=Executors.newSingleThreadExecutor();
        when(f.global.getExact("backend-a")).thenReturn(new HashMap<>());
        when(f.timer.awaitTermination(anyLong(),eq(TimeUnit.NANOSECONDS))).thenAnswer(call -> {sealed.countDown();return true;});
        java.util.concurrent.atomic.AtomicReference<Future<?>> closing=new java.util.concurrent.atomic.AtomicReference<>();
        doAnswer(call -> {
            closing.set(worker.submit(f.handler::close));assertTrue(sealed.await(2,TimeUnit.SECONDS));
            assertFalse((Boolean)value(f.handler,"acceptingGlobalWork"));
            Object result=call.callRealMethod();verify(f.pool,never()).close();return result;
        }).when(f.handler).checkGlobalDataTime(eq(com.bencodez.advancedcore.api.time.TimeType.MONTH),any());
        try {
            f.handler.checkGlobalData();verify(f.handler).checkGlobalDataTime(eq(com.bencodez.advancedcore.api.time.TimeType.MONTH),any());verify(f.handler).checkGlobalDataTime(eq(com.bencodez.advancedcore.api.time.TimeType.WEEK),any());verify(f.handler).checkGlobalDataTime(eq(com.bencodez.advancedcore.api.time.TimeType.DAY),any());
            closing.get().get(2,TimeUnit.SECONDS);verify(f.pool).close();
        } finally {worker.shutdownNow();assertTrue(worker.awaitTermination(2,TimeUnit.SECONDS));}
    }
    static class Fixture {
        final VotingPluginMain plugin=mock(VotingPluginMain.class);final BungeeSettings settings=mock(BungeeSettings.class);final ServerData data=mock(ServerData.class);
        final com.bencodez.advancedcore.api.time.TimeChecker time=mock(com.bencodez.advancedcore.api.time.TimeChecker.class);
        final ScheduledExecutorService timer=mock(ScheduledExecutorService.class);final GlobalDataHandler global=mock(GlobalDataHandler.class);
        final com.bencodez.advancedcore.bungeeapi.globaldata.GlobalMySQL pool=mock(com.bencodez.advancedcore.bungeeapi.globaldata.GlobalMySQL.class);
        final BungeeHandler handler=spy(new BungeeHandler(plugin));
        Fixture() throws Exception {
            when(plugin.getBungeeSettings()).thenReturn(settings);when(settings.getServer()).thenReturn("backend-a");when(settings.isGloblalDataEnabled()).thenReturn(true);
            when(plugin.getServerData()).thenReturn(data);when(plugin.getTimeChecker()).thenReturn(time);when(global.getGlobalMysql()).thenReturn(pool);
            when(timer.awaitTermination(anyLong(),eq(TimeUnit.NANOSECONDS))).thenReturn(true);
            doReturn(global).when(handler).createGlobalDataHandler(anyBoolean());doReturn(timer).when(handler).createGlobalDataTimer();
        }
    }
    static Object value(Object target,String name) throws Exception {
        java.lang.reflect.Field field=BungeeHandler.class.getDeclaredField(name);field.setAccessible(true);return field.get(target);
    }
    static void field(Object target,String name,Object value) throws Exception {
        java.lang.reflect.Field field=BungeeHandler.class.getDeclaredField(name);field.setAccessible(true);field.set(target,value);
    }
}
