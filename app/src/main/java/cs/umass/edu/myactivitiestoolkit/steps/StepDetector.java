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
    private static final double SAMPLE_RATE = 100;

    /** Maintains the set of listeners registered to handle step events. **/
    private ArrayList<OnStepListener> mStepListeners;

    /**
     * The number of steps taken.
     */
    private int stepCount;

    /** Customized filter based on time/frequency */
    private final Filter mFilter;

    /** Holds the current signal values to be processed */
    private final List<Float> mCurrentValueBuffer;

    /** Holds the current timestampt to be processed */
    private final List<Long> mCurrentTimestampBuffer;

    public StepDetector(){
        mStepListeners = new ArrayList<>();
        stepCount = 0;
        mFilter = new Filter(CUTOFF_FREQUENCY);
        mCurrentValueBuffer = new ArrayList<>();
        mCurrentTimestampBuffer = new ArrayList<>();
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

            mCurrentValueBuffer.add(get3DVectorValue(values));
            mCurrentTimestampBuffer.add(timestamp_in_milliseconds);

            if (mCurrentValueBuffer.size() > SAMPLE_RATE) {
                double minValue = Collections.min(mCurrentValueBuffer);
                double maxValue = Collections.max(mCurrentValueBuffer);
//                Log.i(TAG, "minmax:" + minValue + " " + maxValue);
                if (maxValue - minValue > DELTA_THRESHOLD) {
                    double threshold = (maxValue + minValue) / 2;
                    detectSteps(mCurrentValueBuffer, mCurrentTimestampBuffer, threshold);
                }
                mCurrentValueBuffer.clear();
                mCurrentTimestampBuffer.clear();

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

    private float get3DVectorValue(float[] vector) {
        double value = 0;
        for (int i = 0; i < vector.length; i++) {
            value += vector[i] * vector[i];
        }
        return (float) Math.sqrt(value);
    }

    private void detectSteps(List<Float> values, List<Long> timestamps, double threshold) {
        // Check for new step using upward crossing algorithm
        for (int i = 1; i < values.size(); i++) {
            if (values.get(i) > values.get(i - 1)
                    && values.get(i-1) <= threshold
                    && values.get(i) >= threshold)
                onStepDetected(timestamps.get(i), new float[]{values.get(i)});
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
