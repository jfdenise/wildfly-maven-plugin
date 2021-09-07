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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.galleon.util.IoUtils;
import org.wildfly.plugin.cli.CommandConfiguration;
import org.wildfly.plugin.cli.CommandExecutor;
import org.wildfly.plugin.common.MavenModelControllerClientConfiguration;
import org.wildfly.plugin.common.PropertyNames;
import static org.wildfly.plugin.core.GalleonUtils.DOMAIN_XML;
import static org.wildfly.plugin.core.GalleonUtils.STANDALONE;
import static org.wildfly.plugin.core.GalleonUtils.STANDALONE_XML;
import org.wildfly.plugin.deployment.PackageType;

/**
 * Provision a server, copy extra content and deploy primary artifact if it
 * exists
 *
 * @author jfdenise
 */
@Mojo(name = "package", requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, defaultPhase = LifecyclePhase.PACKAGE)
public class PackageServerMojo extends AbstractProvisionServerMojo {

    /**
     * The server groups the content should be deployed to.
     */
    @Parameter(alias = "server-groups", property = PropertyNames.SERVER_GROUPS)
    private List<String> serverGroups;

    /**
     * A list of directories to copy content to the provisioned server. If a
     * directory is not absolute, it has to be relative to the project base
     * directory.
     */
    @Parameter(alias="extra-server-content-dirs")
    List<String> extraServerContentDirs = Collections.emptyList();

    /**
     * The CLI commands to execute before the deployment is deployed.
     */
    @Parameter(property = PropertyNames.COMMANDS)
    private List<String> commands = new ArrayList<>();

    /**
     * The CLI script files to execute before the deployment is deployed.
     */
    @Parameter(property = PropertyNames.SCRIPTS)
    private List<File> scripts = new ArrayList<>();

    /**
     * The file name of the application to be deployed.
     * <p>
     * The {@code filename} property does have a default of
     * <code>${project.build.finalName}.${project.packaging}</code>. The default
     * value is not injected as it normally would be due to packaging types like
     * {@code ejb} that result in a file with a {@code .jar} extension rather
     * than an {@code .ejb} extension.
     * </p>
     */
    @Parameter(property = PropertyNames.DEPLOYMENT_FILENAME)
    private String filename;

    /**
     * The name of the server configuration to use when deploying the
     * deployment. Defaults to 'standalone.xml' if no server-groups have been provided otherwise 'domain.xml'.
     */
    @Parameter(property = PropertyNames.SERVER_CONFIG, alias="server-config")
    private String serverConfig;

    /**
     * Specifies the name used for the deployment.
     */
    @Parameter(property = PropertyNames.DEPLOYMENT_NAME)
    private String name;

    /**
     * The runtime name for the deployment.
     * <p>
     * In some cases users may wish to have two deployments with the same
     * {@code runtime-name} (e.g. two versions of {@code example.war}) both
     * available in the management configuration, in which case the deployments
     * would need to have distinct {@code name} values but would have the same
     * {@code runtime-name}.
     * </p>
     */
    @Parameter(property = PropertyNames.DEPLOYMENT_RUNTIME_NAME, alias="runtime-name")
    private String runtimeName;

    /**
     * Indicates how {@code stdout} and {@code stderr} should be handled for the
     * spawned CLI process. Currently a new process is only spawned if
     * {@code offline} is set to {@code true} or {@code fork} is set to
     * {@code true}. Note that {@code stderr} will be redirected to
     * {@code stdout} if the value is defined unless the value is {@code none}.
     * <div>
     * By default {@code stdout} and {@code stderr} are inherited from the
     * current process. You can change the setting to one of the follow:
     * <ul>
     * <li>{@code none} indicates the {@code stdout} and {@code stderr} stream
     * should not be consumed</li>
     * <li>{@code System.out} or {@code System.err} to redirect to the current
     * processes <em>(use this option if you see odd behavior from maven with
     * the default value)</em></li>
     * <li>Any other value is assumed to be the path to a file and the
     * {@code stdout} and {@code stderr} will be written there</li>
     * </ul>
     * </div>
     */
    @Parameter(name = "stdout", defaultValue = "System.out", property = PropertyNames.STDOUT)
    private String stdout;

    @Inject
    private CommandExecutor commandExecutor;

    @Override
    protected void serverProvisioned(Path jbossHome) throws MojoExecutionException, MojoFailureException {
        try {
            if (!extraServerContentDirs.isEmpty()) {
                getLog().info("Copying extra content to server");
                copyExtraContent(jbossHome);
            }
        } catch (IOException ex) {
            throw new MojoExecutionException(ex.getLocalizedMessage(), ex);
        }
        // CLI execution
        if (!commands.isEmpty() || !scripts.isEmpty()) {
            getLog().info("Excuting CLI commands and scripts");
            final CommandConfiguration cmdConfig = CommandConfiguration.of(this::createClient, this::getClientConfiguration)
                    .addCommands(commands)
                    .addScripts(scripts)
                    .setJBossHome(jbossHome)
                    .setFork(true)
                    .setStdout(stdout)
                    .setOffline(true);
            commandExecutor.execute(cmdConfig);
        }
        final Path deploymentContent = getDeploymentContent();
        if (Files.exists(deploymentContent)) {
            getLog().info("Deploying " + deploymentContent);
            List<String> deploymentCommands = getDeploymentCommands(deploymentContent);
            final CommandConfiguration cmdConfigDeployment = CommandConfiguration.of(this::createClient, this::getClientConfiguration)
                    .addCommands(deploymentCommands)
                    .setJBossHome(jbossHome)
                    .setFork(true)
                    .setStdout(stdout)
                    .setOffline(true);
            commandExecutor.execute(cmdConfigDeployment);
        }
        try {
            cleanupServer(jbossHome);
        } catch (IOException ex) {
            throw new MojoExecutionException(ex.getLocalizedMessage(), ex);
        }
    }

    private List<String> getDeploymentCommands(Path deploymentContent) {
        List<String> deploymentCommands = new ArrayList<>();
        StringBuilder deploymentBuilder = new StringBuilder();
        deploymentBuilder.append("deploy  ").append(deploymentContent).append(" --name=").
                append(name == null ? deploymentContent.getFileName() : name).append(" --runtime-name=").
                append(runtimeName == null ? deploymentContent.getFileName() : runtimeName);
        if (serverGroups == null || serverGroups.isEmpty()) {
            serverConfig = serverConfig == null ? STANDALONE_XML: serverConfig;
            deploymentCommands.add("embed-server --server-config=" + serverConfig);
            deploymentCommands.add(deploymentBuilder.toString());
            deploymentCommands.add("stop-embedded-server");
        } else {
            serverConfig = serverConfig == null ? DOMAIN_XML : serverConfig;
            deploymentCommands.add("embed-host-controller --domain-config=" + serverConfig);
            deploymentBuilder.append(" --server-groups=");
            for (int i = 0; i < serverGroups.size(); i++) {
                deploymentBuilder.append(serverGroups.get(i));
                if (i < serverGroups.size() - 1) {
                    deploymentBuilder.append(",");
                }
            }
            deploymentCommands.add(deploymentBuilder.toString());
            deploymentCommands.add("stop-embedded-host-controller");
        }

        return deploymentCommands;
    }

    public void copyExtraContent(Path target) throws MojoExecutionException, IOException {
        for (String path : extraServerContentDirs) {
            Path extraContent = Paths.get(path);
            extraContent = resolvePath(project, extraContent);
            if (Files.notExists(extraContent)) {
                throw new MojoExecutionException("Extra content dir " + extraContent + " doesn't exist");
            }
            // Check for the presence of a standalone.xml file
            warnExtraConfig(extraContent);
            IoUtils.copy(extraContent, target);
        }

    }

    private void warnExtraConfig(Path extraContentDir) {
        Path config = extraContentDir.resolve(STANDALONE).resolve("configurations").resolve(STANDALONE_XML);
        if (Files.exists(config)) {
            getLog().warn("The file " + config + " overrides the Galleon generated configuration, "
                    + "un-expected behavior can occur when starting the server");
        }
    }

    private Path getDeploymentContent() {
        final PackageType packageType = PackageType.resolve(project);
        final String filename;
        if (this.filename == null) {
            filename = String.format("%s.%s", project.getBuild().getFinalName(), packageType.getFileExtension());
        } else {
            filename = this.filename;
        }
        return targetDir.toPath().resolve(filename);
    }

    private static void cleanupServer(Path jbossHome) throws IOException {
        Path history = jbossHome.resolve("standalone").resolve("configuration").resolve("standalone_xml_history");
        IoUtils.recursiveDelete(history);
        Path tmp = jbossHome.resolve("standalone").resolve("tmp");
        IoUtils.recursiveDelete(tmp);
        Path log = jbossHome.resolve("standalone").resolve("log");
        IoUtils.recursiveDelete(log);
    }

    private MavenModelControllerClientConfiguration getClientConfiguration() {
        return null;
    }

    private ModelControllerClient createClient() {
        return null;
    }

    private static Path resolvePath(MavenProject project, Path path) {
        if (!path.isAbsolute()) {
            path = Paths.get(project.getBasedir().getAbsolutePath()).resolve(path);
        }
        return path;
    }
}