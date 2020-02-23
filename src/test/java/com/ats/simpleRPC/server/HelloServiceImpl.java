package com.ats.simpleRPC.server;

import com.ats.simpleRPC.client.HelloService;
import com.ats.simpleRPC.client.Person;

@RpcService(HelloService.class)
public class HelloServiceImpl {
    public HelloServiceImpl(){

    }

    public String hello(String name){
        return "Hello! " + name;
    }

    public String hello(Person person){
        return "Hello! " + person.getFirstName() + " " + person.getLastName();
    }
}
