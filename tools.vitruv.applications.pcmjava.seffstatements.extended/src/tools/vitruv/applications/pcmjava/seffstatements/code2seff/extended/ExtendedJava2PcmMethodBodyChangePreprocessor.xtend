package tools.vitruv.applications.pcmjava.seffstatements.code2seff.extended

import org.emftext.language.java.members.Method
import org.somox.gast2seff.visitors.InterfaceOfExternalCallFindingFactory
import org.somox.gast2seff.visitors.ResourceDemandingBehaviourForClassMethodFinding
import org.somox.gast2seff.visitors.AbstractFunctionClassificationStrategy
import tools.vitruv.applications.pcmjava.seffstatements.code2seff.Java2PcmMethodBodyChangePreprocessor
import tools.vitruv.applications.pcmjava.seffstatements.code2seff.ClassMethodBodyChangedTransformation
import tools.vitruv.applications.pcmjava.seffstatements.code2seff.BasicComponentFinding
import tools.vitruv.applications.pcmjava.commitintegration.domains.java.AdjustedJavaDomainProvider
import tools.vitruv.domains.pcm.PcmDomainProvider

class ExtendedJava2PcmMethodBodyChangePreprocessor extends Java2PcmMethodBodyChangePreprocessor {

	new() {
		super(new CommitIntegrationCodeToSeffFactory, new AdjustedJavaDomainProvider().domain, new PcmDomainProvider().domain)
	}

	override ClassMethodBodyChangedTransformation createTransformation(Method oldMethod, Method newMethod,
		BasicComponentFinding basicComponentFinding, AbstractFunctionClassificationStrategy classification,
		InterfaceOfExternalCallFindingFactory interfaceOfExternalCallFinderFactory,
		ResourceDemandingBehaviourForClassMethodFinding resourceDemandingBehaviourForClassMethodFinding) {
		return new ExtendedClassMethodBodyChangedTransformation(oldMethod, newMethod, basicComponentFinding,
			classification, interfaceOfExternalCallFinderFactory, resourceDemandingBehaviourForClassMethodFinding)
	}
}
