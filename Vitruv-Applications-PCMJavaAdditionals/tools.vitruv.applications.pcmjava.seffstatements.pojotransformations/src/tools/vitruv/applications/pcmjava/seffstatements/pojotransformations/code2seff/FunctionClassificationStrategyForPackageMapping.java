package tools.vitruv.applications.pcmjava.seffstatements.pojotransformations.code2seff;

import java.util.Set;

import org.apache.log4j.Logger;
import org.emftext.language.java.members.ClassMethod;
import org.emftext.language.java.members.Method;
import org.palladiosimulator.pcm.repository.BasicComponent;
import org.palladiosimulator.pcm.repository.OperationSignature;
import org.somox.gast2seff.visitors.AbstractFunctionClassificationStrategy;
import org.somox.gast2seff.visitors.MethodCallFinder;

import tools.vitruv.applications.pcmjava.seffstatements.code2seff.BasicComponentFinding;
import tools.vitruv.applications.util.temporary.other.UriUtil;
import tools.vitruv.change.correspondence.model.CorrespondenceModel;
import tools.vitruv.change.correspondence.model.CorrespondenceModelUtil;

/**
 * FunctionClassificationStrategy for the simple package mapping Strategy.
 *
 * @author langhamm
 *
 */
public class FunctionClassificationStrategyForPackageMapping extends AbstractFunctionClassificationStrategy {

    private static final Logger LOGGER = Logger
            .getLogger(FunctionClassificationStrategyForPackageMapping.class.getSimpleName());

    private final BasicComponentFinding basicComponentFinding;
    private final CorrespondenceModel correspondenceModel;
    private final BasicComponent myBasicComponent;

    public FunctionClassificationStrategyForPackageMapping(final BasicComponentFinding basicComponentFinding,
            final CorrespondenceModel ci, final BasicComponent myBasicComponent) {
        super(new MethodCallFinder());
        this.basicComponentFinding = basicComponentFinding;
        this.correspondenceModel = ci;
        this.myBasicComponent = myBasicComponent;
    }

    /**
     * A call is an external call if the call's destination is an interface method that corresponds
     * to an OperationSignature, or if the calls destination is outside of the own component.
     */
    @Override
    protected boolean isExternalCall(final Method method) {
        if (!UriUtil.normalizeURI(method)) {
            LOGGER.info("Could not normalize URI for method " + method
                    + ". Method call is not considered as as external call");
            return false;
        }
        final Set<OperationSignature> correspondingSignatures = CorrespondenceModelUtil
                .getCorrespondingEObjects(this.correspondenceModel, method, OperationSignature.class);
        if (null != correspondingSignatures && !correspondingSignatures.isEmpty()) {
            return true;
        }
        if (method instanceof ClassMethod) {
            final BasicComponent basicComponent = this.basicComponentFinding.findBasicComponentForMethod(method,
                    this.correspondenceModel);
            if (null == basicComponent || basicComponent.getId().equals(this.myBasicComponent.getId())) {
                return false;
            }
            return true;
        }
        return false;
    }

    /**
     * The call is a library call if the method that is called does not belong to any
     * BasicComponent.
     */
    @Override
    protected boolean isLibraryCall(final Method method) {
        final BasicComponent basicComponentOfMethod = this.basicComponentFinding.findBasicComponentForMethod(method,
                this.correspondenceModel);
        if (null == basicComponentOfMethod) {
            return true;
        }
        if (basicComponentOfMethod.getId().equals(this.myBasicComponent.getId())) {
            return false;
        }
        LOGGER.warn("The destination of a call to the method " + method
                + " is another component than the source component. This should not happen in isLibraryCall.");
        return true;
    }

}
