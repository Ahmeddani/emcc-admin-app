package com.emcc.admin.app.model;

public class PlaidConnectInfo {
    private final String url;
    private final String env;

    public PlaidConnectInfo(String url, String env) {
        this.url = url;
        this.env = env;
    }

    public String getUrl() { return url; }
    public String getEnv() { return env; }

    public boolean isSandbox() {
        return "sandbox".equalsIgnoreCase(env);
    }
}
