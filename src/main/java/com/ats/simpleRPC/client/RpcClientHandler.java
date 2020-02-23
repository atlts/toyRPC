package com.ats.simpleRPC.client;

import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.ats.simpleRPC.protocol.RpcRequest;
import com.ats.simpleRPC.protocol.RpcResponse;

import java.net.SocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

/**
 * 也就是正常的Client端的handelr，不过为了方便，把sendRequest的方法也塞进来了
 */
public class RpcClientHandler extends SimpleChannelInboundHandler<RpcResponse> {
    private static final Logger logger = LoggerFactory.getLogger(RpcClientHandler.class);

    private ConcurrentHashMap<String,RPCFuture>pendingRPC = new ConcurrentHashMap<>();
    private volatile Channel channel;
    private SocketAddress remotePeer;

    public Channel getChannel(){
        return channel;
    }

    public SocketAddress getRemotePeer(){
        return remotePeer;
    }

    public void channelActive(ChannelHandlerContext ctx)throws Exception{
        super.channelActive(ctx);
        logger.debug("应该连接上去啊");
        this.remotePeer = this.channel.remoteAddress();
    }

    public void channelRegistered(ChannelHandlerContext ctx)throws Exception{
        super.channelRegistered(ctx);
        this.channel = ctx.channel();
    }

    /**
     * 当request发送出去之后，立刻异步得返回rpcFuture
     * 直到收到服务端的response时，说明服务端已经把它要做的事情做完，rpcFuture此时可以返回response得result
     * 此时会调用rpcFuture中的done（）告诉客户端请求已经处理完成
     * @param ctx
     * @param msg
     * @throws Exception
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcResponse msg) throws Exception {
        String requestedId = msg.getRequestId();
        RPCFuture rpcFuture  = pendingRPC.get(requestedId);
        if(rpcFuture != null){
            pendingRPC.remove(requestedId);
            rpcFuture.done(msg);
        }
    }

    public void exceptionCaught(ChannelHandlerContext ctx,Throwable cause){
        logger.error("com.ats.simpleRPC.client caught exception", cause);
        ctx.close();
    }

    public void close(){
        channel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
    }


    /**
     * 为了方便，发送request的逻辑就也放进handler里面了
     * @param request
     * @return
     */
    public RPCFuture sendRequest(RpcRequest request){
        final CountDownLatch latch = new CountDownLatch(1);
        RPCFuture rpcFuture = new RPCFuture(request);
        pendingRPC.put(request.getRequestId(),rpcFuture);

        channel.writeAndFlush(request).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                    latch.countDown();
            }
        });
        try{
            latch.await();
        }catch(Exception e){
            logger.error(e.getMessage());
        }
        return rpcFuture;
    }
}
