package com.jelly.farmhelperv3.event;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.lang.annotation.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.concurrent.CopyOnWriteArrayList;

/** Internal feature dispatch; Fabric callbacks and Minecraft mixins are its producers. */
public final class Events {
    public static final Events BUS = new Events();
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private record Listener(Object target, Method method, Class<?> type, SubscribeEvent subscription) {}

    public synchronized void register(Object target) {
        if (listeners.stream().anyMatch(l -> l.target == target)) return;
        for (Method method : target.getClass().getMethods()) {
            SubscribeEvent subscription = method.getAnnotation(SubscribeEvent.class);
            if (subscription == null) continue;
            if (method.getParameterCount() != 1 || !Event.class.isAssignableFrom(method.getParameterTypes()[0]))
                throw new IllegalArgumentException("Invalid event handler: " + method);
            listeners.add(new Listener(target, method, method.getParameterTypes()[0], subscription));
        }
        listeners.sort(Comparator.comparing(l -> l.subscription.priority()));
    }

    public void unregister(Object target) { listeners.removeIf(l -> l.target == target); }

    public boolean post(Event event) {
        if (this == BUS && !com.jelly.farmhelperv3.FarmHelperClient.ready) return false;
        for (Listener listener : listeners) {
            if (!listener.type.isInstance(event) || event.isCanceled() && !listener.subscription.receiveCanceled()) continue;
            try {
                listener.method.invoke(listener.target, event);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Feature handler failed: " + listener.method,
                        e instanceof InvocationTargetException invocation ? invocation.getCause() : e);
            }
        }
        return event.isCanceled();
    }

    public enum EventPriority { HIGHEST, HIGH, NORMAL, LOW, LOWEST }
    @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD)
    public @interface SubscribeEvent {
        EventPriority priority() default EventPriority.NORMAL;
        boolean receiveCanceled() default false;
    }
    @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE) @Inherited
    public @interface Cancelable {}
    public static class Event {
        private boolean canceled;
        public boolean isCanceled() { return canceled; }
        public void setCanceled(boolean canceled) { this.canceled = canceled; }
    }
    public static class TickEvent extends Event {
        public enum Phase { START, END }
        public final Phase phase;
        protected TickEvent(Phase phase) { this.phase = phase; }
        public static class ClientTickEvent extends TickEvent { public ClientTickEvent(Phase phase) { super(phase); } }
        public static class PlayerTickEvent extends TickEvent { public PlayerTickEvent(Phase phase) { super(phase); } }
    }
    public static class WorldEvent extends Event {
        public final Level world;
        protected WorldEvent(Level world) { this.world = world; }
        public static class Load extends WorldEvent { public Load(Level world) { super(world); } }
        public static class Unload extends WorldEvent { public Unload(Level world) { super(world); } }
    }
    public static class ClientChatReceivedEvent extends Event {
        public final byte type;
        public final Component message;
        public ClientChatReceivedEvent(byte type, Component message) { this.type = type; this.message = message; }
    }
    public static class RenderGameOverlayEvent extends Event {
        public enum ElementType { ALL }
        public final ElementType type = ElementType.ALL;
        public final GuiGraphicsExtractor graphics;
        public final float partialTicks;
        public RenderGameOverlayEvent(GuiGraphicsExtractor graphics, float partialTicks) { this.graphics = graphics; this.partialTicks = partialTicks; }
        public static class Post extends RenderGameOverlayEvent {
            public Post(GuiGraphicsExtractor graphics, float partialTicks) { super(graphics, partialTicks); }
        }
    }
    public static class RenderWorldLastEvent extends Event {
        public final float partialTicks;
        public RenderWorldLastEvent(float partialTicks) { this.partialTicks = partialTicks; }
    }
    public static class InputEvent extends Event {
        public static class KeyInputEvent extends InputEvent {}
    }
    public static class GuiScreenEvent extends Event {
        public final Screen gui;
        public GuiScreenEvent(Screen gui) { this.gui = gui; }
        public static class KeyboardInputEvent extends GuiScreenEvent {
            public KeyboardInputEvent(Screen gui) { super(gui); }
        }
    }
    public static class EntityViewRenderEvent extends Event {
        public static class CameraSetup extends EntityViewRenderEvent {
            public float yaw, pitch, roll;
            public CameraSetup(float yaw, float pitch, float roll) { this.yaw = yaw; this.pitch = pitch; this.roll = roll; }
        }
    }
    public static class PlayerEvent extends Event {
        public static class PlayerLoggedInEvent extends PlayerEvent {}
    }
    public static class FMLNetworkEvent extends Event {
        public static class ClientDisconnectionFromServerEvent extends FMLNetworkEvent {}
    }
    public static class LivingDeathEvent extends Event {
        public final LivingEntity entityLiving;
        public LivingDeathEvent(LivingEntity entity) { entityLiving = entity; }
    }
}
