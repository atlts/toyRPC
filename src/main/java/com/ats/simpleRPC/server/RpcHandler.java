package com.ats.simpleRPC.server;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import net.sf.cglib.reflect.FastClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.ats.simpleRPC.protocol.RpcRequest;
import com.ats.simpleRPC.protocol.RpcResponse;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;

/**
 * RPC中Netty的主要功能并不是为了处理数据，而是要把客户端发来的请求内容告诉服务端
 * 所以在handler中直接调用服务端的线程池，避免因为比如读取数据库等一些耗时操作而阻塞Netty的线程
 */
public class RpcHandler extends SimpleChannelInboundHandler<RpcRequest> {
    private static final Logger logger = LoggerFactory.getLogger(RpcHandler.class);

    private final Map<String,Object>handlerMap;

    public RpcHandler(Map<String,Object>handlerMap){
        this.handlerMap = handlerMap;
    }
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, RpcRequest msg) throws Exception {
      //  System.out.println(ctx.channel().remoteAddress());
        /**
         * 利用线程池处理请求，处理完之后返回结果
         */
        RpcServer.submit(new Runnable() {
            @Override
            public void run() {
                logger.debug("Receive request " + msg.getRequestId());
                RpcResponse response = new RpcResponse();
                response.setRequestId(msg.getRequestId());
                try{
                    Object result = handle(msg);
                    response.setResult(result);
                }catch(Throwable t){
                    response.setError(t.toString());
                    logger.error("RPC Server handle request error",t);
                }
                ctx.writeAndFlush(response).addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        logger.debug("Send response for request " + msg.getRequestId());
                    }
                });
            }
        });
    }

    /**
     * 获取客户端发过来的请求的具体参数，然后取出handlerMap中的处理器对其进行具体的处理
     * @param request
     * @return
     * @throws InvocationTargetException
     */
    private Object handle(RpcRequest request) throws InvocationTargetException {
        String className = request.getClassName();
        Object serviceBean = handlerMap.get(className);
        Class<?> serviceClass = serviceBean.getClass();
        String methodName = request.getMethodName();
        Class<?>[] parameterTypes = request.getParameterTypes();
        Object[] parameters = request.getParameters();

        logger.debug(serviceClass.getName());
        logger.debug(methodName);

        /**
         * 使用FastClass能比反射更快调用增强类方法
         */
        FastClass serviceFastClass  = FastClass.create(serviceClass);
        int methodIndex = serviceFastClass.getIndex(methodName,parameterTypes);
        return serviceFastClass.invoke(methodIndex,serviceBean,parameters);
    }

    public void exceptionCaught(ChannelHandlerContext ctx,Throwable cause){
        logger.error("server caught exception",cause);
        ctx.close();
    }
}
