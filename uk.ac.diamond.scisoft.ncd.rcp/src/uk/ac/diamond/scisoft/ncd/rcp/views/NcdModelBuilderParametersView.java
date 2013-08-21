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

import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.part.ViewPart;

import uk.ac.diamond.scisoft.ncd.rcp.actions.RunNcdModelBuilderPipeline;

public class NcdModelBuilderParametersView extends ViewPart {

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

	private RunNcdModelBuilderPipeline runNcdModelBuilderPipeline;
	
	@Override
	public void createPartControl(Composite parent) {

	}

	@Override
	public void setFocus() {
		// TODO Auto-generated method stub

	}

}
