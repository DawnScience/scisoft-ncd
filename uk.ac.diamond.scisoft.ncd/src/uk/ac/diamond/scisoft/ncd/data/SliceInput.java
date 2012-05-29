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
		startFrame = new Integer(sliceInput.getStartFrame());
		stopFrame = new Integer(sliceInput.getStopFrame());
		advancedSlice = new String(sliceInput.getAdvancedSlice());
	}

	public SliceInput(Integer startFrame, Integer stopFrame) {
		super();
		this.startFrame = startFrame;
		this.stopFrame = stopFrame;
	}

	public SliceInput(String advancedSlice) {
		super();
		this.advancedSlice = new String(advancedSlice);
	}

	public Integer getStartFrame() {
		return startFrame;
	}
	
	public void setStartFrame(Integer startFrame) {
		this.startFrame = startFrame;
	}
	
	public Integer getStopFrame() {
		return stopFrame;
	}
	
	public void setStopFrame(Integer stopFrame) {
		this.stopFrame = stopFrame;
	}
	
	public String getAdvancedSlice() {
		return advancedSlice;
	}
	
	public void setAdvancedSlice(String advancedSlice) {
		this.advancedSlice = advancedSlice;
	}
}
