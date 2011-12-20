/*
 * Copyright 2011 Diamond Light Source Ltd.
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

import java.util.ArrayList;

import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;

import uk.ac.diamond.scisoft.analysis.plotserver.CalibrationPeak;

public class CalibrationTable {
	private TableViewer tViewer;
	private String[] nameList = { "Peak Position (mm from BS)", "Two Theta (deg)", "d Spacing (nm)", "Index (hkl)" };
	private int[] widths = { 150, 150, 150, 150 };


	public CalibrationTable(Composite parent) {
		
		tViewer = new TableViewer(parent, SWT.BORDER | SWT.SINGLE | SWT.V_SCROLL | SWT.FILL);

		for (int i = 0; i < nameList.length; i++) {
			TableViewerColumn tVCol = new TableViewerColumn(tViewer, SWT.NONE);
			TableColumn tCol = tVCol.getColumn();
			tCol.setText(nameList[i]);
			tCol.setWidth(widths[i]);
			tCol.setMoveable(true);
		}

		final Table table = tViewer.getTable();
		table.setHeaderVisible(true);
		table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		tViewer.setContentProvider(new ArrayContentProvider());
		tViewer.setLabelProvider(new CalibrationLabelProvider());

	}

	final public void setInput(ArrayList<CalibrationPeak> cpl) {
		tViewer.setInput(cpl);
	}

	/**
	 * Refresh table viewer
	 */
	final public void refresh() {
		tViewer.refresh();
	}
}

class CalibrationLabelProvider implements ITableLabelProvider {

	@Override
	public void addListener(ILabelProviderListener listener) {
		// TODO Auto-generated method stub

	}

	@Override
	public void dispose() {
		// TODO Auto-generated method stub

	}

	@Override
	public boolean isLabelProperty(Object element, String property) {

		return false;
	}

	@Override
	public void removeListener(ILabelProviderListener listener) {
		// TODO Auto-generated method stub

	}

	@Override
	public Image getColumnImage(Object element, int columnIndex) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getColumnText(Object element, int columnIndex) {
		String msg = null;
		CalibrationPeak cal = (CalibrationPeak) element;
		if (cal != null) {
			switch (columnIndex) {
			case 0:
				msg = String.format("%.2f",cal.getPeakPos());
				break;
			case 1:
				msg = String.format("%.2f",cal.getTwoTheta()*(180/Math.PI));
				break;
			case 2:
				msg = String.format("%.2f",cal.getDSpacing());
				break;
			case 3:
				Integer h = cal.getIndex("h");
				Integer k = cal.getIndex("k");
				Integer l = cal.getIndex("l");
				msg = String.format("(%d %d %d)", h, k, l);
				break;
			}
		}
		return msg;
	}
}
