package io.careroute.app;
import org.junit.Test;
import static org.junit.Assert.*;

public class CareSessionTest {
    private final long now = 10_000;
    private CareSession stop(boolean open) {
        CareSession s = new CareSession(); s.setRestricted(false);
        s.observeGear(true, now); s.observeDoor(open, now); return s;
    }
    @Test public void closedDoorNeverMeansHandoffComplete() {
        CareSession s = stop(true); s.observeDoor(false, now);
        assertEquals(CareSession.Stage.HANDOFF_PENDING, s.stage(now));
        assertFalse(s.finish(now));
    }
    @Test public void allHumanChecksAndClosedDoorRequired() {
        CareSession s = stop(true);
        assertFalse(s.confirm(2, now));
        assertTrue(s.confirm(0, now)); assertTrue(s.confirm(1, now)); assertTrue(s.confirm(2, now));
        assertFalse(s.finish(now));
        s.observeDoor(false, now); assertTrue(s.finish(now));
        assertEquals(CareSession.Stage.COMPLETE,s.stage(now));
    }
    @Test public void unknownAndStaleDataCannotEnableConfirmation() {
        CareSession s = new CareSession(); assertFalse(s.confirm(0,now));
        s = stop(true); assertFalse(s.confirm(0,now+CareSession.MAX_AGE_MS+1));
        assertEquals(CareSession.Stage.UNKNOWN,s.stage(now+CareSession.MAX_AGE_MS+1));
    }
    @Test public void drivingAndPlatformRestrictionsBlockActions() {
        CareSession s = stop(true); s.observeGear(false,now);
        assertFalse(s.confirm(0,now));
        s.observeGear(true,now); s.setRestricted(true); assertFalse(s.confirm(0,now));
    }
    @Test public void restoredSessionRequiresFreshVehicleData() {
        CareSession s=new CareSession(); s.restore(true,true,true,true,false);
        s.setRestricted(false); assertFalse(s.finish(now));
        s.observeGear(true,now);s.observeDoor(false,now);assertTrue(s.finish(now));
    }
    @Test public void changingAnEarlierCheckInvalidatesHandoff() {
        CareSession s=stop(true);s.confirm(0,now);s.confirm(1,now);s.confirm(2,now);
        s.confirm(0,now);assertFalse(s.isConfirmed(2));
    }
    @Test public void repeatedObservationsDoNotDuplicateEvents() {
        CareSession s=stop(false);int n=s.getEvents().size();
        s.observeDoor(false,now+1);s.observeGear(true,now+1);
        assertEquals(n,s.getEvents().size());
    }
    @Test public void openingDoorWhileDrivingDoesNotCountAsParkedExit() {
        CareSession s=new CareSession();s.setRestricted(false);
        s.observeGear(false,now);s.observeDoor(true,now);s.observeGear(true,now);
        assertFalse(s.wasDoorOpened());assertFalse(s.confirm(0,now));
        s.observeDoor(true,now);assertTrue(s.confirm(0,now));
    }
    @Test public void newRecordDoesNotReuseHumanConfirmations() {
        CareSession s=stop(true);s.confirm(0,now);s.reset();
        assertFalse(s.isConfirmed(0));assertFalse(s.isFinished());
    }
}
