package com.brambolt.wrench.trigger;

import groovy.lang.Closure;
import groovy.util.Proxy;
import org.gradle.api.GradleException;
import org.gradle.api.Project;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class RepositorySpec {

    public static RepositorySpec create(TriggersExtension extension, String name) {
        return new RepositorySpec(extension, name);
    }

    private final TriggersExtension extension;

    private final String name;

    private String contextUrl;

    private String repoKey;

    private final Map<String, TriggerSpec> triggers = new HashMap<>();

    public RepositorySpec(TriggersExtension extension, String name) {
        this.extension = extension;
        this.name = name;
    }

    public TriggersExtension getExtension() {
        return extension;
    }

    public Project getProject() {
        return getExtension().getProject();
    }

    public String getRepositoryName() {
        return name;
    }

    public Map<String, TriggerSpec> getHostTriggers() {
        return triggers;
    }

    public RepositorySpec configure(Closure<?> closure) {
        closure.setDelegate(createProxy());
        closure.setResolveStrategy(Closure.DELEGATE_FIRST);
        closure.call();
        return this;
    }

    private Proxy createProxy() {
        return new Proxy() {
            public Object getProperty(String name) {
                return RepositorySpec.this.createAndConfigureHost(name);
            }
            public Object methodMissing(String name, Object args) {
                Method[] methods = RepositorySpec.this.getClass().getDeclaredMethods();
                for (Method method: methods) {
                    if (method.getName().equals(name))
                        try {
                            return method.invoke(RepositorySpec.this, (Object[]) args);
                        } catch (Exception x) {
                            throw new GradleException("Invalid repository spec: " + name, x);
                        }
                }
                return RepositorySpec.this.createAndConfigureHost(name, args);
            }
        };
    }

    public TriggerSpec createHost(String name) {
        throwIfHostExists(name);
        TriggerSpec triggerSpec = TriggerSpec.create(this, name);
        triggers.put(name, triggerSpec);
        return triggerSpec;
    }

    public TriggerSpec createAndConfigureHost(String name) {
        return createHost(name).configureTasks();
    }

    public TriggerSpec createAndConfigureHost(String name, Object args) {
        TriggersExtension.throwIfNotClosure(name, args);
        return createHost(name).configure((Closure<?>) ((Object[]) args)[0]);
    }

    private void throwIfHostExists(String name) {
        if (triggers.containsKey(name))
            throw new IllegalStateException(String.format(
                "Repository %s already has a trigger for %s", this.name, name));
    }

    public String getBaseDirectoryPath() {
        return getExtension().getBaseDirectoryPath();
    }

    public String getContextUrl() {
        return contextUrl;
    }

    public void setContextUrl(String contextUrl) {
        this.contextUrl = contextUrl;
    }

    public void contextUrl(String contextUrl) {
        setContextUrl(contextUrl);
    }

    public String getRepoKey() {
        return repoKey;
    }

    public void setRepoKey(String repoKey) {
        this.repoKey = repoKey;
    }

    public void repoKey(String repoKey) {
        this.repoKey = repoKey;
    }

    public String getGroupId() {
        return getExtension().getGroupId();
    }

    public String getReleaseArtifactId() {
        return getExtension().getReleaseArtifactId();
    }

    public String getStagingArtifactId() {
        return getExtension().getStagingArtifactId();
    }

    public String getStagingArtifactType() {
        return getExtension().getStagingArtifactType();
    }

    public String getStagingArtifactPackaging() {
        return getExtension().getStagingArtifactPackaging();
    }

    public String getStagingTask() {
        return getExtension().getStagingTask();
    }

    public String getVersionHistorySize() {
        return getExtension().getVersionHistorySize();
    }
}
