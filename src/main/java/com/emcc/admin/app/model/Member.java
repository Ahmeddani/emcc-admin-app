package com.emcc.admin.app.model;

public class Member {
    private boolean persisted = false;
    private String membershipId;
    private String name;
    private String address;
    private String phoneNumber;
    private String email;
    private String maritalStatus;
    private String childrenUnder10;
    private String children10To18;
    private String occupation;
    private String createdDate;
    private String monthlyDues;

    public Member(String membershipId, String name, String address, String phoneNumber, String email,
                  String maritalStatus, String childrenUnder10, String children10To18, String occupation,
                  String createdDate, String monthlyDues) {
        this.membershipId = membershipId;
        this.name = name;
        this.address = address;
        this.phoneNumber = phoneNumber;
        this.email = email;
        this.maritalStatus = maritalStatus;
        this.childrenUnder10 = childrenUnder10;
        this.children10To18 = children10To18;
        this.occupation = occupation;
        this.createdDate = createdDate;
        this.monthlyDues = monthlyDues;
    }

    public boolean isPersisted() { return persisted; }
    public void setPersisted(boolean persisted) { this.persisted = persisted; }

    public String getMembershipId() {
        return membershipId;
    }

    public void setMembershipId(String membershipId) {
        this.membershipId = membershipId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getMaritalStatus() {
        return maritalStatus;
    }

    public void setMaritalStatus(String maritalStatus) {
        this.maritalStatus = maritalStatus;
    }

    public String getChildrenUnder10() {
        return childrenUnder10;
    }

    public void setChildrenUnder10(String childrenUnder10) {
        this.childrenUnder10 = childrenUnder10;
    }

    public String getChildren10To18() {
        return children10To18;
    }

    public void setChildren10To18(String children10To18) {
        this.children10To18 = children10To18;
    }

    public String getOccupation() {
        return occupation;
    }

    public void setOccupation(String occupation) {
        this.occupation = occupation;
    }

    public String getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(String createdDate) {
        this.createdDate = createdDate;
    }

    public String getMonthlyDues() {
        return monthlyDues;
    }

    public void setMonthlyDues(String monthlyDues) {
        this.monthlyDues = monthlyDues;
    }
}
