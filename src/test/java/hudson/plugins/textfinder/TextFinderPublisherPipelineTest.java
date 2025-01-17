package hudson.plugins.textfinder;

import hudson.model.Result;
import java.io.File;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class TextFinderPublisherPipelineTest {

    private static final String UNIQUE_TEXT = "foobar";
    private static final String UNIQUE_TEXT_APPEND = " appended";
    private static final String SUPER_ID = "superId";
    private static final String SUPER_ID_KEY = "future name: ";
    private static final String SUPER_ID_LINE = SUPER_ID_KEY + SUPER_ID;
    private static final String ECHO_ID = "echo \"" + SUPER_ID_LINE + "\"";
    private static final String ECHO_UNIQUE_TEXT = "echo " + UNIQUE_TEXT;
    private static final String fileSet = "out.txt";

    @Rule public JenkinsRule rule = new JenkinsRule();

    @Test
    public void successIfFoundInFile() throws Exception {
        WorkflowJob project = rule.createProject(WorkflowJob.class);
        project.setDefinition(
                new CpsFlowDefinition(
                        "node {\n"
                                + "  writeFile file: '"
                                + fileSet
                                + "', text: '"
                                + UNIQUE_TEXT
                                + "'\n"
                                + "  findText regexp: '"
                                + UNIQUE_TEXT
                                + "', fileSet: '"
                                + fileSet
                                + "', succeedIfFound: true\n"
                                + "}\n",
                        true));
        WorkflowRun build = rule.buildAndAssertSuccess(project);
        rule.assertLogContains(
                "[Text Finder] Looking for pattern '"
                        + UNIQUE_TEXT
                        + "' in the files at "
                        + "'"
                        + fileSet
                        + "'",
                build);
        TestUtils.assertFileContainsMatch(
                new File(TestUtils.getWorkspace(build), fileSet), UNIQUE_TEXT, rule, build, false);
    }

    @Test
    public void failureIfFoundInFile() throws Exception {
        WorkflowJob project = rule.createProject(WorkflowJob.class);
        project.setDefinition(
                new CpsFlowDefinition(
                        "node {\n"
                                + "  writeFile file: '"
                                + fileSet
                                + "', text: '"
                                + UNIQUE_TEXT
                                + "'\n"
                                + "  findText regexp: '"
                                + UNIQUE_TEXT
                                + "', fileSet: '"
                                + fileSet
                                + "'\n"
                                + "}\n",
                        true));
        WorkflowRun build = project.scheduleBuild2(0).get();
        rule.waitForCompletion(build);
        rule.assertLogContains(
                "[Text Finder] Looking for pattern '"
                        + UNIQUE_TEXT
                        + "' in the files at "
                        + "'"
                        + fileSet
                        + "'",
                build);
        TestUtils.assertFileContainsMatch(
                new File(TestUtils.getWorkspace(build), fileSet), UNIQUE_TEXT, rule, build, false);
        rule.assertBuildStatus(Result.FAILURE, build);
    }

    @Test
    public void unstableIfFoundInFile() throws Exception {
        WorkflowJob project = rule.createProject(WorkflowJob.class);
        project.setDefinition(
                new CpsFlowDefinition(
                        "node {\n"
                                + "  writeFile file: '"
                                + fileSet
                                + "', text: '"
                                + UNIQUE_TEXT
                                + "'\n"
                                + "  findText regexp: '"
                                + UNIQUE_TEXT
                                + "', fileSet: '"
                                + fileSet
                                + "', unstableIfFound: true\n"
                                + "}\n",
                        true));
        WorkflowRun build = project.scheduleBuild2(0).get();
        rule.waitForCompletion(build);
        rule.assertLogContains(
                "[Text Finder] Looking for pattern '"
                        + UNIQUE_TEXT
                        + "' in the files at "
                        + "'"
                        + fileSet
                        + "'",
                build);
        TestUtils.assertFileContainsMatch(
                new File(TestUtils.getWorkspace(build), fileSet), UNIQUE_TEXT, rule, build, false);
        rule.assertBuildStatus(Result.UNSTABLE, build);
    }

    @Test
    public void notBuiltIfFoundInFile() throws Exception {
        WorkflowJob project = rule.createProject(WorkflowJob.class);
        project.setDefinition(
                new CpsFlowDefinition(
                        "node {\n"
                                + "  writeFile file: '"
                                + fileSet
                                + "', text: '"
                                + UNIQUE_TEXT
                                + "'\n"
                                + "  findText regexp: '"
                                + UNIQUE_TEXT
                                + "', fileSet: '"
                                + fileSet
                                + "', notBuiltIfFound: true\n"
                                + "}\n",
                        true));
        WorkflowRun build = project.scheduleBuild2(0).get();
        rule.waitForCompletion(build);
        rule.assertLogContains(
                "[Text Finder] Looking for pattern '"
                        + UNIQUE_TEXT
                        + "' in the files at "
                        + "'"
                        + fileSet
                        + "'",
                build);
        TestUtils.assertFileContainsMatch(
                new File(TestUtils.getWorkspace(build), fileSet), UNIQUE_TEXT, rule, build, false);
        rule.assertBuildStatus(Result.NOT_BUILT, build);
    }

    @Test
    public void notFoundInFile() throws Exception {
        WorkflowJob project = rule.createProject(WorkflowJob.class);
        project.setDefinition(
                new CpsFlowDefinition(
                        "node {\n"
                                + "  writeFile file: '"
                                + fileSet
                                + "', text: 'foobaz'\n"
                                + "  findText regexp: '"
                                + UNIQUE_TEXT
                                + "', fileSet: '"
                                + fileSet
                                + "'\n"
                                + "}\n",
                        true));
        WorkflowRun build = rule.buildAndAssertSuccess(project);
        rule.assertLogContains(
                "[Text Finder] Looking for pattern '"
                        + UNIQUE_TEXT
                        + "' in the files at "
                        + "'"
                        + fileSet
                        + "'",
                build);
    }

    @Test
    public void successIfFoundInConsole() throws Exception {
        WorkflowJob project = rule.createProject(WorkflowJob.class);
        project.setDefinition(
                new CpsFlowDefinition(
                        "node {\n"
                                + "  isUnix() ? sh('"
                                + ECHO_UNIQUE_TEXT
                                + "') : bat(\"prompt \\$G\\r\\n"
                                + ECHO_UNIQUE_TEXT
                                + "\")\n"
                                + "  findText regexp: '"
                                + UNIQUE_TEXT
                                + "', succeedIfFound: true, alsoCheckConsoleOutput: true\n"
                                + "}\n",
                        true));
        WorkflowRun build = rule.buildAndAssertSuccess(project);
        rule.assertLogContains("[Text Finder] Scanning console output...", build);
        rule.assertLogContains(
                "[Text Finder] Finished looking for pattern '"
                        + UNIQUE_TEXT
                        + "' in the console output",
                build);
        TestUtils.assertConsoleContainsMatch(ECHO_UNIQUE_TEXT, rule, build, true);
    }

    @Test
    public void failureIfFoundInConsole() throws Exception {
        WorkflowJob project = rule.createProject(WorkflowJob.class);
        project.setDefinition(
                new CpsFlowDefinition(
                        "node {\n"
                                + "  isUnix() ? sh('"
                                + ECHO_UNIQUE_TEXT
                                + "') : bat(\"prompt \\$G\\r\\n"
                                + ECHO_UNIQUE_TEXT
                                + "\")\n"
                                + "  findText regexp: '"
                                + UNIQUE_TEXT
                                + "', alsoCheckConsoleOutput: true\n"
                                + "}\n",
                        true));
        WorkflowRun build = project.scheduleBuild2(0).get();
        rule.waitForCompletion(build);
        rule.assertLogContains("[Text Finder] Scanning console output...", build);
        rule.assertLogContains(
                "[Text Finder] Finished looking for pattern '"
                        + UNIQUE_TEXT
                        + "' in the console output",
                build);
        TestUtils.assertConsoleContainsMatch(ECHO_UNIQUE_TEXT, rule, build, true);
        rule.assertBuildStatus(Result.FAILURE, build);
    }

    @Test
    public void unstableIfFoundInConsole() throws Exception {
        WorkflowJob project = rule.createProject(WorkflowJob.class);
        project.setDefinition(
                new CpsFlowDefinition(
                        "node {\n"
                                + "  isUnix() ? sh('"
                                + ECHO_UNIQUE_TEXT
                                + "') : bat(\"prompt \\$G\\r\\n"
                                + ECHO_UNIQUE_TEXT
                                + "\")\n"
                                + "  findText regexp: '"
                                + UNIQUE_TEXT
                                + "', unstableIfFound: true, alsoCheckConsoleOutput: true\n"
                                + "}\n",
                        true));
        WorkflowRun build = project.scheduleBuild2(0).get();
        rule.waitForCompletion(build);
        rule.assertLogContains("[Text Finder] Scanning console output...", build);
        rule.assertLogContains(
                "[Text Finder] Finished looking for pattern '"
                        + UNIQUE_TEXT
                        + "' in the console output",
                build);
        TestUtils.assertConsoleContainsMatch(ECHO_UNIQUE_TEXT, rule, build, true);
        rule.assertBuildStatus(Result.UNSTABLE, build);
    }

    @Test
    public void unstableIfFoundInConsoleWithFutureDisplayName() throws Exception {
        WorkflowJob project = rule.createProject(WorkflowJob.class);
        project.setDefinition(
                new CpsFlowDefinition(
                        "node {\n"
                                + "  isUnix() ? sh('"
                                + ECHO_ID
                                + ";"
                                + ECHO_UNIQUE_TEXT
                                + ";"
                                + "') : bat(\"prompt \\$G\\r\\n"
                                + "echo notTestedOnWidows"
                                + "\")\n"
                                + "  findText regexp: '"
                                + UNIQUE_TEXT
                                + "', buildId: '"
                                + "^"
                                + SUPER_ID_KEY
                                + "', unstableIfFound: true, alsoCheckConsoleOutput: true\n"
                                + "}\n",
                        true));
        WorkflowRun build = project.scheduleBuild2(0).get();
        rule.waitForCompletion(build);
        rule.assertLogContains("[Text Finder] Scanning console output...", build);
        rule.assertLogContains(
                "[Text Finder] Found future buildId line: '" + SUPER_ID_LINE + "'", build);
        rule.assertLogContains("[Text Finder] Leading to buildId of: '" + SUPER_ID + "'", build);
        rule.assertLogContains(
                "[Text Finder] Finished looking for pattern '"
                        + UNIQUE_TEXT
                        + "' in the console output",
                build);
        TestUtils.assertConsoleContainsMatch(ECHO_UNIQUE_TEXT, rule, build, true);
        Assert.assertEquals(SUPER_ID, build.getDisplayName());
        rule.assertBuildStatus(Result.UNSTABLE, build);
    }

    @Test
    public void unstableIfFoundInConsoleWithFutureDescription() throws Exception {
        unstableIfFoundInConsoleWithFutureDescriptionImpl(true);
    }

    @Test
    public void unstableIfFoundInConsoleWithoutFutureDescription() throws Exception {
        unstableIfFoundInConsoleWithFutureDescriptionImpl(false);
    }

    public void unstableIfFoundInConsoleWithFutureDescriptionImpl(boolean setDesc)
            throws Exception {
        WorkflowJob project = rule.createProject(WorkflowJob.class);
        project.setDefinition(
                new CpsFlowDefinition(
                        "node {\n"
                                + "  isUnix() ? sh('"
                                + ECHO_ID
                                + ";"
                                + ECHO_UNIQUE_TEXT
                                + UNIQUE_TEXT_APPEND
                                + ";"
                                + "') : bat(\"prompt \\$G\\r\\n"
                                + "echo notTestedOnWidows"
                                + "\")\n"
                                + "  findText regexp: '"
                                + UNIQUE_TEXT
                                + "', buildId: '"
                                + "^"
                                + SUPER_ID_KEY
                                + "', setDescription: "
                                + setDesc
                                + ", unstableIfFound: true, alsoCheckConsoleOutput: true\n"
                                + "}\n",
                        true));
        WorkflowRun build = project.scheduleBuild2(0).get();
        rule.waitForCompletion(build);
        rule.assertLogContains("[Text Finder] Scanning console output...", build);
        rule.assertLogContains(
                "[Text Finder] Finished looking for pattern '"
                        + UNIQUE_TEXT
                        + "' in the console output",
                build);
        if (setDesc) {
            rule.assertLogContains(
                    "[Text Finder] will set description of: '" + UNIQUE_TEXT_APPEND + "'", build);
        } else {
            rule.assertLogNotContains(
                    "[Text Finder] will set description of: '" + UNIQUE_TEXT_APPEND + "'", build);
        }
        TestUtils.assertConsoleContainsMatch(ECHO_UNIQUE_TEXT, rule, build, true);
        Assert.assertEquals(SUPER_ID, build.getDisplayName());
        rule.assertBuildStatus(Result.UNSTABLE, build);
    }

    @Test
    public void notBuiltIfFoundInConsole() throws Exception {
        WorkflowJob project = rule.createProject(WorkflowJob.class);
        project.setDefinition(
                new CpsFlowDefinition(
                        "node {\n"
                                + "  isUnix() ? sh('"
                                + ECHO_UNIQUE_TEXT
                                + "') : bat(\"prompt \\$G\\r\\n"
                                + ECHO_UNIQUE_TEXT
                                + "\")\n"
                                + "  findText regexp: '"
                                + UNIQUE_TEXT
                                + "', notBuiltIfFound: true, alsoCheckConsoleOutput: true\n"
                                + "}\n",
                        true));
        WorkflowRun build = project.scheduleBuild2(0).get();
        rule.waitForCompletion(build);
        rule.assertLogContains("[Text Finder] Scanning console output...", build);
        rule.assertLogContains(
                "[Text Finder] Finished looking for pattern '"
                        + UNIQUE_TEXT
                        + "' in the console output",
                build);
        TestUtils.assertConsoleContainsMatch(ECHO_UNIQUE_TEXT, rule, build, true);
        rule.assertBuildStatus(Result.NOT_BUILT, build);
    }

    @Test
    public void notFoundInConsole() throws Exception {
        WorkflowJob project = rule.createProject(WorkflowJob.class);
        project.setDefinition(
                new CpsFlowDefinition(
                        "node {\n"
                                + "  findText regexp: '"
                                + UNIQUE_TEXT
                                + "', alsoCheckConsoleOutput: true\n"
                                + "}\n",
                        true));
        WorkflowRun build = rule.buildAndAssertSuccess(project);
        rule.assertLogContains("[Text Finder] Scanning console output...", build);
        rule.assertLogContains(
                "[Text Finder] Finished looking for pattern '"
                        + UNIQUE_TEXT
                        + "' in the console output",
                build);
    }

    @Test
    public void lastFinderWins() throws Exception {
        WorkflowJob project = rule.createProject(WorkflowJob.class, "pipeline");
        project.setDefinition(
                new CpsFlowDefinition(
                        "node {isUnix() ? sh('echo foobar') : bat(\"prompt \\$G\\r\\necho foobar\")}\n"
                                + "node {"
                                + "findText regexp: 'foobar', alsoCheckConsoleOutput: true\n"
                                + "findText regexp: 'foobar', unstableIfFound: true, alsoCheckConsoleOutput: true\n"
                                + "findText regexp: 'foobar', notBuiltIfFound: true, alsoCheckConsoleOutput: true\n"
                                + "}"));
        WorkflowRun build = project.scheduleBuild2(0).get();
        rule.waitForCompletion(build);
        rule.assertLogContains("[Text Finder] Scanning console output...", build);
        rule.assertLogContains(
                "[Text Finder] Finished looking for pattern '"
                        + UNIQUE_TEXT
                        + "' in the console output",
                build);
        TestUtils.assertConsoleContainsMatch(ECHO_UNIQUE_TEXT, rule, build, true);
        rule.assertBuildStatus(Result.NOT_BUILT, build);
    }
}
