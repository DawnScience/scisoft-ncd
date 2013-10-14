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
import java.util.Iterator;
import java.util.Map;

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
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.fieldassist.ContentProposalAdapter;
import org.eclipse.jface.fieldassist.IContentProposal;
import org.eclipse.jface.fieldassist.IContentProposalListener;
import org.eclipse.jface.fieldassist.TextContentAdapter;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DropTarget;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.DropTargetListener;
import org.eclipse.swt.dnd.FileTransfer;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
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
import org.eclipse.ui.IMemento;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ViewPart;
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
import uk.ac.diamond.scisoft.analysis.io.DataHolder;
import uk.ac.diamond.scisoft.analysis.io.HDF5Loader;
import uk.ac.diamond.scisoft.analysis.io.LoaderFactory;
import uk.ac.diamond.scisoft.analysis.roi.RectangularROI;
import uk.ac.diamond.scisoft.ncd.rcp.edna.ModelBuildingParameters;
import uk.ac.diamond.scisoft.ncd.rcp.edna.actions.RunNcdModelBuilderPipeline;
import uk.ac.gda.ui.content.FileContentProposalProvider;

public class NcdModelBuilderParametersView extends ViewPart {
	private static final String Q_REGION_NAME = "q Region";
	public static final String ID = "uk.ac.diamond.scisoft.ncd.rcp.edna.views.NcdModelBuilderParametersView";
	protected static final Logger logger = LoggerFactory.getLogger(NcdModelBuilderParametersView.class);

	public static String[] DATA_TYPES = new String[] { "dat", "nxs" };

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
	private Combo qMaxUnits;
	protected Text startPoint;
	protected Text endPoint;

	protected Text numberOfThreads;

	private Combo builderOptions;

	protected Text minDistanceSearch;
	private Combo minDistanceUnits;
	protected Text maxDistanceSearch;
	private Combo maxDistanceUnits;
	protected Text numberOfSearch;
	protected Text tolerance;

	protected Combo symmetry;
	private Combo dammifMode;

	private Button btnResetParams;

	private Button btnRunNcdModelBuilderJob;

	private Composite compInput;

	private ModelBuildingParameters modelBuildingParameters;

	private RunNcdModelBuilderPipeline runNcdModelBuilderPipeline;
	
	private boolean fileSelected = false;
	private boolean pathEmpty = true;

	private Button browseDataFile;

	private IDataset currentQDataset;
	protected String currentPathToQ;
	protected String currentPathToData;

	// plotting system
	private IPlottingSystem qIntensityPlot;
	private IROIListener qIntensityRegionListener;
	protected ILineTrace lineTrace;
	protected boolean regionDragging;

	@Override
	public void createPartControl(Composite parent) {
		compInput = new Composite(parent, SWT.FILL);
		compInput.setLayout(new GridLayout(1, false));

		try {
			qIntensityPlot = PlottingFactory.createPlottingSystem();


		} catch (Exception ne) {
			logger.error("Cannot locate any plotting systems!", ne);
		}

		// Data parameters

		Group dataParameters = new Group(compInput, SWT.NONE);
		dataParameters.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		dataParameters.setLayout(new GridLayout(2, true));
		dataParameters.setText("Data parameters");

		final Group dataFileGroup = new Group(dataParameters, SWT.NONE);
		GridData dataFileGroupData = new GridData(SWT.FILL, SWT.TOP, true, false);
		dataFileGroupData.horizontalSpan = 2;
		dataFileGroup.setLayoutData(dataFileGroupData);
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
				pathToQCombo.setEnabled(dataFileIsNxsFile);
				pathToDataCombo.setEnabled(dataFileIsNxsFile);
				captureGUIInformation();
				checkWhetherPathsAreEmpty();
				refreshRunButton();
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
					}
				}
			}
		});
		
		new Label(dataParameters, SWT.NONE).setText("Working directory");
		workingDirectory = new Text(dataParameters, SWT.NONE);
		workingDirectory.setToolTipText("Directory where programs leave their files. Must be network accessible (not /scratch or /tmp)");
		workingDirectory.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));

		new Label(dataParameters, SWT.NONE).setText("Nexus path to q");
		pathToQCombo = new Combo(dataParameters, SWT.READ_ONLY);
		pathToQCombo.setToolTipText("Path to q data (only used in Nexus file)");
		pathToQCombo.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		pathToQCombo.addListener(SWT.KeyUp, pathListener);
		pathToQCombo.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				currentPathToQ = pathToQCombo.getText();
			}
		});
		new Label(dataParameters, SWT.NONE).setText("Nexus path to data");
		pathToDataCombo = new Combo(dataParameters, SWT.READ_ONLY);
		pathToDataCombo.setToolTipText("Path to data (only used in Nexus file)");
		pathToDataCombo.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		pathToDataCombo.addListener(SWT.KeyUp, pathListener);
		pathToDataCombo.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				currentPathToData = pathToDataCombo.getText();
			}
		});

		new Label(dataParameters, SWT.NONE).setText("Number of frames");
		numberOfFrames = new Text(dataParameters, SWT.NONE);
		numberOfFrames.setToolTipText("Number of data columns to use in analysis");
		numberOfFrames.addListener(SWT.Verify, verifyInt);
		numberOfFrames.setLayoutData(new GridData(GridData.FILL, SWT.CENTER, true, false));

		Group qParameters = new Group(dataParameters, SWT.NONE);
		GridData qLayout = new GridData(SWT.FILL, SWT.TOP, true, false);
		qLayout.horizontalSpan = 2;
		qParameters.setLayoutData(qLayout);
		qParameters.setLayout(new GridLayout(3, false));
		qParameters.setText("q");

		final String[] qOptionUnits = new String[] { "Angstrom\u207b\u2071", "nm\u207b\u2071"};

		new Label(qParameters, SWT.NONE).setText("q minimum");
		qMin = new Text(qParameters, SWT.NONE);
		qMin.addListener(SWT.Verify, verifyDouble);
		qMin.addListener(SWT.KeyUp, qMinMaxListener);
		qMin.setToolTipText("Minimum q value to be used for GNOM/DAMMIF");
		qMin.setLayoutData(new GridData(GridData.FILL, SWT.CENTER, true, false));
		qMinUnits = new Combo(qParameters, SWT.READ_ONLY);
		qMinUnits.setItems(qOptionUnits);
		qMinUnits.addModifyListener(new ModifyListener() {
			
			@Override
			public void modifyText(ModifyEvent e) {
				modelBuildingParameters.setqMinInverseAngstromUnits(qMinUnits.getText().equals(qOptionUnits[0]));
			}
		});
		new Label(qParameters, SWT.NONE).setText("q maximum");
		qMax = new Text(qParameters, SWT.NONE);
		qMax.addListener(SWT.Verify, verifyDouble);
		qMax.addListener(SWT.KeyUp, qMinMaxListener);
		qMax.setToolTipText("Maximum q value to be used for GNOM/DAMMIF");
		qMax.setLayoutData(new GridData(GridData.FILL, SWT.CENTER, true, false));
		qMaxUnits = new Combo(qParameters, SWT.READ_ONLY);
		qMaxUnits.setItems(qOptionUnits);
		qMaxUnits.addModifyListener(new ModifyListener() {
			
			@Override
			public void modifyText(ModifyEvent e) {
				modelBuildingParameters.setqMaxInverseAngstromUnits(qMaxUnits.getText().equals(qOptionUnits[0]));
			}
		});

		Group pointsParameters = new Group(dataParameters, SWT.NONE);
		GridData pointsLayout = new GridData(SWT.FILL, SWT.TOP, true, false);
		pointsLayout.horizontalSpan = 2;
		pointsParameters.setLayoutData(pointsLayout);
		pointsParameters.setLayout(new GridLayout(2, true));
		pointsParameters.setText("Points");

		new Label(pointsParameters, SWT.NONE).setText("First point");
		startPoint = new Text(pointsParameters, SWT.NONE);
		startPoint.addListener(SWT.Verify, verifyInt);
		startPoint.addListener(SWT.KeyUp, startEndPointListener);
		startPoint.setLayoutData(new GridData(GridData.FILL, SWT.CENTER, true, false));
		new Label(pointsParameters, SWT.NONE).setText("Last point");
		endPoint = new Text(pointsParameters, SWT.NONE);
		endPoint.addListener(SWT.Verify, verifyInt);
		endPoint.addListener(SWT.KeyUp, startEndPointListener);
		endPoint.setLayoutData(new GridData(GridData.FILL, SWT.CENTER, true, false));

		new Label(dataParameters, SWT.NONE).setText("Number of threads");
		numberOfThreads = new Text(dataParameters, SWT.NONE);
		numberOfThreads.addListener(SWT.Verify, verifyInt);
		numberOfThreads.setToolTipText("The maximum number of threads to be used for DAMMIF");
		numberOfThreads.setLayoutData(new GridData(GridData.FILL, SWT.CENTER, true, false));

		String[] builderOptionsNames = new String[]{"GNOM", "GNOM+DAMMIF"};
		new Label(dataParameters, SWT.NONE).setText("Rmax or Rmax + model building");
		builderOptions = new Combo(dataParameters, SWT.READ_ONLY);
		builderOptions.setItems(builderOptionsNames);
		builderOptions.setToolTipText("Choice of analysis to run - GNOM alone or followed by DAMMIF");

		Group gnomParameters = new Group(dataParameters, SWT.NONE);
		GridData gnomLayout = new GridData(SWT.FILL, SWT.TOP, true, false);
		gnomLayout.horizontalSpan = 2;
		gnomParameters.setLayoutData(gnomLayout);
		gnomParameters.setLayout(new GridLayout(3, false));
		gnomParameters.setText("GNOM");

		final String[] distanceOptionsUnits = new String[] {"Angstrom", "nm"};

		new Label(gnomParameters, SWT.NONE).setText("Minimum distance search");
		minDistanceSearch = new Text(gnomParameters, SWT.NONE);
		minDistanceSearch.setToolTipText("Initial value for Rmax to perform search");
		minDistanceSearch.addListener(SWT.Verify, verifyDouble);
		minDistanceSearch.setLayoutData(new GridData(GridData.FILL, SWT.CENTER, true, false));

		minDistanceUnits = new Combo(gnomParameters, SWT.READ_ONLY);
		minDistanceUnits.setItems(distanceOptionsUnits);
		minDistanceUnits.addModifyListener(new ModifyListener() {
			
			@Override
			public void modifyText(ModifyEvent e) {
				modelBuildingParameters.setStartDistanceAngstromUnits(minDistanceUnits.getText().equals(distanceOptionsUnits[0]));
			}
		});

		new Label(gnomParameters, SWT.NONE).setText("Maximum distance search");
		maxDistanceSearch = new Text(gnomParameters, SWT.NONE);
		maxDistanceSearch.addListener(SWT.Verify, verifyDouble);
		maxDistanceSearch.setToolTipText("Final value for Rmax to perform search");
		maxDistanceSearch.setLayoutData(new GridData(GridData.FILL, SWT.CENTER, true, false));
		maxDistanceUnits = new Combo(gnomParameters, SWT.READ_ONLY);
		maxDistanceUnits.setItems(distanceOptionsUnits);
		maxDistanceUnits.addModifyListener(new ModifyListener() {
			
			@Override
			public void modifyText(ModifyEvent e) {
				modelBuildingParameters.setEndDistanceAngstromUnits(maxDistanceUnits.getText().equals(distanceOptionsUnits[0]));
			}
		});

		new Label(gnomParameters, SWT.NONE).setText("Number of search");
		numberOfSearch = new Text(gnomParameters, SWT.NONE);
		numberOfSearch.addListener(SWT.Verify, verifyInt);
		GridData searchLayout = new GridData();
		searchLayout.horizontalSpan = 2;
		searchLayout.grabExcessHorizontalSpace = true;
		numberOfSearch.setLayoutData(searchLayout);
		numberOfSearch.setToolTipText("Number of intervals to use for Rmax search");

		new Label(gnomParameters, SWT.NONE).setText("Tolerance");
		tolerance = new Text(gnomParameters, SWT.NONE);
		tolerance.addListener(SWT.Verify, verifyDouble);
		tolerance.setToolTipText("Stopping criterion for Rmax search");
		tolerance.setLayoutData(new GridData(GridData.FILL, SWT.CENTER, true, false));

		Group dammifParameters = new Group(dataParameters, SWT.NONE);
		dammifParameters.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 2, 1));
		dammifParameters.setLayout(new GridLayout(2, true));
		dammifParameters.setText("DAMMIF");

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

		btnResetParams = new Button(dataParameters, SWT.NONE);
		btnResetParams.setText("Reset all parameters to defaults");
		btnResetParams.setLayoutData(new GridData(GridData.FILL, SWT.CENTER, true, false));
		btnResetParams.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				resetGUI();
			}
		});

		btnRunNcdModelBuilderJob = new Button(dataParameters, SWT.NONE);
		btnRunNcdModelBuilderJob.setText("Run NCD model building");
		btnRunNcdModelBuilderJob.setLayoutData(new GridData(GridData.FILL, SWT.CENTER, true, false));
		btnRunNcdModelBuilderJob.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				runNcdModelBuilder();
			}
		});
		btnRunNcdModelBuilderJob.setEnabled(false);
		
		qIntensityPlot.createPlotPart( compInput, 
				getTitle(), 
				null, 
				PlotType.XY,
				null);
		qIntensityPlot.getPlotComposite().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));

		if (modelBuildingParameters == null)
			modelBuildingParameters = new ModelBuildingParameters();

		if (memento != null) {
			modelBuildingParameters.loadMementoParameters(memento);
			PlatformUI.getWorkbench().getDisplay().syncExec(new Runnable() {
				@Override
				public void run() {
					try {
						restoreState();
					} catch (Exception e) {
						resetGUI();
					}
				}
			});
			Job job = new Job("Retrieving q values and paths from data file") {
				@Override
				protected IStatus run(IProgressMonitor monitor) {

					final DataHolder holder;
					try {
						holder = loadDataFile();
						Display.getDefault().syncExec(new Runnable() {

							@Override
							public void run() {
								if (isNxsFile(modelBuildingParameters.getDataFilename())) {
									findQAndDataPaths();
									retrieveQFromHierarchicalData(holder);
								}
								else {
									retrieveQFromData(holder);
								}
								captureGUIInformation();

								try {
									updatePlot(modelBuildingParameters.getDataFilename());
								} catch (Exception e) {
									logger.error("exception while updating plot");
								}
							}
						});
						checkWhetherPathsAreEmpty();
						refreshRunButton();
					} catch (Exception e1) {
						logger.error("Exception while retrieving Q values from data file", e1);
					}
					return Status.OK_STATUS;
				}
			};
			job.schedule();
		}
		else {
			resetGUI();
		}

		checkFilenameAndColorDataFileBox(this.getSite().getShell().getDisplay());
		
		qIntensityRegionListener = new IROIListener.Stub() {

			@Override
			public void roiDragged(ROIEvent evt) {
				//do nothing here - updating while dragging was slowing things down in HistogramToolPage
			}

			@Override
			public void roiChanged(ROIEvent evt) {
				if (evt.getROI() instanceof RectangularROI) {
					regionDragging = true;
					IRegion region = qIntensityPlot.getRegion(Q_REGION_NAME);
					RectangularROI roi = (RectangularROI) region.getROI();
					qMin.setText( String.valueOf(roi.getPoint()[0]));
					updatePoint(startPoint, String.valueOf(roi.getPoint()[0]));
					qMax.setText(String.valueOf(roi.getEndPoint()[0]));
					updatePoint(endPoint, String.valueOf(roi.getEndPoint()[0]));
					regionDragging=false;
				}
			}
		};

		updatePlot();

		getSite().getWorkbenchWindow().getSelectionService().addSelectionListener(selectionListener);

	}

	private ISelectionListener selectionListener = new ISelectionListener() {

		@Override
		public void selectionChanged(IWorkbenchPart part, ISelection selection) {
			if (selection instanceof IStructuredSelection) {
				Object file = ((IStructuredSelection) selection).getFirstElement();
				if (file instanceof IFile) {
					String fileExtension = ((IFile) file).getFileExtension();
					if(fileExtension != null && (fileExtension.equals(DATA_TYPES[0]) || fileExtension.equals(DATA_TYPES[1]))){
						String filename = ((IFile) file).getRawLocation().toOSString();
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
		final DataHolder holder;
		try {
			holder = loadDataFile();
			if (isNxsFile(modelBuildingParameters.getDataFilename())) {
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
		} catch (Exception e1) {
			logger.error("Exception while retrieving Q values from data file", e1);
		}
		if (currentQDataset != null) {
			if (dataFile != null) {
				dataFile.setText(filename);
			}
			checkWhetherPathsAreEmpty();
			captureGUIInformation();
			refreshRunButton();
			Job updateJob = updateGuiParameters();
			try {
				updateJob.join(); // make sure job is finished so that ROI updates work correctly
			} catch (InterruptedException e) {
				logger.error("GUI update job interrupted");
			}
			checkFilenameAndColorDataFileBox(Display.getDefault());
			Event trigger = new Event();
			trigger.widget = endPoint;
			startEndPointListener.handleEvent(trigger);
		}
	}

	protected void runNcdModelBuilder() {
		runNcdModelBuilderPipeline = new RunNcdModelBuilderPipeline();
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
			refreshRunButton();
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
			updateRoi();
		}
		
	};
	
	private Listener qMinMaxListener = new Listener() {

		@Override
		public void handleEvent(Event event) {
			Text source = (Text) event.widget;
			String sourceText = source.getText();
			if (source == qMin) {
				updatePoint(startPoint, sourceText);
			}
			else if (source == qMax) {
				updatePoint(endPoint, sourceText);
			}
			updateRoi();
		}
		
	};
	
	protected void refreshRunButton() {
		final boolean fileValidAndPathsPopulated = fileSelected && !pathEmpty;
		compInput.getDisplay().asyncExec(new Runnable() {
			@Override
			public void run() {
				btnRunNcdModelBuilderJob.setEnabled(fileValidAndPathsPopulated);
			}
		});
		if (!fileValidAndPathsPopulated) {
			clearQAndPathItems();
		}
	}

	private Job updateGuiParameters() {
		Job job = new Job("Update GUI parameters from data file") {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				final DataHolder holder;
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
				if (currentQDataset != null) {
					Display.getDefault().syncExec(new Runnable() {
						@Override
						public void run() {
							qMin.setText(String.valueOf(currentQDataset.min()));
							qMax.setText(String.valueOf(currentQDataset.max()));
							endPoint.setText(String.valueOf(currentQDataset
									.getSize()));
							//check that the q and data paths are in the file
							String qPath = currentPathToQ;
							String dataPath = currentPathToData;
							if (holder.contains(qPath) && holder.contains(dataPath)) {
								startPoint.setText("1");
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

	private DataHolder loadDataFile() throws Exception {
		DataHolder holder = LoaderFactory.getData(modelBuildingParameters.getDataFilename());
		return holder;
	}

	private void retrieveQFromHierarchicalData(DataHolder holder) {
		currentQDataset = retrieveQ(holder, currentPathToQ);
	}

	private void retrieveQFromData(DataHolder holder) {
		currentQDataset = retrieveQ(holder, "q");
	}

	private IDataset retrieveQ(DataHolder holder, String path) {
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
				qValue = currentQDataset.getDouble(index - 1);
				qTextBox.setText(String.valueOf(qValue));
				return;
			}
		} catch (Exception e) {
			logger.error("Index was not valid.");
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
		qTextBox.setText(String.valueOf(qValue));
	}

	protected void updatePoint(Text pointBox, String text) {
		double newQValue;
		try {
			newQValue = Double.valueOf(text);
		} catch (NumberFormatException e) {
			newQValue = 0;
		}
		if (currentQDataset != null) {
			int index = DatasetUtils.findIndexGreaterThan((AbstractDataset) currentQDataset, newQValue);
			if (index < 1) {
				index = 1;
			}
			else if (index > currentQDataset.getSize()) {
				index = currentQDataset.getSize();
			}
			pointBox.setText(String.valueOf(index));
		}
	}

	protected void updatePlot() {
		getSite().getShell().getDisplay().syncExec(new Runnable() {

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
				qIntensityPlot.getSelectedXAxis().setLog10(true);
				qIntensityPlot.getSelectedXAxis().setTitle("q");
//				qIntensityPlot.getSelectedYAxis().setRange(0, finalScale*256);
				qIntensityPlot.getSelectedYAxis().setLog10(true);
				qIntensityPlot.getSelectedYAxis().setTitle("Intensity");
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

			double qMinValue = Double.valueOf(qMin.getText());
			if (qMinUnits.getSelectionIndex() == 1) {
				qMinValue /= 10;
			}
			modelBuildingParameters.setqMinAngstrom(qMinValue);

			modelBuildingParameters.setFirstPoint(Integer.valueOf(startPoint.getText()));
			modelBuildingParameters.setLastPoint(Integer.valueOf(endPoint.getText()));

			double qMaxValue = Double.valueOf(qMax.getText());
			if (qMaxUnits.getSelectionIndex() == 1) {
				qMaxValue /= 10;
			}
			modelBuildingParameters.setqMaxAngstrom(qMaxValue);

			modelBuildingParameters.setNumberOfThreads(Integer.valueOf(numberOfThreads.getText()));

			modelBuildingParameters.setGnomOnly(builderOptions.getSelectionIndex() == 0);

			double minDistance = Double.valueOf(minDistanceSearch.getText());
			if (minDistanceUnits.getSelectionIndex() == 1) {
				minDistance *= 10;
			}
			modelBuildingParameters.setStartDistanceAngstrom(minDistance);

			double maxDistance = Double.valueOf(maxDistanceSearch.getText());
			if (maxDistanceUnits.getSelectionIndex() == 1) {
				maxDistance *= 10;
			}
			modelBuildingParameters.setEndDistanceAngstrom(maxDistance);

			modelBuildingParameters.setNumberOfSearch(Integer.valueOf(numberOfSearch.getText()));

			modelBuildingParameters.setTolerance(Double.valueOf(tolerance.getText()));

			modelBuildingParameters.setSymmetry(symmetry.getText());

			modelBuildingParameters.setDammifFastMode(dammifMode.getSelectionIndex() == 0);

			return modelBuildingParameters;
		} catch (NumberFormatException e) {
			logger.error("Problems while capturing GUI information", e);
		}
		return modelBuildingParameters;
	}
	
	@Override
	public void setFocus() {
		// do nothing here
	}

	public void resetGUI() {
		compInput.getDisplay().syncExec(new Runnable() {

			@Override
			public void run() {
				String fedId = System.getenv("USER");
				dataFile.setText("");
				workingDirectory.setText("/dls/tmp/" + fedId);
				numberOfThreads.setText("10");
				builderOptions.select(1);
				minDistanceSearch.setText("20");
				minDistanceUnits.select(0);
				maxDistanceSearch.setText("100");
				maxDistanceUnits.select(0);
				numberOfSearch.setText("10");
				tolerance.setText("0.1");
				symmetry.select(0);
				dammifMode.select(0);
				updateRoi();
			}
		});
		clearQAndPathItems();
		qIntensityPlot.clear();
		enable(false);
		checkFilenameAndColorDataFileBox(compInput.getDisplay());
	}
	
	public void clearQAndPathItems() {
		compInput.getDisplay().asyncExec(new Runnable() {

			@Override
			public void run() {
				pathToQCombo.removeAll();
				pathToDataCombo.removeAll();
				numberOfFrames.setText("1");
				qMin.setText("0.01");
				qMinUnits.select(0);
				qMax.setText("0.3");
				qMaxUnits.select(0);
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
		init(site);
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
		qMin.setText(Double.toString(modelBuildingParameters.getqMinAngstrom()));
		qMinUnits.select(modelBuildingParameters.isqMinInverseAngstromUnits() ? 0 : 1);
		qMax.setText(Double.toString(modelBuildingParameters.getqMaxAngstrom()));
		qMaxUnits.select(modelBuildingParameters.isqMaxInverseAngstromUnits() ? 0 : 1);
		startPoint.setText(Integer.toString(modelBuildingParameters.getFirstPoint()));
		endPoint.setText(Integer.toString(modelBuildingParameters.getLastPoint()));
		numberOfThreads.setText(Integer.toString(modelBuildingParameters.getNumberOfThreads()));
		builderOptions.select(modelBuildingParameters.isGnomOnly() ? 0 : 1);
		minDistanceSearch.setText(Double.toString(modelBuildingParameters.getStartDistanceAngstrom()));
		minDistanceUnits.select(modelBuildingParameters.isStartDistanceAngstromUnits() ? 0 : 1);
		maxDistanceSearch.setText(Double.toString(modelBuildingParameters.getEndDistanceAngstrom()));
		maxDistanceUnits.select(modelBuildingParameters.isEndDistanceAngstromUnits() ? 0 : 1);
		numberOfSearch.setText(Integer.toString(modelBuildingParameters.getNumberOfSearch()));
		tolerance.setText(Double.toString(modelBuildingParameters.getTolerance()));
		refreshSymmetryCombo(modelBuildingParameters.getSymmetry());
		dammifMode.select(modelBuildingParameters.isDammifFastMode() ? 0 : 1);
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

			updateRoi();
			region.addROIListener(qIntensityRegionListener);
		} catch (Exception e) {
			logger.error("Couldn't open q view and create ROI", e);
		}
	}

	@Override
	public void dispose() {
		getSite().getWorkbenchWindow().getSelectionService().removeSelectionListener(selectionListener);
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
	}
	private RectangularROI updateRoi() {
		double qmin = Double.parseDouble(qMin.getText());
		double qmax = Double.parseDouble(qMax.getText());
		RectangularROI roi = new RectangularROI(qmin, 1, qmax - qmin, 1, 0);
		qIntensityPlot.getRegion(Q_REGION_NAME).setROI(roi);
		return roi;
	}
	private void updatePlot(String filename) throws Exception {
		if (currentQDataset != null) {
			DataHolder data = loadDataFile();
			Map<String, ILazyDataset> map = data.getMap();
			ILazyDataset data1 = null;
			ILazyDataset q = null;
			for (String key : map.keySet()) {
				if (key.matches("(^/entry1/).*(_result/q$)")) {
					q = data.getLazyDataset(key);
					continue;
				}
				else if (key.matches("(^/entry1/).*(_result/data$)")) {
					data1 = data.getLazyDataset(key);
					continue;
				}
			}
			qIntensityPlot.clear();
			lineTrace = null;

			if (!qIntensityPlot.getTraces().contains(lineTrace)) {
				if (lineTrace == null) {
					lineTrace = qIntensityPlot.createLineTrace("data");
				}
				lineTrace.setData(q.getSlice(new Slice()), data1.getSlice(new Slice()));
				qIntensityPlot.addTrace(lineTrace);
				qIntensityPlot.repaint(true);
			}

			IDataset qSlice = q.getSlice(new Slice());
			qIntensityPlot.getSelectedXAxis().setRange(qSlice.getDouble(0), qSlice.getDouble(q.getSize()-1));
			qIntensityPlot.repaint();
		}
	}
	
	private boolean isNxsFile(String filename) {
		return filename.endsWith(NcdModelBuilderParametersView.DATA_TYPES[1]);
	}
}
