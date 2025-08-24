package com.arzmod.radare;

import com.unity3d.ads.IUnityAdsLoadListener;
import com.unity3d.ads.IUnityAdsShowListener;
import com.unity3d.ads.IUnityAdsInitializationListener;
import com.unity3d.ads.UnityAds;
import com.unity3d.ads.metadata.MetaData;
import android.util.Log;
import android.content.Context;
import com.arzmod.radare.AppContext;
import android.app.Activity;


public class AppAds {
    private static String gameId = "";
    private static String bannerPlacementId = "";
    private static boolean testMode = false;

    public static void initializeAndShow() {
        Context context = AppContext.getContext();
        if (context == null) {
            Log.e("arzmod-unityads-module", "Context is null");
            return;
        }

        Activity activity = (Activity)context;
        Log.d("arzmod-unityads-module", "Activity: " + activity.getClass().getName());

        // UnityAds.initialize(activity, gameId, testMode);
        try {
            UnityAds.initialize(activity, gameId, testMode, new IUnityAdsInitializationListener() {
                public void onInitializationComplete() {
                    Log.d("UnityAds", "Unity Ads initialized successfully");
                    new android.os.Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            showBanner(activity);
                        }
                    }, 3000);
                    
                }

                public void onInitializationFailed(UnityAds.UnityAdsInitializationError error, String message) {
                    Log.e("UnityAds", "Unity Ads initialization failed: " + message);
                }
            });
        } catch (Exception e) {
            Log.e("UnityAds", "Error initializing Unity Ads: " + e.getMessage());
        }
    }

    public static void showBanner(Activity activity) {
        UnityAds.load(bannerPlacementId, new IUnityAdsLoadListener() {
            @Override
            public void onUnityAdsAdLoaded(String placementId) {
                UnityAds.show(activity, placementId, new IUnityAdsShowListener() {
                    @Override
                    public void onUnityAdsShowFailure(String placementId, UnityAds.UnityAdsShowError error, String message) {
                        Log.e("UnityAds", "Banner show failed: " + message);
                    }

                    @Override
                    public void onUnityAdsShowStart(String placementId) {
                        Log.d("UnityAds", "Banner show started");
                    }

                    @Override
                    public void onUnityAdsShowClick(String placementId) {
                        Log.d("UnityAds", "Banner clicked");
                    }

                    @Override
                    public void onUnityAdsShowComplete(String placementId, UnityAds.UnityAdsShowCompletionState state) {
                        Log.d("UnityAds", "Banner show completed");
                    }
                });
            }

            @Override
            public void onUnityAdsFailedToLoad(String placementId, UnityAds.UnityAdsLoadError error, String message) {
                Log.e("UnityAds", "Banner load failed: " + message);
            }
        });
    }
}