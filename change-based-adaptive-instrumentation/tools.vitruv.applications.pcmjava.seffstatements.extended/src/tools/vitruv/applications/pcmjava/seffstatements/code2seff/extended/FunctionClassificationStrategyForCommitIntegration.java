package tools.vitruv.applications.pcmjava.seffstatements.code2seff.extended;

import org.emftext.language.java.LogicalJavaURIGenerator;
import org.emftext.language.java.members.Method;
import org.palladiosimulator.pcm.repository.BasicComponent;

import cipm.consistency.commitintegration.settings.CommitIntegrationSettingsContainer;
import cipm.consistency.commitintegration.settings.SettingKeys;
import tools.vitruv.applications.pcmjava.seffstatements.code2seff.BasicComponentFinding;
import tools.vitruv.applications.pcmjava.seffstatements.pojotransformations.code2seff.FunctionClassificationStrategyForPackageMapping;
import tools.vitruv.change.correspondence.model.CorrespondenceModel;

/**
 * A function classification strategy for the commit-based integration.
 * 
 * @author Martin Armbruster
 */
public class FunctionClassificationStrategyForCommitIntegration
		extends FunctionClassificationStrategyForPackageMapping {

	public FunctionClassificationStrategyForCommitIntegration(BasicComponentFinding basicComponentFinding,
			CorrespondenceModel ci, BasicComponent myBasicComponent) {
		super(basicComponentFinding, ci, myBasicComponent);
	}

	/**
	 * Classifies external calls according to the superclass. In addition, methods
	 * in specific packages which contain REST client APIs are considered as
	 * external calls.
	 */
	@Override
	protected boolean isExternalCall(Method method) {
		boolean superResult = super.isExternalCall(method);
		if (!superResult) {
			String[] packages = CommitIntegrationSettingsContainer.getSettingsContainer()
					.getProperty(SettingKeys.REST_CLIENT_API_PACKAGES).split(";");
			String namespaces = method.getContainingCompilationUnit().getNamespacesAsString();
			if (namespaces.endsWith(LogicalJavaURIGenerator.CLASSIFIER_SEPARATOR)
					|| namespaces.endsWith(LogicalJavaURIGenerator.PACKAGE_SEPARATOR)) {
				namespaces = namespaces.substring(0, namespaces.length() - 1);
			}
			for (String p : packages) {
				if (p.equals(namespaces)) {
					return true;
				}
			}
			return false;
		}
		return superResult;
	}
}
