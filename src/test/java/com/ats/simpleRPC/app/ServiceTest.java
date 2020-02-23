package com.ats.simpleRPC.app;

import com.ats.simpleRPC.client.*;
import com.ats.simpleRPC.client.proxy.IAsyncObjectProxy;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.jnlp.IntegrationService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.hamcrest.CoreMatchers.equalTo;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = "classpath:client-spring.xml")
public class ServiceTest {
    @Autowired
    private RpcClient rpcClient;

    @Test
    public void helloTest1(){
        HelloService helloService = rpcClient.create(HelloService.class);
        String result = helloService.hello("World");
        Assert.assertEquals("Hello! World",result);
    }

    @Test
    public void helloTest2(){
        HelloService helloService = rpcClient.create(HelloService.class);
        Person person = new Person("Yong","Huang");
        String result = helloService.hello(person);
        Assert.assertEquals("Hello! Yong Huang",result);
    }

    @Test
    public void helloPersonTest(){
        PersonService personService = rpcClient.create(PersonService.class);
        int num = 5;
        List<Person>persons = personService.GetTestPerson("xiaoming",num);
        List<Person>expectedPersons = new ArrayList<>();
        for(int i = 0;i < num;i++){
            expectedPersons.add(new Person(Integer.toString(i),"xiaoming"));
        }
        Assert.assertThat(persons,equalTo(expectedPersons));
        for(int i = 0;i < persons.size();i++){
            System.out.println(persons.get(i));
        }
    }

    @Test
    public void helloFutureTest1() throws ExecutionException, InterruptedException {
        IAsyncObjectProxy helloService = rpcClient.createAsync(HelloService.class);
        RPCFuture rpcFuture = helloService.call("hello","World");
        Assert.assertEquals("Hello! World",rpcFuture.get());
    }

    @Test
    public void helloFutureTest2() throws ExecutionException, InterruptedException {
        IAsyncObjectProxy helloService = rpcClient.createAsync(HelloService.class);
        Person person = new Person("Yong","Huang");
        RPCFuture rpcFuture = helloService.call("hello",person);
        Assert.assertEquals("Hello! Yong Huang",rpcFuture.get());
    }

    @Test
    public void helloPersonFutureTest1() throws ExecutionException, InterruptedException {
        IAsyncObjectProxy helloPersonService = rpcClient.createAsync(PersonService.class);
        int num = 5;
        RPCFuture rpcFuture = helloPersonService.call("GetTestPerson","xiaoming",num);
        List<Person>persons = (List<Person>)rpcFuture.get();
        List<Person>expected = new ArrayList<>();
        for(int i = 0;i < num;i++){
            expected.add(new Person(Integer.toString(i),"xiaoming"));
        }
        Assert.assertThat(persons,equalTo(expected));

        for(int i = 0;i < num;i++){
            System.out.println(persons.get(i));
        }
    }
}
