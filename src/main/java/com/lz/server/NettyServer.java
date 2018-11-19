package com.lz.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import javax.annotation.PostConstruct;

/**
 * ClassName:NettyServer 注解式随spring启动
 * Function: TODO ADD FUNCTION.
 *
 * @author hxy
 */
public class NettyServer {
    public static void main(String[] args) {
        new NettyServer().run();
    }

    @PostConstruct
    public void initNetty() {
        new Thread() {
            public void run() {
                new NettyServer().run();
            }
        }.start();
    }

    public void run() {
        System.out.println("===========================Netty端口启动:9999=======");
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workGroup = new NioEventLoopGroup();
        ServerBootstrap b = new ServerBootstrap();
        b.group(bossGroup, workGroup);
        b.channel(NioServerSocketChannel.class);
// ChildChannelHandler 对出入的数据进行的业务操作,其继承ChannelInitializer
        b.childHandler(new ChildChannelHandler());
        try {
            Channel ch = b.bind(9999).sync().channel();
            ch.closeFuture().sync();
            System.out.println("服务端开启等待客户端连接 ... ...");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            bossGroup.shutdownGracefully();
            workGroup.shutdownGracefully();
        }
    }
}