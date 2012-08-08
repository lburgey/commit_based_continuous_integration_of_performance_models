/**
 * 
 */
package org.splevo.diffing;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.compare.diff.metamodel.DiffElement;
import org.eclipse.emf.compare.diff.metamodel.DiffModel;
import org.eclipse.emf.compare.diff.metamodel.ModelElementChangeLeftTarget;
import org.eclipse.emf.compare.diff.metamodel.UpdateAttribute;
import org.eclipse.emf.compare.diff.metamodel.util.DiffSwitch;
import org.eclipse.emf.compare.util.ModelUtils;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.ECrossReferenceAdapter;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.eclipse.gmt.modisco.java.emf.JavaPackage;
import org.eclipse.modisco.java.composition.javaapplication.JavaApplication;
import org.eclipse.modisco.java.composition.javaapplication.JavaapplicationPackage;
import org.junit.Test;
import org.splevo.diffing.kdm.KDMUtil;

/**
 * Unit test for the diffing service class.
 * 
 * @author Benjamin Klatt
 * 
 */
public class MatchEngineDiffingServiceTest {
	
	/** Source path to the native calculator implementation */
	private static final File NATIVE_JAVA2KDMMODEL_FILE = new File("testmodels/implementation/gcd/native/_java2kdm.xmi");
	
	/** Source path to the jscience based calculator implementation */
	private static final File JSCIENCE_JAVA2KDMMODEL_FILE = new File("testmodels/implementation/gcd/jscience/_java2kdm.xmi");

	/** Source path to the native calculator implementation */
	private static final File IMPORT_TEST_FILE_1 = new File("testmodels/implementation/importDiffing/1/1_java2kdm.xmi");
	
	/** Source path to the jscience based calculator implementation */
	private static final File IMPORT_TEST_FILE_2 = new File("testmodels/implementation/importDiffing/2/2_java2kdm.xmi");


	/**
	 * Test method for
	 * {@link org.splevo.diffing.EMFCompareDiffingService#getDiff(org.eclipse.modisco.java.composition.javaapplication.JavaApplication, org.eclipse.modisco.java.composition.javaapplication.JavaApplication)}
	 * .
	 * 
	 * @throws IOException
	 *             Identifies that either the source models could not be loaded
	 *             or the diff model could not be serialized.
	 * @throws InterruptedException 
	 * @throws Exception
	 */
	@Test
	public final void testImportDiff() throws IOException, InterruptedException {
		
		JavaApplication leadingModel = KDMUtil.loadKDMModel(IMPORT_TEST_FILE_1);
		JavaApplication integrationModel = KDMUtil.loadKDMModel(IMPORT_TEST_FILE_2);
		
		MatchEngineDiffingService diffingService = new MatchEngineDiffingService();
		DiffModel diff = diffingService.doDiff(leadingModel,integrationModel);
		
		EList<DiffElement> differences = diff.getDifferences();
		for (DiffElement diffElement : differences) {
			System.out.println(diffElement.getKind()+": "+diffElement.getClass().getName());
		}
		
		System.out.println("Found Differences: "+differences.size());
		
		ModelUtils.save(diff, "importDiffModel.emfdiff");
	}
	
	/**
	 * Test method for
	 * {@link org.splevo.diffing.EMFCompareDiffingService#getDiff(org.eclipse.modisco.java.composition.javaapplication.JavaApplication, org.eclipse.modisco.java.composition.javaapplication.JavaApplication)}
	 * .
	 * 
	 * @throws IOException
	 *             Identifies that either the source models could not be loaded
	 *             or the diff model could not be serialized.
	 * @throws InterruptedException 
	 * @throws Exception
	 */
	//@Test
	public final void testGetDiff() throws IOException, InterruptedException {
		
		JavaApplication leadingModel = KDMUtil.loadKDMModel(NATIVE_JAVA2KDMMODEL_FILE);
		JavaApplication integrationModel = KDMUtil.loadKDMModel(JSCIENCE_JAVA2KDMMODEL_FILE);
		
		MatchEngineDiffingService diffingService = new MatchEngineDiffingService();
		DiffModel diff = diffingService.doDiff(leadingModel,integrationModel);
		
		EList<DiffElement> differences = diff.getDifferences();
		for (DiffElement diffElement : differences) {
			System.out.println(diffElement.getKind()+": "+diffElement.getClass().getName());
		}
		
		System.out.println("Found Differences: "+differences.size());
		
		
		// TODO: process/interprete the differences persisited in the file below
		// This file can be smoothly opened with the sample reflective editor.
		// perhaps using a switch provided by emf compare?
		ModelUtils.save(diff, "diffModel.emfdiff");
	}
}
