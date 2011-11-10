/*-
 * Copyright © 2011 Diamond Light Source Ltd.
 *
 * This file is part of GDA.
 *
 * GDA is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License version 3 as published by the Free
 * Software Foundation.
 *
 * GDA is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along
 * with GDA. If not, see <http://www.gnu.org/licenses/>.
 */

package uk.ac.diamond.scisoft.ncd.rcp.reduction;

import gda.device.detector.NXDetectorData;

import org.eclipse.core.runtime.IProgressMonitor;
import org.nexusformat.NexusFile;

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;

public abstract class LazyDataReduction {

	protected String activeDataset;
	protected int[] frames;
	protected int firstFrame;
	protected int lastFrame;
	protected int gridDim;
	protected NexusFile nxsFile;
	protected AbstractDataset qaxis;
	protected int frameBatch;
	protected String detector;
	protected String calibration;
	protected int normChannel;

	public LazyDataReduction(String activeDataset, int[] frames, int frameBatch, NexusFile nxsFile) {
		this.activeDataset = activeDataset;
		this.frames = frames;
		this.gridDim = frames.length;
		this.nxsFile = nxsFile;
		this.frameBatch = frameBatch;
	}
	
	public void setDetector(String detector) {
		this.detector = detector;
	}

	public void setCalibration(String calibration) {
		this.calibration = calibration;
	}

	public void setNormChannel(int normChannel) {
		this.normChannel = normChannel;
	}

	public void setQaxis(AbstractDataset qaxis) {
		this.qaxis = qaxis;
	}

	public String getActiveDataset() {
		return activeDataset;
	}

	public void setFirstFrame(Integer firstFrame, int dim) {
		if (firstFrame == null) {
			this.firstFrame = 0;
			return;
		}
		if ((firstFrame < 0) || (firstFrame > frames[gridDim - dim - 1] - 1))
			this.firstFrame = 0;
		else
			this.firstFrame = firstFrame;
	}

	public void setLastFrame(Integer lastFrame, int dim) {
		if (lastFrame == null) {
			this.lastFrame = frames[gridDim - dim - 1] - 1;
			return;
		}
		if ((lastFrame < 0) || (lastFrame > frames[gridDim - dim - 1] - 1))
			this.lastFrame = frames[gridDim - dim - 1] - 1;
		else
			this.lastFrame = lastFrame;
	}

	public abstract void execute(NXDetectorData tmpNXdata, int dim, IProgressMonitor monitor) throws Exception;	
}
