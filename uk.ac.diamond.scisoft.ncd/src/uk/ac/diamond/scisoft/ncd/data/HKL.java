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
import java.util.HashMap;

public class HKL implements Serializable {
	private HashMap<String,Integer> hkl;
	
	public HKL(int h, int k, int l) {
		super();
		
		hkl = new HashMap<String, Integer>(3);
		hkl.put("h", h);
		hkl.put("k", k);
		hkl.put("l", l);
	}
	
	public Integer getIndex(String key) {
		return hkl.get(key);
	}
	
	public int[] getIndeces() {
		return new int[] {hkl.get("h"), hkl.get("k"), hkl.get("l")};
	}
	
	public Integer getMaxIndex() {
		return Math.max(Math.max(hkl.get("h"), hkl.get("k")), hkl.get("l"));
	}
	
	public Integer getMinIndex() {
		return Math.min(Math.min(hkl.get("h"), hkl.get("k")), hkl.get("l"));
	}
	
	@Override
	public String toString() {
		String str = String.format("(%d, %d, %d)", hkl.get("h"), hkl.get("k"), hkl.get("l"));
		return str;
	}
}
