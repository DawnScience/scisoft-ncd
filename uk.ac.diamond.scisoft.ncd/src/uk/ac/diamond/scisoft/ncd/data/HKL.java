/*-
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

import java.io.Serializable;
import java.util.Arrays;

public class HKL implements Serializable {
	
	private int[] hkl;
	
	public HKL(int h, int k, int l) {
		super();
		
		hkl = new int[] {h, k, l};
	}
	
	public Integer getH() {
		return hkl[0];
	}
	
	public Integer getK() {
		return hkl[1];
	}
	
	public Integer getL() {
		return hkl[2];
	}
	
	public int[] getIndices() {
		return hkl;
	}
	
	public Integer getMaxIndex() {
		return Math.max(Math.max(getH(), getK()), getL());
	}
	
	public Integer getMinIndex() {
		return Math.min(Math.min(getH(), getK()), getL());
	}
	
	@Override
	public String toString() {
		String str = String.format("(%d, %d, %d)", getH(), getK(), getL());
		return str;
	}
	
	@Override
	public boolean equals(Object input) {
		
		if (this == input) return true;
		
		if (!(input instanceof HKL)) return false;
		
		HKL hklInput = (HKL) input;
		if (!(hklInput.getH().equals(this.getH())))
			return false;
		if (!(hklInput.getK().equals(this.getK())))
			return false;
		if (!(hklInput.getL().equals(this.getL())))
			return false;
		return true;
	}

	@Override
	public int hashCode() {
	    return Arrays.hashCode(getIndices());
	}
}
