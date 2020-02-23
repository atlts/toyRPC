package com.ats.simpleRPC.client.proxy;

import com.ats.simpleRPC.client.ConnectManage;
import com.ats.simpleRPC.client.RPCFuture;
import com.ats.simpleRPC.client.RpcClientHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.ats.simpleRPC.protocol.RpcRequest;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.UUID;

public class ObjectProxy<T> implements InvocationHandler,IAsyncObjectProxy {
    private static final Logger LOGGER = LoggerFactory.getLogger(ObjectProxy.class);
    private Class<T>clazz;

    public ObjectProxy(Class<T> clazz){
        this.clazz = clazz;
    }

    /**
     * 异步的call，直接返回rpcFuture，等需要的时候在调用rpcFuture.get()
     * @param funcName
     * @param args
     * @return
     */
    @Override
    public RPCFuture call(String funcName, Object... args) {
        RpcClientHandler handler = ConnectManage.getInstance().chooseHandler();
        RpcRequest request = createRequest(this.clazz.getName(),funcName,args);
        RPCFuture rpcFuture = handler.sendRequest(request);
        return rpcFuture;
    }

    private RpcRequest createRequest(String className,String methodName,Object[] args){
        RpcRequest request = new RpcRequest();
        request.setRequestId(UUID.randomUUID().toString());
        request.setClassName(className);
        request.setMethodName(methodName);
        request.setParameters(args);

        Class[]parametersTypes = new Class[args.length];
        for(int i = 0;i < args.length;i++){
            parametersTypes[i] = getClassType(args[i]);
        }
        request.setParameterTypes(parametersTypes);

        for(int i = 0;i < args.length;i++){
            LOGGER.debug(args[i].toString() + " " + parametersTypes[i].getName());
        }
        return request;
    }

    /**
     * 要把基本类型类变成基本类型
     * @param obj
     * @return
     */
    private Class<?>getClassType(Object obj){
        Class<?> classType = obj.getClass();
        String typeName = classType.getTypeName();
        switch (typeName){
            case "java.lang.Integer":
                return Integer.TYPE;
            case "java.lang.Long":
                return Long.TYPE;
            case "java.lang.Float":
                return Float.TYPE;
            case "java.lang.Double":
                return Double.TYPE;
            case "java.lang.Character":
                return Character.TYPE;
            case "java.lang.Boolean":
                return Boolean.TYPE;
            case "java.lang.Short":
                return Short.TYPE;
            case "java.lang.Byte":
                return Byte.TYPE;
        }
        return classType;
    }

    /**
     * 对方法的代理，远程调用方法,并得到结果
     * @param proxy
     * @param method
     * @param args
     * @return
     * @throws Throwable
     */
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        /**
         * 当调用方法的类时Object时
         */
        if(Object.class == method.getDeclaringClass()){
            String name = method.getName();
            if("equals".equals(name)){
                return proxy == args[0];
            }else if("hashCode".equals(name)){
                return System.identityHashCode(proxy);
            }else if("toString".equals(name)){
                return proxy.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(proxy))
                         + ",with InvocationHandler " + this;
            }else{
                throw new IllegalStateException(String.valueOf(method));
            }
        }

        RpcRequest request = new RpcRequest();
        request.setRequestId(UUID.randomUUID().toString());
        request.setClassName(method.getDeclaringClass().getName());
        request.setMethodName(method.getName());
        request.setParameterTypes(method.getParameterTypes());
        request.setParameters(args);

        RpcClientHandler handler = ConnectManage.getInstance().chooseHandler();
        RPCFuture rpcFuture = handler.sendRequest(request);
        return rpcFuture.get();
    }


}
