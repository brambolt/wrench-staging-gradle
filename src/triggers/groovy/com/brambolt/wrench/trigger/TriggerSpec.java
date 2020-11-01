package com.brambolt.wrench.trigger;

import groovy.lang.Closure;
import org.gradle.api.GradleException;
import org.gradle.api.Project;

import java.io.File;

public class TriggerSpec {

    public static TriggerSpec create(RepositorySpec repositorySpec, String hostname) {
        return new TriggerSpec(repositorySpec, hostname);
    }

    private static String createName(String repositoryName, String hostname) {
        return repositoryName + hostname.substring(0, 1).toUpperCase() + hostname.substring(1);
    }

    private final RepositorySpec repositorySpec;

    private final String hostname;

    private final String name;

    private Integer versionHistorySize;

    public TriggerSpec(RepositorySpec repositorySpec, String hostname) {
        this.repositorySpec = repositorySpec;
        this.hostname = hostname;
        this.name = createName(repositorySpec.getRepositoryName(), hostname);
    }

    public RepositorySpec getRepositorySpec() {
        return repositorySpec;
    }

    public Project getProject() {
        return getRepositorySpec().getProject();
    }

    public File getBuildDir() {
        return getProject().getBuildDir();
    }

    public String getHostname() {
        return hostname;
    }

    public String getName() {
        return name;
    }

    public File getTriggerDir() {
        return getTriggerDir(new File(getBuildDir(), "triggers"));
    }

    public File getTemplatesDir() {
        return getTriggerDir(new File(getBuildDir(), "vtl"));
    }

    public File getTriggerDir(File baseDir) {
        RepositorySpec repositorySpec = getRepositorySpec();
        File repositoryDir = new File(baseDir, repositorySpec.getRepositoryName());
        return new File(repositoryDir, getHostname());
    }

    public TriggerSpec configure(Closure<?> closure) {
        closure.setDelegate(this);
        closure.setResolveStrategy(Closure.DELEGATE_FIRST);
        closure.call();
        return configureTasks();
    }

    public TriggerSpec configureTasks() {
        TriggersBuildPlugin.configureTriggerTasks(getProject(), this);
        return this;
    }

    public String getBaseDirectoryPath() {
        return getRepositorySpec().getBaseDirectoryPath();
    }
    public String getRepositoryContextUrl() {
        return getRepositorySpec().getContextUrl();
    }

    public String getRepositoryKey() {
        return getRepositorySpec().getRepoKey();
    }

    public String getGroupId() {
        return getRepositorySpec().getGroupId();
    }

    public String getReleaseArtifactId() {
        return getRepositorySpec().getReleaseArtifactId();
    }

    public String getStagingArtifactId() {
        return getRepositorySpec().getStagingArtifactId();
    }

    public String getStagingArtifactType() {
        return getRepositorySpec().getStagingArtifactType();
    }

    public String getStagingArtifactPackaging() {
        return getHostname();
    }

    public String getStagingTask() {
        return getRepositorySpec().getStagingTask();
    }

    public String getVersionHistorySize() {
        return null != versionHistorySize
            ? Integer.toString(versionHistorySize)
            : getRepositorySpec().getVersionHistorySize();
    }

    public void setVersionHistorySize(String versionHistorySize) {
        try {
            this.versionHistorySize = Integer.parseInt(versionHistorySize);
        } catch (NumberFormatException x) {
            throw new GradleException("Unable to parse version history size value", x);
        }
    }
}
