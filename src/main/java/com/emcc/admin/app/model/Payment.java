package com.emcc.admin.app.model;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class Payment {
    private Long id;
    private String memberId;
    private String memberName;
    private String amount;
    private String status;
    private String date;
    private String paymentMethod;
    private String invoiceNumber;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Payment(String memberId, String memberName, String amount, String status, String date, String paymentMethod, String invoiceNumber) {
        this.memberId = memberId;
        this.memberName = memberName;
        this.amount = amount;
        this.status = status;
        this.date = date;
        this.paymentMethod = paymentMethod;
        this.invoiceNumber = invoiceNumber;
    }

    public String getMemberId() {
        return memberId;
    }

    public void setMemberId(String memberId) {
        this.memberId = memberId;
    }

    public String getMemberName() {
        return memberName;
    }

    public void setMemberName(String memberName) {
        this.memberName = memberName;
    }

    public String getAmount() {
        return amount;
    }

    public void setAmount(String amount) {
        this.amount = amount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }

    public String getMonth() {
        if (date == null || date.isEmpty()) {
            return "";
        }
        try {
            LocalDate parsed = LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE);
            String monthName = parsed.getMonth().name();
            return monthName.substring(0, 1) + monthName.substring(1, 3).toLowerCase();
        } catch (Exception ex) {
            return "";
        }
    }
}
