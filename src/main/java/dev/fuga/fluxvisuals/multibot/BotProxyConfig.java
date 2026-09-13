package dev.fuga.fluxvisuals.multibot;

import io.netty.channel.ChannelHandler;

import java.net.InetSocketAddress;
import java.util.Locale;

public final class BotProxyConfig {
    public enum Type {
        SOCKS5,
        HTTP
    }

    private final Type type;
    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String raw;

    public BotProxyConfig(Type type, String host, int port, String username, String password, String raw) {
        this.type = type;
        this.host = host;
        this.port = port;
        this.username = username == null ? "" : username;
        this.password = password == null ? "" : password;
        this.raw = raw;
    }

    public Type getType() {
        return type;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getRaw() {
        return raw;
    }

    public boolean hasAuth() {
        return !username.isBlank() || !password.isBlank();
    }

    public ChannelHandler createHandler() {
        InetSocketAddress proxyAddress = new InetSocketAddress(host, port);
        if (type == Type.HTTP) {
            return new BotHttpProxyHandler(proxyAddress, username, password);
        } else {
            return new BotSocks5Handler(proxyAddress, username, password);
        }
    }

    public static BotProxyConfig parse(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String clean = input.trim();
        Type type = Type.SOCKS5;
        if (clean.toLowerCase(Locale.ROOT).startsWith("http://")) {
            type = Type.HTTP;
            clean = clean.substring(7);
        } else if (clean.toLowerCase(Locale.ROOT).startsWith("https://")) {
            type = Type.HTTP;
            clean = clean.substring(8);
        } else if (clean.toLowerCase(Locale.ROOT).startsWith("socks5://")) {
            type = Type.SOCKS5;
            clean = clean.substring(9);
        } else if (clean.toLowerCase(Locale.ROOT).startsWith("socks4://")) {
            type = Type.SOCKS5;
            clean = clean.substring(9);
        }

        // Format 1: user:pass@host:port (e.g. proxyapi-v2.52e954812d0d439d:dQuVbstd1zsG5x5uDeZq9tcylGhwaEdL@93.123.85.144:1080)
        if (clean.contains("@")) {
            int atIndex = clean.lastIndexOf('@');
            String userPass = clean.substring(0, atIndex);
            String hostPort = clean.substring(atIndex + 1);

            String username = "";
            String password = "";
            int colonUser = userPass.indexOf(':');
            if (colonUser >= 0) {
                username = userPass.substring(0, colonUser);
                password = userPass.substring(colonUser + 1);
            } else {
                username = userPass;
            }

            int colonHost = hostPort.lastIndexOf(':');
            if (colonHost > 0) {
                String host = hostPort.substring(0, colonHost);
                try {
                    int port = Integer.parseInt(hostPort.substring(colonHost + 1));
                    return new BotProxyConfig(type, host, port, username, password, input.trim());
                } catch (NumberFormatException ignored) {}
            }
        }

        // Format 2: host:port:user:pass
        String[] parts = clean.split(":");
        if (parts.length == 4) {
            String host = parts[0];
            try {
                int port = Integer.parseInt(parts[1]);
                String username = parts[2];
                String password = parts[3];
                return new BotProxyConfig(type, host, port, username, password, input.trim());
            } catch (NumberFormatException ignored) {}
        } else if (parts.length == 2) {
            // Format 3: host:port
            String host = parts[0];
            try {
                int port = Integer.parseInt(parts[1]);
                return new BotProxyConfig(type, host, port, "", "", input.trim());
            } catch (NumberFormatException ignored) {}
        }

        return null;
    }

    @Override
    public String toString() {
        if (hasAuth()) {
            return type.name() + "://" + username + ":****@" + host + ":" + port;
        }
        return type.name() + "://" + host + ":" + port;
    }
}
