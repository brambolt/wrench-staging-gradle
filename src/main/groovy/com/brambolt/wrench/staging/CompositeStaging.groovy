package com.brambolt.wrench.staging

import com.brambolt.wrench.StagingPlugin
import org.gradle.api.GradleException
import org.gradle.api.Project

/**
 * This class provides plugin implementation with the <code>#apply</code>
 * method as the main entry point. It is only intended for root projects or
 * intermediate subprojects, not leaf projects.
 *
 * <p>When this class is applied, it locates every leaf project belonging to
 * the project it is applied to; of those leaves, it first filters away any
 * that (a) have a Gradle build file or (b) don't have a file wrench.</p>
 *
 * <p>The remaining leaves with file wrenches and no build files are configured
 * with the wrench staging plugin.</p>
 *
 * <p>The execution sequence is still not quite as nice as it could be; the
 * staging plugin is expected to be applied to a root project or high-level
 * node, before the children have been evaluated. This means that although
 * the implementation pretends to check for artifact identifier presets, etc.,
 * the default is in fact always applied. This is because of complications
 * around JFrog's Artifactory plugin, which doesn't play nice with the
 * <code>plugins { .. }</code> syntax in multi-project builds.</p>
 */
class CompositeStaging {

  /**
   * The Gradle build file name to check for. Defaults to <code>build.gradle</code>.
   */
  String buildFileName = StagingPlugin.DEFAULT_BUILD_FILENAME

  /**
   * The file wrench file name to check for. Defaults to <code>runbook.wrench</code>.
   */
  String wrenchFileName = StagingPlugin.DEFAULT_PLAN_FILENAME

  /**
   * Constructor. The map parameter may be empty, or can include the following:
   * <ul>
   *   <li><code>buildFileName</code>: Overrides the <code>build.gradle</code> default</li>
   *   <li><code>wrenchFileName</code>: Overrides the <code>runbook.wrench</code> default</li>
   * </ul>
   *
   * @param options The construction parameters
   */
  CompositeStaging(Map options) {
    if (null == options)
      return
    if (options.containsKey('buildFileName'))
      this.buildFileName = options.buildFileName
    if (options.containsKey('wrenchFileName'))
      this.wrenchFileName = options.wrenchFileName
  }

  /**
   * Constructor. Applies default values.
   */
  CompositeStaging() {
    this(null)
  }

  /**
   * Applies the staging plugin to leaves below the parameter project.
   *
   * @param project The top-level node to locate leaves under
   */
  void apply(Project project) {
    findWrenchProjects(project).each({ Project p -> applyToWrenchProject(p) })
  }

  /**
   * Finds the applicable leaves to apply the staging plugin to.
   *
   * @param project The top-level node to locate leaves under
   * @return The located leaf subprojects
   */
  def findWrenchProjects(Project project) {
    project.subprojects
      .findAll({ Project p ->
        // Only apply to leaf projects:
        p.subprojects.isEmpty() &&
        // and only leaves with a file wrench:
        new File(p.projectDir as File, wrenchFileName).exists() &&
        // and only leaves without a Gradle build file:
        !(new File(p.projectDir as File, buildFileName).exists())
      })
  }

  /**
   * Applies the staging plugin to the parameter project.
   * @param project The project to apply the staging project to
   * @see PlanStaging
   */
  void applyToWrenchProject(Project project) {
    project.ext {
      if (!project.hasProperty('artifactId') || project.artifactId.trim().isEmpty())
        artifactId = project.path.substring(1).replaceAll(':', '-')
      wrenchDir = new File(project.rootProject.wrenches.target.dir as File, project.parent.name)
    }
    try {
      project.apply(plugin: 'com.brambolt.wrench.staging')
    } catch (Throwable t) {
      throw new GradleException("Unable to apply wrench staging plugin: ${project.path}", t)
    }
  }
}