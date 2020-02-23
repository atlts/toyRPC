package com.ats.simpleRPC.client;

public interface AsyncRPCCallback {
    void success(Object result);

    void fail(Exception e);
}
