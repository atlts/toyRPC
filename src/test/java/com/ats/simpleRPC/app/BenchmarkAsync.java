package com.ats.simpleRPC.app;

import com.ats.simpleRPC.client.HelloService;
import com.ats.simpleRPC.client.RPCFuture;
import com.ats.simpleRPC.client.RpcClient;
import com.ats.simpleRPC.client.proxy.IAsyncObjectProxy;
import com.ats.simpleRPC.registry.ServiceDiscovery;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Test for 10 threads,20 requests for each thread
 * ASync call total_time_cost:168ms,req/s=1190.4761904761904
 */
public class BenchmarkAsync {
    public static CountDownLatch countDownLatch = new CountDownLatch(10);
    public static void main(String[] args) throws Exception{
        ServiceDiscovery serviceDiscovery = new ServiceDiscovery("127.0.0.1:2181");
        final RpcClient rpcClient = new RpcClient(serviceDiscovery);

        int threadNum = 10;
        final int requestNum = 20;
        Thread[]threads = new Thread[threadNum];

        long startTime = System.currentTimeMillis();
        for(int i = 0;i < threadNum;i++){
            threads[i] = new Thread(new Runnable() {
                @Override
                public void run() {
                    for(int i = 0;i < requestNum;i++){
                        try {
                            IAsyncObjectProxy client = rpcClient.createAsync(HelloService.class);
                            RPCFuture helloFuture = client.call("hello", Integer.toString(i));
                            String result = (String) helloFuture.get(3000, TimeUnit.MILLISECONDS);
                            if(!result.equals("Hello! " + i)){
                                System.out.println("error= " + result );
                            }
                        }catch(Exception e){
                            e.printStackTrace();
                        }

                    }
                    countDownLatch.countDown();
                }
            });
            threads[i].start();
        }
        countDownLatch.await();
        long timeCost = (System.currentTimeMillis() - startTime);
        String msg1 = String.format("Test for %s threads,%s requests for each thread",threadNum,requestNum);
        String msg = String.format("ASync call total_time_cost:%sms,req/s=%s",timeCost,((double)(requestNum * threadNum)) / timeCost * 1000);
        System.out.println(msg1);
        System.out.println(msg);
        rpcClient.stop();
    }
}
