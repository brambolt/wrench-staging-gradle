package com.brambolt.wrench


import com.brambolt.gradle.util.jar.Manifests
import com.brambolt.wrench.staging.CompositeStaging
import com.brambolt.wrench.staging.PlanStaging
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * This build prepares and publishes wrenches.
 */
class StagingPlugin implements Plugin<Project> {

  static final String DEFAULT_BUILD_FILENAME = 'build.gradle'

  static final String DEFAULT_PLAN_FILENAME = 'runbook.wrench'

  static final String DEFAULT_STAGING_RELPATH = '.wrench'

  static String getVersionFromManifest() {
    Manifests.getVersionFromManifest(StagingPlugin.class)
  }

  String runbookFilename = DEFAULT_PLAN_FILENAME

  File runbookFile

  String gradleWrapperPath = DEFAULT_STAGING_RELPATH

  /**
   * Applies the plug-in to the parameter project.
   * @param project The project to apply the plug-in to
   */
  void apply(Project project) {
    if (project.subprojects.isEmpty())
      new PlanStaging(
        wrenchFileName: runbookFilename,
        wrenchFile: runbookFile,
        stagingRelpath: gradleWrapperPath)
        .apply(project)
    else
      new CompositeStaging().apply(project)
  }
}
