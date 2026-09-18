package com.emcc.admin.app.model;

public class UnmatchedPayment {
    private Long id;
    private String description;
    private String amount;
    private String date;

    public UnmatchedPayment(Long id, String description, String amount, String date) {
        this.id = id;
        this.description = description;
        this.amount = amount;
        this.date = date;
    }

    public Long getId() { return id; }
    public String getDescription() { return description; }
    public String getAmount() { return amount; }
    public String getDate() { return date; }
}
