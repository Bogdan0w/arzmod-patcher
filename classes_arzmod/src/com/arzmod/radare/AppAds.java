package com.arzmod.radare;

import com.unity3d.ads.IUnityAdsLoadListener;
import com.unity3d.ads.IUnityAdsShowListener;
import com.unity3d.ads.IUnityAdsInitializationListener;
import com.unity3d.ads.UnityAds;
import com.unity3d.ads.metadata.MetaData;
import com.unity3d.services.banners.BannerView;
import com.unity3d.services.banners.UnityBannerSize;
import com.unity3d.services.banners.BannerErrorInfo;
import android.util.Log;
import android.content.Context;
import com.arzmod.radare.AppContext;
import android.app.Activity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.view.Gravity;
import android.view.View;


public class AppAds {
    private static boolean initialized = false;
    private static BannerView banner;

    public static void initializeAndShow() {
        AppContext.getMainEntrenchActivity(new AppContext.MainEntrenchActivityCallback() {
            @Override
            public void onResult(Activity activity) {
                if(initialized) showBanner(activity);
                else
                {
                    Log.d("arzmod-unityads-module", "Activity: " + activity.getClass().getName());
                    try {
                        UnityAds.initialize(activity, "5917151", false, new IUnityAdsInitializationListener() {
                            @Override
                            public void onInitializationComplete() {
                                Log.d("arzmod-unityads-module", "Unity Ads initialized successfully");
                                showBanner(activity);
                                initialized = true;
                            }

                            @Override
                            public void onInitializationFailed(UnityAds.UnityAdsInitializationError error, String message) {
                                Log.e("arzmod-unityads-module", "Unity Ads initialization failed: " + message);
                            }
                        });
                    } catch (Exception e) {
                        Log.e("arzmod-unityads-module", "Error initializing Unity Ads: " + e.getMessage());
                    }
                }
            }
        });
       
    }

    public static void showBanner(Activity activity) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (banner == null) {
                    banner = new BannerView(activity, "Banner_Android", new UnityBannerSize(320, 50));
                    banner.setListener(new BannerView.IListener() {
                        @Override
                        public void onBannerLoaded(BannerView b) {
                            Log.d("arzmod-unityads-module", "Banner loaded");
                            try {
                                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                                        FrameLayout.LayoutParams.WRAP_CONTENT,
                                        FrameLayout.LayoutParams.WRAP_CONTENT
                                );
                                lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                                activity.addContentView(b, lp);
                            } catch (Exception e) {
                                Log.e("arzmod-unityads-module", "Attach banner failed: " + e.getMessage());
                            }
                        }

                        @Override
                        public void onBannerFailedToLoad(BannerView b, BannerErrorInfo errorInfo) {
                            Log.e("arzmod-unityads-module", "Banner load failed: " + errorInfo.errorMessage);
                        }

                        @Override
                        public void onBannerClick(BannerView b) {
                            Log.d("arzmod-unityads-module", "Banner clicked");
                        }

                        @Override
                        public void onBannerLeftApplication(BannerView b) {
                            Log.d("arzmod-unityads-module", "Banner left application");
                        }
                    });
                } else {
                    if (banner.getParent() == null) {
                        try {
                            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                                    FrameLayout.LayoutParams.WRAP_CONTENT,
                                    FrameLayout.LayoutParams.WRAP_CONTENT
                            );
                            lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                            activity.addContentView(banner, lp);
                        } catch (Exception e) {
                            Log.e("arzmod-unityads-module", "Reattach banner failed: " + e.getMessage());
                        }
                    }
                    banner.setVisibility(View.VISIBLE);
                }

                try {
                    banner.load();
                } catch (Exception e) {
                    Log.e("arzmod-unityads-module", "Banner load() error: " + e.getMessage());
                }
            }
        });
    }

    public static void hideBanner() {
        if (banner != null) {
            try {
                banner.setVisibility(View.GONE);
                ViewGroup parent = (ViewGroup) banner.getParent();
                if (parent != null) parent.removeView(banner);
                banner = null;
            } catch (Exception e) {
                Log.e("arzmod-unityads-module", "Hide banner failed: " + e.getMessage());
            }
        }
    }
}