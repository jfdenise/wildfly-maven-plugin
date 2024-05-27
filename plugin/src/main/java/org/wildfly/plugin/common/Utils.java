/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.plugin.common;

import static org.wildfly.plugin.provision.PackageServerMojo.BOOTABLE_JAR_NAME_RADICAL;
import static org.wildfly.plugin.provision.PackageServerMojo.JAR;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.jboss.galleon.api.GalleonBuilder;
import org.jboss.galleon.api.GalleonFeaturePack;
import org.jboss.galleon.api.Provisioning;
import org.jboss.galleon.api.config.GalleonProvisioningConfig;
import org.jboss.galleon.maven.plugin.util.MvnMessageWriter;
import org.jboss.galleon.universe.maven.repo.MavenRepoManager;
import org.wildfly.glow.Arguments;
import org.wildfly.glow.GlowSession;
import org.wildfly.glow.ScanResults;
import org.wildfly.plugin.provision.ChannelMavenArtifactRepositoryManager;
import org.wildfly.plugin.provision.GlowConfig;
import org.wildfly.plugin.tools.GalleonUtils;
import org.wildfly.plugin.tools.bootablejar.BootableJarSupport;

/**
 * A simple utility class.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class Utils {
    private static final Pattern EMPTY_STRING = Pattern.compile("^$|\\s+");

    private static final Pattern WHITESPACE_IF_NOT_QUOTED = Pattern.compile("(\\S+\"[^\"]+\")|\\S+");

    public static final String WILDFLY_DEFAULT_DIR = "server";

    /**
     * Tests if the character sequence is not {@code null} and not empty.
     *
     * @param seq the character sequence to test
     *
     * @return {@code true} if the character sequence is not {@code null} and not empty
     */
    public static boolean isNotNullOrEmpty(final CharSequence seq) {
        return seq != null && !EMPTY_STRING.matcher(seq).matches();
    }

    /**
     * Tests if the arrays is not {@code null} and not empty.
     *
     * @param array the array to test
     *
     * @return {@code true} if the array is not {@code null} and not empty
     */
    public static boolean isNotNullOrEmpty(final Object[] array) {
        return array != null && array.length > 0;
    }

    /**
     * Converts an iterable to a delimited string.
     *
     * @param iterable  the iterable to convert
     * @param delimiter the delimiter
     *
     * @return a delimited string of the iterable
     */
    public static String toString(final Iterable<?> iterable, final CharSequence delimiter) {
        final StringBuilder result = new StringBuilder();
        final Iterator<?> iterator = iterable.iterator();
        while (iterator.hasNext()) {
            result.append(iterator.next());
            if (iterator.hasNext()) {
                result.append(delimiter);
            }
        }
        return result.toString();
    }

    /**
     * Splits the arguments into a list. The arguments are split based on whitespace while ignoring whitespace that is
     * within quotes.
     *
     * @param arguments the arguments to split
     *
     * @return the list of the arguments
     */
    public static List<String> splitArguments(final CharSequence arguments) {
        final List<String> args = new ArrayList<>();
        final Matcher m = WHITESPACE_IF_NOT_QUOTED.matcher(arguments);
        while (m.find()) {
            final String value = m.group();
            if (!value.isEmpty()) {
                args.add(value);
            }
        }
        return args;
    }

    public static ScanResults scanDeployment(GlowConfig discoverProvisioningInfo,
            List<String> layers,
            List<String> excludedLayers,
            List<GalleonFeaturePack> featurePacks,
            boolean dryRun,
            Log log,
            Path deploymentContent,
            MavenRepoManager artifactResolver,
            Path outputFolder,
            GalleonBuilder pm,
            Map<String, String> galleonOptions,
            String layersConfigurationFileName) throws Exception {
        if (!layers.isEmpty()) {
            throw new MojoExecutionException("layers must be empty when enabling glow");
        }
        if (!excludedLayers.isEmpty()) {
            throw new MojoExecutionException("excluded layers must be empty when enabling glow");
        }
        if (!Files.exists(deploymentContent)) {
            throw new MojoExecutionException("A deployment is expected when enabling glow layer discovery");
        }
        Path inProvisioningFile = null;
        Path glowOutputFolder = outputFolder.resolve("glow-scan");
        Files.createDirectories(glowOutputFolder);
        if (!featurePacks.isEmpty()) {
            GalleonProvisioningConfig in = GalleonUtils.buildConfig(pm, featurePacks, layers, excludedLayers, galleonOptions,
                    layersConfigurationFileName);
            inProvisioningFile = glowOutputFolder.resolve("glow-in-provisioning.xml");
            try (Provisioning p = pm.newProvisioningBuilder(in).build()) {
                p.storeProvisioningConfig(in, inProvisioningFile);
            }
        }
        Arguments arguments = discoverProvisioningInfo.toArguments(deploymentContent, inProvisioningFile,
                layersConfigurationFileName,
                (artifactResolver instanceof ChannelMavenArtifactRepositoryManager
                        ? ((ChannelMavenArtifactRepositoryManager) artifactResolver).getChannelSession()
                        : null));
        log.info("Glow is scanning... ");
        ScanResults results;
        GlowMavenMessageWriter writer = new GlowMavenMessageWriter(log);
        try {
            results = GlowSession.scan(artifactResolver, arguments, writer);
        } catch (Exception ex) {
            throw new MojoExecutionException(ex.getLocalizedMessage(), ex);
        }

        log.info("Glow scanning DONE.");
        try {
            results.outputInformation(writer);
        } catch (Exception ex) {
            results.close();
            throw new MojoExecutionException(ex.getLocalizedMessage(), ex);
        }
        if (!dryRun) {
            results.outputConfig(glowOutputFolder, null);
        }
        if (results.getErrorSession().hasErrors()) {
            if (discoverProvisioningInfo.isFailsOnError()) {
                results.close();
                throw new MojoExecutionException("Error detected by WildFly Glow. Aborting.");
            } else {
                log.warn("Some erros have been identified, check logs.");
            }
        }

        return results;
    }

    public static void packageBootableJar(Path jbossHome,
            GalleonProvisioningConfig activeConfig,
            Log log,
            MavenRepoManager artifactResolver,
            MavenProject project,
            MavenProjectHelper helper,
            String bootableJarInstallArtifactClassifier,
            String bootableJarName) throws Exception {
        String jarName = bootableJarName == null ? BOOTABLE_JAR_NAME_RADICAL + BootableJarSupport.BOOTABLE_SUFFIX + "." + JAR
                : bootableJarName;
        Path targetPath = Paths.get(project.getBuild().getDirectory());
        Path targetJarFile = targetPath.toAbsolutePath()
                .resolve(jarName);
        Files.deleteIfExists(targetJarFile);
        BootableJarSupport.packageBootableJar(targetJarFile, targetPath,
                activeConfig, jbossHome,
                artifactResolver,
                new MvnMessageWriter(log));
        attachJar(targetJarFile, log, project, helper, bootableJarInstallArtifactClassifier);
        log.info("Bootable JAR packaging DONE. To run the server: java -jar " + targetJarFile);
    }

    private static void attachJar(Path jarFile, Log log, MavenProject project, MavenProjectHelper helper,
            String bootableJarInstallArtifactClassifier) {
        if (log.isDebugEnabled()) {
            log.debug("Attaching bootable jar " + jarFile + " as a project artifact with classifier "
                    + bootableJarInstallArtifactClassifier);
        }
        helper.attachArtifact(project, JAR, bootableJarInstallArtifactClassifier, jarFile.toFile());
    }

}
