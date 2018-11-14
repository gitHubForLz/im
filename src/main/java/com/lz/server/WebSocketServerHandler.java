package com.lz.server;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.util.concurrent.GlobalEventExecutor;

import java.util.Date;

public class WebSocketServerHandler extends SimpleChannelInboundHandler<WebSocketFrame> {
    public static ChannelGroup channels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame msg0) throws Exception {
        TextWebSocketFrame msg = (TextWebSocketFrame) msg0;
        String text = msg.text();
        String[] parmas = text.split("\\+");
        // [CLIENT][LOGIN][Date][[id]][messsage]
        if (parmas == null) {
            return;
        }
        String command = parmas[1];
            String result = null;
        if("[CONNECT]".equals(command)){
            result = "[SYSTEM]+[CONNECT]+[" + new Date() + "]+[" + null + "]+[" + "connect sucess" + "]";
        }
        else if ("[LOGIN]".equals(command)) {
            String[] uInfo = parmas[5].split(":");
            String username = uInfo[0];
            result = "[SYSTEM]+[ONLINE]+[" + new Date() + "]+[" + username + "]+[" + "login sucess" + "]";
        }else if("[ONLINE]".equals(command)){

        }else  if("[SEND]".equals(command)){
            // TODO: 2018/11/14 发送gei所有人
        }
        ctx.channel().writeAndFlush(new TextWebSocketFrame(result));

        Channel incoming = ctx.channel();
        for (Channel channel : channels) {
            if (channel != incoming) {
                channel.writeAndFlush(new TextWebSocketFrame("[" + incoming.remoteAddress() + "]" + msg.text()));
            } else {
                channel.writeAndFlush(new TextWebSocketFrame("[you]" + msg.text()));
            }
        }
    }
}
