package com.ldp.nettydemo.netty;

import com.ldp.nettydemo.netty.codec.NettyMessageDecoder;
import com.ldp.nettydemo.netty.codec.NettyMessageEncoder;
import com.ldp.nettydemo.netty.handler.HeartBeatReqHandler;
import com.ldp.nettydemo.netty.handler.LoginAuthReqHandler;
import com.ldp.nettydemo.netty.util.NettyConstant;

import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;

/**
 * Created by ldp on 2015/11/1.
 */
public class NettyClient {
    private ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
    private NioEventLoopGroup group = new NioEventLoopGroup();
    public void connect(String host, int port) throws Exception {
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group).
                    channel(NioSocketChannel.class).
                    option(ChannelOption.TCP_NODELAY, true).
                    handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel socketChannel) throws Exception {
                            socketChannel.pipeline().addLast(new NettyMessageDecoder(1024 * 1024, 4, 4))
                                    .addLast("MessageEncoder", new NettyMessageEncoder())
                                    .addLast("ReadTimeoutHandler", new ReadTimeoutHandler(50))
                                    .addLast("LoginAuthHandler", new LoginAuthReqHandler())
                                    .addLast("HeartBeatHandler", new HeartBeatReqHandler());
                        }
                    });
            //发起异步连接操作
            ChannelFuture future = bootstrap.connect(new InetSocketAddress(host, port)).sync();
            future.channel().closeFuture().sync();
        } finally {
            // 所有资源释放完成之后，清空资源，再次发起重连操作
            executor.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        TimeUnit.SECONDS.sleep(1);
                        connect(NettyConstant.REMOTEIP, NettyConstant.PORT);    //发起重连操作
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        }
    }
}
