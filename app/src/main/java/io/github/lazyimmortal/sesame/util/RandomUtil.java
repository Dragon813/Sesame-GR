package io.github.lazyimmortal.sesame.util;

import java.util.Random;
import java.util.UUID;

public class RandomUtil {
    private static final Random rnd = new Random();
    
    public static int delay() {
        return nextInt(100, 300);
    }
    
    public static int nextInt(int min, int max) {
        if (min >= max) return min;
        // 修复边界问题：确保max > min
        if (max - min <= 0) {
            return min;
        }
        return rnd.nextInt(max - min) + min;
    }
    
    public static long nextLong() {
        return rnd.nextLong();
    }
    
    public static long nextLong(long min, long max) {
        if (min >= max) return min;
        long o = max - min;
        return rnd.nextLong() % o + min;
    }
    
    public static double nextDouble() {
        return rnd.nextDouble();
    }
    
    public static String getRandom(int len) {
        StringBuilder rs = new StringBuilder();
        for (int i = 0; i < len; i++) {
            rs.append(rnd.nextInt(10));
        }
        return rs.toString();
    }
    
    public static String getRandomString(int length) {
        String str = "abcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            int number = rnd.nextInt(36);
            sb.append(str.charAt(number));
        }
        return sb.toString();
    }
    
    public static String getRandomUUID() {
        return UUID.randomUUID().toString();
    }
}
