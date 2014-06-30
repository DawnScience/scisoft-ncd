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

package uk.ac.diamond.scisoft.ncd.core.rcp;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlRootElement;

import org.eclipse.ui.ISourceProvider;

@XmlRootElement(name = "data_reduction_parameters")
@XmlAccessorType(XmlAccessType.FIELD)
public class NcdSourceProviderAdapter {
	
	private NcdProcessingSourceProvider ncdProcessingSourceProvider;
	private NcdCalibrationSourceProvider ncdCalibrationSourceProvider;
	private SaxsPlotsSourceProvider saxsPlotsSourceProvider;

	public NcdSourceProviderAdapter() {
		super();
		this.setNcdProcessingSourceProvider(null);
		this.setNcdCalibrationSourceProvider(null);
		this.setSaxsPlotsSourceProvider(null);
	}
	
	
	public NcdSourceProviderAdapter(ISourceProvider ncdProcessingSourceProvider,
			ISourceProvider ncdCalibrationSourceProvider,
			ISourceProvider saxsPlotsSourceProvider) {
		super();
		this.setNcdProcessingSourceProvider((NcdProcessingSourceProvider) ncdProcessingSourceProvider);
		this.setNcdCalibrationSourceProvider((NcdCalibrationSourceProvider) ncdCalibrationSourceProvider);
		this.setSaxsPlotsSourceProvider((SaxsPlotsSourceProvider) saxsPlotsSourceProvider);
	}


	public NcdProcessingSourceProvider getNcdProcessingSourceProvider() {
		return ncdProcessingSourceProvider;
	}


	public void setNcdProcessingSourceProvider(NcdProcessingSourceProvider ncdProcessingSourceProvider) {
		this.ncdProcessingSourceProvider = ncdProcessingSourceProvider;
	}


	public NcdCalibrationSourceProvider getNcdCalibrationSourceProvider() {
		return ncdCalibrationSourceProvider;
	}


	public void setNcdCalibrationSourceProvider(NcdCalibrationSourceProvider ncdCalibrationSourceProvider) {
		this.ncdCalibrationSourceProvider = ncdCalibrationSourceProvider;
	}

	
	public SaxsPlotsSourceProvider getSaxsPlotsSourceProvider() {
		return saxsPlotsSourceProvider;
	}


	private void setSaxsPlotsSourceProvider(SaxsPlotsSourceProvider saxsPlotsSourceProvider) {
		this.saxsPlotsSourceProvider = saxsPlotsSourceProvider;
	}
}