package com.jagrosh.jmusicbot.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TimeUtils {
    public static int parseSeekTime(String timeString) {
        Pattern pattern = Pattern.compile("(-?)(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s)?");
        Matcher matcher = pattern.matcher(timeString);

        if (matcher.matches()) {
            int sign = matcher.group(1) != null && matcher.group(1).equals("-") ? -1 : 1;
            int hours = matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : 0;
            int minutes = matcher.group(3) != null ? Integer.parseInt(matcher.group(3)) : 0;
            int seconds = matcher.group(4) != null ? Integer.parseInt(matcher.group(4)) : 0;

            int totalSeconds = sign * (hours * 3600 + minutes * 60 + seconds);
            return totalSeconds;
        } else {
            return Integer.MIN_VALUE; // Invalid time format
        }
    }

    public static String formatTime(int seconds) {
        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;
        int remainingSeconds = seconds % 60;

        return String.format("%02d:%02d:%02d", hours, minutes, remainingSeconds);
    }
}
