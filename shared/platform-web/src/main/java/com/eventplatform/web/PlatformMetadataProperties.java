package com.eventplatform.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("platform.metadata")
public class PlatformMetadataProperties {

    private String description = "Phase 1 service skeleton";
    private String version = "development";
    private String apiVersion = "v1";

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }
}
