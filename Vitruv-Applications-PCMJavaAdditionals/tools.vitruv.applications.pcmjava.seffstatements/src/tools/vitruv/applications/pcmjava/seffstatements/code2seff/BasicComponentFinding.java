package tools.vitruv.applications.pcmjava.seffstatements.code2seff;

import org.emftext.language.java.members.Method;
import org.palladiosimulator.pcm.repository.BasicComponent;

import tools.vitruv.change.correspondence.Correspondence;
import tools.vitruv.change.correspondence.view.CorrespondenceModelView;

public interface BasicComponentFinding <C extends Correspondence> {

    BasicComponent findBasicComponentForMethod(Method newMethod, CorrespondenceModelView<C> cmv);

}
