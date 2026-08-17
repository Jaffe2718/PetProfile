package io.github.jaffe2718.petprofile.data;

public class NumericPoint {
    public long timestamp;
    public double value;

    public NumericPoint() {
    }

    public NumericPoint(long timestamp, double value) {
        this.timestamp = timestamp;
        this.value = value;
    }
}
