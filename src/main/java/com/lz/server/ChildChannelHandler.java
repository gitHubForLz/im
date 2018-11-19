package com.lz.server;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler;
import io.netty.handler.codec.redis.RedisArrayAggregator;
import io.netty.handler.codec.redis.RedisBulkStringAggregator;
import io.netty.handler.codec.redis.RedisDecoder;
import io.netty.handler.codec.redis.RedisEncoder;
import io.netty.handler.stream.ChunkedWriteHandler;

/**
 * ClassName:ChildChannelHandler
 * Function: TODO ADD FUNCTION.
 *
 * @author hxy
 */
public class ChildChannelHandler extends ChannelInitializer<SocketChannel> {
    @Override
    protected void initChannel(SocketChannel e) throws Exception {
// 设置30秒没有读到数据，则触发一个READER_IDLE事件。
// pipeline.addLast(new IdleStateHandler(30, 0, 0));
// HttpServerCodec：将请求和应答消息解码为HTTP消息
        e.pipeline().addLast("http-codec", new HttpServerCodec());
// HttpObjectAggregator：将HTTP消息的多个部分合成一条完整的HTTP消息
        e.pipeline().addLast("aggregator", new HttpObjectAggregator(65536));

        // e.pipeline().addLast("text", new TextWebSocketFrameHandler());
        e.pipeline().addLast(new WebSocketServerProtocolHandler("/webssss", null, true));
        e.pipeline().addLast(new WebSocketServerCompressionHandler());
        e.pipeline().addLast("socket", new IMCoreServerHandler());

        // redis全家桶
        e.pipeline().addLast(new RedisDecoder());
        e.pipeline().addLast(new RedisBulkStringAggregator());
        e.pipeline().addLast(new RedisArrayAggregator());
        e.pipeline().addLast(new RedisEncoder());

        // ChunkedWriteHandler：向客户端发送HTML5文件
        e.pipeline().addLast("http-chunked", new ChunkedWriteHandler());
        // e.pipeline().addLast("static-file", new HttpStaticFileServerHandler());
    }
}

