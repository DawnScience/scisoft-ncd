/*
 * Copyright 2013 Diamond Light Source Ltd.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.ac.diamond.scisoft.ncd.rcp.edna.views;

import java.io.File;
import java.text.NumberFormat;
import java.util.Iterator;
import java.util.Map;

import org.dawnsci.algorithm.ui.views.runner.AbstractAlgorithmProcessPage;
import org.dawnsci.algorithm.ui.views.runner.IAlgorithmProcessContext;
import org.dawnsci.common.widgets.content.FileContentProposalProvider;
import org.dawnsci.common.widgets.decorator.BoundsDecorator;
import org.dawnsci.common.widgets.decorator.FloatDecorator;
import org.dawnsci.common.widgets.decorator.IValueChangeListener;
import org.dawnsci.common.widgets.decorator.IntegerDecorator;
import org.dawnsci.common.widgets.decorator.ValueChangeEvent;
import org.dawnsci.plotting.api.IPlottingSystem;
import org.dawnsci.plotting.api.PlotType;
import org.dawnsci.plotting.api.PlottingFactory;
import org.dawnsci.plotting.api.region.IROIListener;
import org.dawnsci.plotting.api.region.IRegion;
import org.dawnsci.plotting.api.region.IRegion.RegionType;
import org.dawnsci.plotting.api.region.ROIEvent;
import org.dawnsci.plotting.api.trace.ILineTrace;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.ToolBarManager;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.fieldassist.ContentProposalAdapter;
import org.eclipse.jface.fieldassist.IContentProposal;
import org.eclipse.jface.fieldassist.IContentProposalListener;
import org.eclipse.jface.fieldassist.TextContentAdapter;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DropTarget;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.DropTargetListener;
import org.eclipse.swt.dnd.FileTransfer;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.FocusListener;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.ISourceProvider;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.forms.events.ExpansionAdapter;
import org.eclipse.ui.forms.events.ExpansionEvent;
import org.eclipse.ui.forms.widgets.ExpandableComposite;
import org.eclipse.ui.services.ISourceProviderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.analysis.dataset.DatasetUtils;
import uk.ac.diamond.scisoft.analysis.dataset.IDataset;
import uk.ac.diamond.scisoft.analysis.dataset.ILazyDataset;
import uk.ac.diamond.scisoft.analysis.dataset.Slice;
import uk.ac.diamond.scisoft.analysis.hdf5.HDF5Dataset;
import uk.ac.diamond.scisoft.analysis.hdf5.HDF5File;
import uk.ac.diamond.scisoft.analysis.hdf5.HDF5Group;
import uk.ac.diamond.scisoft.analysis.hdf5.HDF5Node;
import uk.ac.diamond.scisoft.analysis.hdf5.HDF5NodeLink;
import uk.ac.diamond.scisoft.analysis.io.HDF5Loader;
import uk.ac.diamond.scisoft.analysis.io.IDataHolder;
import uk.ac.diamond.scisoft.analysis.io.LoaderFactory;
import uk.ac.diamond.scisoft.analysis.roi.RectangularROI;
import uk.ac.diamond.scisoft.ncd.preferences.NcdPreferences;
import uk.ac.diamond.scisoft.ncd.rcp.edna.ModelBuildingParameters;
import uk.ac.diamond.scisoft.ncd.rcp.edna.actions.RunNcdModelBuilderPipeline;
import uk.ac.diamond.scisoft.ncd.ws.rcp.Activator;

public class NcdModelBuilderParametersView extends AbstractAlgorithmProcessPage {
	private static final String Q_REGION_NAME = "q Region";
	public static final String ID = "uk.ac.diamond.scisoft.ncd.rcp.edna.views.NcdModelBuilderParametersView";
	protected static final Logger logger = LoggerFactory.getLogger(NcdModelBuilderParametersView.class);

	public static String[] DATA_TYPES = new String[] { "dat", "nxs" };

	public static int ROUND_DOUBLES_DIGITS = 4;

	private IMemento memento = null;

	protected Text dataFile;
	protected String dataFilename = "";
	protected Text workingDirectory;
	protected Combo pathToQCombo;
	protected Combo pathToDataCombo;

	protected Text numberOfFrames;
	protected Text qMin;
	private Combo qMinUnits;
	protected Text qMax;
	private Label qMaxUnits;
	private BoundsDecorator qMinBounds, qMaxBounds;
	private NumberFormat doubleNumberFormat = NumberFormat.getNumberInstance();
	protected Text startPoint;
	protected Text endPoint;
	private BoundsDecorator startPointBounds, endPointBounds;

	protected Text numberOfThreads;

	private Combo builderOptions;

	protected Text minDistanceSearch;
	private BoundsDecorator minDistanceBounds, maxDistanceBounds;
	private Label minDistanceUnits;
	protected Text maxDistanceSearch;
	private Label maxDistanceUnits;

	protected Text numberOfSearch;
	protected Text tolerance;

	protected Combo symmetry;
	private Combo dammifMode;

	private Button btnResetParams;

	private Button btnRunNcdModelBuilderJob;

	private ModelBuildingParameters modelBuildingParameters;

	private RunNcdModelBuilderPipeline runNcdModelBuilderPipeline;
	
	private boolean fileSelected = false;
	private boolean pathEmpty = true;
	private boolean forgetLastSelection = false;
	private boolean isGuiInResetState;

	private Button browseDataFile;

	private IDataset currentQDataset;
	protected String currentPathToQ;
	protected String currentPathToData;

	// plotting system
	private IPlottingSystem qIntensityPlot;
	private IROIListener qIntensityRegionListener;
	protected ILineTrace lineTrace;
	protected boolean regionDragging;
	private Combo plotOptions;
	private Button btnResetDataRange;
	protected boolean xAxisIsLog;
	
	private ScrolledComposite scrolledComposite;
	private ExpansionAdapter expansionAdapter;
	private Composite parent;
	private Composite dataParameters;
	private ScrolledComposite plotScrollComposite;
	private Composite plotComposite;
	private final int scrollWidth = 600;
	private int scrollHeight = 1000;

	public NcdModelBuilderParametersView() {
		// Specify the expansion Adapter
		expansionAdapter = new ExpansionAdapter() {
			@Override
			public void expansionStateChanged(ExpansionEvent e) {
				scrolledComposite.setMinSize(dataParameters.computeSize(scrollWidth, SWT.DEFAULT));
				plotScrollComposite.setMinWidth(plotComposite.getBounds().width);
				dataParameters.layout();
				parent.redraw();
			}
		};

	}

	@Override
	public Composite createPartControl(final Composite parent) {
		this.parent = parent;
		parent.setLayout(new GridLayout(1, false));
		parent.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		try {
			qIntensityPlot = PlottingFactory.createPlottingSystem();


		} catch (Exception ne) {
			logger.error("Cannot locate any plotting systems!", ne);
		}

		// Data parameters
		Group dataParametersGroup = new Group(parent, SWT.NONE);
		dataParametersGroup.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		dataParametersGroup.setLayout(new GridLayout());
		dataParametersGroup.setText("Data parameters");

		scrolledComposite = new ScrolledComposite(dataParametersGroup, SWT.H_SCROLL | SWT.V_SCROLL);
		scrolledComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		scrolledComposite.setLayout(new GridLayout());

		dataParameters = new Composite(scrolledComposite, SWT.NONE);
		dataParameters.setLayout(new GridLayout());
		dataParameters.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

		final Group dataFileGroup = new Group(dataParameters, SWT.NONE);
		dataFileGroup.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		dataFileGroup.setLayout(new GridLayout(3, false));
		new Label(dataFileGroup, SWT.NONE).setText("Data file");
		dataFile = new Text(dataFileGroup, SWT.BORDER);
		dataFile.setLayoutData(new GridData(GridData.FILL, SWT.CENTER, true, false));
		dataFile.setText(dataFilename);
		dataFile.setToolTipText("Location of input file");
		dataFile.addListener(SWT.KeyUp, new Listener() {
			Color red = new Color(dataFileGroup.getDisplay(), 255, 0, 0);
			Color white = new Color(dataFileGroup.getDisplay(), 255, 255, 255);
			
			@Override
			public void handleEvent(Event event) {
				if (fileNameIsNotEmptyAndFileExists(dataFile.getText())) {
					dataFile.setBackground(white);
					fileSelected = true;
				} else {
					dataFile.setBackground(red);
					fileSelected = false;
				}
				String filename = dataFile.getText();
				boolean dataFileIsNxsFile = isNxsFile(filename);
				if (dataFileIsNxsFile) {
					findQAndDataPaths();
				}
				enableNexusPathCombos(dataFileIsNxsFile);
				captureGUIInformation();
				checkWhetherPathsAreEmpty();
				refreshRunButton(true);
				checkFilenameAndColorDataFileBox(dataFileGroup.getDisplay());
				updateGuiParameters();
			}
		});

		int style = DND.DROP_COPY | DND.DROP_DEFAULT;
		DropTarget target = new org.eclipse.swt.dnd.DropTarget(dataParameters, style);
		final TextTransfer textTransfer = TextTransfer.getInstance();
		final FileTransfer fileTransfer = FileTransfer.getInstance();
		Transfer[] types = new Transfer[] { fileTransfer, textTransfer };
		target.setTransfer(types);

		target.addDropListener(new DropTargetListener() {

			public void dragEnter(DropTargetEvent event) {
				if (event.detail == DND.DROP_DEFAULT) {
					if ((event.operations & DND.DROP_COPY) != 0) {
						event.detail = DND.DROP_COPY;
					} else {
						event.detail = DND.DROP_NONE;
					}
				}
				// will accept text but prefer to have files dropped
				for (int i = 0; i < event.dataTypes.length; i++) {
					if (fileTransfer.isSupportedType(event.dataTypes[i])) {
						event.currentDataType = event.dataTypes[i];
						// files should only be copied
						if (event.detail != DND.DROP_COPY) {
							event.detail = DND.DROP_NONE;
						}
						break;
					}
				}
			}

			public void dragOver(DropTargetEvent event) {
				event.feedback = DND.FEEDBACK_SELECT | DND.FEEDBACK_SCROLL;
				if (textTransfer.isSupportedType(event.currentDataType)) {
					// NOTE: on unsupported platforms this will return null
					Object o = textTransfer.nativeToJava(event.currentDataType);
					String t = (String) o;
					if (t != null)
						System.out.println(t);
				}
			}

			public void dragOperationChanged(DropTargetEvent event) {
				if (event.detail == DND.DROP_DEFAULT) {
					if ((event.operations & DND.DROP_COPY) != 0) {
						event.detail = DND.DROP_COPY;
					} else {
						event.detail = DND.DROP_NONE;
					}
				}
				// allow text to be moved but files should only be copied
				if (fileTransfer.isSupportedType(event.currentDataType)) {
					if (event.detail != DND.DROP_COPY) {
						event.detail = DND.DROP_NONE;
					}
				}
			}

			public void dragLeave(DropTargetEvent event) {
			}

			public void dropAccept(DropTargetEvent event) {
			}

			public void drop(DropTargetEvent event) {
				if (textTransfer.isSupportedType(event.currentDataType)) {
					String[] files = (String[]) event.data;
					for (int i = 0; i < files.length; i++) {
						if (files[i].toLowerCase().endsWith(DATA_TYPES[0]) || files[i].toLowerCase().endsWith(DATA_TYPES[1])) 
							setFilenameString(files[i]);
					}
				}

				if (fileTransfer.isSupportedType(event.currentDataType)) {
					String[] files = (String[]) event.data;
					for (int i = 0; i < files.length; i++) {
						if (files[i].toLowerCase().endsWith(DATA_TYPES[0]) || files[i].toLowerCase().endsWith(DATA_TYPES[1])) 
							setFilenameString(files[i]);
					}
				}
			}
		});
		
		FileContentProposalProvider prov = new FileContentProposalProvider();
		ContentProposalAdapter ad2 = new ContentProposalAdapter(dataFile,
				new TextContentAdapter(), prov, null, null);
		ad2.setProposalAcceptanceStyle(ContentProposalAdapter.PROPOSAL_REPLACE);
		ad2.addContentProposalListener(new IContentProposalListener() {
			@Override
			public void proposalAccepted(IContentProposal proposal) {
				setFilenameString(proposal.getContent());
			}
		});
		
		browseDataFile = new Button(dataFileGroup, SWT.NONE);
		browseDataFile.setText("...");
		browseDataFile.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				FileDialog fChooser = new FileDialog(Display.getDefault().getActiveShell());
				fChooser.setText("Choose NXS or DAT file");
				fChooser.setFilterPath(dataFilename);
				String extensions[] = { "*.nxs;*.dat", "*.*" };
				fChooser.setFilterExtensions(extensions);
				String fileStr = fChooser.open();
				if (fileStr != null) {
					final File file = new File(fileStr);
					if (file.isFile()) {
						setFilenameString(file.toString());
						isGuiInResetState = false;
					}
				}
			}
		});
		
		Composite dataParametersComposite = new Composite(dataParameters, SWT.NONE);
		dataParametersComposite.setLayout(new GridLayout(2, false));
		GridData dataParametersGridData = new GridData(SWT.FILL, SWT.CENTER, true, false);
		dataParametersComposite.setLayoutData(dataParametersGridData);
		new Label(dataParametersComposite, SWT.NONE).setText("Working directory");
		workingDirectory = new Text(dataParametersComposite, SWT.NONE);
		workingDirectory.setToolTipText("Directory where programs leave their files. Must be network accessible (not /scratch or /tmp)");
		workingDirectory.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

		ExpandableComposite dataPathAndColumnParametersExpandableComposite = new ExpandableComposite(dataParameters, SWT.NONE);
		dataPathAndColumnParametersExpandableComposite.setLayout(new GridLayout());
		dataPathAndColumnParametersExpandableComposite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		dataPathAndColumnParametersExpandableComposite.setText("Data path and column parameters");

		Composite dataPathAndColumnParametersComposite = new Composite(dataPathAndColumnParametersExpandableComposite, SWT.NONE);
		dataPathAndColumnParametersComposite.setLayout(new GridLayout(2, false));
		dataPathAndColumnParametersComposite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

		new Label(dataPathAndColumnParametersComposite, SWT.NONE).setText("Nexus path to q");
		pathToQCombo = new Combo(dataPathAndColumnParametersComposite, SWT.READ_ONLY);
		pathToQCombo.setToolTipText("Path to q data (only used in Nexus file)");
		pathToQCombo.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		pathToQCombo.addListener(SWT.KeyUp, pathListener);
		pathToQCombo.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				currentPathToQ = pathToQCombo.getText();
			}
		});
		new Label(dataPathAndColumnParametersComposite, SWT.NONE).setText("Nexus path to data");
		pathToDataCombo = new Combo(dataPathAndColumnParametersComposite, SWT.READ_ONLY);
		pathToDataCombo.setToolTipText("Path to data (only used in Nexus file)");
		pathToDataCombo.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		pathToDataCombo.addListener(SWT.KeyUp, pathListener);
		pathToDataCombo.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				currentPathToData = pathToDataCombo.getText();
			}
		});

		new Label(dataPathAndColumnParametersComposite, SWT.NONE).setText("Number of data columns");
		numberOfFrames = new Text(dataPathAndColumnParametersComposite, SWT.NONE);
		numberOfFrames.setToolTipText("Number of data columns to use in analysis. For reduced data, this is 1");
		numberOfFrames.addListener(SWT.Verify, verifyInt);
		numberOfFrames.setLayoutData(new GridData(GridData.FILL, SWT.CENTER, true, false));

		dataPathAndColumnParametersExpandableComposite.setClient(dataPathAndColumnParametersComposite);
		dataPathAndColumnParametersExpandableComposite.addExpansionListener(expansionAdapter);

		ExpandableComposite dataChoiceExpanderComposite = new ExpandableComposite(dataParameters, SWT.NONE);
		dataChoiceExpanderComposite.setLayout(new GridLayout());
		dataChoiceExpanderComposite.setLayoutData(new GridData(GridData.FILL, SWT.TOP, true, false));
		dataChoiceExpanderComposite.setText("Data range");
		Composite dataChoiceParameters = new Composite(dataChoiceExpanderComposite, SWT.NONE);
		dataChoiceParameters.setLayout(new GridLayout());
		dataChoiceParameters.setLayoutData(new GridData(GridData.FILL, SWT.FILL, true, true));
		SashForm pointsSash = new SashForm(dataChoiceParameters, SWT.HORIZONTAL);
		pointsSash.setLayout(new GridLayout(2, false));
		pointsSash.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));

		Group pointsComposite = new Group(pointsSash, SWT.NONE);
		pointsComposite.setLayout(new GridLayout());
		GridData pointsGroupLayout = new GridData(GridData.FILL, SWT.CENTER, true, false);
		pointsComposite.setLayoutData(pointsGroupLayout);
		Composite firstPointComposite = new Composite(pointsComposite, SWT.NONE);
		firstPointComposite.setLayout(new GridLayout(2, false));
		firstPointComposite.setLayoutData(new GridData(GridData.FILL, SWT.CENTER, true, false));
		new Label(firstPointComposite, SWT.NONE).setText("First point");
		startPoint = new Text(firstPointComposite, SWT.NONE);
		startPoint.setToolTipText("First point of data to be used for calculations.");
		startPoint.addListener(SWT.Verify, verifyInt);
		startPoint.addListener(SWT.DefaultSelection, pointsQKeyListener);
		startPoint.addFocusListener(pointsQFocusListener);
		startPoint.setLayoutData(new GridData(GridData.FILL, SWT.CENTER, true, false));

		final String[] qOptionUnits = new String[] { "Angstrom\u207b\u00b9", "nm\u207b\u00b9"};
		final String[] gnomUnits = new String[] { "Angstrom", "nm"};

		Composite qMinComposite = new Composite(pointsComposite, SWT.NONE);
		new Label(qMinComposite, SWT.NONE).setText("q minimum");
		qMinComposite.setLayout(new GridLayout(3, false));
		qMinComposite.setLayoutData(new GridData(GridData.FILL, SWT.CENTER, true, false));
		qMin = new Text(qMinComposite, SWT.NONE);
		qMin.addListener(SWT.Verify, verifyDouble);
		qMin.setToolTipText("Minimum q value to be used for GNOM/DAMMIF");
		qMin.setLayoutData(new GridData(GridData.FILL, SWT.CENTER, true, false));
		qMin.addListener(SWT.DefaultSelection, pointsQKeyListener);
		qMin.addFocusListener(pointsQFocusListener);
		qMinUnits = new Combo(qMinComposite, SWT.READ_ONLY);
		qMinUnits.setItems(qOptionUnits);

		Group pointsGroup2 = new Group(pointsSash, SWT.NONE);
		pointsGroup2.setLayout(new GridLayout());
		pointsGroup2.setLayoutData(new GridData(GridData.FILL, SWT.CENTER, true, false));
		Composite lastPointComposite = new Composite(pointsGroup2, SWT.NONE);
		lastPointComposite.setLayout(new GridLayout(2, false));
		lastPointComposite.setLayoutData(new GridData(GridData.FILL, SWT.CENTER, true, false));
		new Label(lastPointComposite, SWT.NONE).setText("Last point");
		endPoint = new Text(lastPointComposite, SWT.NONE);
		endPoint.setToolTipText("Last point of data to be used for calculations");
		endPoint.addListener(SWT.Verify, verifyInt);
		endPoint.addListener(SWT.DefaultSelection, pointsQKeyListener);
		endPoint.addFocusListener(pointsQFocusListener);
		endPoint.setLayoutData(new GridData(GridData.FILL, SWT.CENTER, true, false));

		GridData data = new GridData(GridData.FILL, SWT.CENTER, true, false);
		pointsComposite.setLayoutData(data);
		pointsComposite.setLayout(new GridLayout());
		pointsGroup2.setLayoutData(data);
		pointsGroup2.setLayout(new GridLayout());

		final Composite qMaxComposite = new Composite(pointsGroup2, SWT.NONE);
		new Label(qMaxComposite, SWT.NONE).setText("q maximum");
		qMaxComposite.setLayout(new GridLayout(3, false));
		qMaxComposite.setLayoutData(new GridData(GridData.FILL, SWT.CENTER, true, false));
		qMax = new Text(qMaxComposite, SWT.NONE);
		qMax.addListener(SWT.Verify, verifyDouble);
		qMax.addListener(SWT.DefaultSelection, pointsQKeyListener);
		qMax.addFocusListener(pointsQFocusListener);
		qMax.setToolTipText("Maximum q value to be used for GNOM/DAMMIF");
		qMax.setLayoutData(new GridData(GridData.FILL, SWT.CENTER, true, false));
		qMaxUnits = new Label(qMaxComposite, SWT.NONE);

		startPointBounds = new IntegerDecorator(startPoint);
		startPointBounds.setMinimum(1);
		endPointBounds = new IntegerDecorator(endPoint);
		startPointBounds.addValueChangeListener(valueChangeListener);
		endPointBounds.addValueChangeListener(valueChangeListener);

		qMinBounds = new FloatDecorator(qMin);
		doubleNumberFormat.setMinimumFractionDigits(4);
		qMinBounds.setNumberFormat(doubleNumberFormat);
		qMinBounds.setMinimum(0);
		qMaxBounds = new FloatDecorator(qMax);
		qMaxBounds.setNumberFormat(doubleNumberFormat);
		qMinBounds.addValueChangeListener(valueChangeListener);
		qMaxBounds.addValueChangeListener(valueChangeListener);

		dataChoiceExpanderComposite.setClient(dataChoiceParameters);
		dataChoiceExpanderComposite.addExpansionListener(expansionAdapter);
		dataChoiceExpanderComposite.setExpanded(true);

		ExpandableComposite plotScrolledExpandableComposite = new ExpandableComposite(dataParameters, SWT.NONE);
		plotScrolledExpandableComposite.setLayout(new GridLayout());
		plotScrolledExpandableComposite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		plotScrolledExpandableComposite.setText("Data plot");

		plotComposite = new Composite(plotScrolledExpandableComposite, SWT.NONE);
		plotComposite.setLayout(new GridLayout());
		plotComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		Composite plotAndOptionComposite = new Composite(plotComposite, SWT.NONE);
		plotAndOptionComposite.setLayout(new GridLayout(2, false));
		plotAndOptionComposite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		final String[] plotOptionNames = new String[]{"logI/logq", "logI/q"};
		plotOptions = new Combo(plotAndOptionComposite, SWT.READ_ONLY);
		plotOptions.setItems(plotOptionNames);
		plotOptions.setToolTipText("Choice of plots to show - logI vs. logq or logI vs. q");
		plotOptions.addSelectionListener(new SelectionListener() {
			
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (plotOptions.getText().equals(plotOptionNames[0])) {
					xAxisIsLog = true;
				}
				else {
					xAxisIsLog = false;
				}
				updatePlot();
			}
			
			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
				//do nothing
			}
		});
		plotOptions.select(1); //default is logI/q
		GridData plotOptionsLayout = new GridData(SWT.RIGHT, SWT.CENTER, true, false);
		plotOptions.setLayoutData(plotOptionsLayout);

		btnResetDataRange = new Button(plotAndOptionComposite, SWT.NONE);
		btnResetDataRange.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, true, false));
		btnResetDataRange.setText("Reset data range");
		btnResetDataRange.setToolTipText("Reset the data range to include all of the data");
		btnResetDataRange.addSelectionListener(new SelectionListener() {
			
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (currentQDataset != null) {
					resetRoi();
				}
			}
			
			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
				//do nothing
			}
		});

		ToolBarManager man = new ToolBarManager(SWT.FLAT|SWT.LEFT|SWT.WRAP);
		ToolBar toolBar = man.createControl(plotComposite);
		toolBar.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));

		plotScrollComposite = new ScrolledComposite(plotComposite, SWT.H_SCROLL | SWT.V_SCROLL);
		plotScrollComposite.setLayout(new FillLayout(SWT.FILL));
		Composite child = new Composite(plotScrollComposite, SWT.NONE);
		child.setLayout(new FillLayout(SWT.FILL));
		
		qIntensityPlot.createPlotPart( child, 
				getTitle(), 
				null, 
				PlotType.XY,
				null);

		//qIntensityPlot.getPlotActionSystem().fillZoomActions(man);
		qIntensityPlot.getPlotActionSystem().fillPrintActions(man);
		
		plotScrollComposite.setMinHeight(400);
		plotScrollComposite.setExpandHorizontal(true);
		plotScrollComposite.setExpandVertical(true);
		plotScrollComposite.setContent(child);

		plotScrolledExpandableComposite.setClient(plotComposite);
		plotScrolledExpandableComposite.addExpansionListener(expansionAdapter);
		plotScrolledExpandableComposite.setExpanded(true);

		ExpandableComposite pipelineOptionsExpandableComposite = new ExpandableComposite(dataParameters, SWT.NONE);
		pipelineOptionsExpandableComposite.setLayout(new GridLayout());
		pipelineOptionsExpandableComposite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		pipelineOptionsExpandableComposite.setText("Pipeline parameters");

		Composite otherOptionsComposite = new Composite(pipelineOptionsExpandableComposite, SWT.NONE);
		otherOptionsComposite.setLayout(new GridLayout(2, false));
		GridData otherOptionsGridData = new GridData(SWT.FILL, SWT.CENTER, true, false);
		otherOptionsComposite.setLayoutData(otherOptionsGridData);
		
		new Label(otherOptionsComposite, SWT.NONE).setText("Number of parallel processes");
		numberOfThreads = new Text(otherOptionsComposite, SWT.NONE);
		numberOfThreads.addListener(SWT.Verify, verifyInt);
		numberOfThreads.setToolTipText("The maximum number of cluster processes used to run DAMMIF");
		numberOfThreads.setLayoutData(new GridData(GridData.FILL, SWT.CENTER, true, false));

		String[] builderOptionsNames = new String[]{"GNOM", "GNOM+DAMMIF"};
		new Label(otherOptionsComposite, SWT.NONE).setText("Pipeline processes");
		builderOptions = new Combo(otherOptionsComposite, SWT.READ_ONLY);
		builderOptions.setItems(builderOptionsNames);
		builderOptions.setToolTipText("Choice of analysis to run - GNOM only or GNOM followed by DAMMIF");

		pipelineOptionsExpandableComposite.setClient(otherOptionsComposite);
		pipelineOptionsExpandableComposite.addExpansionListener(expansionAdapter);

		ExpandableComposite gnomParametersExpandableComposite = new ExpandableComposite(dataParameters,  SWT.NONE);
		gnomParametersExpandableComposite.setLayout(new GridLayout());
		gnomParametersExpandableComposite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		gnomParametersExpandableComposite.setText("GNOM parameters");
		final Composite gnomParameters = new Composite(gnomParametersExpandableComposite, SWT.NONE);
		GridData gnomLayout = new GridData(SWT.FILL, SWT.TOP, true, false);
		gnomLayout.horizontalSpan = 2;
		gnomParameters.setLayoutData(gnomLayout);
		gnomParameters.setLayout(new GridLayout(3, false));

		new Label(gnomParameters, SWT.NONE).setText("Dmax search point start");
		minDistanceSearch = new Text(gnomParameters, SWT.NONE);
		minDistanceSearch.setToolTipText("Initial value for the GNOM program, e.g. minimum possible size of protein");
		minDistanceSearch.addListener(SWT.Verify, verifyDouble);
		minDistanceSearch.setLayoutData(new GridData(GridData.FILL, SWT.CENTER, true, false));
		minDistanceSearch.addListener(SWT.DefaultSelection, distanceKeyListener);
		minDistanceSearch.addFocusListener(distanceFocusListener);
		minDistanceBounds = new FloatDecorator(minDistanceSearch);
		minDistanceUnits = new Label(gnomParameters, SWT.NONE);

		new Label(gnomParameters, SWT.NONE).setText("Dmax search point end");
		maxDistanceSearch = new Text(gnomParameters, SWT.NONE);
		maxDistanceSearch.addListener(SWT.Verify, verifyDouble);
		maxDistanceSearch.setToolTipText("Final value for the GNOM program, e.g. maximum possible size of protein");
		maxDistanceSearch.setLayoutData(new GridData(GridData.FILL, SWT.CENTER, true, false));
		maxDistanceSearch.addListener(SWT.DefaultSelection, distanceKeyListener);
		maxDistanceSearch.addFocusListener(distanceFocusListener);
		maxDistanceBounds = new FloatDecorator(maxDistanceSearch);
		maxDistanceUnits = new Label(gnomParameters, SWT.NONE);

		minDistanceBounds.setMinimum(0);
		maxDistanceBounds.setMaximum(Integer.MAX_VALUE);

		minDistanceBounds.addValueChangeListener(valueChangeListener);
		maxDistanceBounds.addValueChangeListener(valueChangeListener);
		
		qMinUnits.addModifyListener(new ModifyListener() {
			
			@Override
			public void modifyText(ModifyEvent e) {
				modelBuildingParameters.setMainUnitAngstrom(qMinUnits.getText().equals(qOptionUnits[0]));
				qMaxUnits.setText(qMinUnits.getText());
				if (qIntensityPlot.getRegion(Q_REGION_NAME) != null) {
					RectangularROI currentROI = (RectangularROI) qIntensityPlot.getRegion(Q_REGION_NAME).getROI();
					setDoubleBox(qMin, currentROI.getPointX() * getAngstromNmFactor());
					setDoubleBox(qMax, (currentROI.getPointX() + currentROI.getLength(0)) * getAngstromNmFactor());
					qMinBounds.setMinimum(getDatasetMinimumInCurrentUnits() - Math.pow(10, -ROUND_DOUBLES_DIGITS));
					qMinBounds.setMaximum((currentROI.getPointX() + currentROI.getLength(0)) * getAngstromNmFactor());
					qMaxBounds.setMaximum(getDatasetMaximumInCurrentUnits() + Math.pow(10, -ROUND_DOUBLES_DIGITS));
					qMaxBounds.setMinimum(currentROI.getPointX() * getAngstromNmFactor());
				}
				resetDmaxParameters();
				String newUnits = modelBuildingParameters.isMainUnitAngstrom() ? gnomUnits[0] : gnomUnits[1];
				minDistanceUnits.setText(newUnits);
				maxDistanceUnits.setText(newUnits);
				qMaxComposite.layout();
				gnomParameters.layout();
			}
		});

		new Label(gnomParameters, SWT.NONE).setText("Number of iterations");
		numberOfSearch = new Text(gnomParameters, SWT.NONE);
		numberOfSearch.addListener(SWT.Verify, verifyInt);
		GridData searchLayout = new GridData();
		searchLayout.horizontalSpan = 2;
		searchLayout.grabExcessHorizontalSpace = true;
		numberOfSearch.setLayoutData(searchLayout);
		numberOfSearch.setToolTipText("Maximum number of iterations for GNOM to calculate Dmax");

		new Label(gnomParameters, SWT.NONE).setText("Iteration tolerance");
		tolerance = new Text(gnomParameters, SWT.NONE);
		tolerance.addListener(SWT.Verify, verifyDouble);
		tolerance.setToolTipText("Tolerance criteria for completion of GNOM");
		tolerance.setLayoutData(new GridData(GridData.FILL, SWT.CENTER, true, false));

		gnomParametersExpandableComposite.setClient(gnomParameters);
		gnomParametersExpandableComposite.addExpansionListener(expansionAdapter);
		gnomParametersExpandableComposite.setExpanded(true);

		ExpandableComposite dammifParametersExpandableComposite = new ExpandableComposite(dataParameters,  SWT.NONE);
		dammifParametersExpandableComposite.setLayout(new GridLayout());
		dammifParametersExpandableComposite.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		dammifParametersExpandableComposite.setText("DAMMIF parameters");
		Composite dammifParameters = new Composite(dammifParametersExpandableComposite, SWT.NONE);
		dammifParameters.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 2, 1));
		dammifParameters.setLayout(new GridLayout(2, true));

		String[] dammifModeOptions = new String[] {"Fast", "Slow"};
		String[] symmetryOptions = getSymmetryOptions();

		new Label(dammifParameters, SWT.NONE).setText("Symmetry");
		symmetry = new Combo(dammifParameters, SWT.READ_ONLY);
		symmetry.setItems(symmetryOptions);
		symmetry.setToolTipText("Symmetry of particle in DAMMIF.");
		new Label(dammifParameters, SWT.NONE).setText("Speed");
		dammifMode = new Combo(dammifParameters, SWT.READ_ONLY);
		dammifMode.setItems(dammifModeOptions);
		dammifMode.setToolTipText("Run DAMMIF analysis in either fast or slow mode");

		dammifParametersExpandableComposite.setClient(dammifParameters);
		dammifParametersExpandableComposite.addExpansionListener(expansionAdapter);

		btnResetParams = new Button(dataParameters, SWT.NONE);
		btnResetParams.setText("Reset all parameters to defaults");
		btnResetParams.setLayoutData(new GridData(GridData.CENTER, SWT.CENTER, false, false));
		btnResetParams.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				resetGUI();
			}
		});

		ActionContributionItem runModelBuilderAction = (ActionContributionItem)algorithmViewPart.getViewSite().getActionBars().getToolBarManager().find(IAlgorithmProcessContext.RUN_ID_STUB+"NCD model building");
		runModelBuilderAction = new ActionContributionItem(runModelBuilderAction.getAction());
		runModelBuilderAction.fill(dataParameters);
		btnRunNcdModelBuilderJob = (Button) runModelBuilderAction.getWidget();
		btnRunNcdModelBuilderJob.setText("Run NCD model building");
		btnRunNcdModelBuilderJob.setLayoutData(new GridData(GridData.CENTER, SWT.CENTER, false, false));
		btnRunNcdModelBuilderJob.setEnabled(false);

		ActionContributionItem stopModelBuilderAction = (ActionContributionItem)algorithmViewPart.getViewSite().getActionBars().getToolBarManager().find(IAlgorithmProcessContext.STOP_ID_STUB+"NCD model building");
		stopModelBuilderAction.setVisible(false);

		scrolledComposite.setContent(dataParameters);
		scrolledComposite.setExpandHorizontal(true);
		scrolledComposite.setExpandVertical(true);
		scrolledComposite.setMinSize(scrollWidth, scrollHeight);
		scrolledComposite.addControlListener(new ControlAdapter() {
			@Override
			public void controlResized(ControlEvent e) {
				scrolledComposite.setMinSize(dataParameters.computeSize(scrollWidth, SWT.DEFAULT));
				plotScrollComposite.setMinWidth(plotComposite.getBounds().width);
				dataParameters.layout();
			}
		});

		if (modelBuildingParameters == null)
			modelBuildingParameters = new ModelBuildingParameters();

		if (memento == null) {
			updatePlot();
		}

		if (memento != null) {
			modelBuildingParameters.loadMementoParameters(memento);
			PlatformUI.getWorkbench().getDisplay().syncExec(new Runnable() {
				@Override
				public void run() {
					try {
						restoreState();
					} catch (Exception e) {
						logger.error("Problem while trying to restore the state", e);
						resetGUI();
					}
				}
			});
			Job job = new Job("Retrieving q values and paths from data file") {
				@Override
				protected IStatus run(IProgressMonitor monitor) {

					final IDataHolder holder;
					try {
						holder = loadDataFile();
						Display.getDefault().syncExec(new Runnable() {

							@Override
							public void run() {
								boolean dataFileIsNxsFile = isNxsFile(modelBuildingParameters.getDataFilename());
								if (dataFileIsNxsFile) {
									findQAndDataPaths();
									retrieveQFromHierarchicalData(holder);
								}
								else {
									retrieveQFromData(holder);
								}
								enableNexusPathCombos(dataFileIsNxsFile);
								captureGUIInformation();

								try {
									updatePlot(modelBuildingParameters.getDataFilename());
								} catch (Exception e) {
									logger.error("exception while updating plot");
								}
								qMinBounds.setMinimum(getDatasetMinimumInCurrentUnits() - Math.pow(10, -ROUND_DOUBLES_DIGITS));
								qMaxBounds.setMaximum(getDatasetMaximumInCurrentUnits() + Math.pow(10, -ROUND_DOUBLES_DIGITS));
								endPointBounds.setMaximum(currentQDataset.getSize());
								IRegion region = qIntensityPlot.getRegion(Q_REGION_NAME);
								if (region !=  null) {
									RectangularROI roi = (RectangularROI) region.getROI();
									qMinBounds.setMaximum(roi.getEndPoint()[0] * getAngstromNmFactor());
									qMaxBounds.setMinimum(roi.getPoint()[0] * getAngstromNmFactor());
								}
							}
						});
						if (isNxsFile(modelBuildingParameters.getDataFilename())) {
							checkWhetherPathsAreEmpty();
						}
						refreshRunButton(true);
					} catch (Exception e1) {
						logger.error("Exception while retrieving Q values from data file", e1);
					}
					return Status.OK_STATUS;
				}
			};
			job.schedule();
			isGuiInResetState = false;
		}
		else {
			resetGUI();
		}

		checkFilenameAndColorDataFileBox(algorithmViewPart.getSite().getShell().getDisplay());
		
		qIntensityRegionListener = new IROIListener.Stub() {

			@Override
			public void roiDragged(ROIEvent evt) {
				//do nothing here - updating while dragging was slowing things down in HistogramToolPage
			}

			@Override
			public void roiChanged(ROIEvent evt) {
				if (evt.getROI() instanceof RectangularROI) {
					regionDragging = true;
					RectangularROI roi = (RectangularROI) evt.getROI();
					setDoubleBox(qMin, roi.getPoint()[0] * getAngstromNmFactor());
					updatePoint(startPoint, String.valueOf(roi.getPoint()[0] * getAngstromNmFactor()));
					setDoubleBox(qMax, roi.getEndPoint()[0] * getAngstromNmFactor());
					updatePoint(endPoint, String.valueOf(roi.getEndPoint()[0] * getAngstromNmFactor()));
					updateInternalBounds(roi.getPoint()[0], roi.getEndPoint()[0], Integer.parseInt(startPoint.getText()), Integer.parseInt(endPoint.getText()));
					regionDragging=false;
				}
			}
		};

		updatePlot();

		algorithmViewPart.getSite().getWorkbenchWindow().getSelectionService().addSelectionListener(selectionListener);
		// Compute the minimum width size for the first time
		plotScrollComposite.setMinWidth(scrolledComposite.computeSize(SWT.DEFAULT, SWT.DEFAULT).x);

		return parent;
	}

	private ISelectionListener selectionListener = new ISelectionListener() {

		@Override
		public void selectionChanged(IWorkbenchPart part, ISelection selection) {
			if (selection instanceof IStructuredSelection) {
				if (getViewIsActive(part)) {
					Object file = ((IStructuredSelection) selection).getFirstElement();
					if (!forgetLastSelection) {
						if (file instanceof IFile || file instanceof File) {
							String filename = null;
							String fileExtension = null;
							if (file instanceof IFile) {
								fileExtension = ((IFile) file).getFileExtension();
								filename = ((IFile) file).getRawLocation().toOSString();
							}
							else if (file instanceof File) {
								filename = ((File) file).getAbsolutePath();
								fileExtension = filename.substring(filename.length()-3);
							}
							if(fileExtension != null && (fileExtension.equals(DATA_TYPES[0]) || fileExtension.equals(DATA_TYPES[1]))){
	//						if (ARPESFileDescriptor.isArpesFile(filename)) {
								try {
									setFilenameString(filename);
									updatePlot(filename);
	
								} catch (Exception e) {
									logger.error("Something went wrong when creating a overview plot",e);
								}
	//						}
							}
						}
					}
					forgetLastSelection = false;
				}
			}

		}
	};

	private FocusListener distanceFocusListener = new FocusListener() {
		
		@Override
		public void focusLost(FocusEvent e) {
			if (e.getSource() == minDistanceSearch) {
				maxDistanceBounds.setMinimum(Double.parseDouble(minDistanceSearch.getText()));
				minDistanceBounds.setMaximum(maxDistanceBounds.getValue());
			}
			else if (e.getSource() == maxDistanceSearch) {
				minDistanceBounds.setMaximum(Double.parseDouble(maxDistanceSearch.getText()));
				maxDistanceBounds.setMinimum(minDistanceBounds.getValue());
			}
			refreshRunButton(false);
		}
		
		@Override
		public void focusGained(FocusEvent e) {
			//do nothing
		}
	};
	private Listener distanceKeyListener = new Listener() {
		
		@Override
		public void handleEvent(Event event) {
			if (event.widget == minDistanceSearch) {
				maxDistanceBounds.setMinimum(Double.parseDouble(minDistanceSearch.getText()));
				minDistanceBounds.setMaximum(maxDistanceBounds.getValue());
			}
			else if (event.widget == maxDistanceSearch) {
				minDistanceBounds.setMaximum(Double.parseDouble(maxDistanceSearch.getText()));
				maxDistanceBounds.setMinimum(minDistanceBounds.getValue());
			}
			refreshRunButton(false);
		}
	};

	IValueChangeListener valueChangeListener = new IValueChangeListener() {
		
		@Override
		public void valueValidating(ValueChangeEvent evt) {
			refreshRunButton(false);
		}
	};

	private Listener pointsQKeyListener = new Listener() {
		
		@Override
		public void handleEvent(Event event) {
			updateAfterGuiEvent(event.widget);
		}
	};

	private FocusListener pointsQFocusListener = new FocusListener() {

		@Override
		public void focusLost(FocusEvent e) {
			updateAfterGuiEvent(e.getSource());
		}

		@Override
		public void focusGained(FocusEvent e) {
			//do nothing
		}
	};
	
	private boolean fileNameIsNotEmptyAndFileExists(String filename) {
		if (!filename.isEmpty() && new File(filename).exists()) {
			return true;
		}
		return false;
	}
	private String[] getSymmetryOptions() {
		String[] symmetryOptions = new String[30];
		for (int i=1; i< 20; ++i) {
			symmetryOptions[i-1] = "P" + i;
		}
		for (int i=2; i< 13; ++i) {
			symmetryOptions[i + 19 - 2] = "P" + i + "2";
		}
		return symmetryOptions;
	}
	
	protected void setFilenameString(String filename) {
		dataFilename = filename;
		fileSelected = true;
		captureGUIInformation();
		modelBuildingParameters.setDataFilename(filename);
		final IDataHolder holder;
		try {
			holder = loadDataFile();
			boolean dataFileIsNxsFile = isNxsFile(modelBuildingParameters.getDataFilename());
			if (dataFileIsNxsFile) {
				Display.getDefault().syncExec(new Runnable() {
					
					@Override
					public void run() {
						findQAndDataPaths();
						checkWhetherPathsAreEmpty();
						retrieveQFromHierarchicalData(holder);
					}
				});
			}
			else {
				retrieveQFromData(holder);
			}
			enableNexusPathCombos(dataFileIsNxsFile);
		} catch (Exception e1) {
			logger.error("Exception while retrieving Q values from data file", e1);
		}
		if (currentQDataset != null) {
			if (dataFile != null) {
				dataFile.setText(filename);
			}
			if (isNxsFile(filename)) {
				checkWhetherPathsAreEmpty();
			}
			captureGUIInformation();
			refreshRunButton(true);
			Job updateJob = updateGuiParameters();
			try {
				updateJob.join(); // make sure job is finished so that ROI updates work correctly
			} catch (InterruptedException e) {
				logger.error("GUI update job interrupted");
			}
			checkFilenameAndColorDataFileBox(Display.getDefault());
			refreshQAndPointFields();
		}
		updateRoi();
		isGuiInResetState = false;
	}
	private void refreshQAndPointFields() {
		Event trigger = new Event();
		trigger.widget = endPoint;
		startEndPointListener.handleEvent(trigger);
	}

	protected void runNcdModelBuilder(ModelBuildingParameters parameters) {
		runNcdModelBuilderPipeline = new RunNcdModelBuilderPipeline(parameters);
		runNcdModelBuilderPipeline.runEdnaJob();
	}

	public ModelBuildingParameters getParameters() {
		return captureGUIInformation();
	}

	private Listener pathListener = new Listener() {
		@Override
		public void handleEvent(Event event) {
			String pathToData = currentPathToData;
			String pathToQ = currentPathToQ;
			modelBuildingParameters.setPathToData(pathToData);
			modelBuildingParameters.setPathToQ(pathToQ);
			refreshRunButton(true);
			checkWhetherPathsAreEmpty();
			updateGuiParameters();
		}
	};

	private void checkWhetherPathsAreEmpty() {
		pathEmpty = (currentPathToData.isEmpty() || currentPathToQ.isEmpty());
	}

	private Listener startEndPointListener = new Listener() {

		@Override
		public void handleEvent(Event event) {
			Text source = (Text) event.widget;
			String sourceText = source.getText();
			if (source == startPoint) {
				updateQ(qMin, sourceText);
			}
			else if (source == endPoint) {
				updateQ(qMax, sourceText);
			}
			captureGUIInformation();
			updateRoi();
		}
		
	};
	
	protected void refreshRunButton(boolean clearQAndPathItemsIfInvalid) {
		final boolean pathsPopulatedParametersValid = ((fileSelected && !pathEmpty && isNxsFile(modelBuildingParameters.getDataFilename())) || fileSelected)
				&& isValid();
		parent.getDisplay().asyncExec(new Runnable() {
			@Override
			public void run() {
				if (btnRunNcdModelBuilderJob != null && !btnRunNcdModelBuilderJob.isDisposed())
					btnRunNcdModelBuilderJob.setEnabled(pathsPopulatedParametersValid);
			}
		});
		if (clearQAndPathItemsIfInvalid && !pathsPopulatedParametersValid && !(modelBuildingParameters.getDataFilename() == null) && isNxsFile(modelBuildingParameters.getDataFilename())) {
			clearQAndPathItems();
		}
		ActionContributionItem runModelBuilderAction = (ActionContributionItem)algorithmViewPart.getViewSite().getActionBars().getToolBarManager().find(IAlgorithmProcessContext.RUN_ID_STUB+"NCD model building");
		runModelBuilderAction.getAction().setEnabled(pathsPopulatedParametersValid);
	}

	private Job updateGuiParameters() {
		Job job = new Job("Update GUI parameters from data file") {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				final IDataHolder holder;
				try {
					holder = loadDataFile();
				} catch (Exception e1) {
					logger.error("Problem while loading file", e1);
					return Status.CANCEL_STATUS;
				}
				if (isNxsFile(modelBuildingParameters.getDataFilename())) {
					retrieveQFromHierarchicalData(holder);
				}
				else {
					retrieveQFromData(holder);
				}
				if (currentQDataset != null && isGuiInResetState == false) {
					Display.getDefault().syncExec(new Runnable() {
						@Override
						public void run() {
							if (Integer.parseInt(endPoint.getText()) > currentQDataset.getSize()) {
								updateQ(qMax, String.valueOf(currentQDataset.getSize()));
								updateRoi();
							}
							if (Double.parseDouble(qMin.getText()) < currentQDataset.getDouble(currentQDataset.minPos()[0])) {
								updateQ(qMin, "1");
								updateRoi();
							}
							//check that the q and data paths are in the file
							String qPath = currentPathToQ;
							String dataPath = currentPathToData;
							if (holder.contains(qPath) && holder.contains(dataPath) && isNxsFile(modelBuildingParameters.getDataFilename())) {
								try {
									IDataset slicedSet = holder.getLazyDataset(
											dataPath).getSlice(new Slice());
									if (slicedSet.getShape().length > 1) {
										numberOfFrames.setText(String
												.valueOf(holder
														.getLazyDataset(dataPath)
														.getSlice(new Slice())
														.getShape()[1]));
									}
									else {
										numberOfFrames.setText("1");
									}
								} catch (Exception e) {
									logger.error(
											"Exception while attempting to retrieve number of frames from dataset",
											e);
								}
							}
							else if (!isNxsFile(modelBuildingParameters.getDataFilename())) {
								numberOfFrames.setText("1");
							}
						}
					});
				}
				else if (currentQDataset != null) {
					Display.getDefault().asyncExec(new Runnable() {
						@Override
						public void run() {
							resetRoi();
						}
					});
				}
				return Status.OK_STATUS;
			}
		};
		job.schedule();
		return job;
	}

	private void findQAndDataPaths() {
		pathToQCombo.removeAll();
		currentPathToQ = "";
		pathToDataCombo.removeAll();
		currentPathToData = "";
		try {
			HDF5Loader loader = new HDF5Loader(modelBuildingParameters.getDataFilename());
			HDF5File file = loader.loadTree();
			HDF5Group group = file.getGroup();
			HDF5NodeLink link = group.iterator().next(); //top level
			findAxesAndSignals(link);
				
		} catch (Exception e1) {
			logger.error("Problem while trying to populate Q and Data combo boxes", e1);
		}
	}

	//recursive depth-first-search to find all possible axes and signals for q and data
	private void findAxesAndSignals(HDF5NodeLink link) {
		HDF5Node node = link.getDestination();
		if (node instanceof HDF5Group) {
			HDF5Group group2 = (HDF5Group) node;
			for (Iterator<String> nodeItr = group2.getNodeNameIterator(); nodeItr
					.hasNext();) {
				String nodeName = nodeItr.next();
				HDF5NodeLink link1 = group2.getNodeLink(nodeName);
				findAxesAndSignals(link1);
			}
		}
		else if (node instanceof HDF5Dataset) {
			HDF5Node dest1 = node;
			for (Iterator<String> attItr = dest1.getAttributeNameIterator(); attItr
					.hasNext();) {
				String attName = attItr.next();
				if (attName.equals("axis")) {
					pathToQCombo.add(link.getFullName());
				} else if (attName.equals("signal")) {
					pathToDataCombo.add(link.getFullName());
				}
			}
			//find best match to q and select that
			String qPathExpression = "(^/entry1/).*(_result/q$)";
			String[] qComboItems = pathToQCombo.getItems();
			for (String qItem : qComboItems) {
				if (qItem.matches(qPathExpression)) {
					pathToQCombo.select(pathToQCombo.indexOf(qItem));
				}
			}
			//find best match to data and select that
			String dataPathExpression = "(^/entry1/).*(_result/data$)";
			String[] dataComboItems = pathToDataCombo.getItems();
			for (String dataItem : dataComboItems) {
				if (dataItem.matches(dataPathExpression)) {
					pathToDataCombo.select(pathToDataCombo.indexOf(dataItem));
				}
			}
		}
	}

	private IDataHolder loadDataFile() throws Exception {
		IDataHolder holder = LoaderFactory.getData(modelBuildingParameters.getDataFilename());
		return holder;
	}

	private void retrieveQFromHierarchicalData(IDataHolder holder) {
		currentQDataset = retrieveDataFromPath(holder, currentPathToQ);
	}

	private void retrieveQFromData(IDataHolder holder) {
		currentQDataset = retrieveDataFromPath(holder, "q");
	}

	private IDataset retrieveDataFromPath(IDataHolder holder, String path) {
		ILazyDataset qDataset = holder.getLazyDataset(path);
		if (qDataset == null) {
			MessageDialog.openError(Display.getDefault().getActiveShell(), "No q field found", "No q field found in this data file.");
			return null;
		}
		return qDataset.getSlice(new Slice());
	}

	protected void updateQ(Text qTextBox, String text) {
		try {
			int index = Integer.valueOf(text);
			if ((currentQDataset != null) && (index > 0 && index <= currentQDataset.getShape()[0])) {
				double qValue;
				qValue = currentQDataset.getDouble(index - 1) * getAngstromNmFactor();
				setDoubleBox(qTextBox, qValue);
				return;
			}
		} catch (Exception e) {
			logger.error("Index was not valid.", e);
		}
		logger.error("Using a default value for q.");
		double qValue;
		if (qTextBox == qMin) {
			qValue = currentQDataset.getDouble(currentQDataset.minPos()[0]);
		}
		else if (qTextBox == qMax) {
			qValue = currentQDataset.getDouble(currentQDataset.maxPos()[0]);
		}
		else {
			qValue = currentQDataset.getDouble(currentQDataset.minPos()[0]);
		}
		qValue *= getAngstromNmFactor();
		setDoubleBox(qTextBox, qValue);
	}

	protected void updatePoint(Text pointBox, String text) {
		double newQValue;
		try {
			newQValue = Double.valueOf(text);
		} catch (NumberFormatException e) {
			newQValue = 0;
		}
		if (currentQDataset != null) {
			int index = DatasetUtils.findIndexGreaterThan((AbstractDataset) currentQDataset, newQValue / getAngstromNmFactor());
			if (index < 1) {
				index = 1;
			}
			else if (index > currentQDataset.getSize()) {
				index = currentQDataset.getSize();
			}
			pointBox.setText(String.valueOf(index));
		}
	}

	private int getAngstromNmFactor() {
		int angstromNmFactor = 1;
		if (!modelBuildingParameters.isMainUnitAngstrom()) {
			angstromNmFactor = 10;
		}
		return angstromNmFactor;
	}

	protected void updatePlot() {
		algorithmViewPart.getSite().getShell().getDisplay().syncExec(new Runnable() {

			@Override
			public void run() {
//				if (histogramDirty) {
//					histoTrace.setData(histogramX, histogramY);
//					histogramDirty = false;
//				}
				if(!regionDragging ) {
					createRegion();
				}
				qIntensityPlot.getSelectedXAxis().setRange(0, 1);
				qIntensityPlot.getSelectedXAxis().setLog10(xAxisIsLog);
				String qAxisLabel = "q (Angstrom)";
				qIntensityPlot.getSelectedXAxis().setAutoFormat(false);
				qIntensityPlot.getSelectedXAxis().setFormatPattern("#.######");
				if (xAxisIsLog) {
					qAxisLabel = "log q (Angstrom)";
					qIntensityPlot.getSelectedXAxis().setFormatPattern("0.####E0");
				}
				qIntensityPlot.getSelectedXAxis().setTitle(qAxisLabel);
//				qIntensityPlot.getSelectedYAxis().setRange(0, finalScale*256);
				qIntensityPlot.getSelectedYAxis().setLog10(true);
				qIntensityPlot.getSelectedYAxis().setTitle("log Intensity");
				qIntensityPlot.getSelectedYAxis().setAutoFormat(false);
				qIntensityPlot.getSelectedYAxis().setFormatPattern("0.####E0");
				qIntensityPlot.repaint();
			}
		});
	}
	protected ModelBuildingParameters captureGUIInformation() {
		try {
			//TODO use WSParameters for these fields? String resultDir = WSParameters.getViewInstance().getResultDirectory();
			modelBuildingParameters.setWorkingDirectory(workingDirectory.getText());

			String filename = dataFile.getText();
			modelBuildingParameters.setDataFilename(filename);
			fileSelected = fileNameIsNotEmptyAndFileExists(filename);

			//will populate parameters assuming that the Nexus type is being used
			modelBuildingParameters.setPathToQ(pathToQCombo.getText());
			modelBuildingParameters.setPathToData(pathToDataCombo.getText());

			modelBuildingParameters.setNumberOfFrames(Integer.valueOf(numberOfFrames.getText()));

			modelBuildingParameters.setFirstPoint(Integer.valueOf(startPoint.getText()));
			modelBuildingParameters.setLastPoint(Integer.valueOf(endPoint.getText()));

			modelBuildingParameters.setNumberOfThreads(Integer.valueOf(numberOfThreads.getText()));

			modelBuildingParameters.setGnomOnly(builderOptions.getSelectionIndex() == 0);

			double minDistance = Double.valueOf(minDistanceSearch.getText());
			modelBuildingParameters.setStartDistanceAngstrom(minDistance * getAngstromNmFactor());

			double maxDistance = Double.valueOf(maxDistanceSearch.getText());
			modelBuildingParameters.setEndDistanceAngstrom(maxDistance * getAngstromNmFactor());

			modelBuildingParameters.setNumberOfSearch(Integer.valueOf(numberOfSearch.getText()));

			modelBuildingParameters.setTolerance(Double.valueOf(tolerance.getText()));

			modelBuildingParameters.setSymmetry(symmetry.getText());

			modelBuildingParameters.setDammifFastMode(dammifMode.getSelectionIndex() == 0);

			modelBuildingParameters.setxAxisIsLog(plotOptions.getSelectionIndex() == 0);

			return modelBuildingParameters;
		} catch (NumberFormatException e) {
			logger.error("Problems while capturing GUI information", e);
		}
		return modelBuildingParameters;
	}

	public void resetGUI() {
		parent.getDisplay().syncExec(new Runnable() {

			@Override
			public void run() {
				String fedId = System.getenv("USER");
				dataFile.setText("");
				workingDirectory.setText("/dls/tmp/" + fedId);
				numberOfThreads.setText("10");
				builderOptions.select(1);
				int minDistance = 20, maxDistance = 100;
				minDistanceSearch.setText(Integer.toString(minDistance));
				minDistanceBounds.setMaximum(maxDistance);
				maxDistanceBounds.setMinimum(minDistance);
				maxDistanceSearch.setText(Integer.toString(maxDistance));
				numberOfSearch.setText("10");
				setDoubleBox(tolerance, 0.1);
				symmetry.select(0);
				dammifMode.select(0);
				try {
					updateRoi();
				} catch (Exception e) {
					logger.error("Exception while setting up ROI", e);
				}
			}
		});
		clearQAndPathItems();
		qIntensityPlot.clear();
		fileSelected = false;
		enable(fileSelected);
		refreshRunButton(true);
		forgetLastSelection = true;
		checkFilenameAndColorDataFileBox(parent.getDisplay());
		isGuiInResetState = true;
	}
	
	public void clearQAndPathItems() {
		parent.getDisplay().asyncExec(new Runnable() {

			@Override
			public void run() {
				pathToQCombo.removeAll();
				pathToDataCombo.removeAll();
				numberOfFrames.setText("1");
				setDoubleBox(qMin, 0.01);
				qMinUnits.select(0);
				setDoubleBox(qMax, 0.3);
				startPoint.setText("1");
				endPoint.setText("1000");
			}
		});
	}
	
	private Listener verifyDouble = new Listener() {

		@Override
		public void handleEvent(Event e) {
			String string = e.text;
			char[] chars = new char[string.length()];
			string.getChars(0, chars.length, chars, 0);
			for (int i = 0; i < chars.length; i++) {
				if (!('0' <= chars[i] && chars[i] <= '9' || chars[i] == '.')) {
					e.doit = false;
					return;
				}
			}
		}
	};

	private Listener verifyInt = new Listener() {

		@Override
		public void handleEvent(Event e) {
			String string = e.text;
			char[] chars = new char[string.length()];
			string.getChars(0, chars.length, chars, 0);
			for (int i = 0; i < chars.length; i++) {
				if (!('0' <= chars[i] && chars[i] <= '9')) {
					e.doit = false;
					return;
				}
			}
		}
	};

	@Override
	public void init(IViewSite site, IMemento memento) throws PartInitException {
		this.memento = memento;
	}

	@Override
	public void saveState(IMemento memento) {
		if (memento != null) {
			captureGUIInformation();
			modelBuildingParameters.storeMementoParameters(memento);
		}
	}
	
	
	protected void restoreState() {
		String dataFilename = modelBuildingParameters.getDataFilename();
		if (dataFilename != null) {
			dataFile.setText(dataFilename);
		}
		workingDirectory.setText(modelBuildingParameters.getWorkingDirectory());
		numberOfFrames.setText(Integer.toString(modelBuildingParameters.getNumberOfFrames()));
		setDoubleBox(qMin, modelBuildingParameters.getqMinAngstrom() * getAngstromNmFactor());
		qMinUnits.select(modelBuildingParameters.isMainUnitAngstrom() ? 0 : 1);
		setDoubleBox(qMax, modelBuildingParameters.getqMaxAngstrom() * getAngstromNmFactor());
		startPoint.setText(Integer.toString(modelBuildingParameters.getFirstPoint()));
		endPoint.setText(Integer.toString(modelBuildingParameters.getLastPoint()));
		numberOfThreads.setText(Integer.toString(modelBuildingParameters.getNumberOfThreads()));
		builderOptions.select(modelBuildingParameters.isGnomOnly() ? 0 : 1);
		resetDmaxParameters();
		numberOfSearch.setText(Integer.toString(modelBuildingParameters.getNumberOfSearch()));
		setDoubleBox(tolerance, modelBuildingParameters.getTolerance());
		refreshSymmetryCombo(modelBuildingParameters.getSymmetry());
		dammifMode.select(modelBuildingParameters.isDammifFastMode() ? 0 : 1);
		plotOptions.select(modelBuildingParameters.isxAxisIsLog() ? 0 : 1);
	}

	public void resetDmaxParameters() {
		double correctedMinDistance = modelBuildingParameters.getStartDistanceAngstrom() / getAngstromNmFactor();
		double correctedMaxDistance = modelBuildingParameters.getEndDistanceAngstrom() / getAngstromNmFactor();
		minDistanceSearch.setText(Double.toString(correctedMinDistance));
		maxDistanceSearch.setText(Double.toString(correctedMaxDistance));
		minDistanceBounds.setMaximum(correctedMaxDistance);
		maxDistanceBounds.setMinimum(correctedMinDistance);
	}

	private void refreshSymmetryCombo(String symmetry2) {
		String[] options = getSymmetryOptions();
		for (int i=0; i< options.length; ++i) {
			if (options[i].equals(symmetry2)) {
				symmetry.select(i);
				break;
			}
		}
	}

	private void createRegion(){
		try {
			IRegion region = qIntensityPlot.getRegion(Q_REGION_NAME);
			//Test if the region is already there and update the currentRegion
			if (region == null || !region.isVisible()) {
				region = qIntensityPlot.createRegion(Q_REGION_NAME, RegionType.XAXIS);
				qIntensityPlot.addRegion(region);
			}
			region.addROIListener(qIntensityRegionListener);

			updateRoi();
		} catch (Exception e) {
			logger.error("Couldn't open q view and create ROI", e);
		}
	}

	@Override
	public void dispose() {
		algorithmViewPart.getSite().getWorkbenchWindow().getSelectionService().removeSelectionListener(selectionListener);
	}
	
	private void checkFilenameAndColorDataFileBox(Display display) {
		Color red = new Color(display, 255, 0, 0);
		Color white = new Color(display, 255, 255, 255);
		
		if (fileNameIsNotEmptyAndFileExists(dataFile.getText())) {
			dataFile.setBackground(white);
			fileSelected = true;
		} else {
			dataFile.setBackground(red);
			fileSelected = false;
		}
		
		enable(fileSelected);
	}

	private void enable(boolean enabled) {
		workingDirectory.setEnabled(enabled);
		numberOfFrames.setEnabled(enabled);
		qMin.setEnabled(enabled);
		qMinUnits.setEnabled(enabled);
		qMax.setEnabled(enabled);
		qMaxUnits.setEnabled(enabled);
		startPoint.setEnabled(enabled);
		endPoint.setEnabled(enabled);
		numberOfThreads.setEnabled(enabled);
		builderOptions.setEnabled(enabled);
		minDistanceSearch.setEnabled(enabled);
		minDistanceUnits.setEnabled(enabled);
		maxDistanceSearch.setEnabled(enabled);
		maxDistanceUnits.setEnabled(enabled);
		numberOfSearch.setEnabled(enabled);
		tolerance.setEnabled(enabled);
		symmetry.setEnabled(enabled);
		dammifMode.setEnabled(enabled);
		plotOptions.setEnabled(enabled);
		btnResetDataRange.setEnabled(enabled);
	}
	private RectangularROI updateRoi() {
		double qmin = 0;
		double qmax = 1;
		try {
			qmin = modelBuildingParameters.getqMinAngstrom();
			qmax = modelBuildingParameters.getqMaxAngstrom();
			updateInternalBounds(qmin, qmax, Integer.parseInt(startPoint.getText()), Integer.parseInt(endPoint.getText()));
		} catch (NumberFormatException e) {
			logger.error("Problem when attempting to get qMin and qMax values", e);
		}
		RectangularROI roi = new RectangularROI(qmin, 1, qmax - qmin, 1, 0);
		qIntensityPlot.getRegion(Q_REGION_NAME).setROI(roi);
		return roi;
	}
	private void updatePlot(String filename) throws Exception {
		if (currentQDataset != null) {
			IDataHolder data = loadDataFile();
			IDataset dataset = null;
			if (isNxsFile(modelBuildingParameters.getDataFilename())) {
				Map<String, ILazyDataset> map = data.toLazyMap();
				for (String key : map.keySet()) {
					if (key.matches("(^/entry1/).*(_result/data$)")) {
						dataset = data.getLazyDataset(key).getSlice(new Slice());
						if (dataset != null) {
							String sliceName = dataset.getName();
							dataset = dataset.squeeze();
							dataset.setName(sliceName);
						}
						continue;
					}
				}
			}
			else {
				dataset = retrieveDataFromPath(data, "I");
			}
			qIntensityPlot.clear();
			lineTrace = null;

			if (!qIntensityPlot.getTraces().contains(lineTrace)) {
				if (lineTrace == null) {
					lineTrace = qIntensityPlot.createLineTrace("data");
				}
				Slice dataSlice;
				if (dataset.getShape().length == 2 && dataset.getShape()[1]>1) {
					dataSlice = new Slice(0,1);
				}
				else {
					dataSlice = new Slice();
				}
				lineTrace.setData(currentQDataset, dataset.getSlice(dataSlice).squeeze());
				qIntensityPlot.addTrace(lineTrace);
				qIntensityPlot.repaint(true);
			}

			qIntensityPlot.getSelectedXAxis().setRange(currentQDataset.getDouble(0), currentQDataset.getDouble(currentQDataset.getSize()-1));
			qIntensityPlot.repaint();
		}
	}
	
	private boolean isNxsFile(String filename) {
		return filename.endsWith(NcdModelBuilderParametersView.DATA_TYPES[1]);
	}
	private void enableNexusPathCombos(boolean dataFileIsNxsFile) {
		boolean toSet = dataFileIsNxsFile && Activator.getDefault().getPreferenceStore()
		.getBoolean(NcdPreferences.NCD_ALLOWSELECTIONOFPATHS);
		pathToQCombo.setEnabled(toSet);
		pathToDataCombo.setEnabled(toSet);
	}

	private boolean getViewIsActive(IWorkbenchPart part) {
		IViewPart view = part.getSite().getWorkbenchWindow().getActivePage().findView(ID);
		return part.getSite().getWorkbenchWindow().getActivePage().isPartVisible(view);
	}

	private void setDoubleBox(Text text, double value) {
		text.setText(String.format("%."+ ROUND_DOUBLES_DIGITS + "f", value));
		if (text == qMin) {
			modelBuildingParameters.setqMinAngstrom(value / getAngstromNmFactor());
		}
		else if (text == qMax) {
			modelBuildingParameters.setqMaxAngstrom(value / getAngstromNmFactor());
		}
	}

	private void resetRoi() {
		updatePoint(startPoint, Double.toString(getDatasetMinimumInCurrentUnits()));
		updatePoint(endPoint, Double.toString(getDatasetMaximumInCurrentUnits()));
		updateQ(qMin, Integer.toString(1));
		updateQ(qMax, Integer.toString(currentQDataset.getSize()));
		endPointBounds.setMaximum(currentQDataset.getSize());
		qMinBounds.setMinimum(getDatasetMinimumInCurrentUnits() - Math.pow(10, -ROUND_DOUBLES_DIGITS));
		qMaxBounds.setMaximum(getDatasetMaximumInCurrentUnits() + Math.pow(10, -ROUND_DOUBLES_DIGITS));
		captureGUIInformation();
		updateRoi();
	}

	private double getDatasetMaximumInCurrentUnits() {
		return currentQDataset.getDouble(currentQDataset.maxPos()[0]) * getAngstromNmFactor();
	}

	private double getDatasetMinimumInCurrentUnits() {
		return currentQDataset.getDouble(currentQDataset.minPos()[0]) * getAngstromNmFactor();
	}

	
	/**
	 * Update the bounds inside the absolute minimum/maximum values. Used to prevent start/end from overlapping.
	 * @param qMinAngstrom
	 * @param qMaxAngstrom
	 * @param startPointInt
	 * @param endPointInt
	 */
	private void updateInternalBounds(double qMinAngstrom, double qMaxAngstrom, int startPointInt, int endPointInt) {
		qMinBounds.setMaximum(qMaxAngstrom * getAngstromNmFactor());
		qMaxBounds.setMinimum(qMinAngstrom * getAngstromNmFactor());
		startPointBounds.setMaximum(endPointInt);
		endPointBounds.setMinimum(startPointInt);
	}

	private boolean isValid() {
		return (!minDistanceBounds.isError() && !maxDistanceBounds.isError() && !qMinBounds.isError() && !qMaxBounds.isError() &&
				!startPointBounds.isError() && !endPointBounds.isError());
	}

	private void updateAfterGuiEvent(Object source) {
		if (source == minDistanceSearch) {
			maxDistanceBounds.setMinimum(Double.parseDouble(minDistanceSearch.getText()));
			minDistanceBounds.setMaximum(maxDistanceBounds.getValue());
		}
		else if (source == maxDistanceSearch) {
			minDistanceBounds.setMaximum(Double.parseDouble(maxDistanceSearch.getText()));
			maxDistanceBounds.setMinimum(minDistanceBounds.getValue());
		}
		else if (source == qMin) {
			updatePoint(startPoint, ((Text)source).getText());
			setDoubleBox((Text)source, Double.parseDouble(((Text)source).getText()));
		}
		else if (source == qMax) {
			updatePoint(endPoint, ((Text)source).getText());
			setDoubleBox((Text)source, Double.parseDouble(((Text)source).getText()));
		}
		else if (source == startPoint) {
			updateQ(qMin, ((Text)source).getText());
		}
		else if (source == endPoint) {
			updateQ(qMax, ((Text)source).getText());
		}
		captureGUIInformation();
		updateRoi();

		refreshRunButton(false);
	}

	@Override
	public String getTitle() {
		return "BioSAXS Model Builder";
	}

	@Override
	public void run(IAlgorithmProcessContext context) throws Exception {
		runNcdModelBuilder(captureGUIInformation());
	}

	@Override
	public ISourceProvider[] getSourceProviders() {
		IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		ISourceProviderService service = (ISourceProviderService) window.getService(ISourceProviderService.class);
		return service.getSourceProviders();
	}
}
