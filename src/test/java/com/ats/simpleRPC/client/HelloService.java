package com.ats.simpleRPC.client;

public interface HelloService {
    String hello(String name);

    String hello(Person person);
}
