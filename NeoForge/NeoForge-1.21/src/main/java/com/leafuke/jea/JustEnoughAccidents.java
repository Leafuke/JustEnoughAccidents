package com.leafuke.jea;

import com.leafuke.jea.runtime.JeaRuntime;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(JustEnoughAccidents.MOD_ID)
public final class JustEnoughAccidents {
    public static final String MOD_ID = "just_enough_accidents";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public JustEnoughAccidents(IEventBus modBus) {
        JeaRuntime.register(modBus);
    }
}
