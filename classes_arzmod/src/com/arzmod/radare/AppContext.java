package com.arzmod.radare;

import android.util.Log;
import android.content.Context;
import android.app.Activity;
import java.util.ArrayList;
import java.util.List;

public class AppContext {
    private static Context context;
    private static Activity activity;
    
    public static void setContext(Context _context) {
        Log.d("arzmod-context-module", "-> context set");
        if (_context != null) {
            Log.d("arzmod-radare-module", "Context class: " + _context.getClass().getName());
            Log.d("arzmod-radare-module", "Context: " + String.valueOf(_context));
        } else {
            Log.e("arzmod-context-module", "context == null");
        }
        context = _context;
    }

    public static Context getContext() {
        return context;
    }

    public static void setActivity(Activity _activity) {
        Log.d("arzmod-context-module", "-> activity set");
        if (_activity != null) {
            Log.d("arzmod-radare-module", "Activity class: " + _activity.getClass().getName());
            Log.d("arzmod-radare-module", "Activity: " + String.valueOf(_activity));
        } else {
            Log.e("arzmod-context-module", "activity == null");
        }
        activity = _activity;
    }

    public static Activity getActivity() {
        return activity;
    }

    public static Activity MainEntrenchActivity;
    public static Activity GTASAActivity;

    private static final List<MainEntrenchActivityCallback> mainCallbacks = new ArrayList<>();
    private static final List<GTASAActivityCallback> gtasaCallbacks = new ArrayList<>();

    public interface MainEntrenchActivityCallback {
        void onResult(Activity activity);
    }

    public interface GTASAActivityCallback {
        void onResult(Activity activity);
    }

    public static synchronized void getMainEntrenchActivity(final MainEntrenchActivityCallback callback) {
        if (MainEntrenchActivity != null) {
            callback.onResult(MainEntrenchActivity);
        } else {
            mainCallbacks.add(callback);
        }
    }

    public static synchronized Activity getMainEntrenchActivity() {
        return MainEntrenchActivity;
    }

    public static synchronized void getGTASAActivity(final GTASAActivityCallback callback) {
        if (GTASAActivity != null) {
            callback.onResult(GTASAActivity);
        } else {
            gtasaCallbacks.add(callback);
        }
    }

    public static synchronized Activity getGTASAActivity() {
        return GTASAActivity;
    }

    public static synchronized void setMainEntrenchActivity(Activity activity) {
        Log.d("arzmod-radare-module", "MainEntrenchActivity class: " + activity.getClass().getName());
        Log.d("arzmod-radare-module", "MainEntrenchActivity: " + String.valueOf(activity));
        MainEntrenchActivity = activity;
        setContext(activity.getApplicationContext());
        setActivity(activity);
        if (activity != null && !mainCallbacks.isEmpty()) {
            List<MainEntrenchActivityCallback> pending = new ArrayList<>(mainCallbacks);
            mainCallbacks.clear();
            for (MainEntrenchActivityCallback cb : pending) {
                try { cb.onResult(activity); } catch (Throwable t) { Log.e("arzmod-context-module", "Main callback error: " + t.getMessage()); }
            }
        }
    }

    public static synchronized void setGTASAActivity(Activity activity) {
        Log.d("arzmod-radare-module", "GTASAActivity class: " + activity.getClass().getName());
        Log.d("arzmod-radare-module", "GTASAActivity: " + String.valueOf(activity));
        GTASAActivity = activity;
        setContext(activity.getApplicationContext());
        setActivity(activity);
        if (activity != null && !gtasaCallbacks.isEmpty()) {
            List<GTASAActivityCallback> pending = new ArrayList<>(gtasaCallbacks);
            gtasaCallbacks.clear();
            for (GTASAActivityCallback cb : pending) {
                try { cb.onResult(activity); } catch (Throwable t) { Log.e("arzmod-context-module", "GTASA callback error: " + t.getMessage()); }
            }
        }
    }
}