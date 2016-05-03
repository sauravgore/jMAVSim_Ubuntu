package me.drton.jmavsim;

import me.drton.jmavlib.mavlink.MAVLinkMessage;
import me.drton.jmavlib.mavlink.MAVLinkSchema;
import me.drton.jmavsim.vehicle.AbstractVehicle;

import javax.vecmath.Vector3d;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * MAVLinkHILSystem is MAVLink bridge between AbstractVehicle and autopilot connected via MAVLink.
 * MAVLinkHILSystem should have the same sysID as the autopilot, but different componentId.
 */
public class MAVLinkHILSystem extends MAVLinkSystem {
    public static final int SENSOR_MSG_FREQ = 400;  // [Hz]
    private AbstractVehicle vehicle;
    private boolean gotHeartBeat = false;
    private boolean inited = false;
    private boolean stopped = false;
    private long initTime = 0;
    private long initDelay = 500;
    private static int sensorMsgInterval;  // [us]
    private ScheduledFuture<?> msgThreadHandle = null;
    private ScheduledExecutorService executor;
    SensorMessageThread msgThread;

    /**
     * Create MAVLinkHILSimulator, MAVLink system that sends simulated sensors to autopilot and passes controls from
     * autopilot to simulator
     *
     * @param sysId       SysId of simulator should be the same as autopilot
     * @param componentId ComponentId of simulator should be different from autopilot
     * @param vehicle     vehicle to connect
     */
    public MAVLinkHILSystem(MAVLinkSchema schema, int sysId, int componentId, AbstractVehicle vehicle) {
        super(schema, sysId, componentId);
        this.vehicle = vehicle;
        msgThread = new SensorMessageThread();
        setSensorMsgFreq(SENSOR_MSG_FREQ);
    }

    @Override
    public void handleMessage(MAVLinkMessage msg) {
        super.handleMessage(msg);
        long t = System.currentTimeMillis();
        if ("HIL_CONTROLS".equals(msg.getMsgName())) {
            List<Double> control = Arrays.asList(msg.getDouble("roll_ailerons"), msg.getDouble("pitch_elevator"),
                    msg.getDouble("yaw_rudder"), msg.getDouble("throttle"), msg.getDouble("aux1"),
                    msg.getDouble("aux2"), msg.getDouble("aux3"), msg.getDouble("aux4"));
            vehicle.setControl(control);
        } else if ("HEARTBEAT".equals(msg.getMsgName())) {
            if (!gotHeartBeat && !stopped) {
                if (sysId < 0 || sysId == msg.systemID) {
                    gotHeartBeat = true;
                    initTime = t + initDelay;
                    if (sysId < 0)
                        sysId = msg.systemID;
                } else if (sysId > -1 && sysId != msg.systemID) {
                    System.out.println("WARNING: Got heartbeat from system #" + Integer.toString(msg.systemID) +
                        " but configured to only accept messages from system #" + Integer.toString(sysId) +
                        ". Please change the system ID parameter to match in order to use HITL/SITL.");
                }
            }
            if (gotHeartBeat && !inited && t > initTime) {
                System.out.println("Init MAVLink");
                initMavLink();
            }
            if ((msg.getInt("base_mode") & 128) == 0) {
                vehicle.setControl(Collections.<Double>emptyList());
            }
        } else if ("STATUSTEXT".equals(msg.getMsgName())) {
            System.out.println("MSG: " + msg.getString("text"));
        }
    }

    public void initMavLink() {
        // Set HIL mode
        MAVLinkMessage msg = new MAVLinkMessage(schema, "SET_MODE", sysId, componentId);
        msg.set("target_system", sysId);
        msg.set("base_mode", 32);     // HIL, disarmed
        sendMessage(msg);
        if (vehicle.getSensors().getGPSStartTime() == -1)
            vehicle.getSensors().setGPSStartTime(System.currentTimeMillis() + 1000);
        stopped = false;
        inited = true;
        toggleMsgThread(true);
    }
    
    public void endSim() {
        if (!inited)
            return;
        // Send message to end HIL mode
        MAVLinkMessage msg = new MAVLinkMessage(schema, "SET_MODE", sysId, componentId);
        msg.set("target_system", sysId);
        msg.set("base_mode", 0);     // disarmed
        sendMessage(msg);
        inited = false;
        gotHeartBeat = false;
        stopped = true;
        vehicle.getSensors().setGPSStartTime(-1);
        toggleMsgThread(false);
    }
    
    public void setSensorMsgFreq(int freq) {
        MAVLinkHILSystem.sensorMsgInterval = (int)1e6 / freq;
        if (msgThreadHandle != null)
            toggleMsgThread(true);
    }

    private void toggleMsgThread(boolean on) {
        if (on) {
            if (msgThreadHandle != null)
                toggleMsgThread(false);
            executor = Executors.newSingleThreadScheduledExecutor();
            msgThreadHandle = executor.scheduleAtFixedRate(msgThread, 0, sensorMsgInterval, TimeUnit.MICROSECONDS);
        }
        else {
            if (msgThreadHandle != null)
                msgThreadHandle.cancel(true);
            executor.shutdown();
            msgThreadHandle = null;
        }
    }
    
    @Override
    public void update(long t) {
        super.update(t);
    }

    private final class SensorMessageThread implements Runnable {
        Vector3d vect;
        MAVLinkMessage msg_sensor = new MAVLinkMessage(schema, "HIL_SENSOR", sysId, componentId);
        MAVLinkMessage msg_gps = new MAVLinkMessage(schema, "HIL_GPS", sysId, componentId);
        private long startTime = System.nanoTime();

        @Override
        public void run() {
            if (!inited)
                return;

            long tu = (System.nanoTime() - this.startTime) / 1000L;   // Time in us
            
            try {
                Sensors sensors = vehicle.getSensors();
                if (sensors == null)
                    return;
    
                // Sensors
                msg_sensor.set("time_usec", tu);
                vect = sensors.getAcc();
                msg_sensor.set("xacc", vect.x);
                msg_sensor.set("yacc", vect.y);
                msg_sensor.set("zacc", vect.z);
                vect = sensors.getGyro();
                msg_sensor.set("xgyro", vect.x);
                msg_sensor.set("ygyro", vect.y);
                msg_sensor.set("zgyro", vect.z);
                vect = sensors.getMag();
                msg_sensor.set("xmag", vect.x);
                msg_sensor.set("ymag", vect.y);
                msg_sensor.set("zmag", vect.z);
                msg_sensor.set("pressure_alt", sensors.getPressureAlt());
                msg_sensor.set("abs_pressure", sensors.getPressure() * 0.01);  // Pa to millibar
                if (sensors.isReset()) {
                    msg_sensor.set("fields_updated", (1<<31));
                    sensors.setReset(false);
                } else
                    msg_sensor.set("fields_updated", 0);
                sendMessage(msg_sensor);
    
                // GPS
                if (sensors.isGPSUpdated()) {
                    GNSSReport gps = sensors.getGNSS();
                    if (gps == null || gps.position == null || gps.velocity == null)
                        return;
                    
                    msg_gps.set("time_usec", tu);
                    msg_gps.set("lat", (long) (gps.position.lat * 1e7));
                    msg_gps.set("lon", (long) (gps.position.lon * 1e7));
                    msg_gps.set("alt", (long) (gps.position.alt * 1e3));
                    msg_gps.set("vn", (int) (gps.velocity.x * 100));
                    msg_gps.set("ve", (int) (gps.velocity.y * 100));
                    msg_gps.set("vd", (int) (gps.velocity.z * 100));
                    msg_gps.set("eph", (int) (gps.eph * 100f));
                    msg_gps.set("epv", (int) (gps.epv * 100f));
                    msg_gps.set("vel", (int) (gps.getSpeed() * 100));
                    msg_gps.set("cog", (int) Math.toDegrees(gps.getCog()) * 100);
                    msg_gps.set("fix_type", gps.fix);
                    msg_gps.set("satellites_visible", 10);
                    sendMessage(msg_gps);
                    
                }
                
            }
            catch (Exception e) {
                System.err.println("Exception in MAVLinkHILSystem.SensorMessageThread");
                e.printStackTrace();
                endSim();
            }
        }
            
    }  // SensorMessageThread class
    
}
