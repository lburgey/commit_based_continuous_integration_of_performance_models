package tools.vitruv.applications.pcmjava.seffstatements.code2seff

import org.emftext.language.java.members.Method
import org.palladiosimulator.pcm.repository.BasicComponent
import org.somox.gast2seff.visitors.InterfaceOfExternalCallFindingFactory
import org.somox.gast2seff.visitors.ResourceDemandingBehaviourForClassMethodFinding
//import tools.vitruv.domains.pcm.PcmDomainProvider
import tools.vitruv.change.correspondence.model.CorrespondenceModel
import tools.vitruv.change.interaction.UserInteractor
import tools.vitruv.change.propagation.impl.AbstractChangePropagationSpecification
import tools.vitruv.change.propagation.ResourceAccess
import tools.vitruv.change.atomic.EChange
import org.somox.gast2seff.visitors.AbstractFunctionClassificationStrategy
//import tools.vitruv.domains.java.JavaDomain
//import tools.vitruv.domains.java.JavaDomainProvider
//import tools.vitruv.change.echange.feature.attribute.ReplaceSingleValuedEAttribute
import org.emftext.language.java.commons.CommonsPackage
//import tools.vitruv.change.domains.AbstractVitruvDomain
import tools.vitruv.change.atomic.feature.attribute.ReplaceSingleValuedEAttribute

class Java2PcmMethodBodyChangePreprocessor extends AbstractChangePropagationSpecification {
	val Code2SeffFactory code2SeffFactory;
	
	new(Code2SeffFactory code2SEFFfactory) {
		this(code2SEFFfactory, new JavaDomainProvider().domain, new PcmDomainProvider().domain);
	}
	
	new(Code2SeffFactory code2SEFFfactory, JavaDomain sourceDomain, AbstractVitruvDomain targetDomain) {
		super(sourceDomain, targetDomain)
		this.code2SeffFactory = code2SEFFfactory
	}

	override propagateChange(EChange change, CorrespondenceModel correspondenceModel, ResourceAccess resourceAccess) {
		if (doesHandleChange(change, correspondenceModel)) {
			val attrChange = change as ReplaceSingleValuedEAttribute<?, ?>;
			val meth = attrChange.affectedEObject as Method;
			executeClassMethodBodyChangeRefiner(correspondenceModel, userInteractor, meth);
		}
	}

	override doesHandleChange(EChange change, CorrespondenceModel correspondenceModel) {
		if (!(change instanceof ReplaceSingleValuedEAttribute)) {
			return false;
		}
		val attrChange = change as ReplaceSingleValuedEAttribute<?, ?>;
		return attrChange.affectedEObject instanceof Method
			&& attrChange.affectedFeature == CommonsPackage.Literals.NAMED_ELEMENT__NAME
			&& !attrChange.newValue.equals("")
	}

	private def void executeClassMethodBodyChangeRefiner(CorrespondenceModel correspondenceModel,
		UserInteractor userInteracting, Method newMethod) {
		val basicComponentFinding = code2SeffFactory.createBasicComponentFinding
		val BasicComponent myBasicComponent = basicComponentFinding.findBasicComponentForMethod(newMethod,
			correspondenceModel);
		val classification = code2SeffFactory.createAbstractFunctionClassificationStrategy(basicComponentFinding,
			correspondenceModel, myBasicComponent);
		val InterfaceOfExternalCallFindingFactory interfaceOfExternalCallFinderFactory = code2SeffFactory.
			createInterfaceOfExternalCallFindingFactory(correspondenceModel, myBasicComponent);
		val ResourceDemandingBehaviourForClassMethodFinding resourceDemandingBehaviourForClassMethodFinding =
			code2SeffFactory.createResourceDemandingBehaviourForClassMethodFinding(correspondenceModel);
		val ClassMethodBodyChangedTransformation methodBodyChanged = createTransformation(
			newMethod, basicComponentFinding, classification, interfaceOfExternalCallFinderFactory,
			resourceDemandingBehaviourForClassMethodFinding);
		methodBodyChanged.execute(correspondenceModel, userInteracting);
	}
	
	protected def ClassMethodBodyChangedTransformation createTransformation(Method newMethod,
		BasicComponentFinding basicComponentFinding, AbstractFunctionClassificationStrategy classification,
		InterfaceOfExternalCallFindingFactory interfaceOfExternalCallFinderFactory,
		ResourceDemandingBehaviourForClassMethodFinding resourceDemandingBehaviourForClassMethodFinding) {
		return new ClassMethodBodyChangedTransformation(newMethod, basicComponentFinding, classification,
			interfaceOfExternalCallFinderFactory, resourceDemandingBehaviourForClassMethodFinding)
	}
}
