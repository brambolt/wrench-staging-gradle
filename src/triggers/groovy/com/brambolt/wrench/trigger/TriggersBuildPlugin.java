package com.brambolt.wrench.trigger;

import com.brambolt.gradle.velocity.tasks.Velocity;
import com.brambolt.gradle.BuildPlugins;
import groovy.lang.Closure;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.publish.maven.MavenArtifact;
import org.gradle.api.tasks.bundling.Zip;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.brambolt.BuildPlugins.configureArtifactPublishing;

/**
 * A Gradle plug-in to build triggers.
 *
 * This plug-in is applied in <code>custom-deploy/custom-triggers</code> to
 * create and publish the triggers for later use.
 *
 * To find the trigger logic implementation, look for <code>TriggerPlugin</code>.
 *
 * The triggers build implemented in this plugin proceeds by first reading the
 * triggers extensions configuration, for example
 * <pre>
 *     triggers {
 *         arion {
 *             aiscalx10
 *             tiscals11
 *         }
 *         brambolt {
 *             brambolt
 *         }
 *     }
 * </pre>
 *
 * The triggers generation task creates this structure in the build directory.
 */
public class TriggersBuildPlugin implements Plugin<Project> {

    public static final String EXTENSION_NAME = "triggers";

    public static void createExtension(Project project) {
        project.getExtensions().create(EXTENSION_NAME, TriggersExtension.class, project);
    }

    public static TriggersExtension getExtension(Project project) {
        return (TriggersExtension) project.getExtensions().getByName(EXTENSION_NAME);
    }

    /**
     * Applies the plug-in to the parameter project.
     *
     * @param project The project to apply the plug-in to
     */
    public void apply(Project project) {
        BuildPlugins.configureCoordinates(project, "Trigger definitions");
        BuildPlugins.configureBasePlugins(project);
        createExtension(project);
        BuildPlugins.configureArtifactory(project, "mavenCustom");
        BuildPlugins.configureMainTasks(project);
    }

    public static void configureTriggerTasks(Project project, TriggerSpec triggerSpec) {
        GenerateTrigger generateTrigger = configureGenerateTriggerTask(project, triggerSpec);
        Velocity velocity = configureVelocityTask(project, triggerSpec, generateTrigger.getName());
        Zip zip = configureZipTask(project, triggerSpec, velocity);
        configureZipPublishing(project, triggerSpec, zip);
    }

    public static GenerateTrigger configureGenerateTriggerTask(Project project, TriggerSpec triggerSpec) {
        return project.getTasks().create(getGenerateTriggerTaskName(triggerSpec), GenerateTrigger.class).configure(triggerSpec);
    }

    public static String getGenerateTriggerTaskName(TriggerSpec triggerSpec) {
        return "generateTrigger_" + triggerSpec.getName();
    }

    public static Velocity configureVelocityTask(Project project, TriggerSpec triggerSpec, String... taskDependencies) {
        return BuildPlugins.configureVelocityTask(
            project,
            getVelocityTaskName(triggerSpec),
            triggerSpec.getTemplatesDir().getAbsolutePath(),
            triggerSpec.getTriggerDir(),
            createVelocityContext(project, triggerSpec),
            taskDependencies);
    }

    public static String getVelocityTaskName(TriggerSpec triggerSpec) {
        return "velocity_" + triggerSpec.getName();
    }

    public static Map<String, Object> createVelocityContext(Project project, TriggerSpec triggerSpec) {
        Map<String, Object> context = new HashMap<>();
        context.put("baseDirectory", triggerSpec.getBaseDirectoryPath());
        context.put("clientName", project.getProperties().get("clientName"));
        context.put("mavenContextUrl", triggerSpec.getRepositoryContextUrl());
        context.put("mavenRepoKey", triggerSpec.getRepositoryKey());
        context.put("release", BuildPlugins.getProductVersion(project));
        context.put("releaseGroupId", triggerSpec.getGroupId());
        context.put("releaseArtifactId", triggerSpec.getReleaseArtifactId());
        context.put("stagingGroupId", triggerSpec.getGroupId());
        context.put("stagingArtifactId", triggerSpec.getStagingArtifactId());
        context.put("stagingArtifactType", triggerSpec.getStagingArtifactType());
        context.put("stagingArtifactPackaging", triggerSpec.getStagingArtifactPackaging());
        context.put("stagingTaskArg", triggerSpec.getStagingTask());
        context.put("systemName", project.getProperties().get("systemName"));
        context.put("bramboltVersion", BuildPlugins.getBramboltVersion(project));
        context.put("versionHistorySize", triggerSpec.getVersionHistorySize());
        return context;
    }

    public static Zip configureZipTask(Project project, TriggerSpec triggerSpec, Velocity velocity) {
        return BuildPlugins.createZipTask(
            project,
            triggerSpec.getName(),
            createZipArchiveFileName(triggerSpec),
            Collections.singletonList(triggerSpec.getTriggerDir().getAbsolutePath()),
            Collections.singletonList(velocity.getName()));
    }

    public static String createZipArchiveFileName(TriggerSpec triggerSpec) {
        return triggerSpec.getName() + ".zip";
    }

    public static void configureZipPublishing(Project project, TriggerSpec triggerSpec, Zip zip) {
        Configuration configuration = project.getConfigurations().maybeCreate(triggerSpec.getName());
        PublishArtifact artifact = BuildPlugins.addZipArtifact(project, zip, configuration.getName());
        configureArtifactPublishing(project, artifact, new Closure<Void>(artifact) {
            @Override
            public Void call() {
                MavenArtifact delegate = (MavenArtifact) getDelegate();
                delegate.setClassifier(triggerSpec.getName());
                return null;
            }
        });
    }
}

