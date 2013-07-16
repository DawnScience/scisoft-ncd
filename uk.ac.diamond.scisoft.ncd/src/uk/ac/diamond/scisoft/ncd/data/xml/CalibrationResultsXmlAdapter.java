/*
 * Copyright 2013 Diamond Light Source Ltd.
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

package uk.ac.diamond.scisoft.ncd.data.xml;

import java.util.ArrayList;
import java.util.List;

import javax.measure.quantity.Length;
import javax.measure.unit.Unit;
import javax.measure.unit.UnitFormat;
import javax.xml.bind.annotation.adapters.XmlAdapter;

import org.jscience.physics.amount.Amount;

import uk.ac.diamond.scisoft.analysis.crystallography.ScatteringVector;
import uk.ac.diamond.scisoft.analysis.crystallography.ScatteringVectorOverDistance;
import uk.ac.diamond.scisoft.ncd.data.CalibrationResultsBean;

public class CalibrationResultsXmlAdapter extends XmlAdapter<CalibrationResultsXmlAdapter.CalibrationDataList, CalibrationResultsBean> {
	
	public static class CalibrationDataList {
		
		public List<CalibrationData> entry = new ArrayList<CalibrationData>();
	}
	
	public static class CalibrationData {
		public String detector;
		public String intercept;
		public String gradient;
		public String meanCameraLength;
		public String unit;
	}
	
    @Override
    public CalibrationResultsBean unmarshal( CalibrationDataList value ){
    	CalibrationResultsBean crb = new CalibrationResultsBean();
    	for (CalibrationData data : value.entry) {
    		String detector = data.detector;
    		Amount<ScatteringVectorOverDistance> gradient = Amount.valueOf(data.gradient).to(ScatteringVectorOverDistance.UNIT);
    		Amount<ScatteringVector> intercept = Amount.valueOf(data.intercept).to(ScatteringVector.UNIT);
    		Amount<Length> meanCameraLength = Amount.valueOf(data.meanCameraLength).to(Length.UNIT);
    		Unit<Length> unit = Unit.valueOf(data.unit).asType(Length.class);
    		crb.putCalibrationResult(detector, gradient, intercept, null, meanCameraLength, unit);
    	}
        return crb;
    } 

    @Override
    public CalibrationDataList marshal( CalibrationResultsBean crb ){
    	CalibrationDataList list = new CalibrationDataList();
    	for (String key : crb.keySet()) {
    		CalibrationData data = new CalibrationData();
    		data.detector = key;
    		data.gradient = crb.getGradient(key).toString();
    		data.intercept = crb.getIntercept(key).toString();
    		data.meanCameraLength = crb.getMeanCameraLength(key).toString();
    		data.unit = UnitFormat.getUCUMInstance().format(crb.getUnit(key));
    		list.entry.add(data);
    	}
        return list;
    }
}