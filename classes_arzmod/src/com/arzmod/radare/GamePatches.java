package com.arzmod.radare;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import com.arzmod.radare.SampQuery;
import com.arzmod.radare.InitGamePatch;
import ru.mrlargha.commonui.elements.hud.presentation.Hud;
import ru.mrlargha.commonui.R;
import com.squareup.picasso.Picasso;

public class GamePatches {
    public static boolean isHudVisible(Hud hud, boolean isDefaultHud) {
        if(SettingsPatch.getSettingsKeyValue(SettingsPatch.IS_UNITY_ELEMENTS) && SettingsPatch.getSettingsKeyInt(SettingsPatch.HUD_TYPE) != 3) {
            hud.binding.leftMenu.getRoot().setVisibility(View.INVISIBLE);
            hud.binding.hudContainer.setVisibility(View.INVISIBLE);
            hud.binding.hudServerInfoContainer.setVisibility(View.VISIBLE);
            int[] server = InitGamePatch.getServer();
            if(server != null) hud.installHud(0, server[1], server[0] == 0 ? 1 : server[0] == 1 ? 2 : server[0] == 2 ? 4 : server[0] == 4 ? 0 : server[0], 0);
            return true;
        }
        return isDefaultHud;
    }

    public static void updateHudShield(Hud hud)
    {
        if(InitGamePatch.isCustomServer())
        {
            String[] ipPort = InitGamePatch.getCustomServer();
            if (ipPort != null) {
                String ip = ipPort[0];
                int port = Integer.parseInt(ipPort[1]);
                Log.d("arzmod-gamepatches-module", "ip: " + ip + " port: " + port);
                SampQuery.getServerName(ip, port, new SampQuery.ServerNameCallback() {
                    @Override
                    public void onResult(String serverName) {
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                Log.d("arzmod-gamepatches-module", "serverName: " + serverName);
                                hud.binding.hudServerShieldSite.setText("arzmod.com");
                                hud.binding.hudServerShieldName.setText(serverName);
                                Picasso.get().load("https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcQIclHyzkxY9JTTGH7uP-vTGI2-wcIBlTh5dA&s").placeholder(R.drawable.logo_phoenix).into(hud.binding.hudServerShieldLogo);
                            }
                        });
                    }
                });
            }
        }
    }
}