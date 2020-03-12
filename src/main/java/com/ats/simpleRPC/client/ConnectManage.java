package com.ats.simpleRPC.client;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;

import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;


/**
 * 管控所有的客户端和服务端的连接·
 */
public class ConnectManage {
    private static final Logger logger = LoggerFactory.getLogger(ConnectManage.class);
    private volatile static ConnectManage connectManage;
    private static  int threads = 16;
    private EventLoopGroup eventLoopGroup = new NioEventLoopGroup(4);
    private static ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(threads,threads,600L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<Runnable>(65536));
    private CopyOnWriteArrayList<RpcClientHandler>connectedHandlers = new CopyOnWriteArrayList<>();
    private Map<InetSocketAddress,RpcClientHandler>connectedServerNodes = new ConcurrentHashMap<>();

    private ReentrantLock lock = new ReentrantLock();
    private Condition connected = lock.newCondition();
    private long connectTimeoutMillis = 6000;
    private AtomicInteger roundRobin = new AtomicInteger(0);
    private volatile boolean isRuning = true;

    private ConnectManage(){

    }

    public static void setThreads(int num){
        threads = num;
    }
    public static ConnectManage getInstance(){
        if(connectManage == null){
            synchronized (ConnectManage.class){
                if(connectManage == null){
                    connectManage = new ConnectManage();
                }
            }
        }
        return connectManage;
    }

    /**
     * 由zookeeper的ServiceDiscovery调用，当注册的服务器节点发生更改时就会更新
     * 负责和Netty的服务端链接上，并保存相应客户端频道上的handler和InetSocketAddress以备用
     * 在serviceDiscovery的watchNode阶段被调用
     * @param allServerAddress
     */
    public void updateConnectedServer(List<String> allServerAddress){

            if(allServerAddress.size() > 0){
                HashSet<InetSocketAddress>newAllServerNodeSet = new HashSet<InetSocketAddress>();
                for(int i = 0;i < allServerAddress.size();i++){
                    String[]array = allServerAddress.get(i).split(":");
                    if(array.length == 2){
                        String host = array[0];
                        int port = Integer.parseInt(array[1]);
                        final InetSocketAddress remotePeer = new InetSocketAddress(host,port);
                        newAllServerNodeSet.add(remotePeer);
                    }
                }

                for(final InetSocketAddress serverNodeAddress : newAllServerNodeSet){
                    if(!connectedServerNodes.keySet().contains(serverNodeAddress)){
                        //用线程池将链接上新的远程服务器，之后将handler加进connectHandlers和connectedServerNodes
                        //并唤醒condition
                        connectServerNode(serverNodeAddress);
                    }
                }

                /**
                 * 将无效的handler和服务端移除
                 */
                for(int i = 0;i < connectedHandlers.size();i++){
                    RpcClientHandler connectedServerHandler = connectedHandlers.get(i);
                    SocketAddress remotePeer = connectedServerHandler.getRemotePeer();
                    if(!newAllServerNodeSet.contains(remotePeer)){
                        logger.info("Remove invalid com.ats.simpleRPC.server node " + remotePeer);
                        RpcClientHandler handler = connectedServerNodes.get(remotePeer);
                        if(handler != null){
                            handler.close();
                        }
                        connectedServerNodes.remove(remotePeer);
                        connectedHandlers.remove(connectedServerHandler);
                    }
                }
            }else{
                logger.error("No available com.ats.simpleRPC.server node.All com.ats.simpleRPC.server nodes are down!!!");
                for(final RpcClientHandler connectedServerHandler : connectedHandlers){
                    SocketAddress remotePeer = connectedServerHandler.getRemotePeer();
                    RpcClientHandler handler = connectedServerNodes.get(remotePeer);
                    handler.close();
                    connectedServerNodes.remove(remotePeer);
                }
                connectedHandlers.clear();
            }
        }


    public void reconnect(final RpcClientHandler handler,final SocketAddress remotePeer){
        if(handler != null){
            connectedHandlers.remove(handler);
            connectedServerNodes.remove(handler.getRemotePeer());
        }
        connectServerNode((InetSocketAddress)remotePeer);
    }
    /**
     * 将客户端链接上新的服务端，并将相应客户端上的handler加进集合
     * @param remotePeer
     */
    private void connectServerNode(final InetSocketAddress remotePeer){
        threadPoolExecutor.submit(new Runnable() {
            @Override
            public void run() {
                Bootstrap b = new Bootstrap();
                b.group(eventLoopGroup)
                        .channel(NioSocketChannel.class)
                        .handler(new RpcClientInitializer());

                ChannelFuture channelFuture = b.connect(remotePeer);
                channelFuture.addListener(new ChannelFutureListener() {
                    /**
                     * 显然当连接完成就会把客户端的handler加进addHandler方法里面
                     * 这里应该只要注意一下调用addHandler的是哪个对象就好了
                     * @param channelFuture
                     * @throws Exception
                     */
                    @Override
                    public void operationComplete(final ChannelFuture channelFuture) throws Exception {
                        if (channelFuture.isSuccess()) {
                            logger.debug("Successfully connect to remote server. remote peer = " + remotePeer);
                            RpcClientHandler handler = channelFuture.channel().pipeline().get(RpcClientHandler.class);
                            addHandler(handler);
                        }
                    }
                });
            }
        });
    }

    private void addHandler(RpcClientHandler handler){
        connectedHandlers.add(handler);
        InetSocketAddress remoteAddress = (InetSocketAddress)handler.getChannel().remoteAddress();
        connectedServerNodes.put(remoteAddress,handler);
        signalAvailableHandler();
    }

    private void signalAvailableHandler(){
        lock.lock();
        try{
            connected.signalAll();
        }finally{
            lock.unlock();
        }
    }

    private boolean waitingForHandler() throws InterruptedException {
        lock.lock();
        try{
            return connected.await(this.connectTimeoutMillis,TimeUnit.MILLISECONDS);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 随机选择一个handler
     * @return
     */
    public RpcClientHandler chooseHandler(){
        int size = connectedHandlers.size();
        while(isRuning && size <= 0){
            try{
                boolean available = waitingForHandler();
                if(available){
                    size = connectedHandlers.size();
                }
            } catch (InterruptedException e) {
                logger.error("Waiting for available node is interrupted! ",e);
                throw new RuntimeException("Can't connect any com.ats.simpleRPC.server!",e);
            }
        }
        int index = (roundRobin.getAndAdd(1) + size) % size;
        return connectedHandlers.get(index);
    }

    public void stop(){
        isRuning = false;
        for(int i = 0;i < connectedHandlers.size();i++){
            RpcClientHandler connectedServerHandler = connectedHandlers.get(i);
            connectedServerHandler.close();
        }
        signalAvailableHandler();
        threadPoolExecutor.shutdown();
        eventLoopGroup.shutdownGracefully();
    }
}
