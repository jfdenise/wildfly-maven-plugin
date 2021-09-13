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

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.xml.stream.XMLStreamException;
import org.apache.maven.plugin.MojoExecutionException;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.ProvisioningManager;
import org.jboss.galleon.config.ProvisioningConfig;
import org.jboss.galleon.maven.plugin.util.Configuration;
import org.jboss.galleon.maven.plugin.util.MavenArtifactRepositoryManager;
import org.jboss.galleon.maven.plugin.util.MvnMessageWriter;
import org.jboss.galleon.xml.ProvisioningXmlWriter;
import static org.wildfly.plugin.core.Constants.PLUGIN_PROVISIONING_FILE;
import org.wildfly.plugin.core.GalleonUtils;
import org.wildfly.plugin.core.Resolver;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

public class GalleonResolver implements Resolver {

    @Override
    public void doit(MavenProject project, Log log, Path target, RepositorySystem repoSystem, RepositorySystemSession repoSession,
            List<RemoteRepository> repositories, Map<String, Object> properties) {

    }

    private void provisionServer(MavenProject project, Log log, Path home, RepositorySystem repoSystem, RepositorySystemSession repoSession,
            List<RemoteRepository> repositories, Map<String, Object> properties) throws ProvisioningException,
            MojoExecutionException, IOException, XMLStreamException {

        MavenArtifactRepositoryManager artifactResolver = offline ? new MavenArtifactRepositoryManager(repoSystem, repoSession)
                : new MavenArtifactRepositoryManager(repoSystem, repoSession, repositories);
        try (ProvisioningManager pm = ProvisioningManager.builder().addArtifactResolver(artifactResolver)
                .setInstallationHome(home)
                .setMessageWriter(new MvnMessageWriter(log))
                .setLogTime(logTime)
                .setRecordState(recordState)
                .build()) {
            log.info("Provisioning server in " + home);
            ProvisioningConfig config = null;
            Path resolvedProvisioningFile = resolvePath(project, provisioningFile.toPath());
            boolean provisioningFileExists = Files.exists(resolvedProvisioningFile);
            if (featurePacks.isEmpty()) {
                if (provisioningFileExists) {
                    getLog().info("Provisioning server using " + resolvedProvisioningFile + " file.");
                    config = GalleonUtils.buildConfig(resolvedProvisioningFile);
                } else {
                    throw new MojoExecutionException("No feature-pack has been configured, can't provision a server.");
                }
            } else {
                if (provisioningFileExists) {
                    log.warn("Galleon provisioning file " + provisioningFile + " is ignored, plugin configuration is used.");
                }
                List<Configuration> serverConfigurations = Collections.emptyList();
                if (serverConfiguration != null) {
                    serverConfigurations = new ArrayList<>();
                    serverConfigurations.add(serverConfiguration);
                }
                config = GalleonUtils.buildConfig(pm, featurePacks, serverConfigurations, pluginOptions);
            }
            pm.provision(config);
            if (!Files.exists(home)) {
                log.error("Invalid galleon provisioning, no server provisioned in " + home);
                throw new MojoExecutionException("Invalid plugin configuration, no server provisioned.");
            }
            if (!recordState) {
                Path file = home.resolve(PLUGIN_PROVISIONING_FILE);
                try (FileWriter writer = new FileWriter(file.toFile())) {
                    ProvisioningXmlWriter.getInstance().write(config, writer);
                }
            }
        }
            
    }

    static Path resolvePath(MavenProject project, Path path) {
        if (!path.isAbsolute()) {
            path = Paths.get(project.getBasedir().getAbsolutePath()).resolve(path);
        }
        return path;
    }
}
