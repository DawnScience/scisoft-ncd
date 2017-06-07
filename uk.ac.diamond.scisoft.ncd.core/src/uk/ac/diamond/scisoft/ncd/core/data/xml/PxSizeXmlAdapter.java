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

import javax.measure.Unit;
import javax.measure.quantity.Length;
import javax.xml.bind.annotation.adapters.XmlAdapter;

import si.uom.SI;
import si.uom.NonSI;

import org.jscience.physics.amount.Amount;

public class PxSizeXmlAdapter extends XmlAdapter<String, Amount<Length>> {
	
	private Unit<Length> unit = SI.MILLIMETER;
	
    @Override
    public Amount<Length> unmarshal( String value ){
		// JScience can't parse brackets
		value = value.replace("(", "").replace(")", "");
        return Amount.valueOf(value).to(unit);
    } 

    @Override
    public String marshal( Amount<Length> pxSize ){
        return pxSize.to(unit).toString();
    }
}