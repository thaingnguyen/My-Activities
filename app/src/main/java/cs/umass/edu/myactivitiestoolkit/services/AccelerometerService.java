package cs.umass.edu.myactivitiestoolkit.services;

import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import android.widget.EditText;

import org.json.JSONException;
import org.json.JSONObject;

import cs.umass.edu.myactivitiestoolkit.R;
import cs.umass.edu.myactivitiestoolkit.constants.Constants;
import cs.umass.edu.myactivitiestoolkit.processing.Filter;
import cs.umass.edu.myactivitiestoolkit.steps.OnStepListener;
import cs.umass.edu.myactivitiestoolkit.steps.StepDetector;
import edu.umass.cs.MHLClient.client.MessageReceiver;
import edu.umass.cs.MHLClient.client.MobileIOClient;
import edu.umass.cs.MHLClient.sensors.AccelerometerReading;
import edu.umass.cs.MHLClient.sensors.SensorReading;

/**
 * This service is responsible for collecting the accelerometer data on
 * the phone. It is an ongoing foreground service that will run even when your
 * application is not running. Note, however, that a process of your application
 * will still be running! The sensor service will receive sensor events in the
 * {@link #onSensorChanged(SensorEvent)} method defined in the {@link SensorEventListener}
 * interface.
 * <br><br>
 * <b>ASSIGNMENT 0 (Data Collection & Visualization)</b> :
 *      In this assignment, you will display and visualize the accelerometer readings
 *      and send the data to the server. In {@link #onSensorChanged(SensorEvent)},
 *      you should send the data to the main UI using the method
 *      {@link #broadcastAccelerometerReading(long, float[])}. You should also
 *      use the {@link #mClient} object to send data to the server. You can
 *      confirm it works by checking that both the local and server-side plots
 *      are updating (make sure your html script is running on your machine!).
 *      APP, VISUALIZATION, PYTHON SCRIPT RUN SUCCESSFUL 9/16 7:30PM, READY FOR A0 SUBMISSION
 * <br><br>
 *
 * <b>ASSIGNMENT 1 (Step Detection)</b> :
 *      In this assignment, you will detect steps using the accelerometer sensor. You
 *      will design both a local step detection algorithm and a server-side (Python)
 *      step detection algorithm. Your algorithm should look for peaks and account for
 *      the fact that humans generally take steps every 0.5 - 2.0 seconds. Your local
 *      and server-side algorithms may be functionally identical, or you may choose
 *      to take advantage of other Python tools/libraries to improve performance.
 *      Call your local step detection algorithm from {@link #onSensorChanged(SensorEvent)}.
 *      <br><br>
 *      To listen for messages from the server,
 *      register a {@link MessageReceiver} with the {@link #mClient} and override
 *      the {@link MessageReceiver#onMessageReceived(JSONObject)} method to handle
 *      the message appropriately. The data will be received as a {@link JSONObject},
 *      which you can parse to acquire the step count reading.
 *      <br><br>
 *      We have provided you with the reading computed by the Android built-in step
 *      detection algorithm as an example and a ground-truth reading that you may
 *      use for comparison. Note that although the built-in algorithm has empirically
 *      been shown to work well, it is not perfect and may be sensitive to the phone
 *      orientation. Also note that it does not update the step count immediately,
 *      so don't be surprised if the step count increases suddenly by a lot!
 *  <br><br>
 *
 * <b>ASSIGNMENT 2 (Activity Detection)</b> :
 *      In this assignment, you will classify the user's activity based on the
 *      accelerometer data. The only modification you should make to the mobile
 *      app is to register a listener which will parse the activity from the acquired
 *      {@link org.json.JSONObject} and update the UI. The real work, that is
 *      your activity detection algorithm, will be running in the Python script
 *      and acquiring data from the server.
 *
 * @author CS390MB
 *
 * @see android.app.Service
 * @see <a href="http://developer.android.com/guide/components/services.html#Foreground">
 * Foreground Service</a>
 * @see SensorEventListener#onSensorChanged(SensorEvent)
 * @see SensorEvent
 * @see MobileIOClient
 */
public class AccelerometerService extends SensorService implements SensorEventListener {

    /** Used during debugging to identify logs by class */
    private static final String TAG = AccelerometerService.class.getName();

    /** Frequency to for step detection filter */
    private static final double CUTOFF_FREQUENCY = 3.0;

    /** Sensor Manager object for registering and unregistering system sensors */
    private SensorManager mSensorManager;

    /** Manages the physical accelerometer sensor on the phone. */
    private Sensor mAccelerometerSensor;

    /** Android built-in step detection sensor **/
    private Sensor mAndroidStepSensor;

    /** Defines your step detection algorithm. **/
    private final StepDetector mStepDetector;

    /** Customized filter based on time/frequency */
    private final Filter mFilter;

    /** The step count as predicted by the Android built-in step detection algorithm. */
    private int mAndroidStepCount = 0;

    /** The step count as predicted by the server side algorithm. */
    private int mServerStepCount = 0;

    private int mCurrentActivity;

    private boolean isCollecting;

    private LocalBroadcastManager mLocalBroadcastManager;

    public AccelerometerService(){
        mLocalBroadcastManager = LocalBroadcastManager.getInstance(this);
        mFilter = new Filter(CUTOFF_FREQUENCY);
        mStepDetector = new StepDetector();
        mStepDetector.registerOnStepListener(new OnStepListener() {
            @Override
            public void onStepCountUpdated(int stepCount) {
                broadcastLocalStepCount(stepCount);
            }

            @Override
            public void onStepDetected(long timestamp, float[] values) {
                broadcastStepDetected(timestamp, values);
            }
        });
    }

    @Override
    protected void onServiceStarted() {
        broadcastMessage(Constants.MESSAGE.ACCELEROMETER_SERVICE_STARTED);
    }

    @Override
    protected void onServiceStopped() {
        broadcastMessage(Constants.MESSAGE.ACCELEROMETER_SERVICE_STOPPED);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, intent.getAction());
        if (intent.getAction() != null) {
            if (intent.getAction().equals(Constants.ACTION.UPDATE_ACTIVITY)) {
                String activity = intent.getStringExtra(Constants.KEY.LABELLED_ACTIVITY);
                if (activity == "Jogging") {
                    mCurrentActivity = 0;
                } else if (activity == "Running") {
                    mCurrentActivity = 1;
                } else if (activity == "Biking") {
                    mCurrentActivity = 2;
                } else if (activity == "Jumping") {
                    mCurrentActivity = 3;
                }
            } else if (intent.getAction().equals(Constants.ACTION.COLLECT_DATA)) {
                isCollecting = intent.getBooleanExtra(Constants.KEY.IS_COLLECTING, false);
            }
        }
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onConnected() {
        super.onConnected();
        mClient.registerMessageReceiver(new MessageReceiver(Constants.MHLClientFilter.STEP_DETECTED) {
            @Override
            protected void onMessageReceived(JSONObject json) {
                Log.i(TAG, "Received step update from server.");
                try {
                    JSONObject data = json.getJSONObject("data");
                    long timestamp = data.getLong("timestamp");
                    mServerStepCount += 1;
                    broadcastServerStepCount(mServerStepCount);
//                    Log.i(TAG, "Step occurred at " + timestamp + ".");
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
        mClient.registerMessageReceiver(new MessageReceiver(Constants.MHLClientFilter.ACTIVITY_DETECTED) {
            @Override
            protected void onMessageReceived(JSONObject json) {
                String activity;
                try {
                    JSONObject data = json.getJSONObject("data");
                    activity = data.getString("activity");
                } catch (JSONException e) {
                    e.printStackTrace();
                    return;
                }
                // TODO : broadcast activity to UI
            }
        });
    }

    /**
     * Register accelerometer sensor listener
     */
    @Override
    protected void registerSensors(){
        mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        mAccelerometerSensor =  mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mAndroidStepSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);

        mSensorManager.registerListener(this, mAccelerometerSensor, SensorManager.SENSOR_DELAY_NORMAL);
        mSensorManager.registerListener(this, mAndroidStepSensor, SensorManager.SENSOR_DELAY_UI);

        mSensorManager.registerListener(mStepDetector, mAccelerometerSensor, SensorManager.SENSOR_DELAY_NORMAL);
    }

    /**
     * Unregister the sensor listener, this is essential for the battery life!
     */
    @Override
    protected void unregisterSensors() {
        if (mAccelerometerSensor != null) {
            mSensorManager.unregisterListener(this, mAccelerometerSensor);
        }
        if (mAndroidStepSensor != null) {
            mSensorManager.unregisterListener(this, mAndroidStepSensor);
        }
        mStepDetector.unregisterOnStepListeners();
    }

    @Override
    protected int getNotificationID() {
        return Constants.NOTIFICATION_ID.ACCELEROMETER_SERVICE;
    }

    @Override
    protected String getNotificationContentText() {
        return getString(R.string.activity_service_notification);
    }

    @Override
    protected int getNotificationIconResourceID() {
        return R.drawable.ic_running_white_24dp;
    }

    /**
     * This method is called when we receive a sensor reading. We will be interested in this method primarily.
     * <br><br>
     *
     * Assignment 0 : Your job is to send the accelerometer readings to the server as you receive
     * them. Use the {@link #mClient} from the base class {@link SensorService} to communicate with
     * the data collection server. Specifically look at {@link MobileIOClient#sendSensorReading(SensorReading)}.
     * <br><br>
     *
     * We will be sending {@link AccelerometerReading}s. When instantiating an {@link AccelerometerReading},
     * pass in your user ID, which is accessible from the base sensor service, your device type and
     * your device identifier, as well as the timestamp and values of the sensor event.
     * <br><br>
     *
     * Note you may leave the device identifier a blank string. For the device type, you can use "MOBILE".
     * <br><br>
     *
     * You also want to broadcast the accelerometer reading to the UI. You can do this by calling
     * {@link #broadcastAccelerometerReading(long, float[])}.
     *
     * @see AccelerometerReading
     * @see SensorReading
     * @see MobileIOClient
     * @see SensorEvent
     * @see #broadcastAccelerometerReading(long, float[])
     */

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {

            float[] values = convertDoublesToFloats(mFilter.getFilteredValues(event.values));

            // convert the timestamp to milliseconds (note this is not in Unix time)
            long timestamp_in_milliseconds = (long) ((double) event.timestamp / Constants.TIMESTAMPS.NANOSECONDS_PER_MILLISECOND);

            broadcastAccelerometerReading(timestamp_in_milliseconds, values);

//            mClient.sendSensorReading(new AccelerometerReading(mUserID, "MOBILE", "", timestamp_in_milliseconds, values));

            if (isCollecting) {
                mClient.sendSensorReading(new AccelerometerReading(mUserID, "MOBILE", "", timestamp_in_milliseconds, mCurrentActivity, values));
            }
        } else if (event.sensor.getType() == Sensor.TYPE_STEP_DETECTOR) {

            // we received a step event detected by the built-in Android step detector (assignment 1)
            broadcastAndroidStepCount(mAndroidStepCount++);

        } else {

            // cannot identify sensor type
            Log.w(TAG, Constants.ERROR_MESSAGES.WARNING_SENSOR_NOT_SUPPORTED);

        }
    }

    private float[] convertDoublesToFloats(double[] input) {
        if (input == null) {
            return null;
        }
        float[] output = new float[input.length];
        for (int i = 0; i < input.length; i++) {
            output[i] = (float) input[i];
        }
        return output;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
//        Log.i(TAG, "Accuracy changed: " + accuracy);
    }

    /**
     * Broadcasts the accelerometer reading to other application components, e.g. the main UI.
     * @param accelerometerReadings the x, y, and z accelerometer readings
     */
    public void broadcastAccelerometerReading(final long timestamp, final float[] accelerometerReadings) {
        mLocalBroadcastManager.sendBroadcast(
                new Intent()
                    .putExtra(Constants.KEY.TIMESTAMP, timestamp)
                    .putExtra(Constants.KEY.ACCELEROMETER_DATA, accelerometerReadings)
                    .setAction(Constants.ACTION.BROADCAST_ACCELEROMETER_DATA));
    }

    // ***************** Methods for broadcasting step counts (assignment 1) *****************

    /**
     * Broadcasts the step count computed by the Android built-in step detection algorithm
     * to other application components, e.g. the main UI.
     */
    public void broadcastAndroidStepCount(int stepCount) {
        mLocalBroadcastManager.sendBroadcast(
                new Intent()
                    .putExtra(Constants.KEY.STEP_COUNT, stepCount)
                    .setAction(Constants.ACTION.BROADCAST_ANDROID_STEP_COUNT));
    }

    /**
     * Broadcasts the step count computed by your step detection algorithm
     * to other application components, e.g. the main UI.
     */
    public void broadcastLocalStepCount(int stepCount) {
        mLocalBroadcastManager.sendBroadcast(
                new Intent()
                    .putExtra(Constants.KEY.STEP_COUNT, stepCount)
                    .setAction(Constants.ACTION.BROADCAST_LOCAL_STEP_COUNT));
    }

    /**
     * Broadcasts the step count computed by server side algorithm
     * to other application components, e.g. the main UI.
     */
    public void broadcastServerStepCount(int stepCount) {
//        Log.i(TAG, "broadcast " + stepCount);
        mLocalBroadcastManager.sendBroadcast(
                new Intent()
                    .putExtra(Constants.KEY.STEP_COUNT, stepCount)
                    .setAction(Constants.ACTION.BROADCAST_SERVER_STEP_COUNT));
    }

    /**
     * Broadcasts a step event to other application components, e.g. the main UI.
     * Use this if you would like to visualize the detected step on the accelerometer signal.
     */
    public void broadcastStepDetected(long timestamp, float[] values) {
//        Log.d(TAG, timestamp + " " + values);
        mLocalBroadcastManager.sendBroadcast(
                new Intent()
                    .putExtra(Constants.KEY.ACCELEROMETER_PEAK_TIMESTAMP, timestamp)
                    .putExtra(Constants.KEY.ACCELEROMETER_PEAK_VALUE, values)
                    .setAction(Constants.ACTION.BROADCAST_ACCELEROMETER_PEAK));
    }
}