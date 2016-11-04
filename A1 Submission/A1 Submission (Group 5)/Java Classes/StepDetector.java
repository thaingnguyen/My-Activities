package cs.umass.edu.myactivitiestoolkit.steps;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import cs.umass.edu.myactivitiestoolkit.constants.Constants;
import cs.umass.edu.myactivitiestoolkit.processing.Filter;

/**
 * This class is responsible for detecting steps from the accelerometer sensor.
 * All {@link OnStepListener step listeners} that have been registered will
 * be notified when a step is detected.
 */
public class StepDetector implements SensorEventListener {
    /** Used for debugging purposes. */
    @SuppressWarnings("unused")
    private static final String TAG = StepDetector.class.getName();

    /** Frequency to for step detection filter */
    private static final double CUTOFF_FREQUENCY = 3.0;

    /** Threshold for difference between max and min in one buffer */
    private static final double DELTA_THRESHOLD = 5;

    /** Rate to run step detection algorithm */
    private static final double SAMPLE_RATE = 200;

    /** Maintains the set of listeners registered to handle step events. **/
    private ArrayList<OnStepListener> mStepListeners;

    /**
     * The number of steps taken.
     */
    private int stepCount;

    /** Customized filter based on time/frequency */
    private final Filter mFilter;

    /** Holds the current signal values to be processed */
    private final List<AccelerometerBuffer> mCurrentBuffers;

    public StepDetector(){
        mStepListeners = new ArrayList<>();
        stepCount = 0;
        mFilter = new Filter(CUTOFF_FREQUENCY);
        mCurrentBuffers = new ArrayList<>();
    }

    /**
     * Registers a step listener for handling step events.
     * @param stepListener defines how step events are handled.
     */
    public void registerOnStepListener(final OnStepListener stepListener){
        mStepListeners.add(stepListener);
    }

    /**
     * Unregisters the specified step listener.
     * @param stepListener the listener to be unregistered. It must already be registered.
     */
    public void unregisterOnStepListener(final OnStepListener stepListener){
        mStepListeners.remove(stepListener);
    }

    /**
     * Unregisters all step listeners.
     */
    public void unregisterOnStepListeners(){
        mStepListeners.clear();
    }

    /**
     * Here is where you will receive accelerometer readings, buffer them if necessary
     * and run your step detection algorithm. When a step is detected, call
     * {@link #onStepDetected(long, float[])} to notify all listeners.
     *
     * Recall that human steps tend to take anywhere between 0.5 and 2 seconds.
     *
     * @param event sensor reading
     */
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {

            float[] values = convertDoublesToFloats(mFilter.getFilteredValues(event.values));
            // convert the timestamp to milliseconds (note this is not in Unix time)
            long timestamp_in_milliseconds = (long) ((double) event.timestamp / Constants.TIMESTAMPS.NANOSECONDS_PER_MILLISECOND);

            mCurrentBuffers.add(new AccelerometerBuffer(values, timestamp_in_milliseconds));

            if (mCurrentBuffers.size() > SAMPLE_RATE) {
                AccelerometerBuffer minBuffer = Collections.min(mCurrentBuffers);
                AccelerometerBuffer maxBuffer = Collections.max(mCurrentBuffers);
                Log.i(TAG, "minmax:" + minBuffer.value + " " + maxBuffer.value);
                if (maxBuffer.value - minBuffer.value > DELTA_THRESHOLD) {
                    double threshold = (maxBuffer.value + minBuffer.value) / 2;
                    detectSteps(mCurrentBuffers, threshold);
                }
                mCurrentBuffers.clear();
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
        // do nothing
    }

    /**
     * This method is called when a step is detected. It updates the current step count,
     * notifies all listeners that a step has occurred and also notifies all listeners
     * of the current step count.
     */
    private void onStepDetected(long timestamp, float[] values){
        stepCount++;
        for (OnStepListener stepListener : mStepListeners){
            stepListener.onStepDetected(timestamp, values);
            stepListener.onStepCountUpdated(stepCount);
        }
    }

    private void detectSteps(List<AccelerometerBuffer> buffers, double threshold) {
        // Check for new step using upward crossing algorithm
        for (int i = 1; i < buffers.size(); i++) {
            if (buffers.get(i).value > buffers.get(i - 1).value
                    && buffers.get(i-1).value <= threshold
                    && buffers.get(i).value >= threshold)
                onStepDetected(buffers.get(i).timestamp, buffers.get(i).vector);
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
}
