package com.ats.simpleRPC.client;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.ats.simpleRPC.protocol.RpcRequest;
import com.ats.simpleRPC.protocol.RpcResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 对于异步的RPC call的回应
 * 客户端发送request之后，异步返回此对象
 * 收到response之后再由此对象做进一步处理
 */
public class RPCFuture implements Future<Object> {
    private static final Logger logger = LoggerFactory.getLogger(RPCFuture.class);

    private Sync sync;
    private RpcRequest request;
    private RpcResponse response;
    private long startTime;
    private static long RESPONSE_TIME_THRESHOLD = 5000;

    private List<AsyncRPCCallback>pendingCallbacks = new ArrayList<>();
    private ReentrantLock lock = new ReentrantLock();

    public RPCFuture(RpcRequest rpcRequest){
        this.sync = new Sync();
        this.request = rpcRequest;
        this.startTime = System.currentTimeMillis();
    }
    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isCancelled() {
        throw new UnsupportedOperationException();
    }

    /**
     * 判断客户端发送的请求是否已经处理完毕，也就是是否已经返回了result
     * 也就是done（）是否被调用
     * @return
     */
    @Override
    public boolean isDone() {
        return sync.isDone();
    }

    /**
     * 堵塞，直到收到服务端的response为止
     * @return
     * @throws InterruptedException
     * @throws ExecutionException
     */
    @Override
    public Object get() throws InterruptedException, ExecutionException {
        sync.acquire(-1);
        if(this.response != null){
            return this.response.getResult();
        }else{
            return null;
        }

    }

    /**
     * 和上面一样，就是加了个时间限制
     * @param timeout
     * @param unit
     * @return
     * @throws InterruptedException
     * @throws ExecutionException
     * @throws TimeoutException
     */
    @Override
    public Object get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        boolean success = sync.tryAcquireNanos(-1,unit.toNanos(timeout));
        if(success){
            if(this.response !=null){
                return this.response.getResult();
            }else{
                return null;
            }
        }else{
            throw new RuntimeException("Timeout exception.RequestId; " + this.request.getRequestId()
            + ".Request class name: " + this.request.getClassName() + ".Request method: " + this.request.getMethodName());
        }
    }

    /**
     * 当客户端收到服务端的response时，RpcClientHandler的channelRead0会调用此方法
     * 将response传递进来
     * @param response
     */
    public void done(RpcResponse response){
        this.response = response;
        sync.release(1);//此时get方法就不再阻塞了
        invokeCallbacks();
        long responseTime = System.currentTimeMillis() - startTime;
        if(responseTime > this.RESPONSE_TIME_THRESHOLD){
            logger.warn("Service response time is too slow.Request id = " + response.getRequestId() +
                    " . Response Time = " + responseTime + "ms");
        }
    }

    /**
     * 收到response之后把客户端的callback处理掉
     */
    private void invokeCallbacks(){
        lock.lock();
        try{
            for(final AsyncRPCCallback callback : pendingCallbacks){
                runCallback(callback);
            }
        }finally {
            lock.unlock();
        }
    }

    /**
     * 在异步调用的过程中，返回rpcFuture之后，可以设置一些服务端返回请求之后客户端需要做的事情，当然也都是放入了线程池
     * @param callback
     * @return
     */
    public RPCFuture addCallback(AsyncRPCCallback callback){
        lock.lock();
        try{
            if(isDone()){
                runCallback(callback);
            }else{
                this.pendingCallbacks.add(callback);
            }
        }finally{
            lock.unlock();
        }
        return this;
    }

    /**
     * 放入线程池，运行callbacks
     * @param callback
     */
    private void runCallback(final AsyncRPCCallback callback){
        final RpcResponse res = this.response;
        RpcClient.submit(new Runnable() {
            @Override
            public void run() {
                if(!res.isError()){
                    callback.success(res.getResult());
                }else{
                    callback.fail(new RuntimeException("Response error ",new Throwable(res.getError())));
                }
            }
        });
    }

    /**
     * 基本上是自己实现了一个countDownLatch的功能
     */
    static class Sync extends AbstractQueuedSynchronizer{
        private static final long serialVersionUID = 1L;

        private final int done = 1;
        private final int pending = 0;

        protected boolean tryAcquire(int arg){
            return getState() == done;
        }

        protected boolean tryRelease(int arg){
            if(getState() == pending){
                if(compareAndSetState(pending,done)){
                    return true;
                }else{
                    return false;
                }
            }else{
                return true;
            }
        }

        public boolean isDone(){
         //   getState();
            return getState() == done;
        }
    }
}
