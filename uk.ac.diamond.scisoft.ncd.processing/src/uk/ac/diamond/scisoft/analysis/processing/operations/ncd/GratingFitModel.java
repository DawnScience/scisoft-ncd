package uk.ac.diamond.scisoft.analysis.processing.operations.ncd;

import org.eclipse.dawnsci.analysis.api.processing.model.AbstractOperationModel;
import org.eclipse.dawnsci.analysis.api.processing.model.OperationModelField;

public class GratingFitModel extends AbstractOperationModel {

	@OperationModelField(hint = "Enter the beam centre as a two comma separated values.", label = "Beam Centre", unit = "px")
	private double[] beamCentre;
	@OperationModelField(hint = "Enter the grating spacing in nm.", label = "Grating Spacing", unit = "nm")
	private double spacing = 100;
	@OperationModelField(hint = "Enter the energy of the beam in keV", label = "Beam Energy", unit = "keV")
	private double energy = 12.399775;
	@OperationModelField(hint = "Enter the pixel pitch in m", label = "Pixel Pitch", unit = "m")
	private double pixelPitch = 0.000172;
	
	public double[] getBeamCentre() {
		return this.beamCentre;
	}
	
	public void setBeamCentre(double[] beamCentre) {
		firePropertyChange("beamCentre", this.beamCentre, this.beamCentre = beamCentre);
	}

	public double getSpacing() {
		return this.spacing;
	}
	
	public void setSpacing(double spacing) {
		firePropertyChange("spacing", this.spacing, this.spacing = spacing);
	}
	
	public double getEnergy() {
		return this.energy;
	}
	
	public void setEnergy(double energy) {
		firePropertyChange("energy", this.energy, this.energy = energy);
	}
	
	public double getPixelPitch() {
		return this.pixelPitch;
	}
	
	public void setPixelPitch(double pixelPitch) {
		firePropertyChange("pixelPitch", this.pixelPitch, this.pixelPitch = pixelPitch);
	}
}
