package com.jelly.farmhelperv3.feature.impl;

import com.jelly.farmhelperv3.FarmHelper;
import com.jelly.farmhelperv3.config.FarmHelperConfig;



import java.net.*;
import java.util.Objects;

public class Proxy {
    private static Proxy instance;

    public static Proxy getInstance() {
        if (instance == null) {
            instance = new Proxy();
        }
        return instance;
    }

    public enum ProxyType {
        SOCKS,
        HTTP,
    }

    public void setProxy(boolean enabled, String host, ProxyType type, String username, String password) {
        FarmHelperConfig.proxyEnabled = enabled;
        FarmHelperConfig.proxyAddress = host;
        FarmHelperConfig.proxyType = type;
        if (!Objects.equals(username, "Username"))
            FarmHelperConfig.proxyUsername = username;
        if (!Objects.equals(password, "Password"))
            FarmHelperConfig.proxyPassword = password;
        FarmHelper.config.save();
    }

    public io.netty.handler.proxy.ProxyHandler createHandler() {
        URI uri = URI.create("http://" + FarmHelperConfig.proxyAddress);
        if (uri.getHost() == null || uri.getPort() < 1 || uri.getPort() > 65535) throw new IllegalArgumentException("Proxy must be host:port");
        InetSocketAddress address = new InetSocketAddress(uri.getHost(), uri.getPort());
        String username = FarmHelperConfig.proxyUsername;
        String password = FarmHelperConfig.proxyPassword;
        boolean authenticated = username != null && !username.isEmpty();
        if (FarmHelperConfig.proxyType == ProxyType.SOCKS)
            return authenticated ? new io.netty.handler.proxy.Socks5ProxyHandler(address, username, password) : new io.netty.handler.proxy.Socks5ProxyHandler(address);
        return authenticated ? new io.netty.handler.proxy.HttpProxyHandler(address, username, password) : new io.netty.handler.proxy.HttpProxyHandler(address);
    }
}
