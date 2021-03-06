/*
 * SonarSource :: IT :: SonarQube Runner
 * Copyright (C) 2009 SonarSource
 * sonarqube@googlegroups.com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package com.sonar.runner.it;

import com.sonar.orchestrator.build.BuildResult;
import com.sonar.orchestrator.build.SonarRunner;
import com.sonar.orchestrator.locator.ResourceLocation;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonar.wsclient.issue.Issue;
import org.sonar.wsclient.issue.IssueQuery;
import org.sonar.wsclient.services.Resource;
import org.sonar.wsclient.services.ResourceQuery;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.fest.assertions.Assertions.assertThat;
import static org.junit.Assume.assumeTrue;

public class JavaTest extends RunnerTestCase {

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  /**
   * No bytecode, only sources
   */
  @Test
  public void scan_java_sources() {
    orchestrator.getServer().restoreProfile(ResourceLocation.create("/sonar-way-profile.xml"));
    orchestrator.getServer().provisionProject("java:sample", "Java Sample, with comma");
    orchestrator.getServer().associateProjectToQualityProfile("java:sample", "java", "sonar-way");

    SonarRunner build = newRunner(new File("projects/java-sample"))
      .setProperty("sonar.verbose", "true")
      .addArguments("-e");
    // SONARPLUGINS-3061
    // Add a trailing slash
    build.setProperty("sonar.host.url", orchestrator.getServer().getUrl() + "/");
    orchestrator.executeBuild(build);

    Resource project = orchestrator.getServer().getWsClient().find(new ResourceQuery("java:sample").setMetrics("files", "ncloc", "classes", "lcom4", "violations"));
    // SONARPLUGINS-2399
    assertThat(project.getName()).isEqualTo("Java Sample, with comma");
    assertThat(project.getDescription()).isEqualTo("This is a Java sample");
    assertThat(project.getVersion()).isEqualTo("1.2.3");
    if (!orchestrator.getServer().version().isGreaterThanOrEquals("4.2")) {
      assertThat(project.getLanguage()).isEqualTo("java");
    }
    assertThat(project.getMeasureIntValue("files")).isEqualTo(2);
    assertThat(project.getMeasureIntValue("classes")).isEqualTo(2);
    assertThat(project.getMeasureIntValue("ncloc")).isGreaterThan(10);
    assertThat(project.getMeasureIntValue("lcom4")).isNull(); // no bytecode
    if (orchestrator.getServer().version().isGreaterThanOrEquals("3.7")) {
      // the squid rules enabled in sonar-way-profile do not exist in SQ 3.0
      assertThat(project.getMeasureIntValue("violations")).isGreaterThan(0);
    }

    Resource file = orchestrator.getServer().getWsClient()
      .find(new ResourceQuery(helloFileKey()).setMetrics("files", "ncloc", "classes", "lcom4", "violations"));
    if (orchestrator.getServer().version().isGreaterThanOrEquals("4.2")) {
      assertThat(file.getName()).isEqualTo("Hello.java");
    } else {
      assertThat(file.getName()).isEqualTo("Hello");
      assertThat(file.getMeasureIntValue("lcom4")).isNull(); // no bytecode
    }
    assertThat(file.getMeasureIntValue("ncloc")).isEqualTo(7);
    if (orchestrator.getServer().version().isGreaterThanOrEquals("3.7")) {
      // the squid rules enabled in sonar-way-profile do not exist in SQ 3.0
      assertThat(file.getMeasureIntValue("violations")).isGreaterThan(0);
    }
  }

  @Test
  public void scan_java_sources_and_bytecode() {
    orchestrator.getServer().restoreProfile(ResourceLocation.create("/requires-bytecode-profile.xml"));
    orchestrator.getServer().provisionProject("java:bytecode", "Java Bytecode Sample");
    orchestrator.getServer().associateProjectToQualityProfile("java:bytecode", "java", "requires-bytecode");

    SonarRunner build = newRunner(new File("projects/java-bytecode"));
    orchestrator.executeBuild(build);

    Resource project = orchestrator.getServer().getWsClient().find(new ResourceQuery("java:bytecode").setMetrics("lcom4", "violations"));
    assertThat(project.getName()).isEqualTo("Java Bytecode Sample");
    if (!orchestrator.getServer().version().isGreaterThanOrEquals("4.1")) {
      // SONAR-4853 LCOM4 is no more computed on SQ 4.1
      assertThat(project.getMeasureIntValue("lcom4")).isGreaterThanOrEqualTo(1);
    }
    // the squid rules enabled in sonar-way-profile do not exist in SQ 3.0
    assertThat(project.getMeasureIntValue("violations")).isGreaterThan(0);

    Resource file = orchestrator.getServer().getWsClient().find(new ResourceQuery(findbugsFileKey()).setMetrics("lcom4", "violations"));
    assertThat(file.getMeasureIntValue("violations")).isGreaterThan(0);

    // findbugs is executed on bytecode
    List<Issue> issues = orchestrator.getServer().wsClient().issueClient().find(IssueQuery.create().componentRoots("java:bytecode").rules("findbugs:DM_EXIT")).list();
    assertThat(issues).hasSize(1);
    assertThat(issues.get(0).ruleKey()).isEqualTo("findbugs:DM_EXIT");

    // Squid performs analysis of dependencies
    issues = orchestrator.getServer().wsClient().issueClient().find(IssueQuery.create().componentRoots("java:bytecode").rules("squid:CallToDeprecatedMethod")).list();
    assertThat(issues).hasSize(1);
    assertThat(issues.get(0).ruleKey()).isEqualTo("squid:CallToDeprecatedMethod");
  }

  @Test
  public void basedir_contains_java_sources() {
    orchestrator.getServer().restoreProfile(ResourceLocation.create("/sonar-way-profile.xml"));
    orchestrator.getServer().provisionProject("java:basedir-with-source", "Basedir with source");
    orchestrator.getServer().associateProjectToQualityProfile("java:basedir-with-source", "java", "sonar-way");

    SonarRunner build = newRunner(new File("projects/basedir-with-source"));
    orchestrator.executeBuild(build);

    Resource project = orchestrator.getServer().getWsClient().find(new ResourceQuery("java:basedir-with-source").setMetrics("files", "ncloc"));
    assertThat(project.getMeasureIntValue("files")).isEqualTo(1);
    assertThat(project.getMeasureIntValue("ncloc")).isGreaterThan(1);
  }

  /**
   * Replace the maven format groupId:artifactId by a single key
   */
  @Test
  public void should_support_simple_project_keys() {
    orchestrator.getServer().restoreProfile(ResourceLocation.create("/sonar-way-profile.xml"));
    orchestrator.getServer().provisionProject("SAMPLE", "Java Sample, with comma");
    orchestrator.getServer().associateProjectToQualityProfile("SAMPLE", "java", "sonar-way");

    SonarRunner build = newRunner(new File("projects/java-sample"))
      .setProjectKey("SAMPLE");
    orchestrator.executeBuild(build);

    Resource project = orchestrator.getServer().getWsClient().find(new ResourceQuery("SAMPLE").setMetrics("files", "ncloc"));
    assertThat(project.getMeasureIntValue("files")).isEqualTo(2);
    assertThat(project.getMeasureIntValue("ncloc")).isGreaterThan(1);
  }

  /**
   * SONARPLUGINS-1230
   */
  @Test
  public void should_override_working_dir_with_relative_path() {
    SonarRunner build = newRunner(new File("projects/override-working-dir"))
      .setProperty("sonar.working.directory", ".overridden-relative-sonar");
    orchestrator.executeBuild(build);

    assertThat(new File("projects/override-working-dir/.sonar")).doesNotExist();
    assertThat(new File("projects/override-working-dir/.overridden-relative-sonar")).exists().isDirectory();
  }

  /**
   * SONARPLUGINS-1230
   */
  @Test
  public void should_override_working_dir_with_absolute_path() {
    File projectHome = new File("projects/override-working-dir");
    SonarRunner build = newRunner(projectHome)
      .setProperty("sonar.working.directory", new File(projectHome, ".overridden-absolute-sonar").getAbsolutePath());
    orchestrator.executeBuild(build);

    assertThat(new File("projects/override-working-dir/.sonar")).doesNotExist();
    assertThat(new File("projects/override-working-dir/.overridden-absolute-sonar")).exists().isDirectory();
  }

  /**
   * SONARPLUGINS-1856
   */
  @Test
  public void should_fail_if_source_dir_does_not_exist() {
    SonarRunner build = newRunner(new File("projects/bad-source-dirs"));

    BuildResult result = orchestrator.executeBuildQuietly(build);
    assertThat(result.getStatus()).isNotEqualTo(0);
    // with the following message
    assertThat(result.getLogs()).contains("The folder 'bad' does not exist for 'bad-source-dirs'");
  }

  /**
   * SONARPLUGINS-2203
   */
  @Test
  public void should_log_message_when_deprecated_properties_are_used() {
    assumeTrue(!orchestrator.getServer().version().isGreaterThanOrEquals("4.3"));
    SonarRunner build = newRunner(new File("projects/using-deprecated-props"));

    BuildResult result = orchestrator.executeBuild(build);
    String logs = result.getLogs();
    assertThat(logs).contains("/!\\ The 'sources' property is deprecated and is replaced by 'sonar.sources'. Don't forget to update your files.");
    assertThat(logs).contains("/!\\ The 'tests' property is deprecated and is replaced by 'sonar.tests'. Don't forget to update your files.");
    assertThat(logs).contains("/!\\ The 'binaries' property is deprecated and is replaced by 'sonar.binaries'. Don't forget to update your files.");
    assertThat(logs).contains("/!\\ The 'libraries' property is deprecated and is replaced by 'sonar.libraries'. Don't forget to update your files.");
  }

  /**
   * SONARPLUGINS-2256
   */
  @Test
  public void should_warn_when_analysis_is_platform_dependent() {
    SonarRunner build = newRunner(new File("projects/java-sample"))
      // ORCH-243
      .setSourceEncoding("");
    String log = orchestrator.executeBuild(build).getLogs();

    // Note: we can't really check the locale value and the charset because the ones used during the Sonar analysis may not be the ones
    // used to launch the tests. But we can check that the analysis is platform dependent (i.e. "sonar.sourceEncoding" hasn't been set).
    assertThat(log).contains("Default locale:");
    assertThat(log).contains(", source code encoding:");
    assertThat(log).contains("(analysis is platform dependent)");
  }

  @Test
  public void should_fail_if_unable_to_connect() {
    SonarRunner build = newRunner(new File("projects/java-sample"))
      .setProperty("sonar.host.url", "http://foo");

    BuildResult result = orchestrator.executeBuildQuietly(build);
    // expect build failure
    assertThat(result.getStatus()).isNotEqualTo(0);
    // with the following message
    assertThat(result.getLogs()).contains("ERROR: Sonar server 'http://foo' can not be reached");
  }

  // SONARPLUGINS-3574
  @Test
  public void run_from_external_location() throws IOException {
    File tempDir = temp.newFolder();
    SonarRunner build = newRunner(tempDir)
      .setProperty("sonar.projectBaseDir", new File("projects/java-sample").getAbsolutePath())
      .addArguments("-e");
    orchestrator.executeBuild(build);

    Resource project = orchestrator.getServer().getWsClient().find(new ResourceQuery("java:sample").setMetrics("files", "ncloc", "classes", "lcom4", "violations"));
    assertThat(project.getDescription()).isEqualTo("This is a Java sample");
    assertThat(project.getVersion()).isEqualTo("1.2.3");
  }

  private String findbugsFileKey() {
    if (orchestrator.getServer().version().isGreaterThanOrEquals("4.2")) {
      return "java:bytecode:src/HasFindbugsViolation.java";
    } else {
      return "java:bytecode:[default].HasFindbugsViolation";
    }
  }

  private String helloFileKey() {
    if (orchestrator.getServer().version().isGreaterThanOrEquals("4.2")) {
      return "java:sample:src/basic/Hello.java";
    } else {
      return "java:sample:basic.Hello";
    }
  }
}
