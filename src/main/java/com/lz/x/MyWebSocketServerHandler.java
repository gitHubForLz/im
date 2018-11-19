package com.lz.x;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.DefaultFileRegion;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedFile;
import io.netty.util.AttributeKey;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static io.netty.handler.codec.http.HttpResponseStatus.OK;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * ClassName:MyWebSocketServerHandler Function: TODO ADD FUNCTION.
 *
 * @author hxy
 */
public class MyWebSocketServerHandler extends SimpleChannelInboundHandler<Object> {
    private static final Logger logger = Logger.getLogger(WebSocketServerHandshaker.class.getName());
    private WebSocketServerHandshaker handshaker;
    private String path;

    {

        path = this.getClass().getResource("/webapp/").getPath();
    }

    /**
     * channel 通道 action 活跃的 当客户端主动链接服务端的链接后，这个通道就是活跃的了。也就是客户端与服务端建立了通信通道并且可以传输数据
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
// 添加

        Global.group.add(ctx.channel());
        System.out.println("客户端与服务端连接开启：" + ctx.channel().remoteAddress().toString());

    }

    /**
     * channel 通道 Inactive 不活跃的 当客户端主动断开服务端的链接后，这个通道就是不活跃的。也就是说客户端与服务端关闭了通信通道并且不可以传输数据
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
// 移除
        Global.group.remove(ctx.channel());
        System.out.println("客户端与服务端连接关闭：" + ctx.channel().remoteAddress().toString());
    }

    /**
     * 接收客户端发送的消息 channel 通道 Read 读 简而言之就是从通道中读取数据，也就是服务端接收客户端发来的数据。但是这个数据在不进行解码时它是ByteBuf类型的
     */
    protected void messageReceived(ChannelHandlerContext ctx, Object msg) throws Exception {
        // 传统的HTTP接入
        if (msg instanceof FullHttpRequest) {
            // handleHttpRequest(ctx, ((FullHttpRequest) msg));
            // requestHandler(ctx, ((FullHttpRequest) msg));
            fileHandler(ctx,((FullHttpRequest) msg));
            // WebSocket接入
        } else if (msg instanceof WebSocketFrame) {
            System.out.println(handshaker.uri());
            if ("anzhuo".equals(ctx.attr(AttributeKey.valueOf("type")).get())) {
                handlerWebSocketFrame(ctx, (WebSocketFrame) msg);
            } else {
                handlerWebSocketFrame2(ctx, (WebSocketFrame) msg);
            }
        }
    }

    /**
     * channel 通道 Read 读取 Complete 完成 在通道读取完成后会在这个方法里通知，对应可以做刷新操作 ctx.flush()
     */
    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }

    private void handlerWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame) {
// 判断是否关闭链路的指令
        if (frame instanceof CloseWebSocketFrame) {
            System.out.println(1);
            handshaker.close(ctx.channel(), (CloseWebSocketFrame) frame.retain());
            return;
        }
// 判断是否ping消息
        if (frame instanceof PingWebSocketFrame) {
            ctx.channel().write(new PongWebSocketFrame(frame.content().retain()));
            return;
        }
// 本例程仅支持文本消息，不支持二进制消息
        if (!(frame instanceof TextWebSocketFrame)) {
            System.out.println("本例程仅支持文本消息，不支持二进制消息");
            throw new UnsupportedOperationException(
                    String.format("%s frame types not supported", frame.getClass().getName()));
        }
// 返回应答消息
        String request = ((TextWebSocketFrame) frame).text();
        System.out.println("服务端收到：" + request);
        if (logger.isLoggable(Level.FINE)) {
            logger.fine(String.format("%s received %s", ctx.channel(), request));
        }
        TextWebSocketFrame tws = new TextWebSocketFrame(new Date().toString() + ctx.channel().id() + "：" + request);
// 群发
        Global.group.writeAndFlush(tws);
// 返回【谁发的发给谁】
// ctx.channel().writeAndFlush(tws);
    }

    private void handlerWebSocketFrame2(ChannelHandlerContext ctx, WebSocketFrame frame) {
// 判断是否关闭链路的指令
        if (frame instanceof CloseWebSocketFrame) {
            handshaker.close(ctx.channel(), (CloseWebSocketFrame) frame.retain());
            return;
        }
// 判断是否ping消息
        if (frame instanceof PingWebSocketFrame) {
            ctx.channel().write(new PongWebSocketFrame(frame.content().retain()));
            return;
        }
// 本例程仅支持文本消息，不支持二进制消息
        if (!(frame instanceof TextWebSocketFrame)) {
            System.out.println("本例程仅支持文本消息，不支持二进制消息");
            throw new UnsupportedOperationException(
                    String.format("%s frame types not supported", frame.getClass().getName()));
        }
// 返回应答消息
        String request = ((TextWebSocketFrame) frame).text();
        System.out.println("服务端2收到：" + request);
        if (logger.isLoggable(Level.FINE)) {
            logger.fine(String.format("%s received %s", ctx.channel(), request));
        }
        TextWebSocketFrame tws = new TextWebSocketFrame(new Date().toString() + ctx.channel().id() + "：" + request);
// 群发
        Global.group.writeAndFlush(tws);
// 返回【谁发的发给谁】
// ctx.channel().writeAndFlush(tws);
    }

    private void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest req) {
        // 如果HTTP解码失败，返回HHTP异常
        if (!req.decoderResult().isSuccess() || (!"websocket".equals(req.headers().get("Upgrade")))) {
            try {
                Map<String, String> parse = parse(req);
                sendHttpResponse(ctx, req,
                        new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.wrappedBuffer("hello".getBytes())));
                return;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        // 获取url后置参数
        HttpMethod method = req.getMethod();
        String uri = req.getUri();
        QueryStringDecoder queryStringDecoder = new QueryStringDecoder(uri);
        Map<String, List<String>> parameters = queryStringDecoder.parameters();
        System.out.println(parameters.get("request").get(0));
        if (method == HttpMethod.GET && "/webssss".equals(uri)) {
            //....处理
            ctx.attr(AttributeKey.valueOf("type")).set("anzhuo");
        } else if (method == HttpMethod.GET && "/websocket".equals(uri)) {
            //...处理
            ctx.attr(AttributeKey.valueOf("type")).set("live");
        }
        // 构造握手响应返回，本机测试
        WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(
                "ws://" + req.headers().get(HttpHeaders.Names.HOST) + uri, null, false);
        handshaker = wsFactory.newHandshaker(req);
        if (handshaker == null) {
            WebSocketServerHandshakerFactory.sendUnsupportedWebSocketVersionResponse(ctx.channel());
        } else {
            handshaker.handshake(ctx.channel(), req);
        }
    }


    private static void sendHttpResponse(ChannelHandlerContext ctx, FullHttpRequest req, DefaultFullHttpResponse res) {
        res.headers().set("Content-Type", "text/plain");
        res.headers().setInt("Content-Type", res.content().readableBytes());
        // // 返回应答给客户端
        // if (res.status().code() != 200) {
        //     ByteBuf buf = Unpooled.copiedBuffer(res.getStatus().toString(), CharsetUtil.UTF_8);
        //     res.content().writeBytes(buf);
        //     buf.release();
        // }
        // // 如果是非Keep-Alive，关闭连接
        // ChannelFuture f = ctx.channel().writeAndFlush(res);
        // boolean keepAlive = HttpUtil.isKeepAlive(req);
        // if (!keepAlive || res.status().code() != 200) {
        //     f.addListener(ChannelFutureListener.CLOSE);
        // }
        boolean keepAlive = HttpUtil.isKeepAlive(req);

        if (!keepAlive) {
            ctx.write(res).addListener(ChannelFutureListener.CLOSE);
        } else {
            res.headers().set("Connection", "keep-alive");
            ctx.write(res);
        }

    }

    /**
     * exception 异常 Caught 抓住 抓住异常，当发生异常的时候，可以做一些相应的处理，比如打印日志、关闭链接
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace();
        ctx.close();
    }

    protected void channelRead0(ChannelHandlerContext channelHandlerContext, Object msg0) throws Exception {

        messageReceived(channelHandlerContext, msg0);

    }


    private void requestHandler(ChannelHandlerContext ctx, FullHttpRequest msg) {
        // TODO: 2018/11/14 添加request header
        // 解决登录问题
        String uri = msg.uri();
        HttpRequest req = (HttpRequest) msg;

        FullHttpResponse response = null;

        if ("/".equals(uri)) {
            response = new DefaultFullHttpResponse(HTTP_1_1, OK, Unpooled.wrappedBuffer("login success".getBytes()));
        } else {
            response = new DefaultFullHttpResponse(HTTP_1_1, HttpResponseStatus.BAD_GATEWAY, Unpooled.wrappedBuffer("sorry".getBytes()));
        }
        Map<String, String> parse = null;
        try {
            parse = parse(msg);
        } catch (IOException e) {
            e.printStackTrace();
        }
        // 取出用户名
        // if (!parse.get("username").equals("admin") || !parse.get("password").equals("123456")) {
        //     return;
        // }
        // TODO: 2018/11/14 连接数据库验证

        // TODO: 响应登陆成功跳转聊天页面

        boolean keepAlive = HttpUtil.isKeepAlive(req);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain");
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        if (!keepAlive) {
            ctx.write(response).addListener(ChannelFutureListener.CLOSE);
        } else {
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderNames.KEEP_ALIVE);
            ctx.write(response);
        }
        try {
            fileHandler(ctx, msg);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void fileHandler(ChannelHandlerContext ctx, FullHttpRequest req) throws IOException {
        RandomAccessFile raf = null;
        String uri = req.uri();
        String msg = null;
        if ("/".equals(uri)) {
            msg = "index.html";
        }
        long length = -1;
        try {
            raf = new RandomAccessFile(new File(path + msg), "r");
            length = raf.length();
        } catch (Exception e) {
            ctx.writeAndFlush("ERR: " + e.getClass().getSimpleName() + ": " + e.getMessage() + '\n');
            return;
        } finally {
            if (length < 0 && raf != null) {
                raf.close();
            }
        }

        ctx.write("OK: " + raf.length() + '\n');
        if (ctx.pipeline().get(SslHandler.class) == null) {
            // SSL not enabled - can use zero-copy file transfer.
            ctx.write(new DefaultFileRegion(raf.getChannel(), 0, length));
        } else {
            // SSL enabled - cannot use zero-copy file transfer.
            ctx.write(new ChunkedFile(raf));
        }
        ctx.writeAndFlush("\n");
    }


    public Map<String, String> parse(FullHttpRequest fullReq) throws IOException {
        HttpMethod method = fullReq.method();
        Map<String, String> parmMap = new HashMap<>();
        if (HttpMethod.GET == method) {
            // 是GET请求
            QueryStringDecoder decoder = new QueryStringDecoder(fullReq.uri());
            decoder.parameters().entrySet().forEach(entry -> {
                //entry.getValue()是一个List, 只取第一个元素
                parmMap.put(entry.getKey(), entry.getValue().get(0));
            });
        } else if (HttpMethod.POST == method) {
            // 是POST请求
            HttpPostRequestDecoder decoder = new HttpPostRequestDecoder(fullReq);
            decoder.offer(fullReq);
            List<InterfaceHttpData> parmList = decoder.getBodyHttpDatas();
            for (InterfaceHttpData parm : parmList) {
                Attribute data = (Attribute) parm;
                parmMap.put(data.getName(), data.getValue());
            }
        } else {

        }
        return parmMap;
    }

}