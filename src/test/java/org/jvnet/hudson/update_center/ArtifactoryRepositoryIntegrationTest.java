package org.jvnet.hudson.update_center;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import org.sonatype.nexus.index.ArtifactInfo;

import java.io.File;
import java.net.URL;
import java.util.Map;

/**
 * If available locally, nexus-maven-repository-index.gz, will be used.
 */
public class ArtifactoryRepositoryIntegrationTest {
    ArtifactoryRepository instance;
    ArtifactInfo a;

    @Before
    public void setUp() throws Exception {
        instance = new ArtifactoryRepository();
        File index = new File("nexus-maven-repository-index.gz");
        URL url = new URL("http://repo.jenkins-ci.org/public/");
        if (index.exists()) {
            instance.addRemoteRepository("public", index.toURL(), url);
        } else {
            instance.addRemoteRepository("public", url);
        }

        a = new ArtifactInfo("", "org.jenkins-ci.plugins", "job-dsl", "1.18", null);
        a.packaging = "jpi";
    }

    @Test
    public void testDownloadPath() throws Exception {
        String path = instance.downloadPath(a);
        assertEquals("org/jenkins-ci/plugins/job-dsl/1.18/job-dsl-1.18.jpi", path);
    }

    @Test
    public void testLoadDigest() throws Exception {
        String sha = instance.loadDigest(a);
        assertEquals("54d78df11e40924a65669e2d5340c3c2328338ca", sha);
    }

    @Test
    public void testLoadManifest() throws Exception {
        Map<String, String> attrs = instance.loadManifestAttributes(a);
        assertEquals("Job DSL", attrs.get("Long-Name"));
        assertEquals("1.18", attrs.get("Plugin-Version"));
    }

    @Test
    public void testLoadTimestamp() throws Exception {
        long timestamp = instance.loadTimestamp(a);
        assertEquals(1381175981063L, timestamp);
        // assertEquals(1381175968000L, timestamp); // Date from zip file
    }

}
