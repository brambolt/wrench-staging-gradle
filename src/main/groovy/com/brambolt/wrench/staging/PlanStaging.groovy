package com.brambolt.wrench.staging

import com.brambolt.gradle.staging.tasks.Stage
import com.brambolt.gradle.text.Strings
import com.brambolt.gradle.velocity.tasks.Velocity
import com.brambolt.util.Resources
import com.brambolt.wrench.StagingPlugin
import com.brambolt.wrench.Target
import com.brambolt.wrench.Wrenches
import com.brambolt.wrench.runbooks.Checkpoint
import com.brambolt.wrench.runbooks.Runbook
import com.brambolt.wrench.runbooks.Step
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.Copy

/**
 * Configures a wrench project.
 *
 * <p>(Missing: Notes about the relationship between the project being
 * configured and the wrench project that will execute the wrench.)</p>
 */
class PlanStaging {

  /**
   * The wrench file name. Defaults to <code>runbook.wrench</code>.
   */
  String wrenchFileName = StagingPlugin.DEFAULT_PLAN_FILENAME

  /**
   * The wrench file. Normally derived from the file name but can be set
   * explicitly. This is very rarely needed.
   */
  File wrenchFile

  /**
   * The relative path to use when running the wrench locally as part of the build.
   * Defaults to <code>.wrench</code>. It is best not to override this value.
   */
  String stagingRelpath = StagingPlugin.DEFAULT_STAGING_RELPATH

  /**
   * Constructor. The map parameter may be empty or may hold:
   * <ul>
   *   <li><code>wrenchFileName</code>: Overrides the wrench file name</li>
   *   <li><code>wrenchFile</code>: Sets the wrench file explicitly</li>
   *   <li><code>stagingRelpath</code>: Changes the default staging location</li>
   * </ul>
   *
   * @param options The options map, which may be empty or null
   */
  PlanStaging(Map options) {
    if (null == options)
      return
    if (options.containsKey('wrenchFileName'))
      this.wrenchFileName = options.wrenchFileName as String
    if (options.containsKey('wrenchFile'))
      this.wrenchFile = options.wrenchFile as File
    if (options.containsKey('stagingPath'))
      this.stagingRelpath = options.stagingPath as String
  }

  /**
   * Constructor that applies default values.
   */
  PlanStaging() {
    this(null)
  }

  /**
   * Configures the parameter project to build a wrench.
   * @param project The project to configure
   */
  void apply(Project project) {
    wrenchFile = findWrenchFile(project)
    applyStagingPlugin(project)
    configureStageTask(project)
    configureStagingExtension(project)
    Velocity velocity = configureVelocityTask(project)
    configureTargets(project, velocity)
    configureBuild(project)
    applyWrench(project)
    configureBuildTasks(project)
    // configurePublishing(project)
  }

  /**
   * Locates the wrench file the project should build, or throws.
   * @param project The project that builds the wrench
   * @return The wrench file the project builds
   * @throws GradleException If the wrench file is not found
   */
  File findWrenchFile(Project project) {
    File result
    if (null == wrenchFile)
      if (null != wrenchFileName && !wrenchFileName.trim().isEmpty())
        result = new File(project.projectDir, wrenchFileName)
    if (null == result)
      throw new GradleException('No wrench file')
    if (!result.exists())
      throw new GradleException("No wrench file: ${result.absolutePath}")
    result
  }

  /**
   * Applies the staging plugin to the project
   * @param project The project being configured
   */
  static void applyStagingPlugin(Project project) {
    project.configure(project) { Project p ->
      p.apply(plugin: 'maven-publish')
      p.apply(plugin: 'com.brambolt.gradle.staging')
    }
  }

  /**
   * Configures the staging extension; not yet configurable; hardcoded.
   * @param project The project being configured
   */
  void configureStagingExtension(Project project) {
    // We need to read these values from properties - hardcoded for now:
    project.extensions.staging.targets(
      brambolt: [name: 'brambolt', hostName: 'brambolt', environmentName: getEnvironmentName(project, 'dev')],
      docker: [name: 'docker', hostName: 'docker', environmentName: getEnvironmentName(project, 'tmp')])
  }

  String getEnvironmentName(Project project, String suffix) {
    String acronym = project.hasProperty('clientAcronym') ? project.clientAcronym : ''
    "${acronym}${suffix}"
  }

  /**
   * Configures the target-agnostic Velocity template instantiation that
   * executes before any target tasks; not yet configurable; hardcoded.
   *
   * @param project The project being configured
   */
  static void configureVelocityTask(Project project) {
    Velocity velocity = (Velocity) project.tasks.findByName(Velocity.DEFAULT_VELOCITY_TASK_NAME)
    if (null == velocity)
      throw new GradleException("Velocity task '${Velocity.DEFAULT_VELOCITY_TASK_NAME}' not found")
    velocity.context {
      // Be careful not to use variable names that are already in scope; this
      // will simply assign a new value to that variable, and not affect the
      // extension...
      brambolt = [
        version     : project.bramboltVersion
      ]
      buildNumber   = project.buildNumber
      client = [
        description : project.description,
        version     : project.bramboltVersion
      ]
      if (project.hasProperty('clientAcronym'))
        client.acronym = project.clientAcronym
      if (project.hasProperty('clientGroup')) {
        client.group = project.clientGroup
        client.groupPath = project.clientGroup.replaceAll('\\.', '/')
      }
      if (project.hasProperty('clientName'))
        client.name = project.clientName
      databases = []
      maven = [
        contextUrl  : project.mavenContextUrl,
        repoKey     : project.mavenRepoKey
//        token       : project.mavenToken,
//        user        : project.mavenUser
      ]
    }
    velocity.inputPath = "${project.projectDir}/src/main/vtl"
  }

  /**
   * Configures the targets defined by the staging extension.
   * @param project The project being configured
   */
  void configureTargets(Project project, Task start) {
    project.extensions.staging.targetValues.get().each { Map.Entry target ->
      configureTarget(project, (Map) target.value, start)
    }
  }

  /**
   * Configures an individual target defined by the staging extension.
   * @param project The project being configured
   * @param target The target being configured
   * @param start A start dependency that needs to execute before the target tasks
   */
  void configureTarget(Project project, Map target, Task start) {
    Task gradleBuild = configureGradleBuild(project, target, start)
    Task gradleWrapper = configureGradleWrapperTask(project, target, gradleBuild)
    Task runbook = configureRunbookTask(project, target, gradleWrapper)
    Task gradleProperties = configureGradlePropertiesTask(project, target, runbook)
    Task settings = configureSettingsTask(project, target, gradleProperties)
    Task targetResources = project.tasks.getByName("${target.name}Resources")
    targetResources.dependsOn(settings)
  }

  /**
   * Creates the task to create the Gradle build file for executing the wrench.
   *
   * <p>This build file is not related to the parameter project. Instead it
   * defines a complete Gradle build. See notes in the class header.</p>
   *
   * @param project The project being configured
   * @param target The target being configured
   * @param start A start dependency that must execute first
   * @return The created and configured task to create the Gradle build file
   */
  Task configureGradleBuild(Project project, Map target, Task start) {
    String taskName = "${target.name}GradleBuild"
    Task existing = project.tasks.findByName(taskName)
    if (null != existing)
      return existing
    project.task([type: DefaultTask, dependsOn: start], taskName) {
      // If there is no runbook to execute then no build is needed:
      onlyIf { null != wrenchFile && wrenchFile.exists() }
      doFirst {
        instantiateBuildTemplate(project, target)
      }
    }
  }

  /**
   * Creates the task to put in place the Gradle wrapper for executing the wrench.
   *
   * @param project The project being configured
   * @param target The target being configured
   * @param start A start dependency that must execute first
   * @return The created and configured task to establish the Gradle wrapper
   */
  Copy configureGradleWrapperTask(Project project, Map target, Task start) {
    String taskName = "${target.name}GradleWrapper"
    // Check whether we already configured the task:
    Copy existing = project.tasks.findByName(taskName) as Copy
    if (null != existing)
      return existing
    (Copy) project.task([type: Copy, dependsOn: start], taskName) {
      // If there is no runbook to execute then no wrapper is needed:
      onlyIf { null != wrenchFile && wrenchFile.exists() }
      from (project.rootDir) {
        include 'gradle/wrapper/*.*'
        include 'gradlew'
        include 'gradlew.bat'
      }
      into getStagingDir(project, target)
    }
  }

  File getStagingDir(Project project, Map target) {
    if (null == stagingRelpath || stagingRelpath.isEmpty() || '.' == stagingRelpath)
      Stage.getResourcesDir(project, target)
    else
      new File("${Stage.getResourcesDir(project, target)}/${stagingRelpath}")
  }

  Copy configureRunbookTask(Project project, Map target, Task previous) {
    String taskName = "${target.name}Runbook"
    Copy existing = project.tasks.findByName(taskName) as Copy
    if (null != existing)
      return existing // We did this already...
    (Copy) project.task([type: Copy, dependsOn: previous], taskName) {
      onlyIf { null != wrenchFile && wrenchFile.exists() }
      from(wrenchFile.parentFile) {
        include wrenchFile.name
      }
      into getStagingDir(project, target) // The same directory as the wrapper...
    }
  }

  Task configureGradlePropertiesTask(Project project, Map target, Task previous) {
    String taskName = "${target.name}GradleProperties"
    Task existing = project.tasks.findByName(taskName)
    if (null != existing)
      return existing
    project.task([type: DefaultTask, dependsOn: previous], taskName) {
      // If there is no runbook to execute then no properties are needed:
      onlyIf { null != wrenchFile && wrenchFile.exists() }
      String filename = 'gradle.properties'
      File destinationDir = getStagingDir(project, target)
      File destinationFile = new File(destinationDir, filename)
      File sourceFile = new File(project.rootProject.projectDir, filename)
      doFirst {
        String content = sourceFile.text
        if (!content.endsWith('\n'))
          content += '\n'
        content += '\n'
        if (!content.contains('bramboltRelease='))
          content += "bramboltRelease=${project.bramboltRelease}\n"
        if (!content.contains('bramboltVersion='))
          content += "bramboltVersion=${project.bramboltVersion}\n"
        if (!content.contains('buildNumber='))
          content += "buildNumber=${project.buildNumber}\n"
        if (!content.contains('version='))
          content += "version=${project.bramboltVersion}\n"
        destinationFile.text = content
      }
    }
  }

  DefaultTask configureSettingsTask(Project project, Map target, Task previous) {
    String taskName = "${target.name}Settings"
    DefaultTask existing = project.tasks.findByName(taskName) as DefaultTask
    if (null != existing)
      return existing
    (DefaultTask) project.task([type: DefaultTask, dependsOn: previous], taskName) {
      // No runbook, no Gradle anything...
      onlyIf { null != wrenchFile && wrenchFile.exists() }
      String filename = 'settings.gradle'
      File destinationDir = getStagingDir(project, target)
      File destinationFile = new File(destinationDir, filename)
      doFirst {
        destinationFile.text = "rootProject.name = '${project.rootProject.name}-staging'"
      }
    }
  }

  void instantiateBuildTemplate(Project project, Map target) {
    final String packagePath = 'com/brambolt/wrench/staging'
    final String baseName = 'build.gradle'
    final String resourcePath = "${packagePath}/${baseName}"
    File destinationDir = getStagingDir(project, target)
    File destinationFile = new File(destinationDir, baseName)
    if (!destinationDir.exists())
      destinationDir.mkdirs()
    File outputFile = Resources.createFileFromResource(resourcePath, destinationFile, target)
    project.logger.info("Copied ${resourcePath} from class path to file system at ${destinationFile}")
    if (null == outputFile || !outputFile.exists())
      throw new GradleException("Unable to write ${resourcePath} to ${destinationFile}: ${outputFile}")
    // Long comment out of date here...

    // Next up, fill in the missing logic to add the build.gradle.vtl
    // file into the build/vtl directory, before the Velocity task is
    // executed; the file probably has to have the target.hostName and
    // target.environmentName variables filled in, so this has to happen
    // during target configuration (?).

    // It's not yet clear how to best do this in the long run. This file
    // does not have to be customizable, for now. It does need to include
    // the buildscript dependencies, which means that defining the wrench
    // class path in the wrench file is somewhat difficult. One possibility
    // for covering this problem is to start reading the wrench while in
    // the buildscript block. This pass would only locate the class path
    // entries. Similar extra passes could be used to populate the rest of
    // the build script template, always by reading the same wrench script.

    // Would it be possible to stop using a Velocity template for the build
    // script, and instead use a master script that drops into the wrench
    // file to fill in the missing pieces?

    // It would be simple enough - given the rigging described above - to
    // also read the wrench file into the staging plugin itself, and use
    // the wrench specification at the top of the file to actually choose the
    // build script. Which... actually gives me the idea of just dropping
    // straight into the wrench file from the buildscript block, but, is this
    // actually possible? How do any third party classes get on the class path
    // used for the build script block? Is the init script used for this? Yes,
    // it looks like it can be, I could put in an init script, call it perhaps
    // wrench.gradle, that would add the necessary dependencies to the class
    // path. The staging plugin could read the location from the wrench header
    // which would give clients control over the repository settings.

    // ... No need for any of this now...
  }

  void configureStageTask(Project project) {
    project.stage {
      includeAllResources = true
    }
  }

  void configureBuild(Project project) {
    Map wrenches = project.wrenches
    Map<String, Object> wrench = [:]
    wrench.target = [:]
    project.ext.wrench = wrench
    wrench.target.dir = (project.hasProperty('wrenchDir')
      ? project.wrenchDir : new File(project.buildDir, 'staging'))
    wrench.target.environment = [:]
    wrench.target.environment.name = getEnvironmentName(project, 'dev')
    wrench.target.staging = [:]
    wrench.target.staging.dir = new File(wrench.target.dir as File, '.wrench')
    wrench.target.hosts = [:]
    wrench.target.hosts.dir = new File(wrench.target.dir as File, 'hosts')
    wrench.target.hosts[wrench.target.environment.name as String] = [:]
    // build/SNAPSHOT-brambolt/hosts/***dev:
    String envName = wrench.target.environment.name as String
    Map env = wrench.target.hosts[envName] as Map
    env.dir = new File(wrench.target.hosts.dir as File, envName)
    // build/SNAPSHOT-brambolt/hosts/dev/brambolt:
    env[wrenches.classifier] = [:]
    (env[wrenches.classifier as String] as Map).dir =
      new File(env.dir as File, wrenches.classifier as String)
    // build/SNAPSHOT-brambolt/hosts/***dev/brambolt-client:
    env["${wrenches.classifier}-client"] = [:]
    (env["${wrenches.classifier}-client"] as Map).dir =
      new File(env.dir as File, "${wrenches.classifier}-client")
    wrench.target.workspace = [:]
    wrench.target.workspace.dir = new File(wrench.target.dir as File, 'workspace')
    wrench.script = (Wrenches.find(project)
      .withTarget(Target.create(project))
      .withContext(context: 'build')
      .bind([
        hostName: wrenches.classifier,
        environmentName: wrench.target.environment.name as String
      ]))
    wrench.gradlew = new File(wrench.target.staging.dir as File, 'gradlew')
  }

  void applyWrench(Project project) {
    project.wrench.script.apply()
  }

  void configureBuildTasks(Project project) {
    configureMainTasks(project)
    configureDelegation(project)
  }

  void configureMainTasks(Project project) {
    project.publishToMavenLocal.dependsOn(project.stage)
    Task local = project.task(
      [type: DefaultTask, dependsOn: project.publishToMavenLocal], 'local')
    project.task([type: DefaultTask], 'undeploy') {
      doFirst {
        project.delete(project.wrench.target.dir as File)
      }
    }
    project.task([type: DefaultTask, dependsOn: local], 'deploy') {
      doFirst {
        File runbookArchive = new File(project.buildDir,
          "libs/${project.artifactId}-${project.version}-${project.wrenches.classifier}.zip")
        project.copy {
          from project.zipTree(runbookArchive) as Object
          into project.wrench.target.dir as File
        }
      }
    }
  }

  void configureDelegation(Project project) {
    Target target = project.wrench.script.target
    target.runbooks.each { Runbook runbook ->
      configureDelegation(project,
        formatDelegateTaskName('runRunbook', runbook.name), runbook.name, 'runbook')
    }
    target.checkpoints.each { Checkpoint checkpoint ->
      configureDelegation(project,
        formatDelegateTaskName('runCheckpoint', checkpoint.name), checkpoint.name, 'checkpoint')
    }
    target.steps.each { Step step ->
      configureDelegation(project,
        formatDelegateTaskName('runStep', step.name), step.name, '')
    }
  }

  private String formatDelegateTaskName(String prefix, String nodeName) {
    "${prefix}${Strings.toCamelCase(nodeName, [';'])}".toString() // Fix...
  }

  void configureDelegation(Project project, String taskName, String nodeName, String qualifier) {
    project.task([type: DefaultTask, dependsOn: 'deploy'], taskName) {
      String qualified = qualifier.isEmpty() ? nodeName : qualifier + Strings.toCamelCase(nodeName, [';'])
      List<String> args = [project.wrench.gradlew, qualified, '--info', '--stacktrace']
      // Include known wrench properties for delegation:
      ['wrenchEnvironmentName', 'wrenchHostName'].each {
        if (project.hasProperty(it))
          args.add("-P${it}=${project.getProperties().get(it)}")
      }
      // If the project has configured properties to be passed along when
      // delegating to wrench tasks, add them here:
      if (project.hasProperty('wrenchDelegation'))
        args.addAll(project.wrenchDelegation as List<String>)
      // Delegate to the wrench:
      doFirst {
        project.exec {
          commandLine(args)
          workingDir(project.wrench.target.staging.dir as File)
        }
      }
    }
  }

  void configurePublishing(Project project) {
    project.configure(project) { Project p ->
      p.apply(plugin: 'maven-publish')
      p.apply(plugin: 'com.jfrog.artifactory')

      Object pomMetaData = {
        licenses {
          p.licenses.each { l ->
            license {
              name l.name
              url l.url
              distribution 'repo'
            }
          }
        }
        developers {
          p.developers.each { d ->
            developer {
              id d.id
              name d.name
              email d.email
            }
          }
        }
        scm {
          url p.vcsUrl
        }
      }
      p.publishing {
        publications {
          mavenJava(MavenPublication) {
            artifactId = p.artifactId
            groupId = p.group
            version = p.version
            from p.components.java
            artifact(p.javadocJar)
            artifact(p.sourceJar)
            // As of com.github.johnrengelman.shadow 6.0.0 we no londer need to
            // explicitly add a shadow jar artifact here (and doing so causes a
            // conflict because the plugin automatically does it...).
            pom.withXml {
              def root = asNode()
              root.appendNode('description', p.description)
              root.appendNode('inceptionYear', p.inceptionYear)
              root.appendNode('name', p.name)
              root.appendNode('url', p.vcsUrl)
              root.children().last() + pomMetaData
            }
          }
        }
      }

      p.artifactory {
        contextUrl = project.artifactoryContextUrl
        publish {
          repository {
            repoKey = project.artifactoryRepoKey
            username = project.artifactoryUser
            password = project.artifactoryToken
            maven = true
          }
         defaults {
           publications('mavenCustom')
           publishArtifacts = true
           publishPom = true
          }
        }
        resolve {
          repository {
            repoKey = project.artifactoryRepoKey
            username = project.artifactoryUser
            password = project.artifactoryToken
            maven = true
          }
        }
      }
      Task all = p.tasks.findByName('all')
      if (null == all)
        all = p.task(type: DefaultTask, 'all')
      all.dependsOn(p.artifactoryPublish)
    }
  }
}