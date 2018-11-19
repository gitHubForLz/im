package com.lz.x;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class WebSocketServerHandler extends SimpleChannelInboundHandler<WebSocketFrame> {
    public static ChannelGroup channels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    public static Map<String, Channel> userOnlineMap = new ConcurrentHashMap(30);
    Logger logger = LoggerFactory.getLogger(this.getClass());

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame msg0) throws Exception {

        Channel incoming = ctx.channel();
        channels.add(incoming);
        ChannelId id = incoming.id();
        String username = null;
        String password = null;
        TextWebSocketFrame msg = (TextWebSocketFrame) msg0;
        String text = msg.text();
        String[] parmas = text.split("\\+");
        // [CLIENT][LOGIN][Date][[id]][messsage][count]
        if (parmas == null) {
            return;
        }
        String ip = incoming.remoteAddress().toString();

        String command = parmas[1];
        String result = null;
        if ("[CONNECT]".equals(command)) {
            result = "[SYSTEM]+[CONNECT]+[" + new Date() + "]+[" + null + "]+[" + "connect sucess" + "]";
        } else if ("[LOGIN]".equals(command)) {
            String[] uInfo = parmas[5].split(":");
            username = uInfo[0] + "]";
            password = uInfo[1] + "]";
            // TODO: 2018/11/16 完善reis or mysql
            userOnlineMap.put(username, incoming);
            result = "[SYSTEM]+[ONLINE]+[" + new Date() + "]+" + username + "+[" + "login sucess" + "]";
            logger.info(ip + result);
        }
        if ("[SEND]".equals(command)) {
            // [CLIENT]+[SEND]+[DATE]+[id]+[message]+[toId]/[ALL]
            String parma = parmas[5];
            if (parma == null || !"[ALL]".equals(parma)) {
                Channel channel = userOnlineMap.get(parma);
                channel.writeAndFlush(new TextWebSocketFrame("[" + ip + "]" + msg.text()));
            } else {
                for (Channel channel : channels) {
                    if (channel != incoming) {
                        channel.writeAndFlush(new TextWebSocketFrame("[" + ip + "]" + msg.text()));
                    }
                }
            }
            logger.info(ip + msg.text());

        } else if ("[ONLINELIST]".equals(command)) {
            Set<String> userList = userOnlineMap.keySet();
            result = "[SYSTEM]+[ONLINELIST]+[" + new Date() + "]+[" + username + "]+" + userList.toString().replaceAll(" ", "");
            logger.info(result);
        } else if ("LOGOFF".equals(command)) {

        }
        // 触发count[SYSTEM][ONLINECOUNT][Date][null][message]
        int count = userOnlineMap.size();
        ctx.channel().writeAndFlush(new TextWebSocketFrame("[SYSTEM]+[ONLINECOUNT]+[" + new Date() + "]+[" + null + "]+[" + count + "]"));
        logger.info("[ONLINECOUNT]" + count);
        //
        if (result != null) {
            ctx.channel().writeAndFlush(new TextWebSocketFrame(result));
        }

    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Channel channel = ctx.channel();
        Collection<Channel> values = userOnlineMap.values();
        if (values.contains(channel) == true) {
            values.remove(channel);
            logger.info(channel.remoteAddress().toString() + "[logoff]");
        }
        super.channelInactive(ctx);
    }
}
