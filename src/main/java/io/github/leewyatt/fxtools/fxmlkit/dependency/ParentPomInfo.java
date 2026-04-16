package io.github.leewyatt.fxtools.fxmlkit.dependency;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Describes the Maven parent POM's dependency-management state for the artifacts
 * relevant to FxmlKit integration. Populated only for Maven projects.
 */
public final class ParentPomInfo {

    private final @Nullable String parentGroupId;
    private final @Nullable String parentArtifactId;
    private final boolean hasDependencyManagement;
    private final boolean managesFxmlKit;
    private final boolean managesJavafxControls;
    private final boolean managesJavafxFxml;
    private final boolean external;
    private final @Nullable VirtualFile parentPomFile;

    public ParentPomInfo(@Nullable String parentGroupId,
                         @Nullable String parentArtifactId,
                         boolean hasDependencyManagement,
                         boolean managesFxmlKit,
                         boolean managesJavafxControls,
                         boolean managesJavafxFxml,
                         boolean external,
                         @Nullable VirtualFile parentPomFile) {
        this.parentGroupId = parentGroupId;
        this.parentArtifactId = parentArtifactId;
        this.hasDependencyManagement = hasDependencyManagement;
        this.managesFxmlKit = managesFxmlKit;
        this.managesJavafxControls = managesJavafxControls;
        this.managesJavafxFxml = managesJavafxFxml;
        this.external = external;
        this.parentPomFile = parentPomFile;
    }

    /**
     * Returns a display label for the parent POM, e.g. "com.example:parent".
     */
    @NotNull
    public String getDisplayLabel() {
        if (parentGroupId != null && parentArtifactId != null) {
            return parentGroupId + ":" + parentArtifactId;
        }
        if (parentArtifactId != null) {
            return parentArtifactId;
        }
        return "parent";
    }

    public boolean hasDependencyManagement() {
        return hasDependencyManagement;
    }

    public boolean managesFxmlKit() {
        return managesFxmlKit;
    }

    public boolean managesJavafxControls() {
        return managesJavafxControls;
    }

    public boolean managesJavafxFxml() {
        return managesJavafxFxml;
    }

    /**
     * Returns true when the parent POM is outside the project tree (e.g., resolved
     * from a remote repository). Such POMs cannot be modified from the IDE.
     */
    public boolean isExternal() {
        return external;
    }

    /**
     * Returns the parent POM file, or null when the parent is external.
     */
    @Nullable
    public VirtualFile getParentPomFile() {
        return parentPomFile;
    }
}
