package cipm.consistency.vsum.test.appspace;

import cipm.consistency.commitintegration.git.GitRepositoryWrapper;
import java.io.IOException;
import java.nio.file.Paths;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.junit.Assert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A test class for the TeaStore.
 * 
 * @author Martin Armbruster
 */
public class MinimalCaseStudyTest extends AppSpaceCITest {
    private static final String COMMIT_1 = "842ec92f3406da965a5f9e7f468eb80eeb287b04";
    private static final String COMMIT_2 = "f63d05a18a5dd36f29e7312d797f2806d935e3a3";
    
    // this commit was a copied code snipped from case study 1, that did cause a problem over there
    private static final String COMMIT_PROBLEM = "4cbb4e16fb348920fbf28ca192e78ec5ce5f07eb";
    private static final String COMMIT_4 = "ed82c041912168e9deeea36076184806daaa312e";
    
    

    @BeforeEach
    public void initialize() throws InvalidRemoteException, TransportException, IOException, GitAPIException {
        super.initialize(this);
    }

    @AfterEach
    public void dispose() {
//        state.createCopyWithTimeStamp("after_testrun");
        state.getDirLayout().delete();
        state.dispose();
    }

    @Override
    public GitRepositoryWrapper getGitRepositoryWrapper()
            throws InvalidRemoteException, TransportException, GitAPIException, IOException {
        var parentGitDir = Paths.get("../../.git");
        var submoduleName = "change-based-adaptive-instrumentation/cipm.consistency.vsum.test/ciTestRepos/minimalCaseStudy";
        return super.getGitRepositoryWrapper()
            .withLocalSubmodule(parentGitDir, submoduleName)
            .initialize();
    }

    @Test
    public void test_complete_history() throws Exception {
        state.setTag("complete_history");
        // propagating the same version twice
        var propagatedChanges = assertSuccessfulPropagation(null, COMMIT_1, COMMIT_2, COMMIT_PROBLEM, COMMIT_4);
        Assert.assertTrue("Four sets of changes must exist", propagatedChanges.size() == 4);
    }

    @Test
    public void test_skip_intermediate() throws Exception {
        state.setTag("skip_intermediate");
        var propagatedChanges = assertSuccessfulPropagation(null, COMMIT_4);
        Assert.assertTrue("One change must exists", propagatedChanges.size() == 1);
//        var result = executePropagationAndEvaluation(null, getLatestCommitId(), 0);
//        Assert.assertTrue(result);
    }

    @Test
    public void test_same_commit_twice() throws Exception {
        state.setTag("same_commit_twice");
        // propagating the same version twice
        var propagatedChanges = assertSuccessfulPropagation(null, COMMIT_2, COMMIT_2);
        var lastPropagationResult = propagatedChanges.get(propagatedChanges.size() - 1);
        Assert.assertTrue("No changes must be generated when propagating a previously propagated commit", lastPropagationResult.isEmpty());
    }
}