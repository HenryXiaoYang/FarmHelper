package com.jelly.farmhelperv3;

import net.fabricmc.api.ClientModInitializer;

/** Keep dependency failures out of vanilla input and connection hooks. */
public final class FarmHelperClient implements ClientModInitializer {
    public static volatile boolean ready;
    public static String failure = "FarmHelper has not finished starting.";

    @Override public void onInitializeClient() {
        try {
            Class.forName("baritone.api.event.listener.IGameEventListener", false, getClass().getClassLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            failure = "FarmHelper requires baritone-api-fabric-1.18.0.jar. Remove the standalone Baritone jar and install the API variant.";
            throw new IllegalStateException(failure, e);
        }
        try {
            var initializer = (ClientModInitializer) Class.forName("com.jelly.farmhelperv3.FarmHelper").getDeclaredConstructor().newInstance();
            initializer.onInitializeClient();
            ready = true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            failure = "FarmHelper failed to initialize. See latest.log for the cause.";
            throw new IllegalStateException(failure, e);
        }
    }
}
