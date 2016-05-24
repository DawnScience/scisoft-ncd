package uk.ac.diamond.scisoft.analysis.processing.operations.ncd;

import org.eclipse.dawnsci.analysis.api.processing.model.AbstractOperationModel;
import org.eclipse.dawnsci.analysis.api.processing.model.OperationModelField;
import org.eclipse.dawnsci.analysis.api.processing.model.RangeType;

public class KratkyInteractiveModel extends AbstractOperationModel {

	@OperationModelField(rangevalue = RangeType.XRANGE, label = "Numerical integration region", hint = "Two values, start and end, separated by a comma, for example 2,4. The values should match the axis. If you delete the text the range is estimated automatically.")
	private double[] kratkyRange = null;
	@OperationModelField(label = "Get auto-fit limits?", hint = "Select this to fit the limits of the straight line fit automatically")
	private boolean autoFit = true;

	public double[] getKratkyRange() {
		return kratkyRange;
	}
	public void setKratkyRange(double [] kratkyRange) {
		firePropertyChange("kratkyRange", this.kratkyRange, this.kratkyRange = kratkyRange);
	}

	public boolean isAutoFit() {
		return this.autoFit;
	}
	public void setAutoFit(boolean autoFit) {
		firePropertyChange("autoFit", this.autoFit, this.autoFit = autoFit);
	}

}
