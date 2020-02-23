package com.ats.simpleRPC.registry;

import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;

/**
 * 就是注册zooKeeper地址，也就是与本机的zooKeeper相连，并且负责将Netty的服务器注册到zooKeeper上
 */
public class ServiceRegistry {
    private static final Logger logger = LoggerFactory.getLogger(ServiceRegistry.class);
    private CountDownLatch latch = new CountDownLatch(1);
    private String registryAddress;

    public ServiceRegistry(String registryAddress){
        this.registryAddress = registryAddress;
    }

    public void registry(String data){
        if(data != null){
            ZooKeeper zk = connectServer();
            if(zk != null){
                AddRootNode(zk);
                createNode(zk,data);
            }
        }
    }

    /**
     * 与本机的zooKeeper相连接
     * @return
     */
    private ZooKeeper connectServer(){
        ZooKeeper zk = null;
        try{
            /**
             * 连接是异步的会立即返回
             * 当链接有了结果会调用watcher中的process将栅栏撤掉
             */
            zk = new ZooKeeper(registryAddress, Constant.ZK_SESSION_TIMEOUT, new Watcher() {
                @Override
                public void process(WatchedEvent event) {
                    if(event.getState() == Event.KeeperState.SyncConnected){
                        latch.countDown();
                    }
                }
            });
            latch.await();
        }catch(Exception e){
            logger.error("Service com.ats.simpleRPC.registry connect com.ats.simpleRPC.server error : " + e);
        }
        return zk;
    }

    /**
     * 当没有根节点时增加根节点
     * @param zk
     */
    private void AddRootNode(ZooKeeper zk){
        try{
            /**
             * 指定为false则指对是否存在感兴趣
             */
            Stat s = zk.exists(Constant.ZK_REGISTERY_PATH,false);
            if(s == null){
                /**
                 * 第二个参数是节点的数据值
                 * 第三个是访问权限
                 * 第四个是节点类型
                 */
                zk.create(Constant.ZK_REGISTERY_PATH,new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            }
        }catch(Exception e){
            logger.error("Service com.ats.simpleRPC.registry add node error : " + e);
        }
    }

    /**
     * 将服务器注册到zooKeeper的根节点下面，data就是可用服务器的ip地址
     * @param zk
     * @param data
     */
    private void createNode(ZooKeeper zk,String data){
        try{
            byte[]bytes = data.getBytes();
            String path = zk.create(Constant.ZK_DATA_PATH,bytes,ZooDefs.Ids.OPEN_ACL_UNSAFE,CreateMode.EPHEMERAL_SEQUENTIAL);
            logger.debug("create zookeeper node ({} => {})",path,data);
        }catch(Exception e){
            logger.error("Service com.ats.simpleRPC.registry create node error: " ,e);
        }
    }
}
