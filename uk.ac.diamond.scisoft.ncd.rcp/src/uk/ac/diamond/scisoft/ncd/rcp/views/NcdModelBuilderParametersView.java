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

package uk.ac.diamond.scisoft.ncd.rcp.views;

import java.io.File;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ITreeSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.part.ViewPart;

import uk.ac.diamond.scisoft.analysis.utils.FileUtils;
import uk.ac.diamond.scisoft.ncd.ModelBuildingParameters;
import uk.ac.diamond.scisoft.ncd.rcp.actions.RunNcdModelBuilderPipeline;
import uk.ac.diamond.scisoft.ws.rcp.WSParameters;

public class NcdModelBuilderParametersView extends ViewPart {
	public static final String ID = "uk.ac.diamond.scisoft.ncd.rcp.views.NcdModelBuilderParametersView";

	public static String[] DATA_TYPES = new String[] { "dat", "nxs" };

	private IWorkbenchWindow window;

	protected Text pathToQ;
	protected Text pathToData;

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
	
	private static final String WELCOMETEXT = "Select a file in Project Explorer to define data file.";

	@Override
	public void createPartControl(Composite parent) {
		//TODO add all of the tooltips for boxes
		window = getSite().getWorkbenchWindow();

		compInput = new Composite(parent, SWT.FILL);
		compInput.setLayout(new GridLayout(1, false));

		Group gpWelcome = new Group(compInput, SWT.NONE);
		gpWelcome.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 1, 1));
		gpWelcome.setLayout(new GridLayout(1, true));

		Label lblWelcome = new Label(gpWelcome, SWT.WRAP);
		lblWelcome.setText(WELCOMETEXT);

		// Data parameters

		Group dataParameters = new Group(compInput, SWT.NONE);
		dataParameters.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		dataParameters.setLayout(new GridLayout(2, true));
		dataParameters.setText("Data parameters");

		new Label(dataParameters, SWT.NONE).setText("Path to q");
		pathToQ = new Text(dataParameters, SWT.NONE);
//		pathToQ.addSelectionListener(listener);
		new Label(dataParameters, SWT.NONE).setText("Path to data");
		pathToData = new Text(dataParameters, SWT.NONE);
//		pathToQ.addSelectionListener(listener);

		new Label(dataParameters, SWT.NONE).setText("Number of Frames");
		numberOfFrames = new Text(dataParameters, SWT.NONE);
		numberOfFrames.setToolTipText("Number of data columns to use in analysis");
		numberOfFrames.addListener(SWT.Verify, verifyInt);

		Group qParameters = new Group(dataParameters, SWT.NONE);
		GridData qLayout = new GridData(SWT.FILL, SWT.TOP, true, false);
		qLayout.horizontalSpan = 2;
		qParameters.setLayoutData(qLayout);
		qParameters.setLayout(new GridLayout(3, false));
		qParameters.setText("q");

		String[] qOptionUnits = new String[] { "Angstrom\u207b\u2071", "nm\u207b\u2071"};

		new Label(qParameters, SWT.NONE).setText("q minimum");
		qMin = new Text(qParameters, SWT.NONE);
		qMin.addListener(SWT.Verify, verifyDouble);
		qMin.setToolTipText("Minimum q value to be used for GNOM/DAMMIF");
		qMinUnits = new Combo(qParameters, SWT.READ_ONLY);
		qMinUnits.setItems(qOptionUnits);
		new Label(qParameters, SWT.NONE).setText("q maximum");
		qMax = new Text(qParameters, SWT.NONE);
		qMax.addListener(SWT.Verify, verifyDouble);
		qMax.setToolTipText("Maximum q value to be used for GNOM/DAMMIF");
		qMaxUnits = new Combo(qParameters, SWT.READ_ONLY);
		qMaxUnits.setItems(qOptionUnits);

		Group pointsParameters = new Group(dataParameters, SWT.NONE);
		GridData pointsLayout = new GridData(SWT.FILL, SWT.TOP, true, false);
		pointsLayout.horizontalSpan = 2;
		pointsParameters.setLayoutData(pointsLayout);
		pointsParameters.setLayout(new GridLayout(2, true));
		pointsParameters.setText("Points");

		new Label(pointsParameters, SWT.NONE).setText("First point");
		startPoint = new Text(pointsParameters, SWT.NONE);
		startPoint.addListener(SWT.Verify, verifyInt);
		new Label(pointsParameters, SWT.NONE).setText("Last point");
		endPoint = new Text(pointsParameters, SWT.NONE);
		endPoint.addListener(SWT.Verify, verifyInt);

		new Label(dataParameters, SWT.NONE).setText("Number of threads");
		numberOfThreads = new Text(dataParameters, SWT.NONE);
		numberOfThreads.addListener(SWT.Verify, verifyInt);
		numberOfThreads.setToolTipText("The maximum number of threads to be used for DAMMIF");

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

		String[] distanceOptionsUnits = new String[] {"Angstrom", "nm"};

		new Label(gnomParameters, SWT.NONE).setText("Minimum distance search");
		minDistanceSearch = new Text(gnomParameters, SWT.NONE);
		minDistanceSearch.setToolTipText("Initial value for Rmax to perform search");
		minDistanceSearch.addListener(SWT.Verify, verifyDouble);
		minDistanceUnits = new Combo(gnomParameters, SWT.READ_ONLY);
		minDistanceUnits.setItems(distanceOptionsUnits);

		new Label(gnomParameters, SWT.NONE).setText("Maximum distance search");
		maxDistanceSearch = new Text(gnomParameters, SWT.NONE);
		maxDistanceSearch.addListener(SWT.Verify, verifyDouble);
		maxDistanceSearch.setToolTipText("Final value for Rmax to perform search");
		maxDistanceUnits = new Combo(gnomParameters, SWT.READ_ONLY);
		maxDistanceUnits.setItems(distanceOptionsUnits);

		new Label(gnomParameters, SWT.NONE).setText("Number of search");
		numberOfSearch = new Text(gnomParameters, SWT.NONE);
		numberOfSearch.addListener(SWT.Verify, verifyInt);
		GridData searchLayout = new GridData();
		searchLayout.horizontalSpan = 2;
		numberOfSearch.setLayoutData(searchLayout);
		numberOfSearch.setToolTipText("Number of intervals to use for Rmax search");

		new Label(gnomParameters, SWT.NONE).setText("Tolerance");
		tolerance = new Text(gnomParameters, SWT.NONE);
		tolerance.addListener(SWT.Verify, verifyDouble);
		tolerance.setToolTipText("Stopping criterion for Rmax search");

		Group dammifParameters = new Group(dataParameters, SWT.NONE);
		dammifParameters.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 2, 1));
		dammifParameters.setLayout(new GridLayout(2, true));
		dammifParameters.setText("DAMMIF");

		String[] dammifModeOptions = new String[] {"Fast", "Slow"};
		String[] symmetryOptions = new String[30];
		for (int i=1; i< 20; ++i) {
			symmetryOptions[i-1] = "P" + i;
		}
		for (int i=2; i< 13; ++i) {
			symmetryOptions[i + 19 - 2] = "P" + i + "2";
		}

		new Label(dammifParameters, SWT.NONE).setText("Symmetry");
		symmetry = new Combo(dammifParameters, SWT.READ_ONLY);
		symmetry.setItems(symmetryOptions);
		symmetry.setToolTipText("Symmetry of particle in DAMMIF.");
		new Label(dammifParameters, SWT.NONE).setText("Speed");
		dammifMode = new Combo(dammifParameters, SWT.READ_ONLY);
		dammifMode.setItems(dammifModeOptions);
		dammifMode.setToolTipText("Run DAMMIF analysis in either fast or slow modes");

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
		
		window.getSelectionService().addSelectionListener(listener);
		
		resetGUI();
	}
	
	protected void runNcdModelBuilder() {
		//TODO these two lines are for testing purposes only
		modelBuildingParameters= captureGUIInformation();
		System.out.println(modelBuildingParameters);
		
		runNcdModelBuilderPipeline = new RunNcdModelBuilderPipeline();
		runNcdModelBuilderPipeline.runEdnaJob();
	}

	public ModelBuildingParameters getParameters() {
		return modelBuildingParameters;
	}

	private ISelectionListener listener = new ISelectionListener() {
		@Override
		public void selectionChanged(IWorkbenchPart sourcepart, ISelection selection) {
			if (isPathToDataOrPathToQEmpty()) {
				compInput.getDisplay().asyncExec(new Runnable() {
					@Override
					public void run() {
						btnRunNcdModelBuilderJob.setEnabled(false);
					}
				});
				return;
			}
			if (sourcepart != NcdModelBuilderParametersView.this) {
				if (selection instanceof ITreeSelection) {
					ITreeSelection treeSelection = (ITreeSelection) selection;
					if (treeSelection.getFirstElement() instanceof IFile) {
						IFile file = (IFile) treeSelection.getFirstElement();
						for (String suffix : NcdModelBuilderParametersView.DATA_TYPES) {
							if (file.getName().endsWith(suffix)) {
								setDataFilename(file.getLocation().toFile());
								compInput.getDisplay().asyncExec(new Runnable() {
									@Override
									public void run() {
										btnRunNcdModelBuilderJob.setEnabled(true);
									}
								});
							} else
								btnRunNcdModelBuilderJob.setEnabled(false);
						}
					}
					if (treeSelection.getFirstElement() instanceof File) {
						File file = (File) treeSelection.getFirstElement();
						if (file.isFile()) {
							String ending = FileUtils.getFileExtension(file);
							for (String suffix : NcdModelBuilderParametersView.DATA_TYPES) {
								if (ending.equals(suffix)) {
									setDataFilename(file);
									compInput.getDisplay().asyncExec(new Runnable() {
										@Override
										public void run() {
											btnRunNcdModelBuilderJob.setEnabled(true);
										}
									});
								}
							}
						}
					} else
						btnRunNcdModelBuilderJob.setEnabled(false);
				}
			}
		}
	};

	private boolean isPathToDataOrPathToQEmpty() {
		return (pathToData.getText().isEmpty() || pathToQ.getText().isEmpty());
	}

	protected ModelBuildingParameters captureGUIInformation() {
		if (modelBuildingParameters == null)
			modelBuildingParameters = new ModelBuildingParameters();

		//will populate parameters assuming that the Nexus type is being used
		modelBuildingParameters.setPathToQ(pathToQ.getText());
		modelBuildingParameters.setPathToData(pathToData.getText());

		String resultDir = WSParameters.getViewInstance().getResultDirectory();
		modelBuildingParameters.setOutputDir(resultDir);

		modelBuildingParameters.setNumberOfFrames(Integer.valueOf(numberOfFrames.getText()));

		double qMinValue = Double.valueOf(qMin.getText());
		if (qMinUnits.getSelectionIndex() == 1) {
			qMinValue /= 10;
		}
		modelBuildingParameters.setqMinAngstrom(qMinValue);

		
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
		//TODO save the info somewhere? saveCell();
	}
	
	@Override
	public void setFocus() {
		// do nothing here
	}

	public void resetGUI() {
		compInput.getDisplay().asyncExec(new Runnable() {

			@Override
			public void run() {
				numberOfFrames.setText("1");
				qMin.setText("0.1");
				qMinUnits.select(0);
				qMax.setText("0.3");
				qMaxUnits.select(0);
				startPoint.setText("1");
				endPoint.setText("1000");
				numberOfThreads.setText("10");
				builderOptions.select(0);
				minDistanceSearch.setText("20");
				minDistanceUnits.select(0);
				maxDistanceSearch.setText("100");
				maxDistanceUnits.select(0);
				numberOfSearch.setText("10");
				tolerance.setText("0.1");
				symmetry.select(0);
				dammifMode.select(0);
			}
		});
	}
	
	private void setDataFilename(File file) {
		captureGUIInformation();
		modelBuildingParameters.setDataFilename(FileUtils.getParentDirName(file.getAbsolutePath()) + file.getName());
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
}
