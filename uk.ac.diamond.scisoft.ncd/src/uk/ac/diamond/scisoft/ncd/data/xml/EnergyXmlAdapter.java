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

import javax.measure.quantity.Energy;
import javax.measure.unit.NonSI;
import javax.measure.unit.SI;
import javax.measure.unit.Unit;
import javax.xml.bind.annotation.adapters.XmlAdapter;

import org.jscience.physics.amount.Amount;

public class EnergyXmlAdapter extends XmlAdapter<String, Amount<Energy>> {
	
	private Unit<Energy> unit = SI.KILO(NonSI.ELECTRON_VOLT);
	
    @Override
    public Amount<Energy> unmarshal( String value ){
        return Amount.valueOf(value).to(unit);
    } 

    @Override
    public String marshal( Amount<Energy> energy ){
        return energy.to(unit).toString();
    }
}