package com.emcc.admin.app.model;

public class Asset {
    private Long id;
    private String assetName;
    private String type;
    private String count;
    private String condition;

    public Asset(String assetName, String type, String count, String condition) {
        this.assetName = assetName;
        this.type = type;
        this.count = count;
        this.condition = condition;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getAssetName() {
        return assetName;
    }

    public void setAssetName(String assetName) {
        this.assetName = assetName;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getCount() {
        return count;
    }

    public void setCount(String count) {
        this.count = count;
    }

    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }
}
