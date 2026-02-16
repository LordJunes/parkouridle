package com.example.parkouridle.math;

import java.util.Objects;

/**
 * Layered base-10 number representation inspired by break_eternity.js.
 * layer=0 => mag
 * layer=1 => 10^mag
 * layer=2 => 10^(10^mag)
 */
public final class BigNumber implements Comparable<BigNumber> {

    private static final double LAYER_0_TO_1_THRESHOLD = Math.pow(10.0, 15.95);
    private static final double EPSILON = 1e-12;

    private final double mag;
    private final long layer;

    public static final BigNumber ZERO = new BigNumber(0.0, 0);
    public static final BigNumber ONE = new BigNumber(1.0, 0);

    public BigNumber(double mag, long layer) {
        if (Double.isNaN(mag) || Double.isInfinite(mag)) {
            this.mag = 0.0;
            this.layer = 0;
            return;
        }
        BigNumber normalized = normalize(mag, layer);
        this.mag = normalized.mag;
        this.layer = normalized.layer;
    }

    public static BigNumber of(double value) {
        return new BigNumber(value, 0);
    }

    public static BigNumber ofLayer(double mag, long layer) {
        return new BigNumber(mag, layer);
    }

    public double mag() {
        return mag;
    }

    public long layer() {
        return layer;
    }

    public boolean isZero() {
        return layer == 0 && mag <= EPSILON;
    }

    public BigNumber add(BigNumber other) {
        if (this.isZero()) {
            return other;
        }
        if (other.isZero()) {
            return this;
        }

        BigNumber a = this;
        BigNumber b = other;
        if (a.compareTo(b) < 0) {
            BigNumber tmp = a;
            a = b;
            b = tmp;
        }

        long layerDiff = a.layer - b.layer;
        if (layerDiff >= 2) {
            return a;
        }

        if (a.layer == 0 && b.layer == 0) {
            return BigNumber.of(a.mag + b.mag);
        }

        if (a.layer == 1 && b.layer == 1) {
            double max = a.mag;
            double min = b.mag;
            return new BigNumber(max + Math.log10(1.0 + Math.pow(10.0, min - max)), 1);
        }

        if (a.layer == 1 && b.layer == 0) {
            if (b.mag <= 0.0) {
                return a;
            }
            double bLog = Math.log10(b.mag);
            return new BigNumber(a.mag + Math.log10(1.0 + Math.pow(10.0, bLog - a.mag)), 1);
        }

        return a;
    }

    public BigNumber subtract(BigNumber other) {
        if (other.isZero()) {
            return this;
        }
        if (this.compareTo(other) <= 0) {
            return ZERO;
        }

        if (this.layer == 0 && other.layer == 0) {
            return BigNumber.of(this.mag - other.mag);
        }

        if (this.layer == 1 && other.layer == 1) {
            double max = this.mag;
            double min = other.mag;
            return new BigNumber(max + Math.log10(Math.max(EPSILON, 1.0 - Math.pow(10.0, min - max))), 1);
        }

        if (this.layer == 1 && other.layer == 0) {
            double otherLog = Math.log10(Math.max(EPSILON, other.mag));
            return new BigNumber(this.mag + Math.log10(Math.max(EPSILON, 1.0 - Math.pow(10.0, otherLog - this.mag))), 1);
        }

        return this;
    }

    public BigNumber multiply(BigNumber other) {
        if (this.isZero() || other.isZero()) {
            return ZERO;
        }

        if ((this.layer == 0 && this.mag < 0.0) || (other.layer == 0 && other.mag < 0.0)) {
            return ZERO;
        }

        if (this.layer == 0 && other.layer == 0) {
            return BigNumber.of(this.mag * other.mag);
        }

        BigNumber logSum = this.log10().add(other.log10());
        return logSum.pow10();
    }

    public BigNumber multiply(double scalar) {
        return multiply(BigNumber.of(scalar));
    }

    public BigNumber pow(double exponent) {
        if (exponent == 0.0) {
            return ONE;
        }
        if (isZero()) {
            return ZERO;
        }
        if (exponent < 0.0) {
            return ZERO;
        }
        BigNumber newLog = this.log10().multiply(BigNumber.of(exponent));
        return newLog.pow10();
    }

    public BigNumber pow(BigNumber exponent) {
        if (exponent.isZero()) {
            return ONE;
        }
        if (isZero()) {
            return ZERO;
        }
        BigNumber newLog = this.log10().multiply(exponent);
        return newLog.pow10();
    }

    /**
     * Adds to the logarithmic layer (tetration-like scaling).
     */
    public BigNumber layerAdd(double add) {
        if (Math.abs(add) <= EPSILON) {
            return this;
        }

        long integer = (long) Math.floor(add);
        double fractional = add - integer;

        long newLayer = this.layer + integer;
        double newMag = this.mag;

        if (fractional > EPSILON) {
            if (newLayer <= 0) {
                double safe = Math.max(newMag, 1.0000001);
                newLayer = 1;
                newMag = Math.log10(safe) + fractional;
            } else {
                newMag += fractional;
            }
        }

        if (newLayer < 0) {
            newLayer = 0;
            newMag = 0.0;
        }

        return new BigNumber(newMag, newLayer);
    }

    public BigNumber log10() {
        if (isZero()) {
            return ZERO;
        }
        if (layer == 0) {
            return BigNumber.of(Math.log10(mag));
        }
        return new BigNumber(mag, layer - 1);
    }

    public BigNumber pow10() {
        if (layer == 0) {
            return new BigNumber(Math.pow(10.0, mag), 0);
        }
        return new BigNumber(mag, layer + 1);
    }

    public double toDouble() {
        if (layer == 0) {
            return mag;
        }
        if (layer == 1 && mag < 308) {
            return Math.pow(10.0, mag);
        }
        return Double.POSITIVE_INFINITY;
    }

    private static BigNumber normalize(double mag, long layer) {
        double m = mag;
        long l = Math.max(0, layer);

        if (l == 0) {
            if (m < 0.0) {
                m = 0.0;
            }
            if (m >= LAYER_0_TO_1_THRESHOLD) {
                l = 1;
                m = Math.log10(m);
            }
            return new BigNumber(m, l, true);
        }

        if (m < 0.0) {
            m = 0.0;
        }

        while (l > 0 && m < 15.95) {
            l -= 1;
            m = Math.pow(10.0, m);
            if (l == 0 && m >= LAYER_0_TO_1_THRESHOLD) {
                l = 1;
                m = Math.log10(m);
                break;
            }
        }

        return new BigNumber(m, l, true);
    }

    private BigNumber(double mag, long layer, boolean alreadyNormalized) {
        this.mag = mag;
        this.layer = layer;
    }

    @Override
    public int compareTo(BigNumber other) {
        if (this.layer != other.layer) {
            return Long.compare(this.layer, other.layer);
        }
        return Double.compare(this.mag, other.mag);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof BigNumber that)) {
            return false;
        }
        return Double.compare(that.mag, mag) == 0 && layer == that.layer;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mag, layer);
    }

    @Override
    public String toString() {
        return "BigNumber{" + "mag=" + mag + ", layer=" + layer + '}';
    }
}
