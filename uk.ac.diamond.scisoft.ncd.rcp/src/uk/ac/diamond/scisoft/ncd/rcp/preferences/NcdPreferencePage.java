/*
 * Copyright 2012 Diamond Light Source Ltd.
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

package uk.ac.diamond.scisoft.ncd.rcp.preferences;

import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.IntegerFieldEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.ControlAdapter;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import uk.ac.diamond.scisoft.ncd.preferences.NcdPreferences;
import uk.ac.diamond.scisoft.ncd.rcp.Activator;

public class NcdPreferencePage  extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {
	
	public NcdPreferencePage() {
		super(GRID);

	}
	
	@Override
	public void init(IWorkbench workbench) {
		setPreferenceStore(Activator.getDefault().getPreferenceStore());
		setDescription("Parameters used in NCD Data Reduction procedure");
	}

	@Override
	protected void createFieldEditors() {
		final ScrolledComposite sc = new ScrolledComposite(getFieldEditorParent(), SWT.VERTICAL);
		sc.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		final Composite c = new Composite(sc, SWT.NONE);
		c.setLayout( new GridLayout(1, false));
		Group g = new Group(c, SWT.BORDER);
		g.setLayout(new GridLayout(1, false));
		g.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		g.setText("Beam position optimiser settings");
		{
			final Composite gc = new Composite(g,  SWT.NONE);
			gc.setLayout(new GridLayout(1, false));
			gc.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
			addField(new IntegerFieldEditor(NcdPreferences.CMAESlambda, "Population size", gc));
			addField(new IntegerFieldEditor(NcdPreferences.CMAESsigma, "Initial search volume", gc));
			addField(new IntegerFieldEditor(NcdPreferences.CMAESmaxiteration, "Maximal number of iterations", gc));
			addField(new IntegerFieldEditor(NcdPreferences.CMAESchecker, "Convergence order", gc));
		}
		
		sc.setContent(c);
		sc.setExpandVertical(true);
		sc.setExpandHorizontal(true);
		sc.addControlListener(new ControlAdapter() {
			@Override
			public void controlResized(ControlEvent e) {
				Rectangle r = sc.getClientArea();
				sc.setMinSize(c.computeSize(r.width, SWT.DEFAULT));
			}
		});
		
	}	
}
