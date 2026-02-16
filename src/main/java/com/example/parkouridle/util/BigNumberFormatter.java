package com.example.parkouridle.util;

import com.example.parkouridle.math.BigNumber;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Locale;

public final class BigNumberFormatter {

    private static final NumberFormat COMMA = NumberFormat.getNumberInstance(Locale.US);
    private static final DecimalFormat SCI = new DecimalFormat("0.00E0", DecimalFormatSymbols.getInstance(Locale.US));
    private static final DecimalFormat LAYER = new DecimalFormat("0.0", DecimalFormatSymbols.getInstance(Locale.US));

    private BigNumberFormatter() {
    }

    public static String formatBigNumber(BigNumber value) {
        if (value.layer() >= 2) {
            if (value.layer() == 2) {
                return "ee" + LAYER.format(value.mag());
            }
            return "ee" + LAYER.format(value.mag()) + "^L" + value.layer();
        }

        if (value.layer() == 1) {
            return "1.00e" + LAYER.format(value.mag());
        }

        double asDouble = Math.max(0.0, value.toDouble());
        if (asDouble < 1_000_000.0) {
            return COMMA.format(asDouble);
        }
        return SCI.format(asDouble).replace('E', 'e');
    }

    public static String formatMillis(long millis) {
        long minutes = millis / 60_000;
        long seconds = (millis % 60_000) / 1_000;
        long ms = millis % 1_000;
        return String.format(Locale.US, "%dm %02d.%03ds", minutes, seconds, ms);
    }
}
