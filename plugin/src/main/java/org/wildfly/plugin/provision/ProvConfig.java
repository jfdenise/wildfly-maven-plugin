/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.wildfly.plugin.provision;

import java.util.Map;

/**
 *
 * @author jdenise
 */
public class ProvConfig {

    private final Map<String, Object> config;

    public ProvConfig(Map<String, Object> config) {
        this.config = config;
    }

    public Map<String, Object> getConfig() {
        return config;
    }
//    @SuppressWarnings({"squid:S3740", "rawtypes", "unchecked"})
//    public void setConfig(Map<String, TreeMap> config) {
//        this.config = new HashMap<>();
//        config.forEach((key, value) -> this.config.put(key, (Map<String, Object>) value));
//        System.out.println("XXXXXXX RESOLVER CONFIG " + this.config);
//    }
}
