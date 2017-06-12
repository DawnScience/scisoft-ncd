/*
 * Copyright 2013, 2017 Diamond Light Source Ltd.
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

package uk.ac.diamond.scisoft.ncd.core.data.xml;

import javax.measure.Quantity;
import javax.measure.Unit;
import javax.measure.quantity.Length;
import javax.xml.bind.annotation.adapters.XmlAdapter;

import tec.units.ri.quantity.Quantities;
import tec.units.ri.unit.MetricPrefix;
import tec.units.ri.unit.Units;

public class PxSizeXmlAdapter extends XmlAdapter<String, Quantity<Length>> {
	
	private Unit<Length> unit = MetricPrefix.MILLI(Units.METRE);
	
    @Override
    public Quantity<Length> unmarshal( String value ){
		// JScience can't parse brackets
		value = value.replace("(", "").replace(")", "");
        return Quantities.getQuantity(Double.valueOf(value), unit);
    } 

    @Override
    public String marshal( Quantity<Length> pxSize ){
        return pxSize.to(unit).toString();
    }
}