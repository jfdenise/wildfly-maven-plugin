/*
 * Copyright 2023 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.plugin.provision;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.wildfly.glow.Arguments;
import org.wildfly.glow.ScanArguments.Builder;

/**
 *
 * @author jdenise
 */
public class GlowConfig {

    String executionContext = "bare-metal";
    String profile;
    Set<String> addOns = Collections.emptySet();
    String version;
    boolean suggest;

    public GlowConfig() {
    }

    public Arguments toArguments(Path deployment, Path inProvisioning) {
        Set<String> profiles = new HashSet<>();
        if (profile != null) {
            profiles.add(profile);
        }
        List<Path> lst = new ArrayList<>();
        lst.add(deployment);
        Builder builder = Arguments.scanBuilder().setExecutionContext(executionContext).setExecutionProfiles(profiles)
                .setUserEnabledAddOns(addOns).setBinaries(lst).setSuggest(suggest).setVersion(version);
        if (inProvisioning != null) {
            builder.setProvisoningXML(inProvisioning);
        }
        return builder.build();
    }

    /**
     * @return the executionContext
     */
    public String getExecutionContext() {
        return executionContext;
    }

    /**
     * @param executionContext the executionContext to set
     */
    public void setExecutionContext(String executionContext) {
        this.executionContext = executionContext;
    }

    /**
     * @return the profile
     */
    public String getProfile() {
        return profile;
    }

    /**
     * @param profile the profile to set
     */
    public void setProfile(String profile) {
        this.profile = profile;
    }

    /**
     * @return the userEnabledAddOns
     */
    public Set<String> getAddOns() {
        return addOns;
    }

    /**
     * @param addOns the userEnabledAddOns to set
     */
    public void setAddOns(Set<String> addOns) {
        this.addOns = addOns;
    }

    /**
     * @return the version
     */
    public String getVersion() {
        return version;
    }

    /**
     * @param version the version to set
     */
    public void setVersion(String version) {
        this.version = version;
    }

    /**
     * @return the suggest
     */
    public boolean isSuggest() {
        return suggest;
    }

    /**
     * @param suggest the suggest to set
     */
    public void setSuggest(boolean suggest) {
        this.suggest = suggest;
    }
}