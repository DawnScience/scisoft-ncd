package uk.ac.diamond.scisoft.analysis.processing.operations.ncd;

import org.eclipse.dawnsci.analysis.api.processing.model.AbstractOperationModel;
import org.eclipse.dawnsci.analysis.api.processing.model.OperationModelField;
import org.eclipse.dawnsci.analysis.dataset.impl.Dataset;

public class NcdDetectorResponseModel extends AbstractOperationModel {

	@OperationModelField(hint="File containing detector response",label = "Response file" )
	public Dataset response;

	public Dataset getResponse() {
		return response;
	}

	public void setResponse(Dataset response) {
		this.response = response;
	}

}
