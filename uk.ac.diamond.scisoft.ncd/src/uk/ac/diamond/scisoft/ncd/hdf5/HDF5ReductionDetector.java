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

package uk.ac.diamond.scisoft.ncd.hdf5;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.ArrayUtils;
import org.eclipse.january.dataset.AbstractDataset;
import org.eclipse.january.dataset.Dataset;
import org.eclipse.january.dataset.DatasetUtils;
import org.eclipse.january.dataset.DoubleDataset;
import org.eclipse.january.dataset.IDataset;

import uk.ac.diamond.scisoft.ncd.core.data.DataSliceIdentifiers;
import uk.ac.diamond.scisoft.ncd.core.data.DetectorTypes;

public class HDF5ReductionDetector {

	protected Dataset data;
	
	private String name;
	protected String key;
	protected Dataset qAxis;
	protected String qAxisUnit;
	protected DataSliceIdentifiers ids, errIds;
	
	protected String detectorType = DetectorTypes.REDUCTION_DETECTOR;
	protected double pixelSize = 0.0;
	protected Map<String, Object> attributeMap = new HashMap<String, Object>();
	public static final String descriptionLabel = "description";
	protected String description;
	protected Dataset mask = null;
	
	public HDF5ReductionDetector(String name, String key) {
		this.key = key;
		this.name = name;
		ids = new DataSliceIdentifiers();
		errIds = new DataSliceIdentifiers();
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	public void setData(Dataset ds) {
		data = ds;
		if (!ds.hasErrors()) {
			data.setErrorBuffer(ds.copy(DoubleDataset.class));
		}
	}

	public void setIDs(DataSliceIdentifiers input_id, DataSliceIdentifiers error_id) {
		ids = new DataSliceIdentifiers(input_id);
		errIds = new DataSliceIdentifiers(error_id);
	}
	
	public String getName() {
		return name;
	}
	
	public String getDetectorType() {
		return detectorType;
	}

	public int[] getDataDimensions() {
		return AbstractDataset.squeezeShape(data.getShape(), false);
	}
	
	public void setAttribute(String attributeName, Object value) {
		if (descriptionLabel.equals(attributeName)) {
			description = (String) value;
		} else if (value != null) {
			attributeMap.put(attributeName, value);
		} else if (attributeMap.containsKey(attributeName)) {
			attributeMap.remove(attributeName);
		}
	}

	public Object getAttribute(String attributeName) {
		if (descriptionLabel.equals(attributeName)) {
			return description;
		} else if (attributeMap.containsKey(attributeName)) {
			return attributeMap.get(attributeName);
		}
		return null;
	}

	public double getPixelSize() {
		return pixelSize;
	}

	public void setPixelSize(double pixelSize) {
		this.pixelSize = pixelSize;
	}
	

	public Dataset getMask() {
		return mask;
	}

	public void setMask(Dataset mask) {
		if (mask == null || ArrayUtils.isEquals(mask.getShape(), getDataDimensions())) {
			this.mask = mask;
		}
	}
	
	@Override
	public String toString() {
		return "Reduction Detector "+getName()+" of class "+getClass()+" working on "+key;
	}
	
	public void setqAxis(IDataset qAxis, String unit) {
		this.qAxis = DatasetUtils.convertToDataset(qAxis);
		this.qAxisUnit = unit;
	}

	public Dataset getqAxis() {
		return qAxis;
	}
	
	protected Dataset flattenGridData(Dataset dataset, int dimension) {
		
		int dataRank = dataset.getRank();
		int[] dataShape = dataset.getShape();
		if (dataRank > (dimension + 1)) {
			int[] frameArray = Arrays.copyOf(dataShape, dataRank - dimension);
			int totalFrames = 1;
			for (int val : frameArray) {
				totalFrames *= val;
			}
			int[] newShape = Arrays.copyOfRange(dataShape, dataRank - dimension - 1, dataRank);
			newShape[0] = totalFrames;
			return dataset.reshape(newShape);
		}
		return dataset;
	}
}
