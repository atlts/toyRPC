package com.ats.simpleRPC.client;

import java.util.List;

public interface PersonService {
    List<Person>GetTestPerson(String name,int num);
}
