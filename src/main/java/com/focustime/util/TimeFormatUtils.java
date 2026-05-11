package com.focustime.util;

public final class TimeFormatUtils {
    private TimeFormatUtils() {
    }

    public static String formatDuration(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    public static String formatDurationShort(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;

        if (hours > 0 && minutes > 0) {
            return hours + " h " + minutes + " min";
        }
        if (hours > 0) {
            return hours + " h";
        }
        if (minutes > 0) {
            return minutes + " min";
        }
        return totalSeconds + " s";
    }
}
