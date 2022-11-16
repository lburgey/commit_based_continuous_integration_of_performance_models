package cipm.consistency.vsum.test.ci;

import cipm.consistency.tools.evaluation.data.EvaluationDataContainer;
import cipm.consistency.tools.evaluation.data.EvaluationDataContainerReaderWriter;
import cipm.consistency.vsum.test.evaluator.IMUpdateEvaluator;
import cipm.consistency.vsum.test.evaluator.InstrumentationEvaluator;
import cipm.consistency.vsum.test.evaluator.JavaModelEvaluator;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.spi.LoggingEvent;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Assert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import tools.vitruv.change.composite.description.PropagatedChange;

public abstract class AppSpaceCITest extends AppSpaceCommitIntegrationController {
    private static final Logger LOGGER = Logger.getLogger(AppSpaceCommitIntegrationController.class.getName());


    private List<String> convertToStringList(List<RevCommit> commits) {
        List<String> result = new ArrayList<>();
        for (RevCommit com : commits) {
            result.add(com.getId()
                .getName());
        }
        return result;
    }

    protected void assertSuccessfulPropagation(String oldCommit, String newCommit) {
        List<PropagatedChange> propagatedChanges = null;
        try {
            propagatedChanges = propagateChanges(oldCommit, newCommit);
        } catch (IOException | GitAPIException e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
        if (propagatedChanges == null) {
            Assert.fail("propagatedChanges must not be null");
        }
    }

    /**
     * Propagates changes between two commits and performs a partial evaluation on the result.
     * 
     * @param oldCommit
     *            the first commit. Can be null.
     * @param newCommit
     *            the second commit.
     * @param num
     *            the number of the propagation.
     * @return true if the changes were propagated. false otherwise.
     * @throws GitAPIException
     *             if an exception occurs during the Git processing.
     * @throws IOException
     *             if an IO operation cannot be performed.
     */
    @SuppressWarnings("restriction")
    protected boolean executePropagationAndEvaluation(String oldCommit, String newCommit, int num) throws IOException {
        EvaluationDataContainer evalResult = new EvaluationDataContainer();
        EvaluationDataContainer.setGlobalContainer(evalResult);

        // TODO this is currently not needed i think
//        String repoFile = getVsumFacade().getPCMWrapper()
//            .getRepository()
//            .eResource()
//            .getURI()
//            .toFileString();
//        FileUtils.copyFile(new File(repoFile), new File(this.getRootPath()
//            .toString(), "Repository.repository"));
//        FileUtils.copyFile(new File(repoFile), new File(this.getRootPath()
//            .toString(), "Repository_" + num + "_mu.repository"));

        assertSuccessfulPropagation(oldCommit, newCommit);

//        Resource codeModel = getModelResource();
//        Resource codeModel = state.getCodeModel().getResources();

        var copy = state.createFileSystemCopy(num + "-" + newCommit);
        LOGGER.debug("Evaluating the instrumentation.");
        new InstrumentationEvaluator.evaluateInstrumentationDependently(state.getImFacade()
            .getModel(),
                state.getCodeModelFacade()
                    .getResource(),
                instrumentedModel, state.getVsumFacade()
                    .getVsum()
                    .getCorrespondenceModel());
        EvaluationDataContainerReaderWriter.write(evalResult, copy.resolve("DependentEvaluationResult.json"));
        LOGGER.debug("Finished the evaluation.");
        return true;
    }

    protected void propagateMultipleCommits(String firstCommit, String lastCommit)
            throws IOException, InterruptedException, InvalidRemoteException, TransportException, GitAPIException {
        List<String> successfulCommits = new ArrayList<>();
        var commits = convertToStringList(
                getGitRepositoryWrapper().getAllCommitsBetweenTwoCommits(firstCommit, lastCommit));
        commits.add(0, firstCommit);
        int startIndex = 0;
        var oldCommit = commits.get(startIndex);
        successfulCommits.add(oldCommit);
        for (int idx = startIndex + 1; idx < commits.size(); idx++) {
            var newCommit = commits.get(idx);
            boolean result = executePropagationAndEvaluation(oldCommit, newCommit, idx);
            if (result) {
                oldCommit = newCommit;
                successfulCommits.add(oldCommit);
                break;
            }
            Thread.sleep(1000);
        }
        for (String c : successfulCommits) {
            LOGGER.debug("Successful propagated: " + c);
        }
    }

    /**
     * Performs an evaluation independent of the change propagation. It requires that changes
     * between two commits has been propagated. It is recommended that this method is not executed
     * with the executePropagationAndEvaluation method at the same time because this can cause a
     * OutOfMemoryError.
     * 
     * @throws IOException
     *             if an IO operation cannot be performed.
     */
    @SuppressWarnings("restriction")
    protected void performIndependentEvaluation() throws IOException {
        String[] commits = loadCommits();
        String oldCommit = commits[0];
        String newCommit = commits[1];
        LOGGER.debug("Evaluating the propagation " + oldCommit + "->" + newCommit);
        EvaluationDataContainer evalResult = EvaluationDataContainer.getGlobalContainer();
        evalResult.getChangeStatistic()
            .setOldCommit(oldCommit);
        evalResult.getChangeStatistic()
            .setNewCommit(newCommit);
        Resource javaModel = state.getCodeModel()
            .getResource();
        LOGGER.debug("Evaluating the Java model.");
        new JavaModelEvaluator().evaluateJavaModels(javaModel, state.getDirLayout()
            .getSettingsFilePath(),
                state.getCodeModel()
                    .getDirLayout()
                    .getLocalRepoDir(),
                evalResult.getJavaComparisonResult(), state.getCodeModel()
                    .getDirLayout()
                    .getModuleConfigurationPath());

        LOGGER.debug("Evaluating the instrumentation model.");
        new IMUpdateEvaluator().evaluateIMUpdate(state.getPcm()
            .getInMemoryPCM()
            .getRepository(),
                state.getIm()
                    .getModel(),
                evalResult.getImEvalResult(), getRootPath().toString());
        LOGGER.debug("Evaluating the instrumentation.");
        new InstrumentationEvaluator().evaluateInstrumentationIndependently(state.getIm()
            .getModel(), javaModel, getCommitChangePropagator().getFileSystemLayout(),
                state.getVsum()
                    .getVsum()
                    .getCorrespondenceModel());
        EvaluationDataContainerReaderWriter.write(evalResult, getRootPath()
            .resolveSibling("EvaluationResult-" + newCommit + "-" + evalResult.getEvaluationTime() + ".json"));
        LOGGER.debug("Finished the evaluation.");
    }

    /**
     * Returns the path to the local directory in which the data is stored.
     * 
     * @return the path.
     */
    public Path getRootPath() {
        return Path.of("testData", "tests", this.getClass()
            .getSimpleName());
    };

    /**
     * Returns the path to the settings file.
     * 
     * @return the path.
     */
    public Path getSettingsPath() {
        return getRootPath().resolve("settings.settings");
    };

    private class TrimmingLogFormat extends PatternLayout {
        private List<String> trim;

        public TrimmingLogFormat(String format, List<String> trim) {
            super(format);
            this.trim = trim;
        }

        @Override
        public String format(LoggingEvent event) {
            String msg = super.format(event);
            for (var t : trim) {
                msg = msg.replace(t, "[..]");
            }
            return msg;
        }
    }

    @BeforeEach
    public void setUpLogging() {
        // set log levels of the framework
        Logger.getLogger("cipm")
            .setLevel(Level.ALL);
        Logger.getLogger("jamopp")
            .setLevel(Level.ALL);
        Logger.getLogger("tools.vitruv")
            .setLevel(Level.WARN);
        Logger.getLogger("mir")
            .setLevel(Level.INFO); // mir belongs to vitruv
        Logger.getLogger("org.xtext.lua")
            .setLevel(Level.INFO);

        var rootLogger = Logger.getRootLogger();
        rootLogger.setLevel(Level.ALL);
        rootLogger.removeAllAppenders();
        var toTrim = List.of(System.getProperty("user.dir"), "cipm.consistency");
        var logFormat = new TrimmingLogFormat("%-5p: %c  %m%n", toTrim);
        ConsoleAppender ap = new ConsoleAppender(logFormat, ConsoleAppender.SYSTEM_OUT);
        rootLogger.addAppender(ap);
    }

    protected String getLatestCommitId() {
        try {
            return getGitRepositoryWrapper().getLatestCommit()
                .getName();
        } catch (GitAPIException | IOException e) {
            e.printStackTrace();
        }
        return null;
    }

}