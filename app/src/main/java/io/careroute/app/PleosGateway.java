package io.careroute.app;

import android.car.Car;
import android.car.drivingstate.CarUxRestrictionsManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.content.Context;
import android.util.Log;
import ai.pleos.playground.vehicle.Vehicle;
import ai.pleos.playground.vehicle.api.Door;
import ai.pleos.playground.vehicle.api.Gear;
import ai.pleos.playground.vehicle.api.model.area.DoorArea;
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds;
import ai.pleos.playground.vehicle.constant.area.DoorValue;
import ai.pleos.playground.vehicle.constant.control.VehicleGear;
import ai.pleos.playground.vehicle.listener.DoorPositionListener;
import ai.pleos.playground.vehicle.listener.GearSelectionListener;
import kotlin.Unit;
import java.util.Map;

/** Read-only SDK adapter. No CONTROL_* permission and no set API is used. */
public final class PleosGateway {
    public interface Observer {
        void gear(boolean park); void door(boolean open); void restricted(boolean value);
        void status(String text, boolean available); void doorUnavailable(); void gearUnavailable();
    }
    private final Context context;
    private final Observer observer;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Vehicle vehicle;
    private Door door;
    private Gear gear;
    private Car car;
    private CarUxRestrictionsManager ux;
    private volatile boolean running;
    private boolean subscribed;
    private volatile boolean supported;
    private boolean receivedDoor;
    private boolean receivedGear;
    private final DoorArea area = new DoorArea(DoorValue.ROW_2_RIGHT);
    private final DoorPositionListener doorListener = new DoorPositionListener() {
        @Override public void onPositionUpdated(Integer value) { handleDoor(value); }
        @Override public void onFailed(Exception error) { failDoor(error); }
    };
    private final GearSelectionListener gearListener = new GearSelectionListener() {
        @Override public void onSelected(VehicleGear value) { handleGear(value); }
        @Override public void onFailed(Exception error) { failGear(error); }
    };
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (!running) return;
            read(); handler.postDelayed(this, 2_000);
        }
    };
    public PleosGateway(Context context, Observer observer) { this.context = context; this.observer = observer; }
    public void start() {
        running = true; observer.restricted(true); observer.status("차량 데이터 연결 중", false);
        try {
            vehicle = new Vehicle(context); vehicle.initialize();
            door = vehicle.getDoor(); gear = vehicle.getGear();
            car = Car.createCar(context);
            if (car != null && car.isConnected()) {
                ux = (CarUxRestrictionsManager) car.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE);
                if (ux != null) {
                    ux.registerListener(value -> post(() -> observer.restricted(value.isRequiresDistractionOptimization())));
                    observer.restricted(ux.getCurrentCarUxRestrictions().isRequiresDistractionOptimization());
                }
            }
            handler.postDelayed(poll, 1_000);
        } catch (Exception | LinkageError error) {
            Log.w("CareRoute", "Vehicle initialization unavailable", error);
            observer.status("차량 연결을 확인해 주세요", false);
        }
    }
    private void read() {
        try {
            if (ux != null) observer.restricted(ux.getCurrentCarUxRestrictions().isRequiresDistractionOptimization());
            else observer.restricted(true);
            if (!supported) {
                door.checkDoorPositionCapabilities((Map<DoorArea, AreaBounds<Integer>> areas) -> {
                    if (!running) return Unit.INSTANCE;
                    supported = areas.containsKey(area);
                    if (!supported) post(() -> observer.status("오른쪽 뒷문 상태를 지원하지 않는 차량입니다", false));
                    return Unit.INSTANCE;
                }, error -> { failDoor(error); return Unit.INSTANCE; });
            }
            if (supported) {
                if (!subscribed) {
                    door.registerDoorPosition(area, doorListener);
                    gear.registerGearSelection(gearListener);
                    subscribed = true;
                }
                door.getCurrentDoorPosition(area, value -> { handleDoor(value); return Unit.INSTANCE; }, error -> { failDoor(error); return Unit.INSTANCE; });
            }
            gear.getCurrentGearSelectionState(value -> { handleGear(value); return Unit.INSTANCE; }, error -> { failGear(error); return Unit.INSTANCE; });
        } catch (Exception error) { failDoor(error); failGear(error); }
    }
    private void handleDoor(Integer value) {
        if (value == null) { post(() -> { receivedDoor = false; observer.doorUnavailable(); observer.status("문 상태 확인 중", false); }); return; }
        post(() -> { receivedDoor = true; observer.door(value > 0); connected(); });
    }
    private void handleGear(VehicleGear value) {
        if (value == null || value == VehicleGear.GEAR_UNKNOWN) { post(() -> { receivedGear = false; observer.gearUnavailable(); observer.status("기어 상태 확인 중", false); }); return; }
        post(() -> { receivedGear = true; observer.gear(value == VehicleGear.GEAR_PARK); connected(); });
    }
    private void connected() { if (receivedDoor && receivedGear) observer.status("차량 SDK 연동", true); }
    private void failDoor(Exception error) { Log.w("CareRoute", "Door read failed", error); post(() -> { receivedDoor = false; observer.doorUnavailable(); observer.status("문 상태 연결을 확인해 주세요", false); }); }
    private void failGear(Exception error) { Log.w("CareRoute", "Gear read failed", error); post(() -> { receivedGear = false; observer.gearUnavailable(); observer.status("기어 상태 연결을 확인해 주세요", false); }); }
    private void post(Runnable runnable) { handler.post(() -> { if (running) runnable.run(); }); }
    public void stop() {
        running = false; handler.removeCallbacksAndMessages(null);
        try { if (subscribed) { door.unregisterDoorPosition(area, doorListener); gear.unregisterGearSelection(gearListener); } } catch (Exception ignored) { }
        try { if (ux != null) ux.unregisterListener(); } catch (Exception ignored) { }
        try { if (car != null) car.disconnect(); } catch (Exception ignored) { }
        try { if (vehicle != null) vehicle.release(); } catch (Exception ignored) { }
    }
}
