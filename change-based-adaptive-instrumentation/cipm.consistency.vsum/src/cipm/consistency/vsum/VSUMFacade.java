package cipm.consistency.vsum;

import cipm.consistency.base.models.instrumentation.InstrumentationModel.InstrumentationModel;
import cipm.consistency.base.models.instrumentation.InstrumentationModel.InstrumentationModelFactory;
import cipm.consistency.base.models.instrumentation.InstrumentationModel.InstrumentationModelPackage;
import cipm.consistency.base.shared.FileBackedModelUtil;
import cipm.consistency.base.shared.pcm.InMemoryPCM;
import cipm.consistency.commitintegration.settings.CommitIntegrationSettingsContainer;
import cipm.consistency.commitintegration.settings.SettingKeys;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import mir.reactions.imUpdate.ImUpdateChangePropagationSpecification;
import mir.reactions.luaPcm.LuaPcmChangePropagationSpecification;
import org.apache.log4j.Logger;
import org.eclipse.emf.ecore.resource.Resource;
import org.palladiosimulator.pcm.allocation.Allocation;
import org.palladiosimulator.pcm.allocation.AllocationFactory;
import org.palladiosimulator.pcm.repository.Repository;
import org.palladiosimulator.pcm.repository.RepositoryFactory;
import org.palladiosimulator.pcm.repository.RepositoryPackage;
import org.palladiosimulator.pcm.resourceenvironment.ResourceEnvironment;
import org.palladiosimulator.pcm.resourceenvironment.ResourceenvironmentFactory;
import org.palladiosimulator.pcm.system.SystemFactory;
import org.palladiosimulator.pcm.usagemodel.UsageModel;
import org.palladiosimulator.pcm.usagemodel.UsagemodelFactory;
import tools.vitruv.change.interaction.UserInteractionFactory;
import tools.vitruv.change.propagation.ChangePropagationSpecification;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.ViewTypeFactory;
import tools.vitruv.framework.views.changederivation.DefaultStateBasedChangeResolutionStrategy;
import tools.vitruv.framework.vsum.VirtualModelBuilder;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;

/**
 * Facade to the V-SUM.
 * 
 * @author Martin Armbruster
 */
@SuppressWarnings("restriction")
public class VSUMFacade {
    private static final Logger LOGGER = Logger.getLogger("cipm.VSUMFacade");
    private File file;
    private InternalVirtualModel vsum;
    private InMemoryPCM pcm;
    private InstrumentationModel imm;

    public VSUMFacade(Path rootDir) throws IOException {
        file = new File(rootDir);
        loadOrCreateVsum();
    }

    private List<ChangePropagationSpecification> getChangePropagationSpecs() {
        List<ChangePropagationSpecification> changePropagationSpecs = new ArrayList<>();

        // the lua->pcm spec is always added
        changePropagationSpecs.add(new LuaPcmChangePropagationSpecification());

        boolean useImUpdateChangeSpec = CommitIntegrationSettingsContainer.getSettingsContainer()
            .getPropertyAsBoolean(SettingKeys.PERFORM_FINE_GRAINED_SEFF_RECONSTRUCTION)
                || CommitIntegrationSettingsContainer.getSettingsContainer()
                    .getPropertyAsBoolean(SettingKeys.USE_PCM_IM_CPRS);

        if (useImUpdateChangeSpec)
            changePropagationSpecs.add(new ImUpdateChangePropagationSpecification());

        return changePropagationSpecs;
    }

    private VirtualModelBuilder getVsumBuilder() {
//		ExtendedPcmDomain pcmDomain = new ExtendedPcmDomainProvider().getDomain();
//		pcmDomain.enableTransitiveChangePropagation();

        return new VirtualModelBuilder()
//				.withDomain(new AdjustedJavaDomainProvider().getDomain())
//				.withDomain(pcmDomain)
//				.withDomain(new InstrumentationModelDomainProvider().getDomain())
            .withStorageFolder(file.getVsumPath())
            .withUserInteractor(UserInteractionFactory.instance.createDialogUserInteractor())
            .withChangePropagationSpecifications(getChangePropagationSpecs());
    }

    private CommittableView getView(InternalVirtualModel theVsum) {
        // TODO i think "myView" should be a magic string
        var viewType = ViewTypeFactory.createIdentityMappingViewType("myView");
        viewType.createSelector(theVsum);

        // TODO From the docs: you receive a selector that allows you to select the elements you
        // want to have in your view.
        var viewSelector = theVsum.createSelector(viewType);
        LOGGER.debug(String.format("View sees: %s", viewSelector.getSelectableElements()));

        // Selecting all elements here
        viewSelector.getSelectableElements()
            .forEach(ele -> viewSelector.setSelected(ele, true));

        var resolutionStrategy = new DefaultStateBasedChangeResolutionStrategy();
        var view = viewSelector.createView()
            .withChangeDerivingTrait(resolutionStrategy);
        return view;
    }

    private void createVsum(VirtualModelBuilder vsumBuilder) {
        // temporary vsum for the initialization
        LOGGER.info("Creating temporary VSUM");
        final var tempVsum = vsumBuilder.buildAndInitialize();

        // build PCM
        final var filePCM = file.getPCM();
        pcm = new InMemoryPCM();
        pcm.setRepository(RepositoryFactory.eINSTANCE.createRepository());
        pcm.setSystem(SystemFactory.eINSTANCE.createSystem());
        pcm.setResourceEnvironmentModel(ResourceenvironmentFactory.eINSTANCE.createResourceEnvironment());
        pcm.setAllocationModel(AllocationFactory.eINSTANCE.createAllocation());
        pcm.getAllocationModel()
            .setSystem_Allocation(pcm.getSystem());
        pcm.getAllocationModel()
            .setTargetResourceEnvironment_Allocation(pcm.getResourceEnvironmentModel());
        pcm.setUsageModel(UsagemodelFactory.eINSTANCE.createUsageModel());
        pcm.syncWithFilesystem(filePCM);

        // build IMM
        imm = InstrumentationModelFactory.eINSTANCE.createInstrumentationModel();
        FileBackedModelUtil.synchronize(imm, file.getImPath()
            .toFile(), InstrumentationModel.class);

        final var view = getView(tempVsum);
        List.of(imm.eResource(), pcm.getRepository()
            .eResource(),
                pcm.getResourceEnvironmentModel()
                    .eResource(),
                pcm.getSystem()
                    .eResource(),
                pcm.getAllocationModel()
                    .eResource(),
                pcm.getUsageModel()
                    .eResource())
            .forEach(resource -> {
                // add resources by registering its root object in the change deriving view
                LOGGER.debug(String.format("Registering resource: %s", resource.toString()));
                var iterator = resource.getAllContents();
                var rootEobject = iterator.next();
                try {
                    view.registerRoot(rootEobject, resource.getURI());
                } catch (IllegalStateException e) {
                    LOGGER.error(String.format("Unable to register root object of resource %s", resource.toString()),
                            e);
                }
            });

        // propagate the registerred resources into the vsum
        try {
            var propagated = view.commitChangesAndUpdate();
            LOGGER.debug(String.format("Propagating %d changes to temporary VSUM", propagated.size()));
        } catch (IllegalStateException e) {
            LOGGER.error("Unable to commit changes", e);
            throw e;
        }

        // add correspondences between the models
        var correspondenceModel = tempVsum.getCorrespondenceModel();
        correspondenceModel.addCorrespondenceBetween(pcm.getRepository(), RepositoryPackage.Literals.REPOSITORY, null);
        correspondenceModel.addCorrespondenceBetween(imm, InstrumentationModelPackage.Literals.INSTRUMENTATION_MODEL,
                null);

        // TODO we cannot access the correspondence model view resource
//        try {
//            correspondenceModel.eResource()
//                .save(null);
//        } catch (IOException e) {
//        }

        LOGGER.debug("Disposing temporary VSUM");
        tempVsum.dispose();
    }

    private void loadOrCreateVsum() throws IOException {
        var vsumBuilder = getVsumBuilder();
        boolean overwrite = true;
        boolean isVsumExistent = Files.exists(file.getVsumPath());

        if (overwrite && isVsumExistent) {
            LOGGER.info("Deleting existing VSUM");
            file.delete();
            isVsumExistent = false;
        }
        if (!isVsumExistent || overwrite) {
            createVsum(vsumBuilder);
        }

        pcm = new InMemoryPCM();

        LOGGER.info("Loading VSUM");
        vsum = vsumBuilder.buildAndInitialize();
        // TODO im trying to add the resources below via this view
        getView(vsum);

        // load the in-memory PCM into the VSUM
        LOGGER.info("Binding PCM models from VSUM");
        Resource resource = vsum.getModelInstance(file.getPcmRepositoryURI())
            .getResource();
        pcm.setRepository((Repository) resource.getContents()
            .get(0));
        resource = vsum.getModelInstance(file.getPcmAllocationURI())
            .getResource();
        pcm.setAllocationModel((Allocation) resource.getContents()
            .get(0));
        resource = vsum.getModelInstance(file.getPcmSystemURI())
            .getResource();
        pcm.setSystem((org.palladiosimulator.pcm.system.System) resource.getContents()
            .get(0));
        resource = vsum.getModelInstance(file.getPcmResourceEnvironmentURI())
            .getResource();
        pcm.setResourceEnvironmentModel((ResourceEnvironment) resource.getContents()
            .get(0));
        resource = vsum.getModelInstance(file.getPcmUsageModelURI())
            .getResource();
        pcm.setUsageModel((UsageModel) resource.getContents()
            .get(0));
        resource = vsum.getModelInstance(file.getImURI())
            .getResource();

        LOGGER.info("Binding IMM");
        imm = (InstrumentationModel) resource.getContents()
            .get(0);
    }

    public InternalVirtualModel getVSUM() {
        return vsum;
    }

    public FileLayout getFileLayout() {
        return file;
    }

    public InstrumentationModel getInstrumentationModel() {
        return imm;
    }

    public InMemoryPCM getPCMWrapper() {
        return pcm;
    }

    /*
     * private void setUp2(Collection<ChangePropagationSpecification> changePropagationSpecs) {
     * boolean isVSUMExistent = Files.exists(file.getVsumPath()); boolean useImUpdateChangeSpec =
     * CommitIntegrationSettingsContainer.getSettingsContainer()
     * .getPropertyAsBoolean(SettingKeys.PERFORM_FINE_GRAINED_SEFF_RECONSTRUCTION) ||
     * CommitIntegrationSettingsContainer.getSettingsContainer()
     * .getPropertyAsBoolean(SettingKeys.USE_PCM_IM_CPRS);
     * 
     * if (useImUpdateChangeSpec) changePropagationSpecs.add(new
     * ImUpdateChangePropagationSpecification());
     * 
     * vsum = getVsumBuilder(changePropagationSpecs).buildAndInitialize(); var resourceUris =
     * Arrays.asList(file.getPcmRepositoryURI(), file.getPcmAllocationURI(), file.getPcmSystemURI(),
     * file.getPcmResourceEnvironmentURI(), file.getPcmUsageModelURI(), file.getImURI());
     * 
     * var correspondenceUri = URI.createURI("foobar"); var metaDataPath = Path.of(".metaData");
     * 
     * try (var modelRepo = new DefaultChangeRecordingModelRepository(correspondenceUri,
     * metaDataPath)) {
     * 
     * // load all resources resourceUris.forEach(uri -> modelRepo.getModelResource(uri)); } catch
     * (Exception e) { // TODO Auto-generated catch block e.printStackTrace(); } }
     */
}
