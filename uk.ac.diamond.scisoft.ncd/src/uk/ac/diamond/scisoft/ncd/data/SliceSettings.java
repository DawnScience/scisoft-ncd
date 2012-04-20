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

import java.util.Arrays;

public class SliceSettings {
	
	long[] frames;
	int[] start;
	int sliceDim;
	int sliceSize;
	
	public SliceSettings(SliceSettings slice) {
		if (slice != null) {
			this.frames = Arrays.copyOf(slice.getFrames(), slice.getFrames().length);
			this.start = Arrays.copyOf(slice.getStart(), slice.getStart().length);
			this.sliceDim = slice.getSliceDim();
			this.sliceSize = slice.getSliceSize();
		}
	}

	public SliceSettings(long[] frames, int sliceDim, int sliceSize) {
		this.frames = frames;
		this.sliceDim = sliceDim;
		this.sliceSize = sliceSize;
	}

	public long[] getFrames() {
		return frames;
	}
	
	public void setFrames(long[] frames) {
		this.frames = frames;
	}

	public int[] getStart() {
		return start;
	}

	public void setStart(int[] start) {
		this.start = start;
	}

	public int getSliceDim() {
		return sliceDim;
	}

	public void setSliceDim(int sliceDim) {
		this.sliceDim = sliceDim;
	}

	public int getSliceSize() {
		return sliceSize;
	}

	public void setSliceSize(int sliceSize) {
		this.sliceSize = sliceSize;
	}
}
