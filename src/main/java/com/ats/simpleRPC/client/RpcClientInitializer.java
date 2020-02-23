package com.ats.simpleRPC.client;

import com.ats.simpleRPC.protocol.RpcResponse;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import com.ats.simpleRPC.protocol.RpcDecoder;
import com.ats.simpleRPC.protocol.RpcEncoder;
import com.ats.simpleRPC.protocol.RpcRequest;

public class RpcClientInitializer extends ChannelInitializer<SocketChannel> {
    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline cp = ch.pipeline();
        cp.addLast(new RpcEncoder(RpcRequest.class));
        /**
         * lengthAdjustment是长度域的矫正，使其表示有效数据的长度
         * initialBytesToStrip表示为了获取有效数据需要丢弃的长度
         * 观看编解码过程可知，使用的TCP协议，只在头部加了字节长度
         */
        cp.addLast(new LengthFieldBasedFrameDecoder(65536,0,4,0,0));
        cp.addLast(new RpcDecoder(RpcResponse.class));
        cp.addLast(new RpcClientHandler());
    }
}
