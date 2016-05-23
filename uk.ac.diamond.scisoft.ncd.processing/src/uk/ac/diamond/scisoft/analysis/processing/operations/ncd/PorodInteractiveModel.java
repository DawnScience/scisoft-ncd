package uk.ac.diamond.scisoft.analysis.processing.operations.ncd;

import org.eclipse.dawnsci.analysis.api.processing.model.AbstractOperationModel;
import org.eclipse.dawnsci.analysis.api.processing.model.OperationModelField;
import org.eclipse.dawnsci.analysis.api.processing.model.RangeType;

public class PorodInteractiveModel extends AbstractOperationModel {

	@OperationModelField(hint = "Subtract the background B parameter from the output data", label = "Subtract background?")
	private boolean subtractBackground = true;
	@OperationModelField(rangevalue = RangeType.XRANGE, label = "Set high-q⁴ fit region", hint = "Two values, start and end, separated by a comma, for example 2,4. The values should match the axis. If you delete the text the range is estimated automatically.")
	private double[] porodRange = null;
	@OperationModelField(label = "Get auto-fit limits?", hint = "Select this to fit the limits of the straight line fit automatically")
	private boolean autoFit = true;
	
	public boolean isSubtractBackground() {
		return subtractBackground;
	}
	
	public void setSubtractBackground(boolean subBak) {
		firePropertyChange("subtractBackground", this.subtractBackground, this.subtractBackground = subBak);
	}
	
	public double[] getPorodRange() {
		return porodRange;
	}
	public void setPorodRange(double [] porodRange) {
		firePropertyChange("porodRange", this.porodRange, this.porodRange = porodRange);
	}

	public boolean isAutoFit() {
		return this.autoFit;
	}
	public void setAutoFit(boolean autoFit) {
		firePropertyChange("autoFit", this.autoFit, this.autoFit = autoFit);
	}
}
