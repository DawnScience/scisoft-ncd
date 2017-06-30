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

package uk.ac.diamond.scisoft.ncd.data.xml;

import javax.measure.Quantity;
import javax.measure.Unit;
import javax.measure.quantity.Energy;
import javax.xml.bind.annotation.adapters.XmlAdapter;

import si.uom.NonSI;
import tec.units.ri.quantity.Quantities;
import tec.units.ri.unit.MetricPrefix;

public class EnergyXmlAdapter extends XmlAdapter<String, Quantity<Energy>> {
	
	private Unit<Energy> unit = MetricPrefix.KILO(NonSI.ELECTRON_VOLT);
	
    @Override
    public Quantity<Energy> unmarshal( String value ){
		// JScience can't parse brackets
		value = value.replace("(", "").replace(")", "");
        return Quantities.getQuantity(Quantities.getQuantity(value).getValue().doubleValue(), unit);
    } 

    @Override
    public String marshal(Quantity<Energy> energy ){
        return energy.to(unit).toString();
    }
}