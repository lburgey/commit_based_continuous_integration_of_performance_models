package tools.vitruv.applications.pcmjava.seffstatements.pojotransformations.code2seff;

import org.palladiosimulator.pcm.repository.BasicComponent;
import org.somox.gast2seff.visitors.AbstractFunctionClassificationStrategy;
import org.somox.gast2seff.visitors.InterfaceOfExternalCallFinding;
import org.somox.gast2seff.visitors.InterfaceOfExternalCallFindingFactory;
import org.somox.gast2seff.visitors.ResourceDemandingBehaviourForClassMethodFinding;
import org.somox.sourcecodedecorator.SourceCodeDecoratorRepository;

import tools.vitruv.applications.pcmjava.seffstatements.code2seff.BasicComponentFinding;
import tools.vitruv.applications.pcmjava.seffstatements.code2seff.Code2SeffFactory;
import tools.vitruv.change.correspondence.model.CorrespondenceModel;

public class PojoJava2PcmCodeToSeffFactory implements Code2SeffFactory {

	@Override
	public BasicComponentFinding createBasicComponentFinding() {
		return new BasicComponentForPackageMappingFinder();
	}

	@Override
	public InterfaceOfExternalCallFindingFactory createInterfaceOfExternalCallFindingFactory(
			final CorrespondenceModel correspondenceModel, final BasicComponent basicComponent) {
		return new InterfaceOfExternalCallFindingFactory() {
			public InterfaceOfExternalCallFinding createInterfaceOfExternalCallFinding(
					SourceCodeDecoratorRepository sourceCodeDecoratorRepository,
					BasicComponent basicComponent) {
				return new InterfaceOfExternalCallFinderForPackageMapping(correspondenceModel, basicComponent);
			}
		};
	}

	@Override
	public ResourceDemandingBehaviourForClassMethodFinding createResourceDemandingBehaviourForClassMethodFinding(
			final CorrespondenceModel correspondenceModel) {
		return new ResourceDemandingBehaviourForClassMethodFinderForPackageMapping(correspondenceModel);
	}

	@Override
	public AbstractFunctionClassificationStrategy createAbstractFunctionClassificationStrategy(
			final BasicComponentFinding basicComponentFinding, final CorrespondenceModel correspondenceModel,
			final BasicComponent basicComponent) {
		return new FunctionClassificationStrategyForPackageMapping(basicComponentFinding, correspondenceModel,
				basicComponent);
	}

}
