package io.careroute.app;

import java.util.ArrayList;
import java.util.List;

/** Passenger handoff workflow. Vehicle observations never imply a human action. */
public final class CareSession {
    public enum Stage { UNKNOWN, MOVING, ARRIVED, EXITING, HANDOFF_PENDING, COMPLETE }
    private boolean parked;
    private boolean doorOpen;
    private boolean gearKnown;
    private boolean doorKnown;
    private boolean restricted = true;
    private boolean doorOpenedDuringStop;
    private final boolean[] confirmed = new boolean[3];
    private boolean finished;
    private final List<String> events = new ArrayList<>();
    private long lastGearMs;
    private long lastDoorMs;
    public static final long MAX_AGE_MS = 7_000;

    public void observeGear(boolean isParked, long now) {
        boolean changed = !gearKnown || parked != isParked;
        parked = isParked; gearKnown = true; lastGearMs = now;
        if (changed) event(isParked ? "P 기어 확인" : "주행 기어 확인");
    }
    public void observeDoor(boolean open, long now) {
        boolean changed = !doorKnown || doorOpen != open;
        doorOpen = open; doorKnown = true; lastDoorMs = now;
        if (parked && open) doorOpenedDuringStop = true;
        if (changed) event(open ? "선택 도어 열림 확인" : "선택 도어 닫힘 확인");
    }
    public void unknownGear() { gearKnown = false; }
    public void unknownDoor() { doorKnown = false; }
    public void setRestricted(boolean value) { restricted = value; }
    public boolean isParked() { return parked && gearKnown; }
    public boolean isDoorOpen() { return doorOpen && doorKnown; }
    public boolean isFresh(long now) {
        return gearKnown && doorKnown && now >= lastGearMs && now >= lastDoorMs
                && now - lastGearMs <= MAX_AGE_MS && now - lastDoorMs <= MAX_AGE_MS;
    }
    public boolean canInteract(long now) { return isFresh(now) && parked && !restricted; }
    public boolean wasDoorOpened() { return doorOpenedDuringStop; }
    public boolean isConfirmed(int index) { return confirmed[index]; }
    public boolean confirm(int index, long now) {
        if (index < 0 || index > 2 || !canInteract(now) || !doorOpenedDuringStop || finished) return false;
        if (index == 2 && (!confirmed[0] || !confirmed[1])) return false;
        confirmed[index] = !confirmed[index];
        if (!confirmed[index] && index < 2) confirmed[2] = false;
        event((confirmed[index] ? "직접 확인: " : "확인 취소: ") + new String[]{"보조기구·짐", "승객 하차", "보호자 인계"}[index]);
        return true;
    }
    public boolean canFinish(long now) {
        return canInteract(now) && doorOpenedDuringStop && !doorOpen
                && confirmed[0] && confirmed[1] && confirmed[2] && !finished;
    }
    public boolean finish(long now) {
        if (!canFinish(now)) return false;
        finished = true; event("운전자 확인 기록 저장 · 이동 마침"); return true;
    }
    public Stage stage(long now) {
        if (!isFresh(now)) return Stage.UNKNOWN;
        if (!parked || restricted) return Stage.MOVING;
        if (finished) return Stage.COMPLETE;
        if (doorOpen) return Stage.EXITING;
        if (doorOpenedDuringStop) return Stage.HANDOFF_PENDING;
        return Stage.ARRIVED;
    }
    public void restore(boolean opened, boolean a, boolean b, boolean c, boolean done) {
        doorOpenedDuringStop = opened;
        confirmed[0] = a; confirmed[1] = b; confirmed[2] = c && a && b;
        finished = done && opened && a && b && c;
        // Restored workflow never restores live vehicle observations.
    }
    public boolean isFinished() { return finished; }
    public List<String> getEvents() { return new ArrayList<>(events); }
    public void reset() {
        confirmed[0] = confirmed[1] = confirmed[2] = false;
        doorOpenedDuringStop = false; finished = false; events.clear();
        if (gearKnown && parked && doorKnown && doorOpen) doorOpenedDuringStop = true;
        event("새 확인 기록 시작");
    }
    private void event(String text) { if (events.size() >= 80) events.remove(0); events.add(text); }
}
