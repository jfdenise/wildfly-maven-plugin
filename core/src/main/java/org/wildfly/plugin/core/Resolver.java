/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.wildfly.plugin.core;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject; 
/**
 *
 * @author jdenise
 */
public interface Resolver {
    public void doit(MavenProject project, 
            Log log, Path target, RepositorySystem repoSystem, RepositorySystemSession repoSession, 
            List<RemoteRepository> repositories, Map<String, Object> properties) throws Exception ;
}
