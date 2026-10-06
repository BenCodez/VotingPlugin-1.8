package com.bencodez.votingplugin.tests.lifecycle;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import com.bencodez.votingplugin.BungeeHandler;
import com.bencodez.advancedcore.bungeeapi.globaldata.GlobalDataHandler;

class LegacyGlobalProducerShutdownTest {
    @Test void stoppingIngressDrainsGlobalProducerWithoutClosingItsDatabase() throws Exception {
        Fixture f=new Fixture();when(f.timer.awaitTermination(5,TimeUnit.SECONDS)).thenReturn(true);f.handler.stopAcceptingMessages();
        verify(f.timer).shutdown();verify(f.timer).awaitTermination(5,TimeUnit.SECONDS);verify(f.timer,never()).shutdownNow();verifyNoInteractions(f.global);
    }
    @Test void unsettledGlobalProducerReportsFailureAndKeepsProviderAvailable() throws Exception {
        Fixture f=new Fixture();assertThrows(IllegalStateException.class,f.handler::stopAcceptingMessages);verify(f.timer,never()).shutdownNow();verifyNoInteractions(f.global);
    }
    @Test void interruptedProducerDrainPreservesInterruptAndDoesNotCloseOrInterruptItsWork() throws Exception {
        Fixture f=new Fixture();when(f.timer.awaitTermination(5,TimeUnit.SECONDS)).thenThrow(new InterruptedException("fixture"));
        try {assertThrows(IllegalStateException.class,f.handler::stopAcceptingMessages);assertTrue(Thread.currentThread().isInterrupted());verify(f.timer,never()).shutdownNow();verifyNoInteractions(f.global);}
        finally {Thread.interrupted();}
    }
    static class Fixture {
        final BungeeHandler handler=mock(BungeeHandler.class,CALLS_REAL_METHODS);final ScheduledExecutorService timer=mock(ScheduledExecutorService.class);final GlobalDataHandler global=mock(GlobalDataHandler.class);
        Fixture()throws Exception {set("timer",timer);set("globalDataHandler",global);}
        void set(String name,Object value)throws Exception {java.lang.reflect.Field field=BungeeHandler.class.getDeclaredField(name);field.setAccessible(true);field.set(handler,value);}
    }
}
