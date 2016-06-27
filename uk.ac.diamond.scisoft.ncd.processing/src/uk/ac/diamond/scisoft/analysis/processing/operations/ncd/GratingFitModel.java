package uk.ac.diamond.scisoft.analysis.processing.operations.ncd;

import org.eclipse.dawnsci.analysis.api.processing.model.AbstractOperationModel;
import org.eclipse.dawnsci.analysis.api.processing.model.OperationModelField;

public class GratingFitModel extends AbstractOperationModel {

	@OperationModelField(hint = "Enter the beam centre as a two comma separated values.", label = "Beam Centre")
	private int[] beamCentre;
	
	public int[] getBeamCentre() {
		return this.beamCentre;
	}
	
	public void setBeamCentre(int[] beamCentre) {
		firePropertyChange("beamCentre", this.beamCentre, this.beamCentre = beamCentre);
	}
	
}
