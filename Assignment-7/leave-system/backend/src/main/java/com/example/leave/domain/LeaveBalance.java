package com.example.leave.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class LeaveBalance {
    @Id
    private String employeeEmail;
    private int remainingDays;

    public LeaveBalance() {}

    public LeaveBalance(String employeeEmail, int remainingDays) {
        this.employeeEmail = employeeEmail;
        this.remainingDays = remainingDays;
    }

    public String getEmployeeEmail() { return employeeEmail; }
    public void setEmployeeEmail(String value) { employeeEmail = value; }
    public int getRemainingDays() { return remainingDays; }
    public void setRemainingDays(int value) { remainingDays = value; }
}