package com.leafuke.jea;

import com.leafuke.jea.runtime.JeaRuntime;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class JustEnoughAccidents implements ModInitializer {
    public static final String MOD_ID = "just_enough_accidents";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        JeaRuntime.register();
    }
}
