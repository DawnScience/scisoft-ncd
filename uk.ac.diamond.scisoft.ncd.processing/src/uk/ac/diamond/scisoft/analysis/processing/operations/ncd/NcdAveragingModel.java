package uk.ac.diamond.scisoft.analysis.processing.operations.ncd;

import org.eclipse.dawnsci.analysis.api.processing.model.AbstractOperationModel;
import org.eclipse.dawnsci.analysis.api.processing.model.OperationModelField;

public class NcdAveragingModel extends AbstractOperationModel {
	@OperationModelField(hint="Use Rg-based filtering to remove bad frames before averaging", label = "Use Rg filtering" )
	private boolean useFiltering = false;

	public boolean isUseFiltering() {
		return useFiltering;
	}

	public void setUseFiltering(boolean useFiltering) {
		this.useFiltering = useFiltering;
	}

}
