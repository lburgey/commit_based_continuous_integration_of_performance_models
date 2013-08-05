package org.splevo.ui.wizards.vpmanalysis;

import java.util.LinkedList;
import java.util.List;

import org.apache.log4j.Logger;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ListViewer;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.FormAttachment;
import org.eclipse.swt.layout.FormData;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.wb.swt.ResourceManager;
import org.splevo.vpm.analyzer.VPMAnalyzer;

/**
 * Wizard page to select and configure the vpm analyzers to be executed.
 * 
 * @author Benjamin Klatt
 */
public class VPMAnalyzerConfigurationPage extends WizardPage {

    /** The logger for this class. */
    private Logger logger = Logger.getLogger(VPMAnalyzerConfigurationPage.class);

    /** The configured analyzer instances. */
    private List<VPMAnalyzer> analyzers = new LinkedList<VPMAnalyzer>();

    /** The editing support to manipulate a configuration table cell. */
    private VPMAnalyzerConfigurationEditingSupport configEditingSupport = null;

	/** The {@link TableViewer} for the configurations table. */
	private TableViewer tv;
	private VPMAnalyzer selectedAnalyzer;

	private ListViewer listViewerAnalysis;

	private Composite configComp;

	private ScrolledComposite scrolledComposite;

    /**
     * Create the wizard page to let the user select the analyzes to be performed.
     */
    public VPMAnalyzerConfigurationPage() {
        super("wizardPage");
        setTitle("Analyzer Configuration");
        setDescription("Select and configure the variation point model analyzer to be executed.");
    }

    /**
     * Create contents of the wizard.
     * 
     * @param parent
     *            The parent ui element this control should be placed in.
     */
    public void createControl(Composite parent) {
    	Composite container = new Composite(parent, SWT.NULL);
    	setControl(container);
    	container.setLayout(new FormLayout());
    	
    	Group grpAnalyzers = new Group(container, SWT.SHADOW_IN);
		grpAnalyzers.setText("Analyzers");
		grpAnalyzers.setLayout(new GridLayout(2, true));
		FormData grpAnalyzersFD = new FormData();
		grpAnalyzersFD.top = new FormAttachment(0);
		grpAnalyzersFD.left = new FormAttachment(0, 5);
		grpAnalyzersFD.right = new FormAttachment(30);
		grpAnalyzersFD.bottom = new FormAttachment(100);
		grpAnalyzers.setLayoutData(grpAnalyzersFD);
		
		listViewerAnalysis = new ListViewer(grpAnalyzers, SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
        listViewerAnalysis.setContentProvider(ArrayContentProvider.getInstance());
        listViewerAnalysis.setInput(analyzers);
        listViewerAnalysis.setLabelProvider(new VPMAnalyzerLabelProvider());
        listViewerAnalysis.getList().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1));
        listViewerAnalysis.addSelectionChangedListener(new ISelectionChangedListener() {

            @Override
            public void selectionChanged(SelectionChangedEvent event) {
                selectedAnalyzer = getSelectedAnalyzer();
                update();
            }
        });        

        GridData gridData = new GridData();
        gridData.horizontalAlignment = SWT.CENTER;
        Button addBtn = new Button(grpAnalyzers, SWT.PUSH);
    	addBtn.setImage(ResourceManager.getPluginImage("org.splevo.ui", "icons/plus.png"));
    	addBtn.addMouseListener(new VPMAnalyzerSelectionDialogListener(this));
    	addBtn.setLayoutData(gridData);
    	Button rmvBtn = new Button(grpAnalyzers, SWT.PUSH);
		rmvBtn.setImage(ResourceManager.getPluginImage("org.splevo.ui", "icons/cross.png"));
		rmvBtn.addMouseListener(new MouseListener() {
			
			@Override
			public void mouseUp(MouseEvent arg0) {
				removeAnalyzer(selectedAnalyzer);
				selectedAnalyzer = null;
				update();
			}

			@Override
			public void mouseDoubleClick(MouseEvent arg0) {
				// Ignore: Not used
			}

			@Override
			public void mouseDown(MouseEvent arg0) {
				// Ignore: Not used
			}
		});
		rmvBtn.setLayoutData(gridData);
        
		final Group grpConfigurations = new Group(container, SWT.SHADOW_IN | SWT.V_SCROLL | SWT.H_SCROLL);
		grpConfigurations.setText("Configurations");
		grpConfigurations.setLayout(new FillLayout());
		FormData fd_grpConfigurations = new FormData();
		fd_grpConfigurations.top = new FormAttachment(0);
		fd_grpConfigurations.left = new FormAttachment(grpAnalyzers, 5);
		fd_grpConfigurations.right = new FormAttachment(100, -5);
		fd_grpConfigurations.bottom = new FormAttachment(100);
		grpConfigurations.setLayoutData(fd_grpConfigurations);
			
		scrolledComposite = new ScrolledComposite(grpConfigurations, SWT.SHADOW_IN | SWT.V_SCROLL);
		configComp = new Composite(scrolledComposite, SWT.NONE);
		GridLayout gridLayout = new GridLayout(2, false);
		gridLayout.verticalSpacing = 20;
		configComp.setLayout(gridLayout);
		updateConfig();
		
		scrolledComposite.setExpandHorizontal(true);
		scrolledComposite.setExpandVertical(true);
		scrolledComposite.addControlListener(new ControlAdapter() {
			public void controlResized(ControlEvent e) {
				Rectangle r = ((ScrolledComposite)e.widget).getClientArea();
				((ScrolledComposite)e.widget).setMinSize(configComp.computeSize(r.width-5, SWT.DEFAULT));
			}
		});
		scrolledComposite.setContent(configComp);
		
    }

    @Override
    public boolean isPageComplete() {
        return (analyzers.size() > 0);
    }

    @Override
    public boolean canFlipToNextPage() {
        return isPageComplete();
    }

    /**
     * Add an analyzer to the list of configured analyzers.
     * 
     * @param analyzer
     *            The analyzer instance to be added.
     */
    public void addAnalyzer(VPMAnalyzer analyzer) {
        this.analyzers.add(analyzer);
        update();
    }    

	/**
     * Remove an analyzer from the list of configured analyzers.
     * 
     * @param analyzer
     *            The analyzer to be removed.
     */
    public void removeAnalyzer(VPMAnalyzer analyzer) {
        this.analyzers.remove(analyzer);
        update();
    }

    /**
     * Get the set of configured analyzer instances.
     * 
     * @return The list of analyzers.
     */
    public List<VPMAnalyzer> getAnalyzers() {
        return analyzers;
    }
    
    /**
     * Get the analyzer currently selected in the analyzer list viewer.
     * 
     * @return Returns the first selected analyzer or null if none is selected.
     */
    private VPMAnalyzer getSelectedAnalyzer() {

        VPMAnalyzer analyzer = null;

        if (listViewerAnalysis.getSelection() instanceof StructuredSelection) {
            Object selection = ((StructuredSelection) listViewerAnalysis.getSelection()).getFirstElement();
            if (selection != null) {
                analyzer = (VPMAnalyzer) selection;
            }
        } else {
            logger.error("Invalid selection type in AnalyzerSelectionPage dialog");
        }
        return analyzer;

    }

	private void update() {
		listViewerAnalysis.refresh();
		updateConfig();
    	getWizard().getContainer().updateButtons();
    }

	private void updateConfig() {		
		disposeAllChildren(configComp);

		if (selectedAnalyzer == null) {
			Label label = new Label(configComp, SWT.NONE);
			label.setText("Please select a VPM Analyzer first.");
			configComp.pack();
			return;
		}
		
		new ConfigurationCompositeFactory(selectedAnalyzer).createConfigComps(configComp);
		configComp.setSize(configComp.computeSize(SWT.DEFAULT, SWT.DEFAULT));
		Rectangle r = scrolledComposite.getClientArea();
		scrolledComposite.setMinSize(configComp.computeSize(r.width-5, SWT.DEFAULT));
	}

	private void disposeAllChildren(Composite composite) {
		for (Control c : configComp.getChildren()) {
			c.dispose();
		}
	}
}
