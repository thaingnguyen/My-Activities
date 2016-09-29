package cs.umass.edu.myactivitiestoolkit.steps;

public class AccelerometerBuffer implements Comparable<AccelerometerBuffer> {

    float[] vector;

    long timestamp;

    float value;

    AccelerometerBuffer(float[] vector, long timestamp) {
        this.vector = vector;
        this.value = get3DVectorValue(vector);
    }

    private float get3DVectorValue(float[] vector) {
        double value = 0;
        for (int i = 0; i < vector.length; i++) {
            value += vector[i] * vector[i];
        }
        return (float) Math.sqrt(value);
    }

    @Override
    public int compareTo(AccelerometerBuffer other) {
        if (this.value > other.value) {
            return 1;
        } else if (this.value < other.value) {
            return -1;
        } else {
            return 0;
        }
    }
}
