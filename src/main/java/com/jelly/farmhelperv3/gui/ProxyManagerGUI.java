package com.jelly.farmhelperv3.gui;

import com.jelly.farmhelperv3.config.FarmHelperConfig;
import com.jelly.farmhelperv3.feature.impl.Proxy;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.net.URI;

public final class ProxyManagerGUI extends Screen {
    private final Screen parent;
    private boolean enabled = FarmHelperConfig.proxyEnabled;
    private Proxy.ProxyType type = FarmHelperConfig.proxyType;
    private EditBox host, username, password;
    private String error = "";
    public ProxyManagerGUI(Screen parent) { super(Component.literal("FarmHelper V3 proxy")); this.parent = parent; }
    @Override protected void init() {
        int x = width / 2 - 140;
        addRenderableWidget(Button.builder(Component.literal(enabled ? "Enabled" : "Disabled"), b -> { enabled = !enabled; b.setMessage(Component.literal(enabled ? "Enabled" : "Disabled")); }).bounds(x, 45, 135, 20).build());
        addRenderableWidget(Button.builder(Component.literal(type.name()), b -> { type = type == Proxy.ProxyType.HTTP ? Proxy.ProxyType.SOCKS : Proxy.ProxyType.HTTP; b.setMessage(Component.literal(type.name())); }).bounds(x + 145, 45, 135, 20).build());
        host = field(x, 85, "Host:port", FarmHelperConfig.proxyAddress);
        username = field(x, 120, "Username", FarmHelperConfig.proxyUsername);
        password = field(x, 155, "Password", FarmHelperConfig.proxyPassword);
        password.addFormatter((text, position) -> net.minecraft.util.FormattedCharSequence.forward("•".repeat(text.length()), net.minecraft.network.chat.Style.EMPTY));
        addRenderableWidget(Button.builder(Component.literal("Save"), b -> {
            try {
                if (enabled) {
                    URI address = URI.create("http://" + host.getValue());
                    if (address.getHost() == null || address.getPort() < 1 || address.getPort() > 65535) throw new IllegalArgumentException();
                }
                Proxy.getInstance().setProxy(enabled, host.getValue(), type, username.getValue(), password.getValue());
                onClose();
            } catch (RuntimeException e) { error = "Enter a valid host:port and check the configuration directory is writable."; }
        }).bounds(x, height - 30, 135, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose()).bounds(x + 145, height - 30, 135, 20).build());
    }
    private EditBox field(int x, int y, String label, String value) {
        EditBox field = addRenderableWidget(new EditBox(font, x, y, 280, 20, Component.literal(label)));
        field.setHint(Component.literal(label)); field.setMaxLength(1024); field.setValue(value == null ? "" : value); return field;
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int x, int y, float delta) {
        super.extractRenderState(graphics, x, y, delta);
        graphics.centeredText(font, title, width / 2, 20, 0xffffffff);
        graphics.centeredText(font, error, width / 2, height - 45, 0xffff5555);
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
}
