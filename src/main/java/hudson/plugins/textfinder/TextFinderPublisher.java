package hudson.plugins.textfinder;

import static hudson.Util.fixEmpty;

import hudson.Extension;
import hudson.FilePath;
import hudson.Functions;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.remoting.RemoteOutputStream;
import hudson.remoting.VirtualChannel;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.FormValidation;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Reader;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.servlet.ServletException;
import jenkins.MasterToSlaveFileCallable;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.types.FileSet;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

/**
 * Text Finder plugin for Jenkins. Search in the workspace using a regular expression and determine
 * build outcome based on matches.
 *
 * @author Santiago.PericasGeertsen@sun.com
 */
public class TextFinderPublisher extends Recorder implements Serializable, SimpleBuildStep {

    /** This is the primary text finder in the configuration. */
    private final TextFinderModel primaryTextFinder;

    /** Additional text finder configurations are stored here. */
    private final List<TextFinderModel> additionalTextFinders = new ArrayList<>();;

    @DataBoundConstructor
    public TextFinderPublisher(String regexp, String buildId) {
        this.primaryTextFinder = new TextFinderModel(regexp, buildId);
        // Attempt to compile regular expression
        try {
            Pattern.compile(regexp);
        } catch (PatternSyntaxException e) {
            // falls through
        }
    }

    /**
     * @param fileSet Kept for backward compatibility with old configuration.
     * @param regexp Kept for backward compatibility with old configuration.
     * @param succeedIfFound Kept for backward compatibility with old configuration.
     * @param unstableIfFound Kept for backward compatibility with old configuration.
     * @param notBuiltIfFound Kept for backward compatibility with old configuration.
     * @param alsoCheckConsoleOutput Kept for backward compatibility with old configuration.
     * @param additionalTextFinders configuration for additional textFinders
     */
    @Deprecated
    private TextFinderPublisher(
            String fileSet,
            String regexp,
            String buildId,
            boolean succeedIfFound,
            boolean unstableIfFound,
            boolean notBuiltIfFound,
            boolean alsoCheckConsoleOutput,
            List<TextFinderModel> additionalTextFinders) {
        this.primaryTextFinder = new TextFinderModel(regexp, buildId);
        this.primaryTextFinder.setFileSet(Util.fixEmpty(fileSet != null ? fileSet.trim() : ""));
        this.primaryTextFinder.setSucceedIfFound(succeedIfFound);
        this.primaryTextFinder.setUnstableIfFound(unstableIfFound);
        this.primaryTextFinder.setAlsoCheckConsoleOutput(alsoCheckConsoleOutput);
        this.primaryTextFinder.setNotBuiltIfFound(notBuiltIfFound);
        this.setAdditionalTextFinders(additionalTextFinders);
    }

    @DataBoundSetter
    public void setFileSet(String fileSet) {
        this.primaryTextFinder.setFileSet(fileSet);
    }

    @DataBoundSetter
    public void setSucceedIfFound(boolean succeedIfFound) {
        this.primaryTextFinder.setSucceedIfFound(succeedIfFound);
    }

    @DataBoundSetter
    public void setUnstableIfFound(boolean unstableIfFound) {
        this.primaryTextFinder.setUnstableIfFound(unstableIfFound);
    }

    @DataBoundSetter
    public void setNotBuiltIfFound(boolean notBuiltIfFound) {
        this.primaryTextFinder.setNotBuiltIfFound(notBuiltIfFound);
    }

    @DataBoundSetter
    public void setAlsoCheckConsoleOutput(boolean alsoCheckConsoleOutput) {
        this.primaryTextFinder.setAlsoCheckConsoleOutput(alsoCheckConsoleOutput);
    }

    @DataBoundSetter
    public void setAdditionalTextFinders(List<TextFinderModel> additionalTextFinders) {
        this.additionalTextFinders.clear();
        if (additionalTextFinders != null && !additionalTextFinders.isEmpty()) {
            this.additionalTextFinders.addAll(additionalTextFinders);
        }
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Override
    public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener)
            throws InterruptedException, IOException {
        findText(primaryTextFinder, run, workspace, listener);
        for (TextFinderModel additionalTextFinder : additionalTextFinders) {
            findText(additionalTextFinder, run, workspace, listener);
        }
    }

    /** Indicates an orderly abortion of the processing. */
    private static final class AbortException extends RuntimeException {}

    private void findText(
            TextFinderModel textFinder, Run<?, ?> run, FilePath workspace, TaskListener listener)
            throws IOException, InterruptedException {
        try {
            PrintStream logger = listener.getLogger();
            FoundAndBuildId foundText = new FoundAndBuildId(false, null);

            if (textFinder.isAlsoCheckConsoleOutput()) {
                // Do not mention the pattern we are looking for to avoid false positives
                logger.println("[Text Finder] Scanning console output...");
                FoundAndBuildId freshFound =
                        checkConsole(
                                run,
                                compilePattern(logger, textFinder.getRegexp()),
                                compileOptionalPattern(logger, textFinder.getBuildId()),
                                logger);
                foundText = new FoundAndBuildId(foundText, freshFound);
                logger.println(
                        "[Text Finder] Finished looking for pattern "
                                + "'"
                                + textFinder.getRegexp()
                                + "'"
                                + " in the console output");
            }

            final RemoteOutputStream ros = new RemoteOutputStream(logger);

            if (textFinder.getFileSet() != null) {
                logger.println(
                        "[Text Finder] Looking for pattern "
                                + "'"
                                + textFinder.getRegexp()
                                + "'"
                                + " in the files at "
                                + "'"
                                + textFinder.getFileSet()
                                + "'");
                FoundAndBuildId freshFound =
                        workspace.act(
                                new FileChecker(
                                        ros,
                                        textFinder.getFileSet(),
                                        textFinder.getRegexp(),
                                        textFinder.getBuildId()));
                foundText = new FoundAndBuildId(foundText, freshFound);
            }
            if (foundText.futureBuildId != null) {
                run.setDisplayName(foundText.futureBuildId);
            }
            if (foundText.patternFound) {
                final Result finalResult;
                if (textFinder.isNotBuiltIfFound()) {
                    finalResult = Result.NOT_BUILT;
                } else if (textFinder.isUnstableIfFound()) {
                    finalResult = Result.UNSTABLE;
                } else if (isSucceedIfFound()) {
                    finalResult = Result.SUCCESS;
                } else {
                    finalResult = Result.FAILURE;
                }
                // avoiding setResult to be able to downgrade to success also from worse states
                changeField(run, "result", finalResult, listener);
            }
        } catch (AbortException e) {
            // files presented, but no test file found.
            run.setResult(Result.UNSTABLE);
        }
    }

    private static void changeField(
            final Object source,
            final String name,
            final Object value,
            final TaskListener listener) {
        try {
            changeFieldImpl(source, name, value, listener);
        } catch (Exception ex) {
            listener.getLogger().println(ex.toString());
        }
    }

    private static void changeFieldImpl(
            final Object source, final String name, final Object value, final TaskListener listener)
            throws IllegalAccessException, NoSuchFieldException {
        Field field = findField(source.getClass(), name);
        if (field == null) {
            throw new NoSuchFieldException(
                    name
                            + " not fund in "
                            + source.getClass().toString()
                            + " not in supperclasses");
        }
        field.setAccessible(true);
        listener.getLogger()
                .println(
                        source.getClass()
                                + " of "
                                + source.toString()
                                + ": "
                                + name
                                + " = "
                                + field.get(source));
        field.set(source, value);
        listener.getLogger()
                .println(
                        source.getClass()
                                + " of "
                                + source.toString()
                                + ": "
                                + name
                                + " = "
                                + field.get(source));
    }

    private static Field findField(final Class clazz, final String name) {
        try {
            return clazz.getDeclaredField(name);
        } catch (NoSuchFieldException ex) {
            if (clazz.getSuperclass() != null) {
                return findField((clazz.getSuperclass()), name);
            } else {
                return null;
            }
        }
    }

    private static final class FoundAndBuildId implements Serializable {
        private static final long serialVersionUID = 1L;
        public final boolean patternFound;
        public final String
                futureBuildId; // this can not be optional, as it can go from slave to master

        public FoundAndBuildId(boolean patternFound, String futureBuildId) {
            this.patternFound = patternFound;
            this.futureBuildId = futureBuildId;
        }

        public FoundAndBuildId(FoundAndBuildId old, FoundAndBuildId fresh) {
            this(old.patternFound | fresh.patternFound, overwriteByNonNull(old, fresh));
        }

        private static String overwriteByNonNull(FoundAndBuildId old, FoundAndBuildId fresh) {
            return fresh.futureBuildId != null ? fresh.futureBuildId : old.futureBuildId;
        }
    }

    /**
     * Search the given regexp pattern.
     *
     * @param abortAfterFirstHit true to return immediately as soon as the first hit is found. this
     *     is necessary when we are scanning the console output, because otherwise we'll loop
     *     forever.
     */
    private static FoundAndBuildId checkPattern(
            final Reader r,
            final Pattern pattern,
            final Optional<Pattern> buildId,
            final PrintStream logger,
            final String header,
            final boolean abortAfterFirstHit)
            throws IOException {
        boolean logFilename = true;
        boolean foundText = false;
        boolean foundBuildId = false;
        String buildIdResult = null;
        Pattern enchancedBuildId;
        if (buildId.isPresent()) {
            enchancedBuildId = Pattern.compile(buildId.get().pattern() + ".*");
        } else {
            enchancedBuildId = null;
        }
        try (BufferedReader reader = new BufferedReader(r)) {
            // Assume default encoding and text files
            String line;
            while ((line = reader.readLine()) != null) {
                if (buildId.isPresent() && !foundBuildId) {
                    Matcher matcher = enchancedBuildId.matcher(line);
                    if (matcher.find()) {
                        logger.println("[Text Finder] Found future buildId line: '" + line + "'");
                        buildIdResult = line.replaceAll(buildId.get().pattern(), "");
                        logger.println(
                                "[Text Finder] Leading to buildId of: '" + buildIdResult + "'");
                        foundBuildId = true;
                    }
                }
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    if (logFilename) { // first occurrence
                        if (header != null) {
                            logger.println(header);
                        }
                        logFilename = false;
                    }
                    logger.println(line);
                    foundText = true;
                    if (abortAfterFirstHit) {
                        return new FoundAndBuildId(true, buildIdResult);
                    }
                }
            }
        }
        return new FoundAndBuildId(foundText, buildIdResult);
    }

    private static FoundAndBuildId checkConsole(
            Run<?, ?> build, Pattern pattern, Optional<Pattern> buildId, PrintStream logger) {
        try (Reader r = build.getLogReader()) {
            return checkPattern(r, pattern, buildId, logger, null, true);
        } catch (IOException e) {
            logger.println("[Text Finder] Error reading console output -- ignoring");
            Functions.printStackTrace(e, logger);
            return new FoundAndBuildId(false, null);
        }
    }

    private static FoundAndBuildId checkFile(
            File f,
            Pattern pattern,
            Optional<Pattern> buildId,
            PrintStream logger,
            Charset charset) {
        try (InputStream is = new FileInputStream(f);
                Reader r = new InputStreamReader(is, charset)) {
            return checkPattern(r, pattern, buildId, logger, f + ":", false);
        } catch (IOException e) {
            logger.println("[Text Finder] Error reading file '" + f + "' -- ignoring");
            Functions.printStackTrace(e, logger);
            return new FoundAndBuildId(false, null);
        }
    }

    private static Pattern compilePattern(PrintStream logger, String regexp) {
        Pattern pattern;
        try {
            pattern = Pattern.compile(regexp);
        } catch (PatternSyntaxException e) {
            logger.println("[Text Finder] Unable to compile regular expression '" + regexp + "'");
            throw new AbortException();
        }
        return pattern;
    }

    private static Optional<Pattern> compileOptionalPattern(PrintStream logger, String regexp) {
        if (regexp == null || regexp.trim().isEmpty()) {
            return Optional.empty();
        }
        Pattern pattern;
        try {
            pattern = Pattern.compile(regexp);
        } catch (PatternSyntaxException e) {
            logger.println("[Text Finder] Unable to compile regular expression '" + regexp + "'");
            throw new AbortException();
        }
        return Optional.of(pattern);
    }

    @SuppressWarnings("unused")
    public List<TextFinderModel> getAdditionalTextFinders() {
        return additionalTextFinders;
    }

    @SuppressWarnings("unused")
    public String getFileSet() {
        return this.primaryTextFinder.getFileSet();
    }

    @SuppressWarnings("unused")
    public String getRegexp() {
        return this.primaryTextFinder.getRegexp();
    }

    @SuppressWarnings("unused")
    public String getBuildId() {
        return this.primaryTextFinder.getBuildId();
    }

    @SuppressWarnings("unused")
    public boolean isSucceedIfFound() {
        return this.primaryTextFinder.isSucceedIfFound();
    }

    @SuppressWarnings("unused")
    public boolean isUnstableIfFound() {
        return this.primaryTextFinder.isUnstableIfFound();
    }

    @SuppressWarnings("unused")
    public boolean isNotBuiltIfFound() {
        return this.primaryTextFinder.isNotBuiltIfFound();
    }

    @SuppressWarnings("unused")
    public boolean isAlsoCheckConsoleOutput() {
        return this.primaryTextFinder.isAlsoCheckConsoleOutput();
    }

    @Symbol("findText")
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        @Override
        public String getDisplayName() {
            return Messages.TextFinderPublisher_DisplayName();
        }

        @Override
        public String getHelpFile() {
            return "/plugin/text-finder/help.html";
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        public List<TextFinderModel.DescriptorImpl> getItemDescriptors() {
            Jenkins jenkins = Jenkins.getInstance();
            if (jenkins != null) {
                return jenkins.getDescriptorList(TextFinderModel.class);
            } else {
                throw new NullPointerException("not able to get Jenkins instance");
            }
        }

        /**
         * Checks the regular expression validity.
         *
         * @param value The expression to check
         * @return The form validation result
         * @throws IOException For backwards compatibility
         * @throws ServletException For backwards compatibility
         */
        public FormValidation doCheckRegexp(@QueryParameter String value)
                throws IOException, ServletException {
            value = fixEmpty(value);
            if (value == null) {
                return FormValidation.ok(); // not entered yet
            }

            try {
                Pattern.compile(value);
                return FormValidation.ok();
            } catch (PatternSyntaxException e) {
                return FormValidation.error(e.getMessage());
            }
        }
    }

    private static class FileChecker extends MasterToSlaveFileCallable<FoundAndBuildId> {

        private final RemoteOutputStream ros;
        private final String fileSet;
        private final String regexp;
        private final String buildId;

        public FileChecker(RemoteOutputStream ros, String fileSet, String regexp, String buildId) {
            this.ros = ros;
            this.fileSet = fileSet;
            this.regexp = regexp;
            this.buildId = buildId;
        }

        @Override
        public FoundAndBuildId invoke(File ws, VirtualChannel channel) throws IOException {
            PrintStream logger = new PrintStream(ros, true, Charset.defaultCharset().toString());

            // Collect list of files for searching
            FileSet fs = new FileSet();
            Project p = new Project();
            fs.setProject(p);
            fs.setDir(ws);
            fs.setIncludes(fileSet);
            DirectoryScanner ds = fs.getDirectoryScanner(p);

            // Any files in the final set?
            String[] files = ds.getIncludedFiles();
            if (files.length == 0) {
                logger.println("[Text Finder] File set '" + fileSet + "' is empty");
                throw new AbortException();
            }

            Pattern pattern = compilePattern(logger, regexp);
            Optional<Pattern> buildIdPattern = compileOptionalPattern(logger, buildId);

            FoundAndBuildId foundText = new FoundAndBuildId(false, null);

            for (String file : files) {
                File f = new File(ws, file);

                if (!f.exists()) {
                    logger.println("[Text Finder] Unable to find file '" + f + "'");
                    continue;
                }

                if (!f.canRead()) {
                    logger.println("[Text Finder] Unable to read from file '" + f + "'");
                    continue;
                }

                FoundAndBuildId freshFound =
                        checkFile(f, pattern, buildIdPattern, logger, Charset.defaultCharset());
                foundText = new FoundAndBuildId(foundText, freshFound);
            }

            return foundText;
        }
    }

    private static final long serialVersionUID = 1L;
}
