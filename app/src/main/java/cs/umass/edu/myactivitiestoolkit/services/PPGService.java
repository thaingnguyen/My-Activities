package cs.umass.edu.myactivitiestoolkit.services;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import cs.umass.edu.myactivitiestoolkit.R;
import cs.umass.edu.myactivitiestoolkit.ppg.HRSensorReading;
import cs.umass.edu.myactivitiestoolkit.ppg.PPGSensorReading;
import cs.umass.edu.myactivitiestoolkit.constants.Constants;
import cs.umass.edu.myactivitiestoolkit.ppg.HeartRateCameraView;
import cs.umass.edu.myactivitiestoolkit.ppg.PPGEvent;
import cs.umass.edu.myactivitiestoolkit.ppg.PPGListener;
import cs.umass.edu.myactivitiestoolkit.processing.FFT;
import cs.umass.edu.myactivitiestoolkit.processing.Filter;
import cs.umass.edu.myactivitiestoolkit.util.Interpolator;
import edu.umass.cs.MHLClient.client.MobileIOClient;

/**
 * Photoplethysmography service. This service uses a {@link HeartRateCameraView}
 * to collect PPG data using a standard camera with continuous flash. This is where
 * you will do most of your work for this assignment.
 * <br><br>
 * <b>ASSIGNMENT (PHOTOPLETHYSMOGRAPHY)</b> :
 * In {@link #onSensorChanged(PPGEvent)}, you should smooth the PPG reading using
 * a {@link Filter}. You should send the filtered PPG reading both to the server
 * and to the {@link cs.umass.edu.myactivitiestoolkit.view.fragments.HeartRateFragment}
 * for visualization. Then call your heart rate detection algorithm, buffering the
 * readings if necessary, and send the bpm measurement back to the UI.
 * <br><br>
 * EXTRA CREDIT:
 *      Follow the steps outlined <a href="http://www.marcoaltini.com/blog/heart-rate-variability-using-the-phones-camera">here</a>
 *      to acquire a cleaner PPG signal. For additional extra credit, you may also try computing
 *      the heart rate variability from the heart rate, as they do.
 *
 * @author CS390MB
 *
 * @see HeartRateCameraView
 * @see PPGEvent
 * @see PPGListener
 * @see Filter
 * @see MobileIOClient
 * @see PPGSensorReading
 * @see Service
 */
public class PPGService extends SensorService implements PPGListener
{
    @SuppressWarnings("unused")
    /** used for debugging purposes */
    private static final String TAG = PPGService.class.getName();

    private static final int MILLIS_PER_MINUTE = 1000 * 60;

    /* Surface view responsible for collecting PPG data and displaying the camera preview. */
    private HeartRateCameraView mPPGSensor;

    private Filter mFilter;

    private final Queue<PPGEvent> currentPeaks = new LinkedList<>();

    private final Queue<PPGEvent> buffer = new LinkedList<>();

    @Override
    protected void start() {
        Log.d(TAG, "START");
        mFilter = new Filter(0.8);
        mPPGSensor = new HeartRateCameraView(getApplicationContext(), null);

        WindowManager winMan = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH |
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT);

        //surface view dimensions and position specified where service intent is called
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 0;

        //display the surface view as a stand-alone window
        winMan.addView(mPPGSensor, params);
        mPPGSensor.setZOrderOnTop(true);

        // only once the surface has been created can we start the PPG sensor
        mPPGSensor.setSurfaceCreatedCallback(new HeartRateCameraView.SurfaceCreatedCallback() {
            @Override
            public void onSurfaceCreated() {
                mPPGSensor.start(); //start recording PPG
            }
        });

        super.start();
    }

    @Override
    protected void onServiceStarted() {
        broadcastMessage(Constants.MESSAGE.PPG_SERVICE_STARTED);
    }

    @Override
    protected void onServiceStopped() {
        if (mPPGSensor != null)
            mPPGSensor.stop();
        if (mPPGSensor != null) {
            ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).removeView(mPPGSensor);
        }
        broadcastMessage(Constants.MESSAGE.PPG_SERVICE_STOPPED);
    }

    @Override
    protected void registerSensors() {
        mPPGSensor.registerListener(this);
    }

    @Override
    protected void unregisterSensors() {
        mPPGSensor.unregisterListeners();
    }

    @Override
    protected int getNotificationID() {
        return Constants.NOTIFICATION_ID.PPG_SERVICE;
    }

    @Override
    protected String getNotificationContentText() {
        return getString(R.string.ppg_service_notification);
    }

    @Override
    protected int getNotificationIconResourceID() {
        return R.drawable.ic_whatshot_white_48dp;
    }

    /**
     * This method is called each time a PPG sensor reading is received.
     * <br><br>
     * You should smooth the data using {@link Filter} and then send the filtered data both
     * to the server and the main UI for real-time visualization. Run your algorithm to
     * detect heart beats, calculate your current bpm and send the bmp measurement to the
     * main UI. Additionally, it may be useful for you to send the peaks you detect to
     * the main UI, using {@link #broadcastPeak(long, double)}. The plot is already set up
     * to draw these peak points upon receiving them.
     * <br><br>
     * Also make sure to send your bmp measurement to the server for visualization. You
     * can do this using {@link HRSensorReading}.
     *
     * @param event The PPG sensor reading, wrapping a timestamp and mean red value.
     *
     * @see PPGEvent
     * @see PPGSensorReading
     * @see HeartRateCameraView#onPreviewFrame(byte[], Camera)
     * @see MobileIOClient
     * @see HRSensorReading
     */
    @SuppressWarnings("deprecation")
    @Override
    public void onSensorChanged(PPGEvent event) {
        // TODO: Smooth the signal using a Butterworth / exponential smoothing filter
        double filteredValue = mFilter.getFilteredValues((long) event.value)[0];
        long filteredValueTimestamp = event.timestamp;

        //use this for peak detection
        PPGEvent filteredEvent = new PPGEvent(filteredValue, filteredValueTimestamp);

        Log.i(TAG, event.value + " " + filteredValue);

        // Send the data to the UI fragment for visualization
        broadcastPPGReading(event.timestamp, filteredValue);

        // Send the filtered mean red value to the server
        mClient.sendSensorReading(new PPGSensorReading(mUserID, "MOBILE", "", event.timestamp, filteredValue));

        // TODO: Buffer data if necessary for your algorithm
        //one minute moving window
        //use a queue
        // enque when you find a peak
        // deque when top of queue is a timestamp that is more than a minute ago
        buffer.add(filteredEvent);
        while (buffer.size() > 0
                && System.currentTimeMillis() - buffer.peek().timestamp > MILLIS_PER_MINUTE) {
            buffer.remove();
        }

        double min = 0;
        double max = 0;
        double threshold = 0;

        for (PPGEvent sample : buffer) {
            double sample_data = sample.value;
            if (sample_data > max)
                max = sample_data;
            else if (sample_data < min)
                min = sample_data;
        }

        threshold = (min + max)/2;


        // TODO: Call your heart beat and bpm detection algorithm
        while (currentPeaks.size() > 0
                && System.currentTimeMillis() - currentPeaks.peek().timestamp > MILLIS_PER_MINUTE) {
            currentPeaks.remove();
        }
        if (isUpwardCrossing(buffer, threshold, currentPeaks)) {
            currentPeaks.add(event);
        }

        // TODO: Send your heart rate estimate to UI and the server
        broadcastBPM(currentPeaks.size());
        mClient.sendSensorReading(new HRSensorReading(mUserID, "MOBILE", "", event.timestamp, currentPeaks.size()));
    }

    /**
     * Broadcasts the PPG reading to other application components, e.g. the main UI.
     * @param ppgReading the mean red value.
     */
    public void broadcastPPGReading(final long timestamp, final double ppgReading) {
        Intent intent = new Intent();
        intent.putExtra(Constants.KEY.PPG_DATA, ppgReading);
        intent.putExtra(Constants.KEY.TIMESTAMP, timestamp);
        intent.setAction(Constants.ACTION.BROADCAST_PPG);
        LocalBroadcastManager manager = LocalBroadcastManager.getInstance(this);
        manager.sendBroadcast(intent);
    }

    /**
     * Broadcasts the current heart rate in BPM to other application components, e.g. the main UI.
     * @param bpm the current beats per minute measurement.
     */
    public void broadcastBPM(final int bpm) {
        Intent intent = new Intent();
        intent.putExtra(Constants.KEY.HEART_RATE, bpm);
        intent.setAction(Constants.ACTION.BROADCAST_HEART_RATE);
        LocalBroadcastManager manager = LocalBroadcastManager.getInstance(this);
        manager.sendBroadcast(intent);
    }

    /**
     * Broadcasts the current heart rate in BPM to other application components, e.g. the main UI.
     * @param timestamp the current beats per minute measurement.
     */
    public void broadcastPeak(final long timestamp, final double value) {
        Intent intent = new Intent();
        intent.putExtra(Constants.KEY.PPG_PEAK_TIMESTAMP, timestamp);
        intent.putExtra(Constants.KEY.PPG_PEAK_VALUE, value);
        intent.setAction(Constants.ACTION.BROADCAST_PPG_PEAK);
        LocalBroadcastManager manager = LocalBroadcastManager.getInstance(this);
        manager.sendBroadcast(intent);
    }

    private boolean isPeak(double value) {
        return value > 215;
    }

    boolean isUpwardCrossing(Queue<PPGEvent> events, double threshold, Queue<PPGEvent> curPeaks) {
        //Get the values from the list of events
        List<Double> values = new ArrayList<Double>();
        for (PPGEvent event : events) {
            values.add(event.value);
        }
        //Get the timestamps from the list of events
        List<Long> timestamps = new ArrayList<Long>();
        for (PPGEvent event : events) {
            timestamps.add(event.timestamp);
        }
                for (int i = 0; i < values.size(); i++) {
                        if (values.get(i) > values.get(i + 1)
                                        && values.get(i-1) <= threshold
                                        && values.get(i) >= threshold
                                        && !containsTimestamp(timestamps.get(i), curPeaks))
                                return true;
                        }
                return false;
                }

    /**
     * Checks to make sure that we don't redetect a peak
     * @param timestamp
     * @return
     */
    boolean containsTimestamp(long timestamp, Queue<PPGEvent> curPeaks) {
        for (PPGEvent event : curPeaks) {
            if (event.timestamp == timestamp)
                return true;
        }
        return false;
    }
}