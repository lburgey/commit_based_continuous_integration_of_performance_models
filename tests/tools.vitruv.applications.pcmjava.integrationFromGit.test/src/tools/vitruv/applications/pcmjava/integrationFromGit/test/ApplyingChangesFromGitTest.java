package tools.vitruv.applications.pcmjava.integrationFromGit.test;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;


import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.eclipse.jgit.api.errors.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.apache.log4j.Logger;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.ui.wizards.JavaCapabilityConfigurationPage;
import org.eclipse.ui.dialogs.IOverwriteQuery;
import org.eclipse.ui.wizards.datatransfer.FileSystemStructureProvider;
import org.eclipse.ui.wizards.datatransfer.ImportOperation;

import tools.vitruv.applications.pcmjava.linkingintegration.tests.CodeIntegrationTest;
import tools.vitruv.applications.pcmjava.linkingintegration.tests.util.CodeIntegrationUtils;
import tools.vitruv.applications.pcmjava.linkingintegration.tests.util.DoneFlagProgressMonitor;
import tools.vitruv.applications.pcmjava.pojotransformations.java2pcm.Java2PcmChangePropagationSpecification;
import tools.vitruv.applications.pcmjava.tests.util.Java2PcmTransformationTest;
import tools.vitruv.applications.pcmjava.integrationFromGit.GitChangeApplier;
import tools.vitruv.applications.pcmjava.integrationFromGit.GitRepository;
import tools.vitruv.domains.java.JavaDomainProvider;
import tools.vitruv.domains.java.builder.VitruviusJavaBuilder;
import tools.vitruv.domains.java.builder.VitruviusJavaBuilderApplicator;
import tools.vitruv.domains.pcm.PcmDomainProvider;
import tools.vitruv.domains.pcm.PcmNamespace;
import tools.vitruv.framework.correspondence.CorrespondenceModel;
import tools.vitruv.framework.domains.VitruvDomain;
import tools.vitruv.framework.correspondence.Correspondence;
import tools.vitruv.framework.tuid.Tuid;
import tools.vitruv.framework.userinteraction.UserInteractionFactory;
import tools.vitruv.framework.vsum.InternalVirtualModel;
import tools.vitruv.framework.vsum.VirtualModelConfiguration;
import tools.vitruv.framework.vsum.VirtualModelImpl;
import tools.vitruv.framework.vsum.VirtualModelManager;
import tools.vitruv.testutils.util.TestUtil;

/**
 * @author Ilia
 *
 */
public class ApplyingChangesFromGitTest /*extends VitruviusUnmonitoredApplicationTest*/{

	
	@SuppressWarnings("unused")
    
	private static final Logger logger = Logger.getLogger(CodeIntegrationTest.class.getSimpleName());
    private static final String META_PROJECT_NAME = "vitruvius.meta";
    
    private IProject testProject;
    private String testProjectName = //"testAddParameter_Fri_Apr_24_18_45_38_CEST_2020";   
    								"humanBeing"; 
    								//"eu.fpetersen.cbs.pc";
    
    private String testProjectPath =//"testProjects/vitruvius/projectToApplyCommitsOn/testAddParameter_Fri_Apr_24_18_45_38_CEST_2020";
    								"testProjects/chupakhin/projectToApplyCommitsOn/humanBeing";
    								//"testProjects/petersen/projectToApplyCommitsOn/eu.fpetersen.cbs.pc";
    
    private GitRepository gitRepository;
    private String gitRepositoryPath = //"testProjects/vitruvius/projectWithCommits";
    									"testProjects/chupakhin/projectWithCommits";
    									//"testProjects/petersen/projectWithCommits";
    private String clonedGitRepositoryPath = "temporaryGitRepository";
    
    private IWorkspace workspace;
    private InternalVirtualModel virtualModel;
    
 
    

    @Before
    public void beforeTest() throws InvocationTargetException, InterruptedException, IOException, URISyntaxException, GitAPIException, CoreException {
        this.workspace = ResourcesPlugin.getWorkspace();
        //imporort test project into workspace
        importAndCopyProjectIntoWorkspace(workspace, testProjectName, testProjectPath);
        //Import vsum into workspace
        //importAndCopyProjectIntoWorkspace(workspace, "testAddParameter_vsum__Fri_Apr_24_18_45_38_CEST_2020", "C:/Users/Ilia/Desktop/testAddParameter_vsum__Fri_Apr_24_18_45_38_CEST_2020");
        
        IProject[] iProjects = this.workspace.getRoot().getProjects();
        IProject project = this.workspace.getRoot().getProject(this.testProjectName);
        
        assert project != null;
        this.testProject = project;
        
        File clonedGitRepository = (new File(workspace.getRoot().getLocation().toFile(), "clonedGitRepositories"));
        clonedGitRepository.mkdir();
        clonedGitRepository.createNewFile();
        
        File originGitRepository = new File(gitRepositoryPath);
        
        
        gitRepository = new GitRepository(clonedGitRepository, originGitRepository.getAbsolutePath());

        //Integrate test project in Vitruv
        CodeIntegrationUtils.integratProject(project);
        
    }
    
    
	@Ignore @Test
	public void testApplyCommits() throws NoHeadException, GitAPIException, IOException, CoreException {
		CorrespondenceModel correspModel = virtualModel.getCorrespondenceModel();
		List<Correspondence> correspondences = correspModel.getAllCorrespondences();
		GitChangeApplier changeApplier = new GitChangeApplier(gitRepository);
		List<RevCommit> commits = gitRepository.getAllCommits();
		
		for (int i = commits.size() - 1; i > 0; i--) {
			changeApplier.applyChangesFromCommit(commits.get(i), commits.get(i - 1), testProject);
		}
		
		System.out.println("dummy line");
	}
    
	
	
	@Test
    public void testStandardCodeIntegration() throws Throwable {

        File vsumFolder = new File(workspace.getRoot().getLocation().toFile(), "vitruvius.meta"/*"testAddParameter_vsum__Fri_Apr_24_18_45_38_CEST_2020"*/);
		    
        //two methods to connect Vitruv with the project
        //TestUtil.createVirtualModel(vsumFile, false, metamodels, Collections.singletonList(new Java2PcmChangePropagationSpecification()), UserInteractionFactory.instance.createDialogUserInteractor());
        //or:
        //virtualModel = new VirtualModelImpl(vsumFolder/*vsumFile*//*metaProject*/, UserInteractionFactory.instance.createDialogUserInteractor(), config);
        
        VirtualModelManager virtualModelManager = VirtualModelManager.getInstance();
        virtualModel = virtualModelManager.getVirtualModel(vsumFolder);
        
        //DoneFlagProgressMonitor progress = new DoneFlagProgressMonitor();
        // add PCM Java Builder to Project under test
        final VitruviusJavaBuilderApplicator pcmJavaBuilder = new VitruviusJavaBuilderApplicator();
        pcmJavaBuilder.addToProject(this.testProject, vsumFolder, Collections.singletonList(PcmNamespace.REPOSITORY_FILE_EXTENSION));
        // build the project
        this.testProject.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, VitruviusJavaBuilder.BUILDER_ID,
                new HashMap<String, String>(), new NullProgressMonitor()/*progress*/);
        /*
        while (!progress.isDone()) {
            Thread.sleep(100);
        }
         */
        testApplyCommits();
    }
	
	
	
	 public static void importAndCopyProjectIntoWorkspace(IWorkspace workspace, String projectName, String projectPath
		 	/*String testBundleName, String testSourceAndModelFolder*/)
	            throws IOException, URISyntaxException, InvocationTargetException, InterruptedException {
	        IOverwriteQuery overwriteQuery = new IOverwriteQuery() {
	            public String queryOverwrite(String file) {
	                return ALL;
	            };
	        };
	        IPath workspacePath = workspace.getRoot().getFullPath().append("/" + projectName);

	        //Bundle bundle = Platform.getBundle(testBundleName);
	        //URL projectBluePrintBundleURL = bundle.getEntry(testSourceAndModelFolder);
	        //URL fileURL = FileLocator.resolve(projectBluePrintBundleURL);
	        //File file = new File(fileURL.toURI());
	        //String baseDir = file.getAbsolutePath();// location of files to import
	        ImportOperation importOperation = new ImportOperation(workspacePath, new File(projectPath/*baseDir*/),
	                FileSystemStructureProvider.INSTANCE, overwriteQuery);
	        importOperation.setCreateContainerStructure(false);
	        //DoneFlagProgressMonitor progress = new DoneFlagProgressMonitor();//probably does not work correctly, because progress.isDone() is never true
	        importOperation.run(new NullProgressMonitor()/*progress*//*null*/);

	        // Wait for the project to be imported 
	        /*
	        while (!progress.isDone()) {
	            Thread.sleep(100);
	        }
	        */
	 }
	 
	 public static void importProjectIntoWorkspace(IWorkspace workspace, String pathToProject) throws CoreException {

	     IPath projectDotProjectFile = new Path(pathToProject + "/.project");
		 IProjectDescription projectDescription = workspace.loadProjectDescription(projectDotProjectFile);
		 IProject project = workspace.getRoot().getProject(projectDescription.getName());
		 JavaCapabilityConfigurationPage.createProject(project, projectDescription.getLocationURI(), null);
	     
	 }

	 
		
		/**
		 * @throws java.lang.Exception
		 */
		@BeforeClass
		public static void setUpBeforeClass() throws Exception {
		}

		/**
		 * @throws java.lang.Exception
		 */
		@AfterClass
		public static void tearDownAfterClass() throws Exception {
		}

		/**
		 * @throws java.lang.Exception
		 */
		@Before
		public void setUp() throws Exception {
		
		}

		/**
		 * @throws java.lang.Exception
		 */
		@After
		public void tearDown() throws Exception {
		}


	 

}
