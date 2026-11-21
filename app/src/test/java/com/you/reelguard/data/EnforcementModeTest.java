package com.you.reelguard.data;

import org.junit.Test;
import static org.junit.Assert.*;

public final class EnforcementModeTest {
    @Test public void queuedActionsStayInvalidAfterTurningLimitsBackOn() {
        EnforcementMode mode = new EnforcementMode(true);
        long priorAction = mode.revision();
        assertTrue(mode.permits(priorAction));

        mode.beginWrite();
        assertTrue(mode.enabled());
        assertTrue(mode.pending());
        assertFalse(mode.permits(priorAction));
        mode.finishWrite(false);
        assertFalse(mode.enabled());
        assertFalse(mode.active());

        mode.beginWrite();
        long actionDuringPause = mode.revision();
        mode.finishWrite(true);
        assertFalse(mode.permits(priorAction));
        assertFalse(mode.permits(actionDuringPause));
        assertTrue(mode.permits(mode.revision()));
    }

    @Test public void overlappingWritesKeepEnforcementSuspendedUntilTheLastResult() {
        EnforcementMode mode = new EnforcementMode(true);
        mode.beginWrite();
        mode.beginWrite();
        mode.finishWrite(true);
        assertTrue(mode.enabled());
        assertTrue(mode.pending());
        assertFalse(mode.active());

        mode.finishWrite(false);
        assertFalse(mode.enabled());
        assertFalse(mode.pending());
        assertFalse(mode.active());
    }

    @Test public void failedDisableRestoresModeWithAFreshActionRevision() {
        EnforcementMode mode = new EnforcementMode(true);
        long priorAction = mode.revision();
        mode.beginWrite();
        mode.finishWrite(true);
        assertTrue(mode.enabled());
        assertFalse(mode.permits(priorAction));
        assertTrue(mode.permits(mode.revision()));
    }
}
