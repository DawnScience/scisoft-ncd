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

package uk.ac.diamond.scisoft.ncd.core.data;

public class SliceInput {
	
	private Integer startFrame, stopFrame;
	private String advancedSlice;
	private boolean isAdvanced;
	
	public SliceInput() {
		startFrame = null;
		stopFrame = null;
		advancedSlice = null;
		isAdvanced = false;
	}

	public SliceInput(SliceInput sliceInput) {
		Integer tmpStartFrame = sliceInput.getStartFrame();
		Integer tmpStopFrame = sliceInput.getStopFrame();
		String tmpAdvancedSlice = sliceInput.getAdvancedSlice();
		startFrame = (tmpStartFrame != null) ? new Integer(tmpStartFrame) : null;
		stopFrame = (tmpStopFrame != null) ? new Integer(tmpStopFrame) : null;
		advancedSlice = (tmpAdvancedSlice != null) ? new String(tmpAdvancedSlice) : null;
		isAdvanced = sliceInput.isAdvanced();
	}

	public SliceInput(Integer startFrame, Integer stopFrame) {
		super();
		setStartFrame(startFrame);
		setStopFrame(stopFrame);
		setAdvanced(false);
	}

	public SliceInput(String advancedSlice) {
		super();
		setAdvancedSlice(advancedSlice);
		setAdvanced(true);
	}

	public Integer getStartFrame() {
		return startFrame;
	}
	
	public void setStartFrame(Integer startFrame) {
		this.startFrame = (startFrame != null) ? new Integer(startFrame) : null;
	}
	
	public Integer getStopFrame() {
		return stopFrame;
	}
	
	public void setStopFrame(Integer stopFrame) {
		this.stopFrame = (stopFrame != null) ? new Integer(stopFrame) : null;
	}
	
	public boolean isAdvanced() {
		return isAdvanced;
	}

	public void setAdvanced(boolean isAdvanced) {
		this.isAdvanced = isAdvanced;
	}

	public String getAdvancedSlice() {
		return advancedSlice;
	}
	
	public void setAdvancedSlice(String advancedSlice) {
		this.advancedSlice = (advancedSlice != null) ? new String(advancedSlice) : null;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((advancedSlice == null) ? 0 : advancedSlice.hashCode());
		result = prime * result + (isAdvanced ? 1231 : 1237);
		result = prime * result + ((startFrame == null) ? 0 : startFrame.hashCode());
		result = prime * result + ((stopFrame == null) ? 0 : stopFrame.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		SliceInput other = (SliceInput) obj;
		if (advancedSlice == null) {
			if (other.advancedSlice != null)
				return false;
		} else if (!advancedSlice.equals(other.advancedSlice))
			return false;
		if (isAdvanced != other.isAdvanced)
			return false;
		if (startFrame == null) {
			if (other.startFrame != null)
				return false;
		} else if (!startFrame.equals(other.startFrame))
			return false;
		if (stopFrame == null) {
			if (other.stopFrame != null)
				return false;
		} else if (!stopFrame.equals(other.stopFrame))
			return false;
		return true;
	}
	
}
