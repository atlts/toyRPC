package com.ats.simpleRPC.client.proxy;

import com.ats.simpleRPC.client.RPCFuture;

public interface IAsyncObjectProxy {
    public RPCFuture call(String funcName,Object... args);
}
