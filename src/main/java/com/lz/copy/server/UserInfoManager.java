package com.lz.copy.server;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;


import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class UserInfoManager {
    private static ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock(true);
    private static ConcurrentMap<Channel, UserInfo> userInfos = new ConcurrentHashMap<Channel, UserInfo>();

    /**
     * 登录注册 channel
     */
    public static void addChannel(Channel channel, String uid) {
        String remoteAddr = channel.remoteAddress().toString();
        UserInfo userInfo = new UserInfo();
        userInfo.setUserId(uid);
        userInfo.setAddr(remoteAddr);
        userInfo.setChannel(channel);
        userInfos.put(channel, userInfo);
    }

    /**
     * 普通消息
     *
     * @param message
     */
    public static void broadcastMess(String uid, String message, String sender) {

        try {
            rwLock.readLock().lock();
            Set<Channel> keySet = userInfos.keySet();
            for (Channel ch : keySet) {
                UserInfo userInfo = userInfos.get(ch);
                if (!userInfo.getUserId().equals(uid)) continue;
                String backmessage = sender + "," + message;
                ch.writeAndFlush(new TextWebSocketFrame(backmessage)); /*  responseToClient(ch,message);*/
            }
        } finally {
            rwLock.readLock().unlock();

        }
    }

    public static void removeChannel(io.netty.channel.Channel channel) {
    }

    public static UserInfo getUserInfo(io.netty.channel.Channel channel) {
        return null;
    }
}