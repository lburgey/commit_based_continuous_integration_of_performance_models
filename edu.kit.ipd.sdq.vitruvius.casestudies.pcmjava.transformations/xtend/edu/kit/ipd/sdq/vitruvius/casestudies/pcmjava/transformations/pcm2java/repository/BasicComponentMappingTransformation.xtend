package edu.kit.ipd.sdq.vitruvius.casestudies.pcmjava.transformations.pcm2java.repository

import de.uka.ipd.sdq.pcm.repository.BasicComponent
import de.uka.ipd.sdq.pcm.repository.RepositoryFactory
import edu.kit.ipd.sdq.vitruvius.casestudies.pcmjava.PCMJaMoPPNamespace
import edu.kit.ipd.sdq.vitruvius.casestudies.pcmjava.transformations.JaMoPPPCMUtils
import edu.kit.ipd.sdq.vitruvius.framework.contracts.datatypes.TransformationChangeResult
import edu.kit.ipd.sdq.vitruvius.framework.meta.correspondence.Correspondence
import edu.kit.ipd.sdq.vitruvius.framework.run.transformationexecuter.EmptyEObjectMappingTransformation
import edu.kit.ipd.sdq.vitruvius.framework.run.transformationexecuter.TransformationUtils
import java.util.ArrayList
import org.apache.log4j.Logger
import org.eclipse.emf.ecore.EAttribute
import org.eclipse.emf.ecore.EObject
import org.eclipse.emf.ecore.EReference
import org.eclipse.emf.ecore.util.EcoreUtil
import org.emftext.language.java.classifiers.Class
import org.emftext.language.java.classifiers.ClassifiersFactory
import org.emftext.language.java.containers.CompilationUnit
import org.emftext.language.java.containers.ContainersFactory
import org.emftext.language.java.containers.Package
import org.emftext.language.java.modifiers.ModifiersFactory

class BasicComponentMappingTransformation extends EmptyEObjectMappingTransformation {

	val private static Logger logger = Logger.getLogger(BasicComponentMappingTransformation.simpleName)

	override getClassOfMappedEObject() {
		return BasicComponent
	}

	override createEObject(EObject eObject) {
		val BasicComponent basicComponent = eObject as BasicComponent

		var Package rootPackage = JaMoPPPCMUtils.findCorrespondingPackageByName(basicComponent.repository__RepositoryComponent.entityName,
			correspondenceInstance, basicComponent.repository__RepositoryComponent)

		// create JaMoPP Package
		val Package jaMoPPPackage = ContainersFactory.eINSTANCE.createPackage
		jaMoPPPackage.name = basicComponent.entityName
		jaMoPPPackage.namespaces.addAll(rootPackage.namespaces)
		jaMoPPPackage.namespaces.add(rootPackage.name)

		//create JaMoPP compilation unit and JaMoPP class 
		val Class jaMoPPClass = ClassifiersFactory.eINSTANCE.createClass
		jaMoPPClass.name = basicComponent.entityName + "Impl"
		jaMoPPClass.addModifier(ModifiersFactory.eINSTANCE.createPublic)
		val CompilationUnit jaMoPPCompilationUnit = ContainersFactory.eINSTANCE.createCompilationUnit
		jaMoPPCompilationUnit.name = jaMoPPClass.name + ".java"

		// add classifier to compilation unit
		jaMoPPCompilationUnit.classifiers.add(jaMoPPClass)

		// add compilation unit to package		
		jaMoPPPackage.compilationUnits.add(jaMoPPCompilationUnit)
		jaMoPPCompilationUnit.namespaces.addAll(jaMoPPPackage.namespaces)
		jaMoPPCompilationUnit.namespaces.add(jaMoPPPackage.name)
		jaMoPPPackage.compilationUnits.add(jaMoPPCompilationUnit)

		return #[jaMoPPPackage, jaMoPPCompilationUnit, jaMoPPClass];
	}

	override createNonRootEObjectInList(EObject affectedEObject, EReference affectedReference, EObject newValue,
		int index, EObject[] newCorrespondingEObjects) {
		val BasicComponent basicComponent = newValue as BasicComponent

		// get root (aka repository) package
		val Package rootPackage = correspondenceInstance.
			claimUniqueCorrespondingEObjectByType(basicComponent.repository__RepositoryComponent, Package)
		val Correspondence parentCorrespondence = correspondenceInstance.
			claimUniqueOrNullCorrespondenceForEObject(rootPackage)

		val transformationResult = TransformationUtils.
			createTransformationChangeResultForNewRootEObjects(newCorrespondingEObjects)
		for (jaMoPPElement : newCorrespondingEObjects) {
			transformationResult.addNewCorrespondence(correspondenceInstance, basicComponent, jaMoPPElement,
				parentCorrespondence)
		}
		return transformationResult
	}

	override removeEObject(EObject eObject) {
		val correspondences = correspondenceInstance.getAllCorrespondences(eObject);
		val correspondingEObjects = correspondenceInstance.getAllCorrespondingEObjects(eObject)
		var eObjectsToDelete = new ArrayList<EObject>()
		if (!correspondences.nullOrEmpty) {
			eObjectsToDelete.addAll(correspondences)
		}
		if (!correspondingEObjects.nullOrEmpty) {
			eObjectsToDelete.addAll(correspondingEObjects)
		}
		return eObjectsToDelete
	}

	override deleteNonRootEObjectInList(EObject affectedEObject, EReference affectedReference, EObject oldValue,
		int index, EObject[] oldCorrespondingEObjectsToDelete) {
		if (oldCorrespondingEObjectsToDelete.nullOrEmpty) {
			return TransformationUtils.createEmptyTransformationChangeResult
		}
		oldCorrespondingEObjectsToDelete.forEach[eObject|EcoreUtil.delete(eObject)]
		return TransformationUtils.createEmptyTransformationChangeResult
	}

	/**
	 * Called when a basic component is renamed. Following things are done in order to preserve conistency:
	 * 1) remove old package-info.java file and adds a new one in the new package
	 * 2) remove old compilation unit and move it to the new package with the new name + Impl
	 * 3) rename the classifier in the compilaiton unit accordingly
	 * 
	 */
	override updateSingleValuedEAttribute(EObject eObject, EAttribute affectedAttribute, Object oldValue,
		Object newValue) {
		val affectedEObjects = PCM2JaMoPPUtils.checkKeyAndCorrespondingObjects(eObject, affectedAttribute,
			featureCorrespondenceMap, correspondenceInstance)
		if (affectedEObjects.nullOrEmpty) {
			return TransformationUtils.createEmptyTransformationChangeResult
		}
		val tcr = new TransformationChangeResult
		val jaMoPPPackages = affectedEObjects.filter(typeof(Package))
		if (!jaMoPPPackages.nullOrEmpty) {
			val Package jaMoPPPackage = jaMoPPPackages.get(0)
			PCM2JaMoPPUtils.handleJavaRootNameChange(jaMoPPPackage, affectedAttribute, newValue, tcr,
				correspondenceInstance, true)
		}

		val cus = affectedEObjects.filter(typeof(CompilationUnit))
		if (!cus.nullOrEmpty) {
			val CompilationUnit cu = cus.get(0)
			PCM2JaMoPPUtils.handleJavaRootNameChange(cu, affectedAttribute, newValue, tcr, correspondenceInstance, true)
		}
		return tcr
	}

	override setCorrespondenceForFeatures() {
		var basicComponentNameAttribute = RepositoryFactory.eINSTANCE.createBasicComponent.eClass.getEAllAttributes.
			filter[attribute|attribute.name.equalsIgnoreCase(PCMJaMoPPNamespace.PCM::PCM_ATTRIBUTE_ENTITY_NAME)].
			iterator.next
		var classNameAttribute = ClassifiersFactory.eINSTANCE.createClass.eClass.getEAllAttributes.filter[attribute|
			attribute.name.equalsIgnoreCase(PCMJaMoPPNamespace.JaMoPP::JAMOPP_ATTRIBUTE_NAME)].iterator.next
		var packageNameAttribute = ContainersFactory.eINSTANCE.createPackage.eClass.getEAllAttributes.filter[attribute|
			attribute.name.equalsIgnoreCase(PCMJaMoPPNamespace.JaMoPP::JAMOPP_ATTRIBUTE_NAME)].iterator.next
		featureCorrespondenceMap.put(basicComponentNameAttribute, classNameAttribute)
		featureCorrespondenceMap.put(basicComponentNameAttribute, packageNameAttribute)
	}

}
