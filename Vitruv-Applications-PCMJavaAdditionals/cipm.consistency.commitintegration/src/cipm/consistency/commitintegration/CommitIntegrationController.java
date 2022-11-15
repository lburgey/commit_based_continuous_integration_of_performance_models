package cipm.consistency.commitintegration;

import cipm.consistency.models.CodeModel;
import cipm.consistency.tools.evaluation.data.EvaluationDataContainer;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.apache.log4j.Logger;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.revwalk.RevCommit;
import tools.vitruv.change.composite.description.PropagatedChange;

/**
 * This class is responsible for controlling the complete change propagation and adaptive
 * instrumentation.
 * 
 * @author Martin Armbruster
 * @author Lukas Burgey
 */
public abstract class CommitIntegrationController<CM extends CodeModel> {
    private static final Logger LOGGER = Logger.getLogger(CommitIntegrationController.class.getName());
    protected CommitIntegrationState<CM> state;

    public void initialize(CommitIntegration<CM> commitIntegration) throws InvalidRemoteException, TransportException, IOException, GitAPIException {
        state = new CommitIntegrationState<CM>();
        state.initialize(commitIntegration);
    } 
    
    /**
     * 
     * @return The changes which were propagated when propagating the current checked out version
     */
    public List<PropagatedChange> propagateCurrentCheckout() {
        var workTree = state.getGitRepositoryWrapper().getWorkTree().toPath();
        var resource = state.getCodeModel().parseSourceCodeDir(workTree);
        return state.getVsum().propagateResource(resource);
    }
    
    /**
     * Propagates changes between two commits to the VSUM.
     * 
     * @param start
     *            the first commit.
     * @param end
     *            the second commit.
     * @return A list of propagated changes (which may be empty) or {null}.
     * @throws IncorrectObjectTypeException
     * @throws IOException
     *             if something from the repositories cannot be read.
     */
    public List<PropagatedChange> propagateChanges(RevCommit start, RevCommit end)
            throws IncorrectObjectTypeException, IOException {
        String commitId = end.getId()
            .getName();
        LOGGER.debug("Obtaining all differences.");
        List<DiffEntry> diffs = state.getGitRepositoryWrapper().computeDiffsBetweenTwoCommits(start, end);
        if (diffs.size() == 0) {
            LOGGER.info("No source files changed for " + commitId + ": No propagation is performed.");
            return List.of();
        }
        var cs = EvaluationDataContainer.getGlobalContainer()
            .getChangeStatistic();
        String oldId = start != null ? start.getId()
            .getName() : null;
        cs.setOldCommit(oldId != null ? oldId : "");
        cs.setNewCommit(commitId);
        cs.setNumberCommits(state.getGitRepositoryWrapper().getAllCommitsBetweenTwoCommits(oldId, commitId)
            .size() + 1);

        if (checkout(commitId)) {
            return propagateCurrentCheckout();
        }
        return null;
    }

    /**
     * Propagates the changes between an empty repository and the latest commit.
     * 
     * @throws IOException
     *             if something from the repositories cannot be read.
     * @throws GitAPIException
     *             if there is an exception within the Git usage.
     */
    public void propagteChanges() throws IOException, GitAPIException {
        propagateChanges(state.getGitRepositoryWrapper()
            .getLatestCommit());
    }

    /**
     * Propagates changes for a given list of commits.
     * 
     * @param ids
     *            ids of the commits.
     * @throws GitAPIException
     *             if there is an exception within the Git usage.
     * @throws IOException
     *             if the repository cannot be read.
     */
    public void propagateChanges(String... ids) throws GitAPIException, IOException {
        List<RevCommit> commits = new ArrayList<>();
        for (String id : ids) {
            commits.add(state.getGitRepositoryWrapper()
                .getCommitForId(id));
        }
        propagateChanges(commits);
    }

    /**
     * Fetches changes from the remote repository and propagates them to the VSUM.
     * 
     * @throws IOException
     *             if something from the repositories cannot be read.
     * @throws GitAPIException
     *             if there is an exception within the Git usage.
     */
    public void fetchAndPropagateChanges() throws IOException, GitAPIException {
        RevCommit lastCommit = state.getGitRepositoryWrapper()
            .getLatestCommit();
        LOGGER.debug("Latest commit is " + lastCommit.getId()
            .getName());
        LOGGER.debug("Fetching remote repository to get new commits.");
        List<RevCommit> nextCommits = state.getGitRepositoryWrapper()
            .fetchAndGetNewCommits();
        LOGGER.debug("Got " + nextCommits.size() + " new commits.");
        nextCommits.add(0, lastCommit);
        propagateChanges(nextCommits);
        LOGGER.debug("Finished the change propagation.");
    }

    /**
     * Propagates changes from a given list of commits to the VSUM.
     * 
     * @param commits
     *            the list of commits with changes to propagate.
     * @throws GitAPIException
     *             if there is an exception within the Git usage.
     * @throws IOException
     *             if something from the repositories cannot be read.
     */
    public void propagateChanges(List<RevCommit> commits) throws GitAPIException, IOException {
        List<List<PropagatedChange>> allPropagatedChanges = new ArrayList<>();
        if (commits.size() > 0) {
            RevCommit first = commits.remove(0);
            LOGGER.debug("Propagating " + commits.size() + " commits.");
            for (RevCommit next : commits) {
                var propagatedChanges = propagateChanges(first, next);
                if (null != propagatedChanges) {
                    allPropagatedChanges.add(propagatedChanges);
                    first = next;
                }
            }
            LOGGER.debug("Finished propagating the commits.");
        }
    }

    /**
     * Propagates changes between an empty repository and a specific commit.
     * 
     * @param commitId
     *            id of the commit.
     * @throws GitAPIException
     *             if there is an exception within the Git usage.
     * @throws IOException
     *             if the repository cannot be read.
     */
    public void propagateChanges(String commitId) throws GitAPIException, IOException {
        propagateChanges(state.getGitRepositoryWrapper()
            .getCommitForId(commitId));
    }

    /**
     * Propagates changes between an empty repository and a specific commit.
     * 
     * @param commit
     *            the commit.
     * @throws GitAPIException
     *             if there is an exception within the Git usage.
     * @throws IOException
     *             if something from the repositories cannot be read.
     */
    public void propagateChanges(RevCommit commit) throws GitAPIException, IOException {
        propagateChanges(null, commit);
    }

    /**
     * Propagates changes between two commits to the VSUM.
     * 
     * @param startId
     *            id of the first commit.
     * @param endId
     *            id of the second commit.
     * @return true if the changes are successfully propagated. false indicates that there are no
     *         changes for Java files or the pre-processing failed.
     * @throws GitAPIException
     *             if there is an exception within the Git usage.
     * @throws IOException
     *             it the repository cannot be read.
     */
    public List<PropagatedChange> propagateChanges(String startId, String endId)
            throws GitAPIException, IOException, IllegalArgumentException {
        return propagateChanges(state.getGitRepositoryWrapper()
            .getCommitForId(startId),
                state.getGitRepositoryWrapper()
                    .getCommitForId(endId));
    }

    /**
     * Can be overwritten to do processing after every checkout
     * 
     * @return
     */
    protected boolean preprocessCheckout() {
        return true;
    }

    public boolean checkout(String commitId) {
        LOGGER.debug("Checkout of " + commitId);
        try {
            state.getGitRepositoryWrapper()
                .checkout(commitId);
            if (!preprocessCheckout()) {
                LOGGER.debug("The preprocessing failed. Aborting.");
                return false;
            }
            return true;
        } catch (GitAPIException e) {
            LOGGER.error("Unable to checkout", e);
        }
        return false;
    }

//    /**
//     * Propagates changes between two commits to the VSUM.
//     * 
//     * @param start
//     *            the first commit.
//     * @param end
//     *            the second commit.
//     * @return A list of propagated changes (which may be empty) or {null}.
//     * @throws IncorrectObjectTypeException
//     * @throws IOException
//     *             if something from the repositories cannot be read.
//     */
//    public List<PropagatedChange> propagateChanges(RevCommit start, RevCommit end)
//            throws IncorrectObjectTypeException, IOException {
//        String commitId = end.getId()
//            .getName();
//        LOGGER.debug("Obtaining all differences.");
//        List<DiffEntry> diffs = state.getGitRepositoryWrapper()
//            .computeDiffsBetweenTwoCommits(start, end);
//        if (diffs.size() == 0) {
//            LOGGER.info("No source files changed for " + commitId + ": No propagation is performed.");
//            return List.of();
//        }
//        var cs = EvaluationDataContainer.getGlobalContainer()
//            .getChangeStatistic();
//        String oldId = start != null ? start.getId()
//            .getName() : null;
//        cs.setOldCommit(oldId != null ? oldId : "");
//        cs.setNewCommit(commitId);
//        cs.setNumberCommits(state.getGitRepositoryWrapper()
//            .getAllCommitsBetweenTwoCommits(oldId, commitId)
//            .size() + 1);
//
//        if (checkout(commitId)) {
//            return propagateCurrentCheckout();
//        }
//        return null;
//    }
//
//    /**
//     * Propagates the changes between two commits.
//     * 
//     * @param oldCommit
//     *            the first commit or null.
//     * @param newCommit
//     *            the second commit. Changes between the oldCommit and newCommit are propagated.
//     * @param storeInstrumentedModel
//     *            true if the instrumented code model shall be stored in this instance.
//     * @return true if the propagation was successful. false otherwise.
//     * @throws IOException
//     *             if an IO operation fails.
//     * @throws GitAPIException
//     *             if a Git operation fails.
//     */
//    public List<PropagatedChange> propagateChanges(String oldCommit, String newCommit, boolean storeInstrumentedModel)
//            throws IOException, GitAPIException {
//        {
//            var parent = state.getDirLayout()
//                .getCommitsFilePath()
//                .toAbsolutePath()
//                .getParent();
//            if (Files.notExists(parent)) {
//                Files.createDirectories(parent);
//            }
//        }
//        try (BufferedWriter writer = Files.newBufferedWriter(state.getDirLayout()
//            .getCommitsFilePath())) {
//            if (oldCommit != null) {
//                writer.write(oldCommit + "\n");
//            }
//            writer.write(newCommit + "\n");
//        }
//
//        long overallTimer = System.currentTimeMillis();
//        state.getIm()
//            .getDirLayout()
//            .clean();
//        var insDir = state.getIm()
//            .getDirLayout()
//            .getRootDirPath();
//
//        // Deactivate all action instrumentation points.
//        state.getIm()
//            .getModel()
//            .getPoints()
//            .forEach(sip -> sip.getActionInstrumentationPoints()
//                .forEach(aip -> aip.setActive(false)));
//        state.getIm()
//            .saveToDisk();
//
//        long fineTimer = System.currentTimeMillis();
//
//        // Propagate the changes.
//        var propagatedChanges = propagateChanges(oldCommit, newCommit);
//
//        fineTimer = System.currentTimeMillis() - fineTimer;
//        EvaluationDataContainer.getGlobalContainer()
//            .getExecutionTimes()
//            .setChangePropagationTime(fineTimer);
//
//        if (propagatedChanges != null) {
//            // TODO out commented this
////            @SuppressWarnings("restriction")
////            ExternalCallEmptyTargetFiller filler = new ExternalCallEmptyTargetFiller(facade.getVSUM()
////                .getCorrespondenceModel(),
////                    facade.getPCMWrapper()
////                        .getRepository(),
////                    propagator.getJavaFileLayout()
////                        .getExternalCallTargetPairsFile());
////            filler.fillExternalCalls();
//
//            boolean hasChangedIM = false;
//            for (var sip : state.getIm()
//                .getModel()
//                .getPoints()) {
//                for (var aip : sip.getActionInstrumentationPoints()) {
//                    hasChangedIM |= aip.isActive();
//                }
//            }
//            if (!hasChangedIM) {
//                LOGGER.debug("No instrumentation points changed.");
//            }
//            boolean fullInstrumentation = CommitIntegrationSettingsContainer.getSettingsContainer()
//                .getPropertyAsBoolean(SettingKeys.PERFORM_FULL_INSTRUMENTATION);
//
//            // Instrument the code only if there is a new action instrumentation point or if a
//            // full
//            // instrumentation
//            // shall be performed.
//            if (hasChangedIM || fullInstrumentation) {
//                fineTimer = System.currentTimeMillis();
//                Resource insModel = performInstrumentation(insDir, fullInstrumentation);
//                fineTimer = System.currentTimeMillis() - fineTimer;
//                EvaluationDataContainer.getGlobalContainer()
//                    .getExecutionTimes()
//                    .setInstrumentationTime(fineTimer);
//                if (storeInstrumentedModel) {
//                    this.instrumentedModel = insModel;
//                }
//            }
//        }
//        overallTimer = System.currentTimeMillis() - overallTimer;
//        EvaluationDataContainer.getGlobalContainer()
//            .getExecutionTimes()
//            .setOverallTime(overallTimer);
//
//        return propagatedChanges;
//    }
//
//    /**
//     * Removes potentially available instrumented code and performs a new instrumentation.
//     * 
//     * @param performFullInstrumentation
//     *            true if a full instrumentation shall be performed. false otherwise.
//     * @return the instrumented code model as a copy of the code model in the V-SUM.
//     */
//    public Resource instrumentCode(boolean performFullInstrumentation) {
//        Path insDir = state.getIm()
//            .getDirLayout()
//            .getRootDirPath();
//        removeInstrumentationDirectory(insDir);
//        return performInstrumentation(insDir, performFullInstrumentation);
//    }
//
//    private void removeInstrumentationDirectory(Path instrumentationDirectory) {
//        if (Files.exists(instrumentationDirectory)) {
//            LOGGER.debug("Deleting the instrumentation directory.");
//            try {
//                FileUtils.deleteDirectory(instrumentationDirectory.toFile());
//            } catch (IOException e) {
//                LOGGER.error(e);
//            }
//        }
//    }
//
//    @SuppressWarnings("restriction")
//    private Resource performInstrumentation(Path instrumentationDirectory, boolean performFullInstrumentation) {
//        Resource javaModel = state.getCodeModel()
//            .getResource();
//        return CodeInstrumenter.instrument(state.getIm()
//            .getModel(),
//                state.getVsum()
//                    .getVsum()
//                    .getCorrespondenceModel(),
//                javaModel, instrumentationDirectory, state.getGitRepositoryWrapper()
//                    .getWorkTree(),
//                !performFullInstrumentation);
//    }
//
//    /**
//     * Compiles and deploy the instrumented code.
//     * 
//     * @throws IOException
//     *             if an IO operation fails.
//     */
//    public void compileAndDeployInstrumentedCode() throws IOException {
//        Path instrumentationCodeDir = state.getIm()
//            .getDirLayout()
//            .getRootDirPath();
//        if (Files.exists(instrumentationCodeDir)) {
//            boolean compilationResult = compileInstrumentedCode(instrumentationCodeDir);
//            if (compilationResult) {
//                Path deployPath = Paths.get(CommitIntegrationSettingsContainer.getSettingsContainer()
//                    .getProperty(SettingKeys.DEPLOYMENT_PATH));
//                var result = copyArtifacts(instrumentationCodeDir, deployPath);
//                LOGGER.debug("Removing the monitoring classes.");
//                result.forEach(p -> {
//                    try {
//                        removeMonitoringClasses(p);
//                    } catch (IOException e) {
//                        LOGGER.error(e);
//                    }
//                });
//            } else {
//                LOGGER.debug("Could not compile the instrumented code.");
//            }
//        }
//        LOGGER.debug("Finished the compilation and deployment.");
//    }
//
//    private boolean compileInstrumentedCode(Path insCode) {
//        LOGGER.debug("Compiling the instrumented code.");
//        String compileScript = CommitIntegrationSettingsContainer.getSettingsContainer()
//            .getProperty(SettingKeys.PATH_TO_COMPILATION_SCRIPT);
//        compileScript = new File(compileScript).getAbsolutePath();
//        return ExternalCommandExecutionUtils.runScript(insCode.toFile(), compileScript);
//    }
//
//    private List<Path> copyArtifacts(Path insCode, Path deployPath) throws IOException {
//        LOGGER.debug("Copying the artifacts to " + deployPath);
//        var warFiles = Files.walk(insCode)
//            .filter(Files::isRegularFile)
//            .filter(p -> p.getFileName()
//                .toString()
//                .endsWith(".war"))
//            .collect(Collectors.toCollection(ArrayList::new));
//        List<String> fileNames = new ArrayList<>();
//        for (int idx = 0; idx < warFiles.size(); idx++) {
//            String name = warFiles.get(idx)
//                .getFileName()
//                .toString();
//            if (fileNames.contains(name)) {
//                warFiles.remove(idx);
//                idx--;
//            } else {
//                fileNames.add(name);
//            }
//        }
//        List<Path> result = new ArrayList<>();
//        warFiles.forEach(p -> {
//            Path target = deployPath.resolve(p.getFileName());
//            try {
//                Files.copy(p, target, StandardCopyOption.REPLACE_EXISTING);
//                result.add(target);
//            } catch (IOException e) {
//                LOGGER.error(e);
//            }
//        });
//        return result;
//    }
//
//    private void removeMonitoringClasses(Path file) throws IOException {
//        Map<String, String> options = new HashMap<>();
//        options.put("create", "false");
////        try (FileSystem fileSys = FileSystems.newFileSystem(file, options)) {
////            String tmcEndPath = "cipm/consistency/bridge/monitoring/controller/ThreadMonitoringController.class";
////            String spEndPath = "cipm/consistency/bridge/monitoring/controller/ServiceParameters.class";
////            fileSys.getRootDirectories()
////                .forEach(root -> {
////                    try {
////                        Files.walk(root)
////                            .filter(p -> {
////                                String fullPath = p.toString();
////                                if (fullPath.endsWith(".jar") || fullPath.endsWith(".war")
////                                        || fullPath.endsWith(".zip")) {
////                                    try {
////                                        removeMonitoringClasses(p);
////                                    } catch (IOException e) {
////                                        LOGGER.error(e);
////                                    }
////                                } else if (fullPath.endsWith(tmcEndPath) || fullPath.endsWith(spEndPath)) {
////                                    return true;
////                                }
////                                return false;
////                            })
////                            .forEach(p -> {
////                                try {
////                                    Files.delete(p);
////                                } catch (IOException e) {
////                                    LOGGER.error(e);
////                                }
////                            });
////                    } catch (IOException e) {
////                        LOGGER.error(e);
////                    }
////                });
////        }
//    }
//
//    /**
//     * Loads the propagated commits.
//     * 
//     * @return an empty array if the commits cannot be loaded. Otherwise, the first index contains
//     *         the start commit (possibly null for the initial commit), and the second index
//     *         contains the target commit.
//     */
    public String[] loadCommits() {
        try {
            var lines = Files.readAllLines(state.getDirLayout()
                .getCommitsFilePath());
            String[] result = new String[2];
            if (lines.size() == 1) {
                result[1] = lines.get(0);
            } else {
                result[0] = lines.get(0);
                result[1] = lines.get(1);
            }
            return result;
        } catch (IOException e) {
            return new String[0];
        }
    }
//
//    public abstract List<PropagatedChange> propagateCurrentCheckout();
//
//    /**
//     * Propagates the changes between two commits.
//     * 
//     * @param oldCommit
//     *            the first commit or null.
//     * @param newCommit
//     *            the second commit. Changes between the oldCommit and newCommit are propagated.
//     * @return true if the propagation was successful. false otherwise.
//     * @throws IOException
//     *             if an IO operation fails.
//     * @throws GitAPIException
//     *             if a Git operation fails.
//     */
//
////    @SuppressWarnings("restriction")
////    public Resource getModelResource() {
////        var modelUri = getFileLayout().getModelFileUri();
////        var model = getVsumFacade().getVsum()
////            .getModelInstance(modelUri);
////        if (model != null)
////            return model.getResource();
////
////        LOGGER.error(String.format("Model URI does not exist in vsum: %s", modelUri));
////        return null;
////    }
//
////    public Resource getLastInstrumentedModelResource() {
////        return instrumentedModel;
////    }
}
