# nettyForRPC
  Test for 10 threads,100 requests for each thread
  Sync call total_time_cost:242ms,req/s=4132.231404958678
  
  
  基于TCP数据包的通信以获得更高的效率
  
  使用Netty进行客户端与服务端之间的通信，简化了NIO的复杂的配置和epoll空轮询的问题
  
  利用Spring依赖注入，管理server，handler等bean
  
  使用了长连接，并将所有连接放入ConnetcManager中进行集中管理，便于收集需要的handler
  
  当调用时通过代理的方法，将请求发到服务端之后立刻异步RpcFuture，利用AQS同步器实现类似countDownLatch的功能来等待远程调用结果的返回
  
  服务器端利用线程池，多线程处理请求
  
  实现了有callBacks和无callBacks两种异步阻塞调用
  

  
  
