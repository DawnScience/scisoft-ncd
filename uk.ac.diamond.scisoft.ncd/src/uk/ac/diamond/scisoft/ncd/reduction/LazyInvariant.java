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

package uk.ac.diamond.scisoft.ncd.reduction;

import org.eclipse.core.runtime.jobs.ILock;

import uk.ac.diamond.scisoft.analysis.dataset.AbstractDataset;
import uk.ac.diamond.scisoft.ncd.data.DataSliceIdentifiers;
import uk.ac.diamond.scisoft.ncd.hdf5.HDF5Invariant;

public class LazyInvariant extends LazyDataReduction {

	public static String name = "Invariant";
	
	public AbstractDataset execute(int dim, AbstractDataset data, DataSliceIdentifiers inv_id, DataSliceIdentifiers inv_errors_id, ILock lock) {
		HDF5Invariant reductionStep = new HDF5Invariant("inv", "data");
		reductionStep.setData(data);
		reductionStep.setIDs(inv_id, inv_errors_id);
		
		return reductionStep.writeout(dim, lock);
	}
	
	
}
