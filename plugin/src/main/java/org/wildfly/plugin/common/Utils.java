/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.wildfly.plugin.common;

import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.jboss.galleon.ProvisioningManager;
import org.jboss.galleon.config.ProvisioningConfig;
import org.jboss.galleon.universe.maven.repo.MavenRepoManager;
import org.jboss.galleon.xml.ProvisioningXmlWriter;
import org.wildfly.glow.Arguments;
import org.wildfly.glow.GlowSession;
import org.wildfly.glow.ScanResults;
import org.wildfly.plugin.core.FeaturePack;
import org.wildfly.plugin.core.GalleonUtils;
import org.wildfly.plugin.provision.GlowConfig;

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
            List<FeaturePack> featurePacks,
            boolean dryRun,
            Log log,
            Path deploymentContent,
            MavenRepoManager artifactResolver,
            Path outputFolder,
            ProvisioningManager pm,
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
            ProvisioningConfig in = GalleonUtils.buildConfig(pm, featurePacks, layers, excludedLayers, galleonOptions,
                    layersConfigurationFileName);
            inProvisioningFile = glowOutputFolder.resolve("glow-in-provisioning.xml");
            try (FileWriter fileWriter = new FileWriter(inProvisioningFile.toFile())) {
                ProvisioningXmlWriter.getInstance().write(in, fileWriter);
            }
        }
        Arguments arguments = discoverProvisioningInfo.toArguments(deploymentContent, inProvisioningFile);
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
            throw new MojoExecutionException(ex.getLocalizedMessage(), ex);
        }
        if (!dryRun) {
            results.outputConfig(glowOutputFolder, false);
        }
        if (results.getErrorSession().hasErrors()) {
            if (discoverProvisioningInfo.isFailsOnError()) {
                throw new MojoExecutionException("Error detected by glow. Aborting.");
            } else {
                log.warn("Some erros have been identified, check logs.");
            }
        }

        return results;
    }
}
