package com.ats.simpleRPC.server;

import com.ats.simpleRPC.client.Person;
import com.ats.simpleRPC.client.PersonService;

import java.util.ArrayList;
import java.util.List;

@RpcService(PersonService.class)
public class PersonServiceImpl {
    public List<Person>GetTestPerson(String name,int num){
        List<Person>persons = new ArrayList<>(num);
        for(int i = 0;i < num;i++){
            persons.add(new Person(Integer.toString(i),name));
        }
        return persons;
    }
}
