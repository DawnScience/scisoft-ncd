package uk.ac.diamond.scisoft.analysis.processing.operations.ncd;

import org.eclipse.dawnsci.analysis.api.processing.model.AbstractOperationModel;
import org.eclipse.dawnsci.analysis.api.processing.model.OperationModelField;

import uk.ac.diamond.scisoft.ncd.core.data.SaxsAnalysisPlotType;

public class SaxsPlotModel extends AbstractOperationModel {

	@OperationModelField(label = "SAXS Tool", hint = "Select the SAXS tool to use")
	private SaxsAnalysisPlotType plotType = SaxsAnalysisPlotType.LOGLOG_PLOT;
	
	public SaxsAnalysisPlotType getPlotType() {
		return plotType;
	}
	
	public void setPlotType(SaxsAnalysisPlotType plotType) {
		firePropertyChange("plotType", this.plotType, this.plotType = plotType);
	}
}
