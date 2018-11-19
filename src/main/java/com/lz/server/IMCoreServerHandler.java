package com.lz.server;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.stream.ChunkedFile;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.GlobalEventExecutor;
import io.netty.util.internal.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.activation.MimetypesFileTypeMap;
import java.io.*;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class IMCoreServerHandler extends SimpleChannelInboundHandler<Object> {

    public static final String HTTP_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz";
    public static final String HTTP_DATE_GMT_TIMEZONE = "GMT";
    public static final int HTTP_CACHE_SECONDS = 60;
    public static Properties pro;
    public static String LINUX_WEBAPP_PATH ="/home/lz/im/webapp";
    public static String WIN_WEBAPP_PATH ="E:\\gihubSource\\TestDir\\im\\src\\main\\resources\\webapp";
    // public static String WIN_PASS_CONF=""  ;
    public static String LINUX_PASS_CONF = "/home/lz/im/pass.conf";

    static {
        try {
            FileInputStream in = new FileInputStream(new File(LINUX_PASS_CONF));
            pro = new Properties();
            pro.load(in);

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    {
        //LINUX_WEBAPP_PATH=WIN_WEBAPP_PATH;
    }

    public void requestChannelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        if (!request.decoderResult().isSuccess()) {
            sendError(ctx, BAD_REQUEST);
            return;
        }

        if (request.method() != GET) {
            sendError(ctx, METHOD_NOT_ALLOWED);
            return;
        }

        final String uri = request.uri();
        final String path = sanitizeUri(uri);
        if (path == null) {
            sendError(ctx, FORBIDDEN);
            return;
        }

        File file = new File(path);
        if (file.isHidden() || !file.exists()) {
            sendError(ctx, NOT_FOUND);
            return;
        }

        if (file.isDirectory()) {
            if (uri.endsWith("/")) {
                sendListing(ctx, file, uri);
            } else {
                sendRedirect(ctx, uri + '/');
            }
            return;
        }

        if (!file.isFile()) {
            sendError(ctx, FORBIDDEN);
            return;
        }

        // Cache Validation
        // String ifModifiedSince = request.headers().get(HttpHeaderNames.IF_MODIFIED_SINCE);
        // if (ifModifiedSince != null && !ifModifiedSince.isEmpty()) {
        //     SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
        //     Date ifModifiedSinceDate = dateFormatter.parse(ifModifiedSince);
        //
        //     // Only compare up to the second because the datetime format we send to the client
        //     // does not have milliseconds
        //     long ifModifiedSinceDateSeconds = ifModifiedSinceDate.getTime() / 1000;
        //     long fileLastModifiedSeconds = file.lastModified() / 1000;
        //     if (ifModifiedSinceDateSeconds == fileLastModifiedSeconds) {
        //         sendNotModified(ctx);
        //         return;
        //     }
        // }

        RandomAccessFile raf;
        try {
            raf = new RandomAccessFile(file, "r");
        } catch (FileNotFoundException ignore) {
            sendError(ctx, NOT_FOUND);
            return;
        }
        long fileLength = raf.length();

        HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
        HttpUtil.setContentLength(response, fileLength);
        setContentTypeHeader(response, file);
        setDateAndCacheHeaders(response, file);
        if (HttpUtil.isKeepAlive(request)) {
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
        }

        // Write the initial line and the header.
        ctx.write(response);

        // Write the content.
        ChannelFuture sendFileFuture;
        ChannelFuture lastContentFuture;
        if (ctx.pipeline().get(SslHandler.class) == null) {
            sendFileFuture =
                    ctx.write(new DefaultFileRegion(raf.getChannel(), 0, fileLength), ctx.newProgressivePromise());
            // Write the end marker.
            lastContentFuture = ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT);
        } else {
            sendFileFuture =
                    ctx.writeAndFlush(new HttpChunkedInput(new ChunkedFile(raf, 0, fileLength, 8192)),
                            ctx.newProgressivePromise());
            // HttpChunkedInput will write the end marker (LastHttpContent) for us.
            lastContentFuture = sendFileFuture;
        }

        sendFileFuture.addListener(new ChannelProgressiveFutureListener() {
            public void operationProgressed(ChannelProgressiveFuture future, long progress, long total) {
                if (total < 0) { // total unknown
                    System.err.println(future.channel() + " Transfer progress: " + progress);
                } else {
                    System.err.println(future.channel() + " Transfer progress: " + progress + " / " + total);
                }
            }

            public void operationComplete(ChannelProgressiveFuture future) {
                System.err.println(future.channel() + " Transfer complete.");
            }
        });

        // Decide whether to close the connection or not.
        if (!HttpUtil.isKeepAlive(request)) {
            // Close the connection when the whole content is written out.
            lastContentFuture.addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        if (ctx.channel().isActive()) {
            sendError(ctx, INTERNAL_SERVER_ERROR);
        }
    }

    private static final Pattern INSECURE_URI = Pattern.compile(".*[<>&\"].*");

    private static String sanitizeUri(String uri) {
        // Decode the path.
        try {
            uri = URLDecoder.decode(uri, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new Error(e);
        }

        if (uri.isEmpty() || uri.charAt(0) != '/') {
            return null;
        }

        // Convert file separators.
        uri = uri.replace('/', File.separatorChar);

        // Simplistic dumb security check.
        // You will have to do something serious in the production environment.
        if (uri.contains(File.separator + '.') ||
                uri.contains('.' + File.separator) ||
                uri.charAt(0) == '.' || uri.charAt(uri.length() - 1) == '.' ||
                INSECURE_URI.matcher(uri).matches()) {
            return null;
        }

        // Convert to absolute path.
        if (uri.equals("\\")) {
            uri += "test.html";
        }

        // String s = LINUX_WEBAPP_PATH + "webapp" + File.separator + uri;
        String s = LINUX_WEBAPP_PATH  + File.separator + uri;
        // String replace = s.replace("/", "\\");
         String replace = s.replace("\\", "/");
        System.out.println(replace);
        return replace;

    }

    private static final Pattern ALLOWED_FILE_NAME = Pattern.compile("[^-\\._]?[^<>&\\\"]*");

    private static void sendListing(ChannelHandlerContext ctx, File dir, String dirPath) {
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, OK);
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/html; charset=UTF-8");

        StringBuilder buf = new StringBuilder()
                .append("<!DOCTYPE html>\r\n")
                .append("<html><head><meta charset='utf-8' /><title>")
                .append("Listing of: ")
                .append(dirPath)
                .append("</title></head><body>\r\n")

                .append("<h3>Listing of: ")
                .append(dirPath)
                .append("</h3>\r\n")

                .append("<ul>")
                .append("<li><a href=\"../\">..</a></li>\r\n");

        for (File f : dir.listFiles()) {
            if (f.isHidden() || !f.canRead()) {
                continue;
            }

            String name = f.getName();
            if (!ALLOWED_FILE_NAME.matcher(name).matches()) {
                continue;
            }

            buf.append("<li><a href=\"")
                    .append(name)
                    .append("\">")
                    .append(name)
                    .append("</a></li>\r\n");
        }

        buf.append("</ul></body></html>\r\n");
        ByteBuf buffer = Unpooled.copiedBuffer(buf, CharsetUtil.UTF_8);
        response.content().writeBytes(buffer);
        buffer.release();

        // Close the connection as soon as the error message is sent.
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private static void sendRedirect(ChannelHandlerContext ctx, String newUri) {
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, FOUND);
        response.headers().set(HttpHeaderNames.LOCATION, newUri);

        // Close the connection as soon as the error message is sent.
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private static void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
        FullHttpResponse response = new DefaultFullHttpResponse(
                HTTP_1_1, status, Unpooled.copiedBuffer("Failure: " + status + "\r\n", CharsetUtil.UTF_8));
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");

        // Close the connection as soon as the error message is sent.
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    /**
     * When file timestamp is the same as what the browser is sending up, send a "304 Not Modified"
     *
     * @param ctx Context
     */
    private static void sendNotModified(ChannelHandlerContext ctx) {
        FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, NOT_MODIFIED);
        setDateHeader(response);

        // Close the connection as soon as the error message is sent.
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    /**
     * Sets the Date header for the HTTP response
     *
     * @param response HTTP response
     */
    private static void setDateHeader(FullHttpResponse response) {
        SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
        dateFormatter.setTimeZone(TimeZone.getTimeZone(HTTP_DATE_GMT_TIMEZONE));

        Calendar time = new GregorianCalendar();
        response.headers().set(HttpHeaderNames.DATE, dateFormatter.format(time.getTime()));
    }

    /**
     * Sets the Date and Cache headers for the HTTP Response
     *
     * @param response    HTTP response
     * @param fileToCache file to extract content type
     */
    private static void setDateAndCacheHeaders(HttpResponse response, File fileToCache) {
        SimpleDateFormat dateFormatter = new SimpleDateFormat(HTTP_DATE_FORMAT, Locale.US);
        dateFormatter.setTimeZone(TimeZone.getTimeZone(HTTP_DATE_GMT_TIMEZONE));

        // Date header
        Calendar time = new GregorianCalendar();
        response.headers().set(HttpHeaderNames.DATE, dateFormatter.format(time.getTime()));

        // Add cache headers
        time.add(Calendar.SECOND, HTTP_CACHE_SECONDS);
        response.headers().set(HttpHeaderNames.EXPIRES, dateFormatter.format(time.getTime()));
        response.headers().set(HttpHeaderNames.CACHE_CONTROL, "private, max-age=" + HTTP_CACHE_SECONDS);
        response.headers().set(
                HttpHeaderNames.LAST_MODIFIED, dateFormatter.format(new Date(fileToCache.lastModified())));
    }

    /**
     * Sets the content type header for the HTTP Response
     *
     * @param response HTTP response
     * @param file     file to extract content type
     */
    private static void setContentTypeHeader(HttpResponse response, File file) {
        MimetypesFileTypeMap mimeTypesMap = new MimetypesFileTypeMap();
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, mimeTypesMap.getContentType(file.getPath()));
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof FullHttpRequest) {
            FullHttpRequest request = (FullHttpRequest) msg;
            requestChannelRead0(ctx, request);
        } else if (msg instanceof WebSocketFrame) {
            WebSocketFrame request = (WebSocketFrame) msg;
            webSocketChannelread0(ctx, request);
            // } else if(msg instanceof RedisMessage ){
            //     RedisMessage msg0= (RedisMessage) msg;
            //     redisChannelRead(ctx,msg0);
        }
    }

    public static ChannelGroup channels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    public static Map<String, Channel> userOnlineMap = new ConcurrentHashMap(30);
    Logger logger = LoggerFactory.getLogger(this.getClass());

    @SuppressWarnings("all")
    private void webSocketChannelread0(ChannelHandlerContext ctx, WebSocketFrame msg0) {

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
            password = "[" + uInfo[1];
            // TODO: 2018/11/16 完善reis or mysql
            String data = pro.getProperty(username);
            if (StringUtil.isNullOrEmpty(data) || !data.equals(password)) {
                result = "[SYSTEM]+[ONLINE]+[" + new Date() + "]+" + username + "+[0]";
                logger.info(ip + result);

            } else {
                userOnlineMap.put(username, incoming);
                result = "[SYSTEM]+[ONLINE]+[" + new Date() + "]+" + username + "+[1]";
                logger.info(ip + result);
            }
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

    /**
     * redis
     */
  /*@SuppressWarnings("all")
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        String[] commands = ((String) msg).split("\\s+");
        List<RedisMessage> children = new ArrayList<RedisMessage>(commands.length);
        for (String cmdString : commands) {
            children.add(new FullBulkStringRedisMessage(ByteBufUtil.writeUtf8(ctx.alloc(), cmdString)));
        }
        RedisMessage request = new ArrayRedisMessage(children);
        ctx.write(request, promise);
    }
    public void redisChannelRead(ChannelHandlerContext ctx, Object msg) {
        RedisMessage redisMessage = (RedisMessage) msg;

        ReferenceCountUtil.release(redisMessage);
    }
*/
}

