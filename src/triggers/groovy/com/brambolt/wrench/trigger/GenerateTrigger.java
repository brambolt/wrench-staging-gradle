package com.brambolt.wrench.trigger;

import groovy.lang.Closure;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecSpec;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

import static com.brambolt.util.Resources.stream;

class GenerateTrigger extends DefaultTask {

    private TriggerSpec triggerSpec;

    public GenerateTrigger() {}

    public TriggerSpec getTriggerSpec() {
        return triggerSpec;
    }

    public void setTriggerSpec(TriggerSpec triggerSpec) {
        this.triggerSpec = triggerSpec;
    }

    public GenerateTrigger configure(TriggerSpec triggerSpec) {
        setTriggerSpec(triggerSpec);
        return this;
    }

    @TaskAction
    void apply() {
        apply(getTriggerSpec().getTriggerDir(), getTriggerSpec().getTemplatesDir());
    }

    void apply(File triggerDir, File templatesDir) {
        copyGradleWrapper(triggerDir);
        copyTemplates(templatesDir);
    }

    void copyGradleWrapper(File destinationDir) {
        Logger logger = getProject().getLogger();
        Arrays.asList(
            "gradlew", "gradlew.bat",
            "gradle/wrapper/gradle-wrapper.jar",
            "gradle/wrapper/gradle-wrapper.properties")
            .forEach(relativePath -> copyTriggerResource(relativePath, destinationDir, logger));
        getProject().exec(new Closure<Void>(getProject()) {
            @Override
            public Void call() {
                ExecSpec spec = (ExecSpec) getDelegate();
                spec.commandLine("chmod", "+x", "gradlew");
                spec.workingDir(destinationDir);
                return null;
            }
        });
    }

    void copyTemplates(File destinationDir) {
        Logger logger = getProject().getLogger();
        Arrays.asList("build.gradle.vtl", "gradle.properties.vtl", "settings.gradle.vtl")
            .forEach(relativePath -> copyTriggerResource(relativePath, destinationDir, logger));
    }

    static void copyTriggerResource(String relativePath, File triggerDir, Logger logger) {
        final String resourceRoot = "com/brambolt/wrench/trigger";
        final String resourcePath = resourceRoot + "/" + relativePath;
        File destinationFile = new File(triggerDir.getAbsolutePath() + "/" + relativePath);
        boolean created = destinationFile.getParentFile().mkdirs();
        if (created)
            logger.debug("Created " + destinationFile.getParentFile().getAbsolutePath());
        copyResource(resourcePath, destinationFile, logger);
    }

    static void copyResource(String resourcePath, File destinationFile, Logger logger) {
        try {
            InputStream stream = stream(resourcePath);
            if (null == stream)
                throw new GradleException("No resource found at " + resourcePath);
            Files.copy(stream, destinationFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            logger.debug("Copied " + resourcePath + " to " + destinationFile.getAbsolutePath());
        } catch (IOException x) {
            throw new GradleException("Unable to copy " + resourcePath + " to " + destinationFile.getAbsolutePath(), x);
        }
    }
}
