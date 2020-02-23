package com.ats.simpleRPC.app;

import com.ats.simpleRPC.client.*;
import com.ats.simpleRPC.client.proxy.IAsyncObjectProxy;
import com.ats.simpleRPC.registry.ServiceDiscovery;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;

/**
 * Test for 10 threads,200 requests and callbacks for each thread
 * ASync call total_time_cost:555ms,req/s=3603.6036036036035
 */
public class PersonCallbackTest2 {
    public static void main(String[] args) {
        ServiceDiscovery serviceDiscovery = new ServiceDiscovery("127.0.0.1:2181");
        final RpcClient rpcClient = new RpcClient(serviceDiscovery);
        final CountDownLatch countDownLatch = new CountDownLatch(10);
        int threadNum = 10;
        int requestNum = 200;

        Thread[] threads = new Thread[threadNum];
        long startTime = System.currentTimeMillis();
        try {
            for (int i = 0; i < threadNum; i++) {
                threads[i] = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        for (int j = 0; j < requestNum; j++) {
                            IAsyncObjectProxy client = rpcClient.createAsync(PersonService.class);
                            int num = 5;
                            RPCFuture helloPersonFuture = client.call("GetTestPerson", "xiaoming", num);
                            helloPersonFuture.addCallback(new AsyncRPCCallback() {
                                @Override
                                public void success(Object result) {
                                    List<Person> persons = (List<Person>) result;
                                    for (int i = 0; i < persons.size(); i++) {
                                        System.out.println(persons.get(i));
                                     //   countDownLatch.countDown();
                                    }
                                }
                                @Override
                                public void fail(Exception e) {
                                    System.out.println(e);
                                   // countDownLatch.countDown();
                                }
                            });
                            try {
                                System.out.println(helloPersonFuture.get());
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            } catch (ExecutionException e) {
                                e.printStackTrace();
                            }
                        }
                        countDownLatch.countDown();

                    }
                });
                threads[i].start();
            }
        } catch (Exception e) {
            System.out.println(e);
        }
        try {
            countDownLatch.await();
        } catch (Exception e) {
            e.printStackTrace();
        }
        long timeCost = (System.currentTimeMillis() - startTime);
        String msg1 = String.format("Test for %s threads,%s requests and callbacks for each thread",threadNum,requestNum);
        String msg = String.format("ASync call total_time_cost:%sms,req/s=%s",timeCost,((double)(requestNum * threadNum)) / timeCost * 1000);
        System.out.println(msg1);
        System.out.println(msg);
        rpcClient.stop();
        System.out.println("End");
    }
}
