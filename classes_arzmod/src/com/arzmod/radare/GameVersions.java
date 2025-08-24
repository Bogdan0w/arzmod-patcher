package com.arzmod.radare;

import java.util.HashMap;
import java.util.Map;
import com.arizona.game.BuildConfig;

public class GameVersions {
    private static Map<Integer, String> versions = new HashMap<>();

    static {
        versions.put(BuildConfig.VERSION_CODE, BuildConfig.VERSION_CODE + " actual");
    }

    public static Map<Integer, String> getVersions() {
        return versions;
    }

    public static boolean isVersionSupported(int version) {
        return versions.containsKey(version);
    }

    public static int getLatestVersion() {
        return BuildConfig.VERSION_CODE;
    }
} 