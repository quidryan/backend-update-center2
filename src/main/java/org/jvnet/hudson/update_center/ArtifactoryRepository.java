package org.jvnet.hudson.update_center;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.*;
import org.sonatype.nexus.index.ArtifactInfo;
import org.sonatype.nexus.index.context.UnsupportedExistingLuceneIndexException;
import us.monoid.web.JSONResource;
import us.monoid.web.Resty;

import javax.xml.bind.DatatypeConverter;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.Map;
import java.util.jar.Manifest;

/**
 * Standard Repository, optimizing with Artifactory REST APIs when possible.
 */
public class ArtifactoryRepository extends MavenRepositoryImpl {
    private URL artifactoryURL = null;
    String repo;
    String context;

    public ArtifactoryRepository() throws Exception {
    }

    protected File resolve(ArtifactInfo a, String type, String classifier) throws AbstractArtifactResolutionException {
        // TODO Hit Artifactory API directory
        return super.resolve(a, type, classifier);
    }

    private void parseFullUrl(URL repoURL) throws MalformedURLException {
        String path = repoURL.getFile();
        int repoIndex = path.lastIndexOf('/');
        if (repoIndex==path.length() - 1) {
            path = path.substring(0, repoIndex);
            repoIndex = path.lastIndexOf('/');
        }
        repo = path.substring(repoIndex+1);
        context = path.substring(0, repoIndex+1);

        this.artifactoryURL = new URL(repoURL, context);
    }

    @Override
    public void addRemoteRepository(String id, File indexDirectory, URL repository) throws IOException, UnsupportedExistingLuceneIndexException {
        if (artifactoryURL!=null) {
            throw new IllegalStateException("Artifactory Repository currently only handles a single remote repository");
        }
        parseFullUrl(repository);
        super.addRemoteRepository(id, indexDirectory, repository);
    }

    String downloadPath(ArtifactInfo a) throws IOException {
        return downloadPath(a,a.packaging, null);
    }

    String downloadPath(ArtifactInfo a, String type, String classifier) throws IOException {
        // Hacky Maven artifact url creation
        StringBuilder sb = new StringBuilder();
        sb.append( a.groupId.replaceAll("\\.", "/") );
        sb.append("/").append(a.artifactId);
        sb.append("/").append(a.version);
        sb.append("/").append(a.artifactId).append("-").append(a.version);
        if (classifier!=null) {
            sb.append("-").append(classifier);
        }
        sb.append(".").append(type);

        return sb.toString();
    }

    @Override
    /**
     * Not using MANIFEST.MF date from hpi file, instead using the date it hit the binary repository.
     */
    protected long loadTimestamp(ArtifactInfo artifactInfo) throws IOException {
        Resty r = new Resty();
        String url = artifactoryURL.toString() + "/api/storage/" + repo + "/" + downloadPath(artifactInfo);
        try {
            String createdStr = r.json(url).get("created").toString();

            Calendar createdCal = DatatypeConverter.parseDateTime(createdStr);
            return createdCal.getTime().getTime();
        } catch(Exception e) {
            throw new IOException(e);
        }
    }

    @Override
    protected String loadDigest(ArtifactInfo artifactInfo) throws IOException {
        Resty r = new Resty();
        try {
            return r.json(artifactoryURL.toString() + "/api/storage/" + repo + "/" + downloadPath(artifactInfo)).get("checksums.sha1").toString();
        } catch(Exception e) {
            throw new IOException(e);
        }
    }

    @Override
    protected Map<String, String> loadManifestAttributes(ArtifactInfo artifactInfo) throws IOException {
        Resty r = new Resty();
        try {
            String url = artifactoryURL.toString() + "/" + repo + "/" + downloadPath(artifactInfo) + "!/META-INF/MANIFEST.MF";
            Manifest manifest = new Manifest(r.bytes(url).stream());
            return loadManifestAttributes(manifest);
        } catch(Exception e) {
            throw new IOException(e);
        }
    }
}
