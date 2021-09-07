/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates
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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.xml.stream.XMLStreamException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.ProvisioningManager;
import org.jboss.galleon.config.ProvisioningConfig;
import org.jboss.galleon.maven.plugin.util.Configuration;
import org.jboss.galleon.maven.plugin.util.FeaturePack;
import org.jboss.galleon.maven.plugin.util.MavenArtifactRepositoryManager;
import org.jboss.galleon.maven.plugin.util.MvnMessageWriter;
import org.jboss.galleon.universe.maven.repo.MavenRepoManager;
import org.jboss.galleon.util.IoUtils;
import org.jboss.galleon.xml.ProvisioningXmlWriter;
import org.wildfly.plugin.common.PropertyNames;
import org.wildfly.plugin.common.Utils;
import org.wildfly.plugin.core.GalleonUtils;
import static org.wildfly.plugin.core.GalleonUtils.PLUGIN_PROVISIONING_FILE;
import org.wildfly.plugin.core.MavenRepositoriesEnricher;


/**
 * Provision a server
 *
 * @author jfdenise
 */
abstract class AbstractProvisionServerMojo extends AbstractMojo {

    @Component
    RepositorySystem repoSystem;

    @Component
    MavenProjectHelper projectHelper;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    RepositorySystemSession repoSession;

    @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true, required = true)
    List<RemoteRepository> repositories;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    MavenSession session;

    /**
     * Arbitrary Galleon options used when provisioning the server. In case you
     * are building a large amount of server in the same maven session, it
     * is strongly advised to set 'jboss-fork-embedded' option to 'true' in
     * order to fork Galleon provisioning and CLI scripts execution in dedicated
     * processes. For example:
     * <br/>
     * &lt;plugin-options&gt;<br/>
     * &lt;jboss-fork-embedded&gt;true&lt;/jboss-fork-embedded&gt;<br/>
     * &lt;/plugin-options&gt;
     */
    @Parameter(required = false, alias = "plugin-options")
    Map<String, String> pluginOptions = Collections.emptyMap();

    /**
     * Whether to use offline mode when the plugin resolves an artifact. In
     * offline mode the plugin will only use the local Maven repository for an
     * artifact resolution.
     */
    @Parameter(defaultValue = "false")
    boolean offline;

    /**
     * Whether to log provisioning time at the end
     */
    @Parameter(alias = "log-time", defaultValue = "false")
    boolean logTime;

    /**
     * Whether to record provisioned state in .galleon directory.
     */
    @Parameter(alias = "record-state", defaultValue = "false")
    boolean recordState;

    /**
     * Set to {@code true} if you want the goal to be skipped, otherwise
     * {@code false}.
     */
    @Parameter(defaultValue = "false", property = PropertyNames.SKIP)
    boolean skip;

    /**
     * The directory name inside the buildDir where to provision the server. By default the server is provisioned into the 'server' directory.
     */
    @Parameter(alias="provision-directory-name", property = PropertyNames.WILDFLY_PROVISION_DIRECTORY_NAME, defaultValue = Utils.WILDFLY_DEFAULT_DIR)
    private String provisionDirectoryName;

    /**
     * A list of feature-pack configurations to install, can be combined with
     * layers.
     */
    @Parameter(required = false, alias= "feature-packs")
    List<FeaturePack> featurePacks = Collections.emptyList();

    /**
     * A list of custom configurations to install.
     */
    @Parameter(required = false)
    private List<Configuration> configurations = Collections.emptyList();

    /**
     * The target directory the application to be deployed is located.
     */
    @Parameter(defaultValue = "${project.build.directory}/", property = PropertyNames.DEPLOYMENT_TARGET_DIR)
    protected File targetDir;

    private Path wildflyDir;

    private MavenRepoManager artifactResolver;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().debug(String.format("Skipping run of %s:%s", project.getGroupId(), project.getArtifactId()));
            return;
        }
        MavenRepositoriesEnricher.enrich(session, project, repositories);
        artifactResolver = offline ? new MavenArtifactRepositoryManager(repoSystem, repoSession)
                : new MavenArtifactRepositoryManager(repoSystem, repoSession, repositories);

        wildflyDir = targetDir.toPath().resolve(provisionDirectoryName);
        IoUtils.recursiveDelete(wildflyDir);

        try {
            provisionServer(wildflyDir);
        } catch (ProvisioningException | IOException | XMLStreamException ex) {
            throw new MojoExecutionException("Provisioning failed", ex);
        }
        serverProvisioned(wildflyDir);
    }

    protected abstract void serverProvisioned(Path jbossHome) throws MojoExecutionException, MojoFailureException;

    private void provisionServer(Path home) throws ProvisioningException,
            MojoExecutionException, IOException, XMLStreamException {
        try (ProvisioningManager pm = ProvisioningManager.builder().addArtifactResolver(artifactResolver)
                .setInstallationHome(home)
                .setMessageWriter(new MvnMessageWriter(getLog()))
                .setLogTime(logTime)
                .setRecordState(recordState)
                .build()) {
            getLog().info("Provisioning server in " + home);
            ProvisioningConfig config = GalleonUtils.buildConfig(pm, featurePacks, configurations, pluginOptions);
            pm.provision(config);
            if (!recordState) {
                Path file = home.resolve(PLUGIN_PROVISIONING_FILE);
                try (FileWriter writer = new FileWriter(file.toFile())) {
                    ProvisioningXmlWriter.getInstance().write(config, writer);
                }
            }
        }
    }
}