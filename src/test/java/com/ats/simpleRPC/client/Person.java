package com.ats.simpleRPC.client;

import lombok.Data;

@Data
public class Person {
    private String firstName;
    private String lastName;

    public Person(){

    }

    public Person(String firstName,String lastName){
        this.firstName = firstName;
        this.lastName = lastName;
    }

    public String toString(){
        return firstName + " " + lastName;
    }

    public int hashCode(){
        return this.firstName.hashCode() ^ this.lastName.hashCode();
    }

    public boolean equals(Object obj){
        if(!(obj instanceof Person)){
            return false;
        }
        Person p = (Person)obj;
        return this.firstName.equals(p.firstName) && this.lastName.equals(p.lastName);
    }
}
