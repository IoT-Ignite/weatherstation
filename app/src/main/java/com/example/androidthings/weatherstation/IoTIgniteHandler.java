/*
 * Copyright 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.androidthings.weatherstation;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.util.Log;

import com.ardic.android.iotignite.callbacks.ConnectionCallback;
import com.ardic.android.iotignite.enumerations.NodeType;
import com.ardic.android.iotignite.enumerations.ThingCategory;
import com.ardic.android.iotignite.enumerations.ThingDataType;
import com.ardic.android.iotignite.exceptions.UnsupportedVersionException;
import com.ardic.android.iotignite.exceptions.UnsupportedVersionExceptionType;
import com.ardic.android.iotignite.listeners.NodeListener;
import com.ardic.android.iotignite.listeners.ThingListener;
import com.ardic.android.iotignite.nodes.IotIgniteManager;
import com.ardic.android.iotignite.nodes.Node;
import com.ardic.android.iotignite.things.Thing;
import com.ardic.android.iotignite.things.ThingActionData;
import com.ardic.android.iotignite.things.ThingConfiguration;
import com.ardic.android.iotignite.things.ThingData;
import com.ardic.android.iotignite.things.ThingType;

import java.util.Hashtable;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class IoTIgniteHandler implements ConnectionCallback {

    public static final String NODE = "WeatherStationNode";
    public static final String TEMP_THING = "Temperature";
    public static final String PRESS_THING = "Pressure";
    private static final String TAG = IoTIgniteHandler.class.getSimpleName();
    private static final int NUMBER_OF_THREADS_IN_EXECUTOR = 2;
    private static final long EXECUTOR_START_DELAY = 100L;

    /**
     * Try reconnect to IoTIgnite in time period below
     */
    private static final long IGNITE_TIMER_PERIOD = 5000L;
    private volatile ScheduledExecutorService mExecutor;
    private static IotIgniteManager mIotIgniteManager;
    private Hashtable<String, ScheduledFuture<?>> tasks = new Hashtable<>();
    private Node myNode;
    private Thing mTemperatureThing, mPressureThing;
    private ThingType mTempThingType, mPressureThingType;
    private ThingDataHandler mThingDataHandler;
    private boolean versionError = false;
    private float mLastTemperature, mLastPressure;
    private boolean igniteConnected = false;

    /**
     * Temperature Thing Listener
     * Receives configuration and action
     */
    private ThingListener tempThingListener = new ThingListener() {
        @Override
        public void onConfigurationReceived(Thing thing) {
            Log.i(TAG, "Config arrived for " + thing.getThingID());
            applyConfiguration(thing);
        }

        @Override
        public void onActionReceived(String nodeId, String sensorId, ThingActionData thingActionData) {
            // There will be no action defined
        }

        @Override
        public void onThingUnregistered(String nodeId, String sensorId) {
            Log.i(TAG, "Temperature thing unregistered!");
            stopReadDataTask(nodeId, sensorId);
        }
    };

    /**
     * Pressure Thing Listener
     * Receives configuration and action
     */
    private ThingListener pressureThingListener = new ThingListener() {
        @Override
        public void onConfigurationReceived(Thing thing) {
            Log.i(TAG, "Config arrived for " + thing.getThingID());
            applyConfiguration(thing);
        }

        @Override
        public void onActionReceived(String nodeId, String sensorId, ThingActionData thingActionData) {
            // There will be no action defined
        }

        @Override
        public void onThingUnregistered(String nodeId, String sensorId) {
            Log.i(TAG, "Pressure thing unregistered!");
            stopReadDataTask(nodeId, sensorId);
        }
    };
    private Timer igniteTimer = new Timer();

    private Context applicationContext;

    private IgniteWatchDog igniteWatchDog = new IgniteWatchDog();
    private SensorEventListener mTemperatureListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            mLastTemperature = event.values[0];
            // Sends data to IotIgnite immediately
            // If reading frequency is READING_WHEN_ARRIVE in thing configuration
            if (isConfigReadWhenArrive(mTemperatureThing)) {
                sendData(mTemperatureThing, mLastTemperature);
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // Nothing to do
        }
    };
    private SensorEventListener mPressureListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            mLastPressure = event.values[0];
            // Sends data to IotIgnite immediately
            // If reading frequency is READING_WHEN_ARRIVE in thing configuration
            if (isConfigReadWhenArrive(mPressureThing)) {
                sendData(mPressureThing, mLastPressure);
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // Nothing to do
        }
    };

    public IoTIgniteHandler(Activity activity, Context appContext) {
        this.applicationContext = appContext;
    }

    public void start() {
        // Build IoT-Ignite Manager
        try {
            mIotIgniteManager = new IotIgniteManager.Builder()
                    .setContext(applicationContext)
                    .setConnectionListener(this)
                    .build();
        } catch (UnsupportedVersionException e) {
            Log.e(TAG, e.getMessage());
            versionError = true;
            if (UnsupportedVersionExceptionType.UNSUPPORTED_IOTIGNITE_AGENT_VERSION.toString().equals(e.getMessage())) {
                Log.e(TAG, "UNSUPPORTED_IOTIGNITE_AGENT_VERSION");
            } else {
                Log.e(TAG, "UNSUPPORTED_IOTIGNITE_SDK_VERSION");
            }
        }
        cancelAndScheduleIgniteTimer();
    }

    public void stop() {
        if (igniteConnected) {
            myNode.setConnected(false, "Application destroyed");
        }
        if (mExecutor != null) {
            mExecutor.shutdown();
        }
    }

    /**
     * IoT Ignite connected.
     * Register node and things
     */
    @Override
    public void onConnected() {
        Log.i(TAG, "IoT Ignite connected!");
        igniteConnected = true;
        initIgniteVariables();
        cancelAndScheduleIgniteTimer();
    }

    @Override
    public void onDisconnected() {
        Log.i(TAG, "IoT Ignite disconnected!");
        igniteConnected = false;
        cancelAndScheduleIgniteTimer();
    }

    /**
     * Create node and things and register them
     */
    private void initIgniteVariables() {
        mTempThingType = new ThingType("BMP280 Temperature", "Bosch", ThingDataType.FLOAT);
        mPressureThingType = new ThingType("BMP280 Pressure", "Bosch", ThingDataType.FLOAT);

        myNode = IotIgniteManager.NodeFactory.createNode(NODE, NODE, NodeType.GENERIC, null, new NodeListener() {
            @Override
            public void onNodeUnregistered(String s) {
                Log.i(TAG, NODE + " unregistered!");
            }
        });

        if (!myNode.isRegistered() && myNode.register()) {
            myNode.setConnected(true, NODE + " is online");
            Log.i(TAG, myNode.getNodeID() + " is successfully registered!");
        } else {
            myNode.setConnected(true, NODE + " is online");
            Log.i(TAG, myNode.getNodeID() + " is already registered!");
        }
        if (myNode.isRegistered()) {
            mTemperatureThing = myNode.createThing(TEMP_THING, mTempThingType, ThingCategory.EXTERNAL, false, tempThingListener, null);
            mPressureThing = myNode.createThing(PRESS_THING, mPressureThingType, ThingCategory.EXTERNAL, false, pressureThingListener, null);
            registerThingIfNotRegistered(mTemperatureThing);
            registerThingIfNotRegistered(mPressureThing);
        }
    }

    private void registerThingIfNotRegistered(Thing t) {
        if (!t.isRegistered() && t.register()) {
            t.setConnected(true, t.getThingID() + " connected");
            Log.i(TAG, t.getThingID() + " is successfully registered!");
        } else {
            t.setConnected(true, t.getThingID() + " connected");
            Log.i(TAG, t.getThingID() + " is already registered!");
        }
        applyConfiguration(t);
    }

    /**
     * Schedule data readers for things as defined in thing configuration
     */
    private void applyConfiguration(Thing thing) {
        if (thing != null) {
            stopReadDataTask(thing.getNodeID(), thing.getThingID());
            if (thing.getThingConfiguration().getDataReadingFrequency() > 0) {
                mThingDataHandler = new ThingDataHandler(thing);

                mExecutor = Executors.newScheduledThreadPool(NUMBER_OF_THREADS_IN_EXECUTOR);

                ScheduledFuture<?> sf = mExecutor.scheduleAtFixedRate(mThingDataHandler, EXECUTOR_START_DELAY
                        , thing.getThingConfiguration().getDataReadingFrequency(), TimeUnit.MILLISECONDS);
                String key = thing.getNodeID() + "|" + thing.getThingID();
                tasks.put(key, sf);
            }
        }
    }

    /**
     * Stop Scheduled Future task which reads and sends data
     */
    public void stopReadDataTask(String nodeId, String sensorId) {
        String key = nodeId + "|" + sensorId;
        if (tasks.containsKey(key)) {
            try {
                tasks.get(key).cancel(true);
                tasks.remove(key);
            } catch (Exception e) {
                Log.d(TAG, "Could not stop schedule send data task" + e);
            }
        }
    }

    private void cancelAndScheduleIgniteTimer() {
        igniteTimer.cancel();
        igniteWatchDog.cancel();
        igniteWatchDog = new IgniteWatchDog();
        igniteTimer = new Timer();
        igniteTimer.schedule(igniteWatchDog, IGNITE_TIMER_PERIOD);
    }

    private boolean isConfigReadWhenArrive(Thing mThing) {
        if (mThing!= null && mThing.getThingConfiguration().getDataReadingFrequency() == ThingConfiguration.READING_WHEN_ARRIVE) {
            return true;
        }
        return false;
    }

    /**
     * Send data to IoT Ignite
     */
    public void sendData(Thing thing, float value) {
        if (igniteConnected) {
            if (thing != null) {
                ThingData mthingData = new ThingData();
                mthingData.addData(value);
                if (thing.sendData(mthingData)) {
                    Log.i(TAG, "DATA SENT SUCCESSFULLY : " + mthingData);
                } else {
                    Log.i(TAG, "DATA SENT FAILURE");
                }
            } else {
                Log.i(TAG, "Thing is not registered");
            }
        } else {
            Log.i(TAG, "Ignite Disconnected!");
        }
    }

    public SensorEventListener getTemperatureListener() {
        return mTemperatureListener;
    }

    public SensorEventListener getPressureListener() {
        return mPressureListener;
    }

    /**
     * Handle IoT-Ignite connection with timer task
     */
    private class IgniteWatchDog extends TimerTask {
        @Override
        public void run() {
            if (!igniteConnected && !versionError) {
                Log.i(TAG, "Rebuild Ignite...");
                start();
            }
        }
    }

    /**
     * Get thing last values and then send these values to IoT-Ignite
     */
    private class ThingDataHandler implements Runnable {

        Thing mThing;

        ThingDataHandler(Thing thing) {
            mThing = thing;
        }

        @Override
        public void run() {
            if (mThing.equals(mTemperatureThing)) {
                sendData(mTemperatureThing, mLastTemperature);
            } else if (mThing.equals(mPressureThing)) {
                sendData(mPressureThing, mLastPressure);
            }
        }
    }
}
