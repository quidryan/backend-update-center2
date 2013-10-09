package org.jvnet.hudson.update_center;

import hudson.util.VersionNumber;
import org.apache.commons.codec.binary.Base64;
import org.apache.maven.artifact.resolver.AbstractArtifactResolutionException;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.sonatype.nexus.index.ArtifactInfo;
import org.sonatype.nexus.index.context.UnsupportedExistingLuceneIndexException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

/**
 * A collection of artifacts from which we build index.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class MavenRepository {
    /**
     * Discover all plugins from this Maven repository.
     */
    public abstract Collection<PluginHistory> listHudsonPlugins() throws PlexusContainerException, ComponentLookupException, IOException, UnsupportedExistingLuceneIndexException, AbstractArtifactResolutionException;

    /**
     * Discover all plugins from this Maven repository in order released, not using PluginHistory.
     */
    public Map<Date,Map<String,HPI>> listHudsonPluginsByReleaseDate() throws PlexusContainerException, ComponentLookupException, IOException, UnsupportedExistingLuceneIndexException, AbstractArtifactResolutionException {
        Collection<PluginHistory> all = listHudsonPlugins();

        Map<Date, Map<String,HPI>> plugins = new TreeMap<Date, Map<String,HPI>>();

        for (PluginHistory p : all) {
            for (HPI h : p.artifacts.values()) {
                try {
                    Date releaseDate = h.getTimestampAsDate();
                    System.out.println("adding " + h.artifact.artifactId + ":" + h.version);
                    Map<String,HPI> pluginsOnDate = plugins.get(releaseDate);
                    if (pluginsOnDate==null) {
                        pluginsOnDate = new TreeMap<String,HPI>();
                        plugins.put(releaseDate, pluginsOnDate);
                    }
                    pluginsOnDate.put(p.artifactId,h);
                } catch (IOException e) {
                    // if we fail to resolve artifact, move on
                    e.printStackTrace();
                }
            }
        }

        return plugins;
    }

    /**
     * find the HPI for the specified plugin
     * @return the found HPI or null
     */
    public HPI findPlugin(String groupId, String artifactId, String version) throws PlexusContainerException, ComponentLookupException, IOException, UnsupportedExistingLuceneIndexException, AbstractArtifactResolutionException {
        Collection<PluginHistory> all = listHudsonPlugins();

        for (PluginHistory p : all) {
            for (HPI h : p.artifacts.values()) {
                if (h.isEqualsTo(groupId, artifactId, version))
                  return h;
            }
        }
        return null;
    }


    /**
     * Discover all hudson.war versions.
     */
    public abstract TreeMap<VersionNumber,HudsonWar> getHudsonWar() throws IOException, AbstractArtifactResolutionException;

    protected File resolve(ArtifactInfo a) throws AbstractArtifactResolutionException {
        return resolve(a,a.packaging, null);
    }

    /**
     * Converts AbstractArtifactResolutionException to IOException
     * @param artifactInfo
     * @return resolved file
     * @throws IOException
     */
    protected File ioResolve(ArtifactInfo artifactInfo) throws IOException {
        try {
            return resolve(artifactInfo);
        } catch(AbstractArtifactResolutionException aare) {
            throw new IOException(aare);
        }
    }

    /**
     * Load timestamp.
     * @throws IOException
     */
    protected long loadTimestamp(ArtifactInfo artifactInfo) throws IOException {
        File f = ioResolve(artifactInfo);

        JarFile jar = null;
        try {
            jar = new JarFile(f);
            ZipEntry e = jar.getEntry("META-INF/MANIFEST.MF");
            return e.getTime();
        } catch (IOException x) {
            throw (IOException) new IOException("Failed to open "+f).initCause(x);
        } finally {
            if (jar !=null) {
                jar.close();
            }
        }
    }

    protected String loadDigest(ArtifactInfo artifactInfo) throws IOException {
        File f = ioResolve(artifactInfo);

        try {
            MessageDigest sig = MessageDigest.getInstance("SHA1");
            FileInputStream fin = new FileInputStream(f);
            byte[] buf = new byte[2048];
            int len;
            while ((len=fin.read(buf,0,buf.length))>=0)
                sig.update(buf,0,len);

            return new String(Base64.encodeBase64(sig.digest()));
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    /**
     * Load manifest attributes as a Map, do not mutate anything.
     * @throws IOException
     */
    protected Map<String, String> loadManifestAttributes(ArtifactInfo artifactInfo) throws IOException {
        File f = ioResolve(artifactInfo);
        Manifest manifest = loadManifest(f);
        return loadManifestAttributes(manifest);
    }

    protected Map<String, String> loadManifestAttributes(Manifest manifest) throws IOException {
        Map<String, String> attrMap = new TreeMap<String, String>();
        for(Map.Entry entry: manifest.getMainAttributes().entrySet()) {
            attrMap.put(entry.getKey().toString(), entry.getValue().toString());
        }
        return attrMap;
    }

    public static Manifest loadManifest(File f) throws IOException {
        JarFile jar = null;
        try {
            jar = new JarFile(f);
            return jar.getManifest();
        } catch (IOException x) {
            throw (IOException) new IOException("Failed to open "+f).initCause(x);
        } finally {
            if (jar!=null) {
                jar.close();
            }
        }
    }

    protected abstract File resolve(ArtifactInfo a, String type, String classifier) throws AbstractArtifactResolutionException;
}
