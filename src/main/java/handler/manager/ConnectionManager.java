package handler.manager;

import io.netty.channel.Channel;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ConnectionManager {

    // key 可以是 userId 或者 channel.id().asLongText()
    private static final ConcurrentMap<String, Channel> CHANNEL_MAP = new ConcurrentHashMap<>();

    /**
     * 添加客户端
     */
    public static void add(String id, Channel channel) {
        CHANNEL_MAP.put(id, channel);
    }

    /**
     * 移除客户端
     */
    public static void remove(String id) {
        CHANNEL_MAP.remove(id);
    }

    /**
     * 根据 id 获取 Channel
     */
    public static Channel get(String id) {
        return CHANNEL_MAP.get(id);
    }

    /**
     * 获取所有 Channel
     */
    public static Collection<Channel> all() {
        return CHANNEL_MAP.values();
    }
}
