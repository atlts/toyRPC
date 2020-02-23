package com.ats.simpleRPC.app;

import com.ats.simpleRPC.client.HelloService;
import com.ats.simpleRPC.client.RpcClient;
import com.ats.simpleRPC.registry.ServiceDiscovery;

import java.util.concurrent.CountDownLatch;

/**
 * Test for 10 threads,100 requests for each thread
 * Sync call total_time_cost:242ms,req/s=4132.231404958678
 */
public class Benchmark {
    public static CountDownLatch countDownLatch = new CountDownLatch(10);
    public static void main(String[] args) throws InterruptedException {
        ServiceDiscovery serviceDiscovery = new ServiceDiscovery("127.0.0.1:2181");
        final RpcClient rpcClient = new RpcClient(serviceDiscovery);

        int threadNum = 10;
        final int requestNum = 100;
        Thread[] threads = new Thread[threadNum];

        long startTime = System.currentTimeMillis();
        for(int i = 0;i < threadNum;i++){
            threads[i] = new Thread(new Runnable() {
                @Override
                public void run() {
                    for(int i = 0;i < requestNum;i++){
                        final HelloService syncClient = rpcClient.create(HelloService.class);
                        String result = syncClient.hello(Integer.toString(i));
                        if(!result.equals("Hello! " + i)){
                            System.out.println("error= " + result);
                        }
                    }
                    countDownLatch.countDown();
                }
            });
            threads[i].start();
        }
//        for(int i = 0;i < threads.length;i++){
//            threads[i].join();
//        }
        countDownLatch.await();
        long timeCost = (System.currentTimeMillis() - startTime);
        String msg1 = String.format("Test for %s threads,%s requests for each thread",threadNum,requestNum);
        String msg = String.format("Sync call total_time_cost:%sms,req/s=%s",timeCost,((double)(requestNum * threadNum)) / timeCost * 1000);
        System.out.println(msg1);
        System.out.println(msg);
        rpcClient.stop();
    }
}
