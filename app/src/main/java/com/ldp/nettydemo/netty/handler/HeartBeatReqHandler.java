package com.ldp.nettydemo.netty.handler;


import com.ldp.nettydemo.netty.struct.Header;
import com.ldp.nettydemo.netty.struct.NettyMessage;
import com.ldp.nettydemo.netty.util.MessageType;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;

/**
 * Created by ldp on 2015/11/1.
 */
public class HeartBeatReqHandler extends ChannelHandlerAdapter {
    private volatile ScheduledFuture<?> heartBeat;
    // 客户端连续N次没有收到服务端的pong消息 计数器
    private int unRecPongTimes = 0;
    // 定义客户端没有收到服务端的pong消息的最大次数
    private static final int MAX_UN_REC_PONG_TIMES = 3;
    // 隔N秒后重连
    private static final int RE_CONN_WAIT_SECONDS = 30;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg)
            throws Exception {
        NettyMessage message = (NettyMessage) msg;
        // 握手成功，主动发送心跳消息
        if (message.getHeader() != null
                && message.getHeader().getType() == MessageType.LOGIN_RESP
                .value()) {
            heartBeat = ctx.executor().scheduleAtFixedRate(
                    new HeartBeatReqHandler.HeartBeatTask(ctx), 0, RE_CONN_WAIT_SECONDS,
                    TimeUnit.SECONDS);
        } else if (message.getHeader() != null
                && message.getHeader().getType() == MessageType.HEARTBEAT_RESP
                .value()) {
            //计数器清零
            unRecPongTimes = 0;
            System.out.println("Client receive server heart beat message : ---> " + message);
        } else
            ctx.fireChannelRead(msg);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt)
            throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state() == IdleState.READER_IDLE) {
				/* 读超时 */
                System.out.println("===客户端===(READER_IDLE 读超时)");
            } else if (event.state() == IdleState.WRITER_IDLE) {
				/* 写超时 */
                System.out.println("===客户端===(WRITER_IDLE 写超时)");
                if (unRecPongTimes < MAX_UN_REC_PONG_TIMES) {
                    NettyMessage heatBeat = new NettyMessage();
                    Header header = new Header();
                    header.setType(MessageType.HEARTBEAT_REQ.value());
                    heatBeat.setHeader(header);
                    System.out.println("Client send heart beat messsage to server : ---> "
                            + heatBeat);
                    ctx.writeAndFlush(heatBeat);
                    unRecPongTimes++;
                } else {
                    ctx.channel().close();
                }
            } else if (event.state() == IdleState.ALL_IDLE) {
				/* 总超时 */
                System.out.println("===客户端===(ALL_IDLE 总超时)");
            }
        }
    }

    private class HeartBeatTask implements Runnable {
        private final ChannelHandlerContext ctx;

        public HeartBeatTask(final ChannelHandlerContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public void run() {
            NettyMessage heatBeat = buildHeatBeat();
            System.out.println("Client send heart beat messsage to server : ---> "
                            + heatBeat);
            ctx.writeAndFlush(heatBeat);
        }

        private NettyMessage buildHeatBeat() {
            NettyMessage message = new NettyMessage();
            Header header = new Header();
            header.setType(MessageType.HEARTBEAT_REQ.value());
            message.setHeader(header);
            return message;
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
            throws Exception {
        cause.printStackTrace();
        if (heartBeat != null) {
            heartBeat.cancel(true);
            heartBeat = null;
        }
        ctx.fireExceptionCaught(cause);
    }
}
