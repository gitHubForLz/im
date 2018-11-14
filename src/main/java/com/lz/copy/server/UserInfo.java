package com.lz.copy.server;


import io.netty.channel.Channel;

import java.rmi.server.UID;

public class UserInfo {
    private String userId;

    private String addr;
    private Channel channel;

    public String getAddr() {
        return addr;
    }

    public void setAddr(String addr) {
        this.addr = addr;
    }

    public Channel getChannel() {
        return channel;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }
}
