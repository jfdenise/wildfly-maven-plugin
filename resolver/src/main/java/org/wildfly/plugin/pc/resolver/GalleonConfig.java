/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2021 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.plugin.pc.resolver;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.jboss.galleon.maven.plugin.util.FeaturePack;
import org.apache.maven.plugin.MojoExecutionException;
import org.wildfly.plugin.core.GalleonUtils.ServerConfiguration;
/**
 *
 * @author jdenise
 */
@SuppressWarnings("unchecked")
public class GalleonConfig {
    private static final String OFFLINE = "offline";
    private static final String LOGTIME = "log-time";
    private static final String RECORDSTATE = "record-state";
    private static final String FEATUREPACKS = "feature-packs";
    private static final String SERVERCONFIGURATION = "server-configuration";
    private static final String PROVISIONINGFILE = "provisionng-file";
    private static final String PLUGIN_OPTIONS = "plugin-options";
    private static final String SERVER_CONFIGURATION = "server-configuration";
    private static final String MODEL = "model";
    private static final String NAME = "name";
    private static final String LAYERS = "layers";
    private static final String EXCLUDED_LAYERS = "excluded-layers";
    
    private final Map<String, String> pluginOptions;
    private final boolean offline;
    private final boolean logTime;
    private final boolean recordState;
    private final List<FeaturePack> featurePacks;
    private final ServerConfiguration serverConfiguration;
    private final File provisioningFile;
    private GalleonConfig(Map<String, Object> map) throws MojoExecutionException {
        offline = getBoleanValue(map, OFFLINE);
        logTime = getBoleanValue(map, LOGTIME);
        recordState = getBoleanValue(map, RECORDSTATE);
        String provFile = (String) map.get(PROVISIONINGFILE);
        provisioningFile = provFile == null ? null : new File(provFile);
        Map<String, Object> pOptions = (Map<String, Object>) map.get(PLUGIN_OPTIONS);
        pluginOptions = pOptions == null ? Collections.emptyMap() : toStringMap(pOptions);
        serverConfiguration = toServerConfiguration((Map<String, Object>) map.get(SERVER_CONFIGURATION));
        featurePacks = toFeaturePacks((Map<String, Object>) map.get(FEATUREPACKS));
    }

    private ServerConfiguration toServerConfiguration(Map<String, Object> map) {
        ServerConfiguration serverConfiguration = new ServerConfiguration();
        String model = (String) map.get(MODEL);
        if (model != null) {
            serverConfiguration.setModel(model);
        }
        String name = (String) map.get(NAME);
        if (name != null) {
            serverConfiguration.setName(name);
        }
        Map<String, Object> layers = (Map<String, Object>) map.get(LAYERS);
        if (layers != null) {
            List<String> layersList = new ArrayList<>();
            for(Entry<String, Object> entry : layers.entrySet()) {
                String value = (String) entry.getValue();
                layersList.add(value);
            }
            serverConfiguration.setLayers(layersList);
        }
        Map<String, Object> excludedLayers = (Map<String, Object>) map.get(EXCLUDED_LAYERS);
        if (excludedLayers != null) {
            List<String> layersList = new ArrayList<>();
            for(Entry<String, Object> entry : excludedLayers.entrySet()) {
                String value = (String) entry.getValue();
                layersList.add(value);
            }
            serverConfiguration.setExcludedLayers(layersList);
        }
        return serverConfiguration;
    }
    
    private List<FeaturePack> toFeaturePacks(Map<String, Object> map) {
        List<FeaturePack> lst = new ArrayList<>();
        for(Entry<String, Object> entry : map.entrySet()) {
            lst.add(toFeaturePack((Map<String, Object>) entry.getValue()));
        }
        return lst;
    }
    
    private FeaturePack toFeaturePack(Map<String, Object> map) {
        ServerConfiguration serverConfiguration = new ServerConfiguration();
        String model = (String) map.get(MODEL);
        if (model != null) {
            serverConfiguration.setModel(model);
        }
        String name = (String) map.get(NAME);
        if (name != null) {
            serverConfiguration.setName(name);
        }
        Map<String, Object> layers = (Map<String, Object>) map.get(LAYERS);
        if (layers != null) {
            List<String> layersList = new ArrayList<>();
            for(Entry<String, Object> entry : layers.entrySet()) {
                String value = (String) entry.getValue();
                layersList.add(value);
            }
            serverConfiguration.setLayers(layersList);
        }
        Map<String, Object> excludedLayers = (Map<String, Object>) map.get(EXCLUDED_LAYERS);
        if (excludedLayers != null) {
            List<String> layersList = new ArrayList<>();
            for(Entry<String, Object> entry : excludedLayers.entrySet()) {
                String value = (String) entry.getValue();
                layersList.add(value);
            }
            serverConfiguration.setExcludedLayers(layersList);
        }
        return serverConfiguration;
    }
    
    private Map<String, String> toStringMap(Map<String, Object> map) {
        Map<String, String> ret = new HashMap<>();
        for(Entry<String, Object> entry : map.entrySet()) {
            ret.put(entry.getKey(), (String) entry.getValue());
        }
        return ret;
    }

    private boolean getBoleanValue(Map<String, Object> map, String key) throws MojoExecutionException {
        Object ret = map.get(key);
        if (ret == null) {
            return false;
        }
        String val = (String) ret;
        return Boolean.valueOf(val);
    }

    public boolean isOffline() {
        Object ret = config.get(OFFLINE);
        if (ret == null) {
            return false;
        }
        String val = (String) ret;
        return 
    }
    static GalleonConfig from(Map<String, Object> config) {
        config.get
        return new GalleonConfig();
    }
}
