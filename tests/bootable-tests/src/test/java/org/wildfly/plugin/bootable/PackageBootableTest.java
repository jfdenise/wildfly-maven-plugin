/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.plugin.bootable;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.maven.plugin.Mojo;
import org.junit.Test;
import org.wildfly.plugin.tests.AbstractProvisionConfiguredMojoTestCase;
import org.wildfly.plugin.tests.AbstractWildFlyMojoTest;

public class PackageBootableTest extends AbstractProvisionConfiguredMojoTestCase {

    private static final String BOOTABLE_JAR_NAME = "server-bootable.jar";

    public PackageBootableTest() {
        super("wildfly-maven-plugin");
    }

    @Test
    public void testBootablePackage() throws Exception {

        final Mojo packageMojo = lookupConfiguredMojo(
                AbstractWildFlyMojoTest.getPomFile("package-bootable-pom.xml").toFile(), "package");
        packageMojo.execute();
        String[] layers = { "jaxrs-server" };
        String deploymentName = "test.war";
        checkJar(AbstractWildFlyMojoTest.getBaseDir(), BOOTABLE_JAR_NAME, deploymentName,
                true, layers, null, true);
        checkDeployment(AbstractWildFlyMojoTest.getBaseDir(), BOOTABLE_JAR_NAME, "test");
    }

    @Test
    public void testBootableRootPackage() throws Exception {

        final Mojo packageMojo = lookupConfiguredMojo(
                AbstractWildFlyMojoTest.getPomFile("package-bootable-root-pom.xml").toFile(), "package");
        String deploymentName = "ROOT.war";
        Path rootWar = AbstractWildFlyMojoTest.getBaseDir().resolve("target").resolve(deploymentName);
        Path testWar = AbstractWildFlyMojoTest.getBaseDir().resolve("target").resolve("test.war");
        Files.copy(testWar, rootWar);
        Files.delete(testWar);
        packageMojo.execute();
        String[] layers = { "jaxrs-server" };
        String fileName = "jar-root.jar";
        checkJar(AbstractWildFlyMojoTest.getBaseDir(), fileName, deploymentName, true, layers, null, true);
        checkDeployment(AbstractWildFlyMojoTest.getBaseDir(), fileName, null);
    }

    @Test
    public void testGlowPackage() throws Exception {

        final Mojo packageMojo = lookupConfiguredMojo(
                AbstractWildFlyMojoTest.getPomFile("package-bootable-glow-pom.xml").toFile(), "package");
        String[] layers = { "ee-core-profile-server", "microprofile-openapi" };
        packageMojo.execute();
        String deploymentName = "test.war";
        checkJar(AbstractWildFlyMojoTest.getBaseDir(), BOOTABLE_JAR_NAME, deploymentName,
                true, layers, null, true);
    }
}
