package com.brambolt.wrench.trigger;

import groovy.lang.Closure;
import org.gradle.api.GradleException;
import org.gradle.api.Project;

import java.util.HashMap;
import java.util.Map;

public class TriggersExtension {

    public static final Integer DEFAULT_VERSION_HISTORY_SIZE = 2;

    private final Project project;

    private String baseDirectoryPath;

    private String releaseArtifactId;

    private String stagingArtifactId;

    private String stagingArtifactType = "zip";

    private String stagingArtifactPackaging = null;

    private String stagingTask;

    private Integer versionHistorySize;

    private final Map<String, RepositorySpec> repositories = new HashMap<>();

    public TriggersExtension(Project project) {
        this.project = project;
    }

    public Project getProject() {
        return project;
    }

    public Map<String, RepositorySpec> getRepositories() {
        return repositories;
    }

    Object methodMissing(String name, Object args) {
        throwIfNotClosure(name, args);
        return configureRepository(name, (Closure<?>) ((Object[]) args)[0]);
    }

    Object configureRepository(String name, Closure<?> closure) {
        throwIfRepositoryExists(name);
        repositories.put(name, RepositorySpec.create(this, name).configure(closure));
        return this;
    }

    static void throwIfNotClosure(String name, Object args) {
        if (!(args instanceof Object[]))
            throwNotClosure(name, args);
        Object[] array = (Object[]) args;
        if (1 != array.length)
            throwNotClosure(name, array);
        if (!(array[0] instanceof Closure))
            throwNotClosure(name, args);
    }

    static void throwNotClosure(String name, Object args) {
        throw new IllegalArgumentException(String.format(
            "%s must be configured with a closure, got %s [class %s]",
            name,
            null == args ? "null" : args.toString(),
            null == args ? "null" : args.getClass().getCanonicalName()));
    }

    void throwIfRepositoryExists(String name) {
        if (repositories.containsKey(name))
            throw new IllegalStateException("Repository " + name + " exists already");
    }

    public String getBaseDirectoryPath() {
        return baseDirectoryPath;
    }

    public void setBaseDirectoryPath(String baseDirectoryPath) {
        this.baseDirectoryPath = baseDirectoryPath;
    }

    public void baseDirectory(String path) {
        setBaseDirectoryPath(path);
    }

    public String getGroupId() {
        return getProject().getGroup().toString();
    }

    public String getReleaseArtifactId() {
        return releaseArtifactId;
    }

    public void setReleaseArtifactId(String artifactId) {
        this.releaseArtifactId = artifactId;
    }

    public void releaseArtifact(String artifactId) {
        this.releaseArtifactId = artifactId;
    }

    public String getStagingArtifactId() {
        return stagingArtifactId;
    }

    public void setStagingArtifactId(String artifactId) {
        this.stagingArtifactId = artifactId;
    }

    public void stagingArtifact(String artifactId) {
        setStagingArtifactId(artifactId);
    }

    public String getStagingArtifactType() {
        return stagingArtifactType;
    }

    public void setStagingArtifactType(String type) {
        this.stagingArtifactType = type;
    }

    public void stagingArtifactType(String type) {
        setStagingArtifactType(type);
    }

    public String getStagingArtifactPackaging() {
        return stagingArtifactPackaging;
    }

    public void setStagingArtifactPackaging(String packaging) {
        stagingArtifactPackaging = packaging;
    }

    public void stagingArtifactPackaging(String packaging) {
        setStagingArtifactPackaging(packaging);
    }

    public String getStagingTask() {
        return stagingTask;
    }

    public void setStagingTask(String taskName) {
        this.stagingTask = taskName;
    }

    public void stagingTask(String taskName) {
        setStagingTask(taskName);
    }

    public String getVersionHistorySize() {
        return Integer.toString(null != versionHistorySize ? versionHistorySize : DEFAULT_VERSION_HISTORY_SIZE);
    }

    public void setVersionHistorySize(Integer size) {
        this.versionHistorySize = size;
    }

    public void versionHistorySize(Integer size) {
        setVersionHistorySize(size);
    }

    public void setVersionHistorySize(String versionHistorySize) {
        try {
            this.versionHistorySize = Integer.parseInt(versionHistorySize);
        } catch (NumberFormatException x) {
            throw new GradleException("Unable to parse version history size value", x);
        }
    }

    public void versionHistorySize(String size) {
        setVersionHistorySize(size);
    }
}

