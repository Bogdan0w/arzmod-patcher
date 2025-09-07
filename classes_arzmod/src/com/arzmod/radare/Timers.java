package com.arzmod.radare;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class Timers {
    private static final String TAG = "arzmod-timers-module";
    
    public interface TimerCallback {
        void onTimerTick(int timerId, int tickCount, long currentTime);
    }
    
    public interface TimerCallbackWithData<T> {
        void onTimerTick(int timerId, int tickCount, long currentTime, T data);
    }
    
    private static class TimerInfo {
        final Handler handler;
        final Runnable runnable;
        final TimerCallback callback;
        final TimerCallbackWithData<?> callbackWithData;
        final Object data;
        final int intervalMs;
        final int timerId;
        int tickCount;
        boolean isRunning;
        
        TimerInfo(int timerId, int intervalMs, TimerCallback callback, TimerCallbackWithData<?> callbackWithData, Object data) {
            this.timerId = timerId;
            this.intervalMs = intervalMs;
            this.callback = callback;
            this.callbackWithData = callbackWithData;
            this.data = data;
            this.tickCount = 0;
            this.isRunning = false;
            this.handler = new Handler(Looper.getMainLooper());
            this.runnable = createRunnable();
        }
        
        private Runnable createRunnable() {
            return new Runnable() {
                @Override
                public void run() {
                    if (!isRunning) return;
                    
                    tickCount++;
                    long currentTime = System.currentTimeMillis();
                    
                    try {
                        if (callback != null) {
                            callback.onTimerTick(timerId, tickCount, currentTime);
                        } else if (callbackWithData != null) {
                            try {
                                @SuppressWarnings("unchecked")
                                TimerCallbackWithData<Object> typedCallback = (TimerCallbackWithData<Object>) callbackWithData;
                                typedCallback.onTimerTick(timerId, tickCount, currentTime, data);
                            } catch (ClassCastException e) {
                                Log.e(TAG, "Type cast error in timer callback: " + e.getMessage());
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error in timer callback: " + e.getMessage());
                    }
                    
                    if (isRunning) {
                        handler.postDelayed(this, intervalMs);
                    }
                }
            };
        }
        
        void start() {
            if (isRunning) return;
            isRunning = true;
            handler.post(runnable);
            Log.d(TAG, "Timer " + timerId + " started with interval " + intervalMs + "ms");
        }
        
        void stop() {
            isRunning = false;
            handler.removeCallbacks(runnable);
            Log.d(TAG, "Timer " + timerId + " stopped at tick #" + tickCount);
        }
        
        void pause() {
            isRunning = false;
            handler.removeCallbacks(runnable);
            Log.d(TAG, "Timer " + timerId + " paused at tick #" + tickCount);
        }
        
        void resume() {
            if (isRunning) return;
            isRunning = true;
            handler.postDelayed(runnable, intervalMs);
            Log.d(TAG, "Timer " + timerId + " resumed");
        }
    }
    
    private static final ConcurrentHashMap<Integer, TimerInfo> activeTimers = new ConcurrentHashMap<>();
    private static final AtomicInteger timerIdCounter = new AtomicInteger(1);
    
    public static int startTimer(int intervalMs, TimerCallback callback) {
        return startTimer(intervalMs, callback, null, null);
    }

    public static <T> int startTimer(int intervalMs, TimerCallbackWithData<T> callback, T data) {
        return startTimer(intervalMs, null, callback, data);
    }
    
    private static int startTimer(int intervalMs, TimerCallback callback, TimerCallbackWithData<?> callbackWithData, Object data) {
        if (intervalMs <= 0) {
            throw new IllegalArgumentException("Interval must be positive");
        }
        if (callback == null && callbackWithData == null) {
            throw new IllegalArgumentException("Callback must be provided");
        }
        
        int timerId = timerIdCounter.getAndIncrement();
        TimerInfo timerInfo = new TimerInfo(timerId, intervalMs, callback, callbackWithData, data);
        
        activeTimers.put(timerId, timerInfo);
        timerInfo.start();
        
        return timerId;
    }
    

    public static boolean stopTimer(int timerId) {
        TimerInfo timerInfo = activeTimers.remove(timerId);
        if (timerInfo != null) {
            timerInfo.stop();
            return true;
        }
        return false;
    }
    
    public static boolean pauseTimer(int timerId) {
        TimerInfo timerInfo = activeTimers.get(timerId);
        if (timerInfo != null) {
            timerInfo.pause();
            return true;
        }
        return false;
    }
    
    public static boolean resumeTimer(int timerId) {
        TimerInfo timerInfo = activeTimers.get(timerId);
        if (timerInfo != null) {
            timerInfo.resume();
            return true;
        }
        return false;
    }
    
    public static boolean isTimerRunning(int timerId) {
        TimerInfo timerInfo = activeTimers.get(timerId);
        return timerInfo != null && timerInfo.isRunning;
    }
    
    public static int getTimerTickCount(int timerId) {
        TimerInfo timerInfo = activeTimers.get(timerId);
        return timerInfo != null ? timerInfo.tickCount : -1;
    }

    public static void stopAllTimers() {
        for (TimerInfo timerInfo : activeTimers.values()) {
            timerInfo.stop();
        }
        activeTimers.clear();
        Log.d(TAG, "All timers stopped");
    }
    
    public static int getActiveTimerCount() {
        return activeTimers.size();
    }
    

    public static int[] getActiveTimerIds() {
        Integer[] ids = activeTimers.keySet().toArray(new Integer[0]);
        int[] result = new int[ids.length];
        for (int i = 0; i < ids.length; i++) {
            result[i] = ids[i];
        }
        return result;
    }
    

    public static boolean changeTimerInterval(int timerId, int newIntervalMs) {
        if (newIntervalMs <= 0) {
            throw new IllegalArgumentException("New interval must be positive");
        }
        
        TimerInfo timerInfo = activeTimers.get(timerId);
        if (timerInfo != null) {
            boolean wasRunning = timerInfo.isRunning;
            if (wasRunning) {
                timerInfo.pause();
            }
            TimerInfo newTimerInfo = new TimerInfo(timerId, newIntervalMs, 
                timerInfo.callback, timerInfo.callbackWithData, timerInfo.data);
            newTimerInfo.tickCount = timerInfo.tickCount;
            
            activeTimers.put(timerId, newTimerInfo);
            
            if (wasRunning) {
                newTimerInfo.resume();
            }
            
            Log.d(TAG, "Timer " + timerId + " interval changed to " + newIntervalMs + "ms");
            return true;
        }
        return false;
    }
}
