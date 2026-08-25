package com.mvpitsolutions.rooftopcamera;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class RooftopCameraPluginTest
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(RooftopCameraPlugin.class);
        RuneLite.main(args);
    }
}
