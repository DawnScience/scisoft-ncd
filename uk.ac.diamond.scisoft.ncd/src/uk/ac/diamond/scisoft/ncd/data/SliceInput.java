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

package uk.ac.diamond.scisoft.ncd.data;

public class SliceInput {
	
	private Integer startFrame, stopFrame;
	private String advancedSlice;
	
	public SliceInput(SliceInput sliceInput) {
		Integer tmpStartFrame = sliceInput.getStartFrame();
		Integer tmpStopFrame = sliceInput.getStopFrame();
		String tmpAdvancedSlice = sliceInput.getAdvancedSlice();
		startFrame = (tmpStartFrame != null) ? new Integer(tmpStartFrame) : null;
		stopFrame = (tmpStopFrame != null) ? new Integer(tmpStopFrame) : null;
		advancedSlice = (tmpAdvancedSlice != null) ? new String(tmpAdvancedSlice) : null;
	}

	public SliceInput(Integer startFrame, Integer stopFrame) {
		super();
		this.startFrame = (startFrame != null) ? new Integer(startFrame) : null;
		this.stopFrame = (stopFrame != null) ? new Integer(stopFrame) : null;
	}

	public SliceInput(String advancedSlice) {
		super();
		this.advancedSlice = (advancedSlice != null) ? new String(advancedSlice) : null;
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
	
	public String getAdvancedSlice() {
		return advancedSlice;
	}
	
	public void setAdvancedSlice(String advancedSlice) {
		this.advancedSlice = (advancedSlice != null) ? new String(advancedSlice) : null;
	}
}
