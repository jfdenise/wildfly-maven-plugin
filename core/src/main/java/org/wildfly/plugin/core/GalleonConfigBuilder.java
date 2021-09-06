/*
 * Copyright 2016-2021 Red Hat, Inc. and/or its affiliates
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
package org.wildfly.plugin.core;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.jboss.galleon.ProvisioningDescriptionException;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.ProvisioningManager;
import org.jboss.galleon.config.ConfigId;
import org.jboss.galleon.config.ConfigModel;
import org.jboss.galleon.config.FeaturePackConfig;
import org.jboss.galleon.config.ProvisioningConfig;
import org.jboss.galleon.maven.plugin.util.ConfigurationId;
import org.jboss.galleon.maven.plugin.util.FeaturePack;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.jboss.galleon.xml.ProvisioningXmlParser;

/**
 *
 * @author jdenise
 */
public class GalleonConfigBuilder {

    public static final String STANDALONE = "standalone";
    public static final String STANDALONE_XML = "standalone.xml";
    public static final String SERVER_CONFIG = "--server-config";
    public static final String MAVEN_REPO_PLUGIN_OPTION = "jboss-maven-repo";
    public static final String MAVEN_REPO_LOCAL = "maven.repo.local";
    public static final String PLUGIN_PROVISIONING_FILE = ".wildfly-maven-plugin-provisioning.xml";

    public interface GalleonConfig {

        ProvisioningConfig buildConfig() throws ProvisioningException;
    }

    /**
     * Parse provisioning.xml to build the configuration.
     */
    private class ProvisioningFileConfig implements GalleonConfig {

        @Override
        public ProvisioningConfig buildConfig() throws ProvisioningException {
            return ProvisioningXmlParser.parse(getProvisioningFile());
        }
    }

    /**
     * Abstract Galleon config that handles plugin options and build the config
     * based on the state provided by sub class.
     */
    private abstract class AbstractGalleonConfig implements GalleonConfig {

        protected final ConfigModel.Builder configBuilder;

        AbstractGalleonConfig(ConfigModel.Builder configBuilder) throws ProvisioningException {
            Objects.requireNonNull(configBuilder);
            this.configBuilder = configBuilder;
            setupPluginOptions();
        }

        private void setupPluginOptions() throws ProvisioningException {
            // passive+ in all cases
            // For included default config not based on layers, default packages
            // must be included.
            if (pluginOptions.isEmpty()) {
                pluginOptions = Collections.
                        singletonMap(org.jboss.galleon.Constants.OPTIONAL_PACKAGES, org.jboss.galleon.Constants.PASSIVE_PLUS);
            } else {
                if (!pluginOptions.containsKey(org.jboss.galleon.Constants.OPTIONAL_PACKAGES)) {
                    pluginOptions.put(org.jboss.galleon.Constants.OPTIONAL_PACKAGES, org.jboss.galleon.Constants.PASSIVE_PLUS);
                }
                if (pluginOptions.containsKey(MAVEN_REPO_PLUGIN_OPTION)) {
                    String val = pluginOptions.get(MAVEN_REPO_PLUGIN_OPTION);
                    if (val != null) {
                        Path path = Paths.get(val);
                        if (!path.isAbsolute()) {
                            path = resolvePath(project, path);
                            pluginOptions.put(MAVEN_REPO_PLUGIN_OPTION, path.toString());
                        }
                    }
                }
            }
        }

        protected abstract ProvisioningConfig.Builder buildState() throws ProvisioningException;

        @Override
        public ProvisioningConfig buildConfig() throws ProvisioningException {
            ProvisioningConfig.Builder state = buildState();
            state.addConfig(configBuilder.build());
            state.addOptions(pluginOptions);
            return state.build();
        }
    }

    /**
     * Abstract config for config based on added Galleon layers. Parent class of
     * all configuration constructed from Galleon layers + FPL or set of
     * feature-packs.
     */
    private abstract class AbstractLayersConfig extends AbstractGalleonConfig {

        public AbstractLayersConfig() throws ProvisioningDescriptionException, ProvisioningException {
            super(ConfigModel.builder(STANDALONE, STANDALONE_XML));
            for (String layer : layers) {
                configBuilder.includeLayer(layer);
            }

            for (String layer : excludedLayers) {
                configBuilder.excludeLayer(layer);
            }
        }
    }

    /**
     * Galleon layers based config that uses the set of feature-packs.
     */
    private class LayersFeaturePacksConfig extends AbstractLayersConfig {

        private final ProvisioningConfig.Builder state;

        private LayersFeaturePacksConfig(ProvisioningConfig.Builder state) throws ProvisioningDescriptionException, ProvisioningException {
            this.state = state;
        }

        @Override
        public ProvisioningConfig.Builder buildState() throws ProvisioningException {
            return state;
        }
    }

    private static ConfigModel.Builder buildDefaultConfigBuilder(ConfigId defaultConfigId) {
        Objects.requireNonNull(defaultConfigId);
        ConfigModel.Builder configBuilder = ConfigModel.builder(defaultConfigId.getModel(), defaultConfigId.getName());
        configBuilder.setProperty(SERVER_CONFIG, STANDALONE_XML);
        return configBuilder;
    }

    /**
     * Abstract config, parent of all config based on a default configuration.
     * Default configuration can be explicitly included in feature-packs or be a
     * default one (microprofile or microprofile-ha for Cloud). These
     * configurations benefit from layers exclusion and extra layers added by
     * cloud.
     */
    private abstract class AbstractDefaultConfig extends AbstractGalleonConfig {

        private AbstractDefaultConfig(ConfigId defaultConfigId) throws ProvisioningException {
            super(buildDefaultConfigBuilder(defaultConfigId));
            // We can exclude layers from a default config.
            for (String layer : excludedLayers) {
                configBuilder.excludeLayer(layer);
            }
            // We can have layers to add to default config.
            for (String layer : layers) {
                configBuilder.includeLayer(layer);
            }
        }

    }

    /**
     * A config based on the set of feature-packs. Default config is explicitly
     * included or is the default.
     */
    private class DefaultFeaturePacksConfig extends AbstractDefaultConfig {

        private final ProvisioningConfig.Builder state;

        private DefaultFeaturePacksConfig(ConfigId defaultConfigId, ProvisioningConfig.Builder state) throws ProvisioningException {
            super(defaultConfigId);
            Objects.requireNonNull(state);
            this.state = state;
        }

        @Override
        protected ProvisioningConfig.Builder buildState() throws ProvisioningException {
            return state;
        }

    }
    private List<FeaturePack> featurePacks;
    private final File provisioningFile;
    private final String featurePackLocation;
    private final List<String> layers;
    private final List<String> excludedLayers;
    private Map<String, String> pluginOptions;
    private final Log log;
    private final MavenProject project;
    public GalleonConfigBuilder(MavenProject project, Log log,
            List<FeaturePack> featurePacks,
            String featurePackLocation,
            File provisioningFile,
            List<String> layers,
            List<String> excludedLayers,
            Map<String, String> pluginOptions) throws MojoExecutionException {
        this.project = project;
        this.log = log;
        this.featurePacks = featurePacks;
        this.featurePackLocation = featurePackLocation;
        this.provisioningFile = provisioningFile;
        this.layers = layers;
        this.excludedLayers = excludedLayers;
        this.pluginOptions = pluginOptions;
        normalizeFeaturePackList();
    }

    public List<FeaturePack> getFeaturePacks() {
        return featurePacks;
    }

    private void normalizeFeaturePackList() throws MojoExecutionException {
        if (featurePackLocation != null && !featurePacks.isEmpty()) {
            throw new MojoExecutionException("feature-pack-location can't be used with a list of feature-packs");
        }

        if (featurePackLocation != null) {
            featurePacks = new ArrayList<>();
            FeaturePack fp = new FeaturePack();
            fp.setLocation(featurePackLocation);
            featurePacks.add(fp);
        } else {
            for (FeaturePack fp : featurePacks) {
                if (fp.getLocation() == null) {
                    if (fp.getGroupId() == null || fp.getArtifactId() == null) {
                        throw new MojoExecutionException("Invalid Maven coordinates for galleon feature-pack ");
                    }
                }
            }
        }
    }

    public GalleonConfig buildGalleonConfig(ProvisioningManager pm) throws ProvisioningException, MojoExecutionException {
        boolean isLayerBasedConfig = !layers.isEmpty();
        boolean hasFeaturePack = !featurePacks.isEmpty();
        boolean hasProvisioningFile = Files.exists(getProvisioningFile());
        if (!hasFeaturePack && !hasProvisioningFile) {
            throw new ProvisioningException("No valid provisioning configuration, "
                    + "you must set a feature-pack-location, a list of feature-packs or use a provisioning.xml file");
        }

        if (hasFeaturePack && hasProvisioningFile) {
            log.warn("Feature packs defined in pom.xml override provisioning file located in " + getProvisioningFile());
        }

        if (isLayerBasedConfig) {
            if (!hasFeaturePack) {
                throw new ProvisioningException("No server feature-pack location to provision layers, you must set a feature-pack-location");
            }
            return buildFeaturePacksConfig(pm, true);
        }

        // Based on default config
        if (!featurePacks.isEmpty()) {
            log.info("Provisioning server using feature-packs");
            return buildFeaturePacksConfig(pm, isLayerBasedConfig);
        }

        if (hasProvisioningFile) {
            log.info("Provisioning server using " + getProvisioningFile());
            return new ProvisioningFileConfig();
        }
        throw new ProvisioningException("Invalid Galleon configuration");
    }

    private GalleonConfig buildFeaturePacksConfig(ProvisioningManager pm, boolean hasLayers) throws ProvisioningException, MojoExecutionException {
        ProvisioningConfig.Builder state = ProvisioningConfig.builder();
        ConfigId provisionedConfigId = null;
        for (FeaturePack fp : featurePacks) {

            if (fp.getLocation() == null && (fp.getGroupId() == null || fp.getArtifactId() == null)
                    && fp.getNormalizedPath() == null) {
                throw new MojoExecutionException("Feature-pack location, Maven GAV or feature pack path is missing");
            }

            final FeaturePackLocation fpl;
            if (fp.getNormalizedPath() != null) {
                fpl = pm.getLayoutFactory().addLocal(fp.getNormalizedPath(), false);
            } else if (fp.getGroupId() != null && fp.getArtifactId() != null) {
                String coords = getMavenCoords(fp);
                fpl = FeaturePackLocation.fromString(coords);
            } else {
                fpl = FeaturePackLocation.fromString(fp.getLocation());
            }

            final FeaturePackConfig.Builder fpConfig = FeaturePackConfig.builder(fpl);
            if (fp.isInheritConfigs() != null) {
                fpConfig.setInheritConfigs(fp.isInheritConfigs());
            }
            if (fp.isInheritPackages() != null) {
                fpConfig.setInheritPackages(fp.isInheritPackages());
            }

            if (!fp.getExcludedConfigs().isEmpty()) {
                for (ConfigurationId configId : fp.getExcludedConfigs()) {
                    if (configId.isModelOnly()) {
                        fpConfig.excludeConfigModel(configId.getId().getModel());
                    } else {
                        fpConfig.excludeDefaultConfig(configId.getId());
                    }
                }
            }
            if (!fp.getIncludedConfigs().isEmpty()) {
                for (ConfigurationId configId : fp.getIncludedConfigs()) {
                    if (configId.isModelOnly()) {
                        fpConfig.includeConfigModel(configId.getId().getModel());
                    } else {
                        fpConfig.includeDefaultConfig(configId.getId());
                    }
                }
            }

            if (!fp.getIncludedPackages().isEmpty()) {
                for (String includedPackage : fp.getIncludedPackages()) {
                    fpConfig.includePackage(includedPackage);
                }
            }
            if (!fp.getExcludedPackages().isEmpty()) {
                for (String excludedPackage : fp.getExcludedPackages()) {
                    fpConfig.excludePackage(excludedPackage);
                }
            }

            state.addFeaturePackDep(fpConfig.build());
        }
        if (hasLayers) {
            log.info("Provisioning server configuration based on the set of configured layers");
        } else {
            log.info("Provisioning server configuration based on the " + provisionedConfigId.getName() + " default configuration.");
        }
        return hasLayers ? new LayersFeaturePacksConfig(state) : new DefaultFeaturePacksConfig(provisionedConfigId, state);
    }

    private Path getProvisioningFile() {
        return resolvePath(project, provisioningFile.toPath());
    }

    private static String getMavenCoords(FeaturePack fp) {
        StringBuilder builder = new StringBuilder();
        builder.append(fp.getGroupId()).append(":").append(fp.getArtifactId());
        String type = fp.getExtension() == null ? fp.getType() : fp.getExtension();
        if (fp.getClassifier() != null || type != null) {
            builder.append(":").append(fp.getClassifier() == null ? "" : fp.getClassifier()).append(":").append(type == null ? "" : type);
        }
        if (fp.getVersion() != null) {
            builder.append(":").append(fp.getVersion());
        }
        return builder.toString();
    }

    public static Path resolvePath(MavenProject project, Path path) {
        if (!path.isAbsolute()) {
            path = Paths.get(project.getBasedir().getAbsolutePath()).resolve(path);
        }
        return path;
    }
}
