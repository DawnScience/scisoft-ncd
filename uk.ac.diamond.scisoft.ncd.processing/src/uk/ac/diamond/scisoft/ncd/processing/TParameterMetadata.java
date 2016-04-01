/*-
 * Copyright 2016 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.ncd.processing;

import org.eclipse.dawnsci.analysis.api.metadata.MetadataType;

/**
 * Metadata to carry the fitted parameters through the NCD/SAXS processing chain 
 * @author Timothy Spain, timothy.spain@diamond.ac.uk
 *
 */
@SuppressWarnings("serial")
public class TParameterMetadata implements MetadataType {

	double qPorodMin, qKratkyMin, porodConstant, kratkyIntegral;
	
	public double getqPorodMin() {
		return qPorodMin;
	}

	public void setqPorodMin(double qPorodMin) {
		this.qPorodMin = qPorodMin;
	}

	public double getqKratkyMin() {
		return qKratkyMin;
	}

	public void setqKratkyMin(double qKratkyMin) {
		this.qKratkyMin = qKratkyMin;
	}

	public double getPorodConstant() {
		return porodConstant;
	}

	public void setPorodConstant(double porodConstant) {
		this.porodConstant = porodConstant;
	}

	public double getKratkyIntegral() {
		return kratkyIntegral;
	}

	public void setKratkyIntegral(double kratkyIntegral) {
		this.kratkyIntegral = kratkyIntegral;
	}

	@Override
	public MetadataType clone() {
		TParameterMetadata clone = new TParameterMetadata();
		clone.kratkyIntegral = this.kratkyIntegral;
		clone.qKratkyMin = this.qKratkyMin;
		clone.porodConstant = this.porodConstant;
		clone.qPorodMin = this.qPorodMin;
		return clone;
	}

}
