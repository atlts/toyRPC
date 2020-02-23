package com.ats.simpleRPC.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import org.apache.commons.collections4.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import com.ats.simpleRPC.protocol.RpcDecoder;
import com.ats.simpleRPC.protocol.RpcEncoder;
import com.ats.simpleRPC.protocol.RpcRequest;
import com.ats.simpleRPC.protocol.RpcResponse;
import com.ats.simpleRPC.registry.ServiceRegistry;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 继承了spring中的两个类，在初始化过程中会自动调用setApplicationContext和afterPropertiesSet方法
 */
public class RpcServer implements ApplicationContextAware, InitializingBean {
    private static final Logger logger = LoggerFactory.getLogger(RpcServer.class);

    private String serverAddress;
    private ServiceRegistry serviceRegistry;
    //存储handler
    private Map<String,Object>handlerMap = new HashMap<>();

    /**
     *延迟初始化
     */
    private static volatile ThreadPoolExecutor threadPoolExecutor;
    private EventLoopGroup bossGroup= null;
    private EventLoopGroup workerGroup = null;

    public RpcServer(String serverAddress){
        this.serverAddress = serverAddress;
    }

    public RpcServer(String serverAddress,ServiceRegistry serviceRegistry){
        this.serverAddress = serverAddress;
        this.serviceRegistry = serviceRegistry;
    }

    /**
     *将真正的请求处理器放入handlerMap中
     * @param ctx
     * @throws BeansException
     */
    @Override
    public void setApplicationContext(ApplicationContext ctx) throws BeansException{
        //获得所有具有RpcService注解的类，这些类是实现了某些接口的
        // 也就是服务器要调用的处理器，到时候发送请求时，调用的是接口的名字，以此实现服务的多态性
        Map<String,Object>serviceBeanMap = ctx.getBeansWithAnnotation(RpcService.class);
        if(MapUtils.isNotEmpty(serviceBeanMap)){
            for(Object serviceBean : serviceBeanMap.values()){
                String interfaceName = serviceBean.getClass().getAnnotation(RpcService.class).value().getName();
                logger.info("Loading service:{} " , interfaceName);
                handlerMap.put(interfaceName,serviceBean);
            }
        }
    }
    @Override
    public void afterPropertiesSet() throws Exception {
        start();
    }

    public void stop(){
        if(bossGroup != null){
            bossGroup.shutdownGracefully();
        }
        if(workerGroup != null){
            workerGroup.shutdownGracefully();
        }
    }

    /**
     * 双重检查单例模式延迟初始化
     * @param task
     */
    public static void submit(Runnable task){
        if(threadPoolExecutor == null){
            synchronized (RpcServer.class){
                if(threadPoolExecutor == null){
                    threadPoolExecutor = new ThreadPoolExecutor(16,16,600L,
                            TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(65536));
                }
            }
        }
        threadPoolExecutor.submit(task);
    }


    public RpcServer addService(String interfaceName,Object serviceBean){
        if(!handlerMap.containsKey(interfaceName)){
            logger.info("Loading service:{}",interfaceName);
            handlerMap.put(interfaceName, serviceBean);
        }
        return this;
    }

    /**
     * 这也就是正常的Netty启动流程，因为利用的是Tcp包，所以采用了Length Field Based Frame Decoder整理数据包之后再解码
     * 加解码利用的是Serialization Util（Based on Protostuff）
     * Netty链接上服务端之后就将服务地址注册到了zooKeeper
     * @throws InterruptedException
     */
    public void start() throws InterruptedException {
        if(bossGroup == null && workerGroup == null){
            bossGroup = new NioEventLoopGroup();
            workerGroup = new NioEventLoopGroup();
            ServerBootstrap bootStrap = new ServerBootstrap();
            bootStrap.group(bossGroup,workerGroup).channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(65536,0,4,0,0))
                                .addLast(new RpcDecoder(RpcRequest.class))
                                .addLast(new RpcEncoder(RpcResponse.class))
                                .addLast(new RpcHandler(handlerMap));
                        }
                    }).option(ChannelOption.SO_BACKLOG,128)
                    .childOption(ChannelOption.SO_KEEPALIVE,true);

            String[]array = serverAddress.split(":");
            String host = array[0];
            int port = Integer.parseInt(array[1]);
            ChannelFuture future = bootStrap.bind(host,port).sync();
            logger.info("Server start on port {}",port);
            if(serviceRegistry != null){
                serviceRegistry.registry(serverAddress);
            }
            future.channel().closeFuture().sync();
        }
    }

}
