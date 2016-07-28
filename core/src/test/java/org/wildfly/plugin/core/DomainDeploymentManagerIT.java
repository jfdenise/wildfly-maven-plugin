/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2016 Red Hat, Inc., and individual contributors
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

package org.wildfly.plugin.core;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.controller.client.helpers.domain.DomainClient;
import org.jboss.dmr.ModelNode;
import org.jboss.shrinkwrap.api.Archive;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.wildfly.core.launcher.DomainCommandBuilder;
import org.wildfly.core.launcher.ProcessHelper;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class DomainDeploymentManagerIT extends AbstractDeploymentManagerTest {
    private static final String DEFAULT_SERVER_GROUP = "main-server-group";

    @SuppressWarnings("StaticVariableMayNotBeInitialized")
    private static ServerProcess process;
    @SuppressWarnings("StaticVariableMayNotBeInitialized")
    private static DomainClient client;

    @BeforeClass
    public static void startServer() throws Exception {
        boolean ok = false;
        try {
            client = DomainClient.Factory.create(Environment.createClient());
            if (ServerHelper.isDomainRunning(client) || ServerHelper.isStandaloneRunning(client)) {
                Assert.fail("A WildFly server is already running: " + ServerHelper.getContainerDescription(client));
            }
            final DomainCommandBuilder commandBuilder = DomainCommandBuilder.of(Environment.WILDFLY_HOME);
            process = ServerProcess.start(commandBuilder, null, System.out);
            ServerHelper.waitForDomain(client, Environment.TIMEOUT);
            ok = true;
        } finally {
            if (!ok) {
                final Process p = process;
                final ModelControllerClient c = client;
                process = null;
                client = null;
                try {
                    ProcessHelper.destroyProcess(p);
                } finally {
                    safeClose(c);
                }
            }
        }
    }

    @AfterClass
    @SuppressWarnings("StaticVariableUsedBeforeInitialization")
    public static void shutdown() throws Exception {
        try {
            if (client != null) {
                ServerHelper.shutdownDomain(client);
                safeClose(client);
            }
        } finally {
            if (process != null) {
                process.destroy();
                process.waitFor();
            }
        }
    }

    @Test
    public void testFailedDeploy() throws Exception {
        // Expect a failure with no server groups defined
        final Deployment failedDeployment = createDefaultDeployment("test-failed-deployment.war", false);
        assertFailed(deploymentManager.deploy(failedDeployment));
        assertDeploymentDoesNotExist(failedDeployment);
    }

    @Test
    public void testFailedDeployMulti() throws Exception {
        // Expect a failure with no server groups defined
        final Set<Deployment> failedDeployments = new HashSet<>();
        failedDeployments.add(createDefaultDeployment("test-failed-deployment-1.war"));
        failedDeployments.add(createDefaultDeployment("test-failed-deployment-2.war", false));
        assertFailed(deploymentManager.deploy(failedDeployments));
        for (Deployment failedDeployment : failedDeployments) {
            assertDeploymentDoesNotExist(failedDeployment);
        }
    }

    @Test
    public void testFailedForceDeploy() throws Exception {
        // Expect a failure with no server groups defined
        final Deployment failedDeployment = createDefaultDeployment("test-failed-deployment.war", false);
        assertFailed(deploymentManager.forceDeploy(failedDeployment));
        assertDeploymentDoesNotExist(failedDeployment);

    }

    @Test
    public void testFailedRedeploy() throws Exception {
        // Expect a failure with no server groups defined
        assertFailed(deploymentManager.redeploy(createDefaultDeployment("test-redeploy.war", false)));
    }

    @Test
    public void testFailedUndeploy() throws Exception {
        // Undeploy with an additional server-group where the deployment does not exist
        undeployForSuccess(
                UndeployDescription.of("test-undeploy-multi-server-groups.war")
                        .setFailOnMissing(false)
                        .setRemoveContent(false)
                        .addServerGroup("other-server-group")
                , Collections.singleton(DEFAULT_SERVER_GROUP), false);

        // Undeploy with an additional server-group where the deployment does not exist
        final Deployment deployment = createDefaultDeployment("test-undeploy-multi-server-groups-failed.war");
        deployForSuccess(deployment);
        final DeploymentResult result = deploymentManager.undeploy(
                UndeployDescription.of(deployment)
                        .setFailOnMissing(true)
                        .addServerGroup("other-server-group")
        );
        assertFailed(result);
        assertDeploymentExists(deployment, true);
    }

    @Test
    public void testDeploymentQueries() throws Exception {
        Assert.assertTrue("No deployments should exist.", deploymentManager.getDeployments().isEmpty());
        Assert.assertTrue("No deployments should exist.", deploymentManager.getDeploymentNames().isEmpty());
        Assert.assertTrue(String.format("No deployments should exist on %s", DEFAULT_SERVER_GROUP),
                deploymentManager.getDeployments(DEFAULT_SERVER_GROUP).isEmpty());
    }

    @Override
    protected ModelControllerClient getClient() {
        return client;
    }

    @Override
    protected ModelNode createDeploymentResourceAddress(final String deploymentName) throws IOException {
        return ServerHelper.determineHostAddress(getClient())
                .add(ClientConstants.SERVER, "server-one")
                .add(ClientConstants.DEPLOYMENT, deploymentName);
    }

    @Override
    Deployment createDefaultDeployment(final String name) {
        return createDefaultDeployment(name, true);
    }

    @Override
    Deployment createDeployment(final Archive<?> archive) {
        return createDeployment(archive, true);
    }

    private Deployment createDefaultDeployment(final String name, final boolean addServerGroup) {
        return createDeployment(createDefaultArchive(name), addServerGroup);
    }

    private Deployment createDeployment(final Archive<?> archive, final boolean addServerGroup) {
        final Deployment result = super.createDeployment(archive);
        if (addServerGroup) {
            result.addServerGroups(DEFAULT_SERVER_GROUP);
        }
        return result;
    }
}