package com.intuit.graphql.demo.persistence.entity;

import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.Date;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "employee")
public class EmployeeEntity {

    @Id
    String id;
    private String firstName;
    private String lastName;
    private Date birthDate;
    private Integer age;

    @OneToMany(mappedBy = "employee")
    private Set<AddressEntity> addresses;
}
