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
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.part.ViewPart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.ac.diamond.scisoft.analysis.utils.FileUtils;
import uk.ac.diamond.scisoft.ncd.ModelBuildingParameters;
import uk.ac.diamond.scisoft.ncd.rcp.actions.RunNcdModelBuilderPipeline;
import uk.ac.diamond.scisoft.ws.rcp.WSParameters;

public class NcdModelBuilderParametersView extends ViewPart {
	private static Logger logger = LoggerFactory.getLogger(NcdModelBuilderParametersView.class);

	public static final String ID = "uk.ac.diamond.scisoft.ncd.views.NcdModelBuilderParametersView";

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

	protected Text symmetry;
	private Combo dammifMode;

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

		new Label(dataParameters, SWT.NONE).setText("Number of Frames");
		numberOfFrames = new Text(dataParameters, SWT.NONE);
		numberOfFrames.setToolTipText("Number of data columns to use in analysis");

		Group qParameters = new Group(dataParameters, SWT.NONE);
		GridData qLayout = new GridData(SWT.FILL, SWT.TOP, true, false);
		qLayout.horizontalSpan = 2;
		qParameters.setLayoutData(qLayout);
		qParameters.setLayout(new GridLayout(3, false));
		qParameters.setText("q");

		String[] qOptionUnits = new String[] {"Angstrom-1", "nm-1"};

		new Label(qParameters, SWT.NONE).setText("q minimum");
		qMin = new Text(qParameters, SWT.NONE);
		qMin.setToolTipText("Minimum q value to be used for GNOM/DAMMIF");
		qMinUnits = new Combo(qParameters, SWT.NONE);
		qMinUnits.setItems(qOptionUnits);
		new Label(qParameters, SWT.NONE).setText("q maximum");
		qMax = new Text(qParameters, SWT.NONE);
		qMax.setToolTipText("Maximum q value to be used for GNOM/DAMMIF");
		qMaxUnits = new Combo(qParameters, SWT.NONE);
		qMaxUnits.setItems(qOptionUnits);

		Group pointsParameters = new Group(dataParameters, SWT.NONE);
		GridData pointsLayout = new GridData(SWT.FILL, SWT.TOP, true, false);
		pointsLayout.horizontalSpan = 2;
		pointsParameters.setLayoutData(pointsLayout);
		pointsParameters.setLayout(new GridLayout(2, true));
		pointsParameters.setText("Points");

		new Label(pointsParameters, SWT.NONE).setText("First point");
		startPoint = new Text(pointsParameters, SWT.NONE);
		new Label(pointsParameters, SWT.NONE).setText("Last point");
		endPoint = new Text(pointsParameters, SWT.NONE);

		new Label(dataParameters, SWT.NONE).setText("Number of threads");
		numberOfThreads = new Text(dataParameters, SWT.NONE);
		numberOfThreads.setToolTipText("The maximum number of threads to be used for DAMMIF");

		String[] builderOptionsNames = new String[]{"GNOM", "GNOM+DAMMIF"};
		builderOptions = new Combo(dataParameters, SWT.NONE);
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
		minDistanceUnits = new Combo(gnomParameters, SWT.NONE);
		minDistanceUnits.setItems(distanceOptionsUnits);

		new Label(gnomParameters, SWT.NONE).setText("Maximum distance search");
		maxDistanceSearch = new Text(gnomParameters, SWT.NONE);
		minDistanceSearch.setToolTipText("Final value for Rmax to perform search");
		maxDistanceUnits = new Combo(gnomParameters, SWT.NONE);
		maxDistanceUnits.setItems(distanceOptionsUnits);

		new Label(gnomParameters, SWT.NONE).setText("Number of search");
		numberOfSearch = new Text(gnomParameters, SWT.NONE);
		GridData searchLayout = new GridData();
		searchLayout.horizontalSpan = 2;
		numberOfSearch.setLayoutData(searchLayout);
		numberOfSearch.setToolTipText("Number of intervals to use for Rmax search");

		new Label(gnomParameters, SWT.NONE).setText("Tolerance");
		tolerance = new Text(gnomParameters, SWT.NONE);
		tolerance.setToolTipText("Stopping criterion for Rmax search");

		Group dammifParameters = new Group(dataParameters, SWT.NONE);
		dammifParameters.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 2, 1));
		dammifParameters.setLayout(new GridLayout(2, true));
		dammifParameters.setText("DAMMIF");

		String[] dammifModeOptions = new String[] {"Fast", "Slow"};

		new Label(dammifParameters, SWT.NONE).setText("Symmetry");
		symmetry = new Text(dammifParameters, SWT.NONE);
		symmetry.setToolTipText("Symmetry of particle in DAMMIF. Must be Pn where n=1-19 or Pn2 where n=1-12.");
		dammifMode = new Combo(dammifParameters, SWT.NONE);
		dammifMode.setItems(dammifModeOptions);

		//TODO add option to restore default parameters
//		btnResetParam = new Button(controls, SWT.NONE);
//		btnResetParam.setText("Clear Sweep Parameters");
//		btnResetParam.setLayoutData(new GridData(GridData.FILL, SWT.CENTER, true, false));
//		btnResetParam.addSelectionListener(new SelectionAdapter() {
//			@Override
//			public void widgetSelected(SelectionEvent e) {
//				resetGUI();
//			}
//		});

		btnRunNcdModelBuilderJob = new Button(dataParameters, SWT.NONE);
		btnRunNcdModelBuilderJob.setText("Run Data Processing On Selected Sweep");
		btnRunNcdModelBuilderJob.setLayoutData(new GridData(GridData.FILL, SWT.CENTER, true, false));
		btnRunNcdModelBuilderJob.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				//TODO implement this late runNcdModelBuilder();
			}
		});
		btnRunNcdModelBuilderJob.setEnabled(false);
		
		
		window.getSelectionService().addSelectionListener(listener);
	}
	
	private ISelectionListener listener = new ISelectionListener() {
		@Override
		public void selectionChanged(IWorkbenchPart sourcepart, ISelection selection) {

			if (sourcepart != NcdModelBuilderParametersView.this) {
				if (selection instanceof ITreeSelection) {
					ITreeSelection treeSelection = (ITreeSelection) selection;
					if (treeSelection.getFirstElement() instanceof IFile) {
						IFile file = (IFile) treeSelection.getFirstElement();
						for (String suffix : NcdModelBuilderParametersView.DATA_TYPES) {
							if (file.getName().endsWith(suffix)) {
								//TODO fix the following so that the data type is correct
//								createmodelBuildingParameters(file.getName(), file.getLocation().removeLastSegments(1).toString());
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
									//TODO not sure if this is needed
//									createmodelBuildingParameters(file.getName(),FileUtils.getParentDirName(file.getAbsolutePath()));
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


	protected void captureGUIInformation() {
		if (modelBuildingParameters == null)
			modelBuildingParameters = new ModelBuildingParameters();

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
				numberOfFrames.setText("");
				qMin.setText("");
				qMinUnits.select(0);
				qMax.setText("");
				qMaxUnits.select(0);
				startPoint.setText("");
				endPoint.setText("");
				numberOfThreads.setText("");
				builderOptions.select(0);
				minDistanceSearch.setText("");
				minDistanceUnits.select(0);
				maxDistanceSearch.setText("");
				maxDistanceUnits.select(0);
				numberOfSearch.setText("");
				tolerance.setText("");
				symmetry.setText("P1");
				dammifMode.select(0);
			}
		});
	}
}
