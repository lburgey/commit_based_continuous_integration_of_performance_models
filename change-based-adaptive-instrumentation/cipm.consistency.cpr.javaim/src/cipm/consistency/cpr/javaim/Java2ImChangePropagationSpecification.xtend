package cipm.consistency.cpr.javaim

import cipm.consistency.base.models.instrumentation.InstrumentationModel.InstrumentationModel
import cipm.consistency.base.models.instrumentation.InstrumentationModel.InstrumentationModelPackage
import cipm.consistency.models.instrumentation.InstrumentationModelUtil
import org.emftext.language.java.members.Method
import org.palladiosimulator.pcm.seff.ResourceDemandingSEFF
import tools.vitruv.applications.pcmjava.seffstatements.code2seff.extended.ExtendedJava2PcmMethodBodyChangePreprocessor
import tools.vitruv.change.atomic.EChange
import tools.vitruv.change.atomic.feature.attribute.ReplaceSingleValuedEAttribute
import tools.vitruv.change.correspondence.model.CorrespondenceModel
import tools.vitruv.change.interaction.UserInteractor
import tools.vitruv.change.propagation.ResourceAccess
import tools.vitruv.change.correspondence.Correspondence
import tools.vitruv.change.correspondence.view.CorrespondenceModelView
import java.util.Set

/**
 * Propagates changes in method bodies to the extended instrumentation model.
 * 
 * @author Noureddine Dahmane
 * @author Martin Armbruster
 */
class Java2ImChangePropagationSpecification<C extends Correspondence> extends ExtendedJava2PcmMethodBodyChangePreprocessor {
// The change propagation between Java->PCM and Java->IM should be separated. However, there is a temporal constraint
// for Java->IM: the changes can only be propagated after the SEFF has been reconstructed because, otherwise, there are
// no actions for which instrumentation points can be generated. As a result, to ensure that the temporal constraint is
// met, the propagation rules for Java->IM extend the rules for Java->PCM and are executed after them.
	
	override propagateChange(EChange change, CorrespondenceModelView<C> correspondenceModelView, ResourceAccess resourceAccess) {
		super.propagateChange(change, correspondenceModel, resourceAccess)
		if (super.doesHandleChange(change, correspondenceModel)) {
			val attrChange = change as ReplaceSingleValuedEAttribute<?, ?>;
			val meth = attrChange.affectedEObject as Method;
			executeJava2ImTransformation(correspondenceModelView, userInteractor, meth)
		}
	}
	
	private def executeJava2ImTransformation(CorrespondenceModelView<C> correspondenceModelView,
		UserInteractor userInteracting, Method newMethod) {
		
		val correspondingSEFFs = correspondenceModelView.getCorrespondingEObjects(newMethod, null)
		val im = correspondenceModelView.getCorrespondingEObjects(InstrumentationModelPackage.Literals.INSTRUMENTATION_MODEL, null) as InstrumentationModel
		
		if (!correspondingSEFFs.empty) {
			val seff = correspondingSEFFs.last as ResourceDemandingSEFF
			var sip = im.points.filter[it.service == seff].last
			if (sip !== null) {
				sip.actionInstrumentationPoints.clear
				InstrumentationModelUtil.recursiveBuildImm(seff, sip)
			} else {
				sip = InstrumentationModelUtil.recursiveBuildImm(seff)
				im.points.add(sip)
			}
		}
	}
}
