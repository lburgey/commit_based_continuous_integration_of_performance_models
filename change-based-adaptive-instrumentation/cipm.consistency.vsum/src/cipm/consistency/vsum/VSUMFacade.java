package cipm.consistency.vsum;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;

import org.eclipse.emf.common.util.URI;
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

import cipm.consistency.base.models.instrumentation.InstrumentationModel.InstrumentationModel;
import cipm.consistency.base.models.instrumentation.InstrumentationModel.InstrumentationModelFactory;
import cipm.consistency.base.models.instrumentation.InstrumentationModel.InstrumentationModelPackage;
import cipm.consistency.base.shared.FileBackedModelUtil;
import cipm.consistency.base.shared.pcm.InMemoryPCM;
import cipm.consistency.commitintegration.settings.CommitIntegrationSettingsContainer;
import cipm.consistency.commitintegration.settings.SettingKeys;
//import cipm.consistency.domains.im.InstrumentationModelDomainProvider;
//import cipm.consistency.domains.java.AdjustedJavaDomainProvider;
//import cipm.consistency.domains.pcm.ExtendedPcmDomain;
//import cipm.consistency.domains.pcm.ExtendedPcmDomainProvider;
import mir.reactions.imUpdate.ImUpdateChangePropagationSpecification;
import tools.vitruv.change.interaction.UserInteractionFactory;
import tools.vitruv.change.propagation.ChangePropagationSpecification;
import tools.vitruv.change.propagation.ChangePropagationSpecificationProvider;
import tools.vitruv.change.propagation.impl.DefaultChangeRecordingModelRepository;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.ViewTypeFactory;
//import tools.vitruv.dsls.reactions.runtime.helper.ReactionsCorrespondenceHelper;
import tools.vitruv.framework.vsum.VirtualModelBuilder;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;

/**
 * Facade to the V-SUM.
 * 
 * @author Martin Armbruster
 */
@SuppressWarnings("restriction")
public class VSUMFacade {
	private File file;
	private InternalVirtualModel vsum;
	private InMemoryPCM pcm;
	private InstrumentationModel imm;

	public VSUMFacade(Path rootDir, ChangePropagationSpecification javaPCMSpecificaton) {
		file = new File(rootDir);
		loadOrCreateVsum(Arrays.asList(javaPCMSpecificaton));
	}

	private void setUp2(Collection<ChangePropagationSpecification> changePropagationSpecs) {
		boolean isVSUMExistent = Files.exists(file.getVsumPath());
		boolean useImUpdateChangeSpec = CommitIntegrationSettingsContainer.getSettingsContainer()
				.getPropertyAsBoolean(SettingKeys.PERFORM_FINE_GRAINED_SEFF_RECONSTRUCTION)
				|| CommitIntegrationSettingsContainer.getSettingsContainer()
						.getPropertyAsBoolean(SettingKeys.USE_PCM_IM_CPRS);

		if (useImUpdateChangeSpec)
			changePropagationSpecs.add(new ImUpdateChangePropagationSpecification());

		vsum = getVsumBuilder(changePropagationSpecs).buildAndInitialize();
		var resourceUris = Arrays.asList(file.getPcmRepositoryURI(), file.getPcmAllocationURI(), file.getPcmSystemURI(),
				file.getPcmResourceEnvironmentURI(), file.getPcmUsageModelURI(), file.getImURI());

		var correspondenceUri = URI.createURI("foobar");
		var metaDataPath = Path.of(".metaData");

		try (var modelRepo = new DefaultChangeRecordingModelRepository(correspondenceUri, metaDataPath)) {

			// load all resources
			resourceUris.forEach(uri -> modelRepo.getModelResource(uri));
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	
	private VirtualModelBuilder getVsumBuilder(Collection<ChangePropagationSpecification> changeSpecs) {
		// TODO add the three domains to the specs
		
		
		

//		ExtendedPcmDomain pcmDomain = new ExtendedPcmDomainProvider().getDomain();
//		pcmDomain.enableTransitiveChangePropagation();

		return new VirtualModelBuilder()
//				.withDomain(new AdjustedJavaDomainProvider().getDomain())
//				.withDomain(pcmDomain)
//				.withDomain(new InstrumentationModelDomainProvider().getDomain())
				.withStorageFolder(file.getVsumPath())
				.withUserInteractor(UserInteractionFactory.instance.createDialogUserInteractor())
				.withChangePropagationSpecifications(changeSpecs);
	}

	private CommittableView getView() {
		// TODO i think "myView" should be a magic string
		var viewType = ViewTypeFactory.createIdentityMappingViewType("myView");
		viewType.createSelector(vsum);
		var viewSelector = vsum.createSelector(viewType);
		var view = viewSelector.createView().withChangeDerivingTrait();
		return view;
	}

	private void createVsum(VirtualModelBuilder vsumBuilder) {
		InternalVirtualModel interimVsum = vsumBuilder.buildAndInitialize();
		var filePCM = file.getPCM();
		var pcm = new InMemoryPCM();

		pcm.setRepository(RepositoryFactory.eINSTANCE.createRepository());
		pcm.setSystem(SystemFactory.eINSTANCE.createSystem());
		pcm.setResourceEnvironmentModel(ResourceenvironmentFactory.eINSTANCE.createResourceEnvironment());
		pcm.setAllocationModel(AllocationFactory.eINSTANCE.createAllocation());
		pcm.getAllocationModel().setSystem_Allocation(pcm.getSystem());
		pcm.getAllocationModel().setTargetResourceEnvironment_Allocation(pcm.getResourceEnvironmentModel());
		pcm.setUsageModel(UsagemodelFactory.eINSTANCE.createUsageModel());
		pcm.syncWithFilesystem(filePCM);
		imm = InstrumentationModelFactory.eINSTANCE.createInstrumentationModel();
		FileBackedModelUtil.synchronize(imm, file.getImPath().toFile(), InstrumentationModel.class);

		// TODO find out how the view based change propagation works
//			vsum.propagateChangedState(imm.eResource());
//			vsum.propagateChangedState(pcm.getRepository().eResource());
//			vsum.propagateChangedState(pcm.getResourceEnvironmentModel().eResource());
//			vsum.propagateChangedState(pcm.getSystem().eResource());
//			vsum.propagateChangedState(pcm.getAllocationModel().eResource());
//			vsum.propagateChangedState(pcm.getUsageModel().eResource());

		final var resources = Arrays.asList(imm.eResource(), pcm.getRepository().eResource(),
				pcm.getResourceEnvironmentModel().eResource(), pcm.getSystem().eResource(),
				pcm.getAllocationModel().eResource(), pcm.getUsageModel().eResource());

		final var view = getView();

		resources.forEach(r -> {
			// TODO we need to build the changes using the ChangeDerivingView

			// we don't have to call vsum.propagateChange(...)
			// we instead use this:
			view.commitChangesAndUpdate();
		});

		interimVsum.getCorrespondenceModel().addCorrespondenceBetween(pcm.getRepository(),
				RepositoryPackage.Literals.REPOSITORY, null);
		var correspondence = interimVsum.getCorrespondenceModel().addCorrespondenceBetween(imm,
				InstrumentationModelPackage.Literals.INSTRUMENTATION_MODEL, null);
		try {
			correspondence.eResource().save(null);
			interimVsum.dispose();
		} catch (IOException e) {
		}

	}

	private void loadOrCreateVsum(Collection<ChangePropagationSpecification> changePropagationSpecs) {
		boolean useImUpdateChangeSpec = CommitIntegrationSettingsContainer.getSettingsContainer()
				.getPropertyAsBoolean(SettingKeys.PERFORM_FINE_GRAINED_SEFF_RECONSTRUCTION)
				|| CommitIntegrationSettingsContainer.getSettingsContainer()
						.getPropertyAsBoolean(SettingKeys.USE_PCM_IM_CPRS);

		if (useImUpdateChangeSpec)
			changePropagationSpecs.add(new ImUpdateChangePropagationSpecification());
		
		var vsumBuilder = getVsumBuilder(changePropagationSpecs);

		boolean isVsumExistent = Files.exists(file.getVsumPath());
		if (!isVsumExistent)
			createVsum(vsumBuilder);

		vsum = vsumBuilder.buildAndInitialize();

		pcm = new InMemoryPCM();

		// load InMemoryPCM from vsum
		Resource resource = vsum.getModelInstance(file.getPcmRepositoryURI()).getResource();
		pcm.setRepository((Repository) resource.getContents().get(0));
		resource = vsum.getModelInstance(file.getPcmAllocationURI()).getResource();
		pcm.setAllocationModel((Allocation) resource.getContents().get(0));
		resource = vsum.getModelInstance(file.getPcmSystemURI()).getResource();
		pcm.setSystem((org.palladiosimulator.pcm.system.System) resource.getContents().get(0));
		resource = vsum.getModelInstance(file.getPcmResourceEnvironmentURI()).getResource();
		pcm.setResourceEnvironmentModel((ResourceEnvironment) resource.getContents().get(0));
		resource = vsum.getModelInstance(file.getPcmUsageModelURI()).getResource();
		pcm.setUsageModel((UsageModel) resource.getContents().get(0));
		resource = vsum.getModelInstance(file.getImURI()).getResource();
		imm = (InstrumentationModel) resource.getContents().get(0);
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
}
