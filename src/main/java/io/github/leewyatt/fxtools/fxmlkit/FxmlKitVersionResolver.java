package io.github.leewyatt.fxtools.fxmlkit;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.util.io.HttpRequests;
import io.github.leewyatt.fxtools.FxToolsBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Resolves the latest released FxmlKit version from Maven Central's standard
 * {@code maven-metadata.xml}. The result is cached for the lifetime of the IDE
 * process. On any failure (offline, timeout, parse error) returns a bundled
 * fallback version.
 */
public final class FxmlKitVersionResolver {

    private static final Logger LOG = Logger.getInstance(FxmlKitVersionResolver.class);

    /** Bundled fallback used when Maven Central is unreachable or the response cannot be parsed. */
    public static final String FALLBACK_VERSION = "1.5.1";

    private static final String METADATA_URL =
            "https://repo1.maven.org/maven2/com/dlsc/fxmlkit/fxmlkit/maven-metadata.xml";
    private static final int CONNECT_TIMEOUT_MS = 3000;
    private static final int READ_TIMEOUT_MS = 3000;

    private static volatile String cachedVersion;

    private FxmlKitVersionResolver() {
    }

    /**
     * Returns the latest released FxmlKit version. Blocks the EDT with a modal
     * progress dialog while the HTTP fetch is in flight. Subsequent calls in the
     * same IDE session return the cached value without any network activity.
     */
    @NotNull
    public static String resolveVersion(@Nullable Project project) {
        String cached = cachedVersion;
        if (cached != null) {
            return cached;
        }
        AtomicReference<String> fetched = new AtomicReference<>();
        ProgressManager.getInstance().run(new Task.Modal(
                project,
                FxToolsBundle.message("fxmlkit.version.checking"),
                true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                fetched.set(fetchLatestFromMaven());
            }
        });
        String resolved = fetched.get();
        if (resolved == null || resolved.isBlank()) {
            resolved = FALLBACK_VERSION;
        }
        cachedVersion = resolved;
        return resolved;
    }

    @Nullable
    private static String fetchLatestFromMaven() {
        try {
            String xml = HttpRequests.request(METADATA_URL)
                    .connectTimeout(CONNECT_TIMEOUT_MS)
                    .readTimeout(READ_TIMEOUT_MS)
                    .readString();
            return parseLatest(xml);
        } catch (Exception e) {
            LOG.info("Failed to fetch FxmlKit version from Maven Central, using fallback", e);
            return null;
        }
    }

    @Nullable
    private static String parseLatest(@NotNull String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(xml)));
            String release = firstNodeText(doc.getElementsByTagName("release"));
            if (release != null) {
                return release;
            }
            String latest = firstNodeText(doc.getElementsByTagName("latest"));
            if (latest != null) {
                return latest;
            }
            NodeList versions = doc.getElementsByTagName("version");
            if (versions.getLength() > 0) {
                String last = versions.item(versions.getLength() - 1).getTextContent();
                if (last != null && !last.isBlank()) {
                    return last.trim();
                }
            }
        } catch (Exception e) {
            LOG.info("Failed to parse maven-metadata.xml", e);
        }
        return null;
    }

    @Nullable
    private static String firstNodeText(@NotNull NodeList nodes) {
        if (nodes.getLength() == 0) {
            return null;
        }
        String text = nodes.item(0).getTextContent();
        if (text == null || text.isBlank()) {
            return null;
        }
        return text.trim();
    }
}
