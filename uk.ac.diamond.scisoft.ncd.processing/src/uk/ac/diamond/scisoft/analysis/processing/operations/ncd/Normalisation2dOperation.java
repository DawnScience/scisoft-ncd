package uk.ac.diamond.scisoft.analysis.processing.operations.ncd;

import org.eclipse.dawnsci.analysis.api.processing.OperationRank;

public class Normalisation2dOperation extends NormalisationOperation<NormalisationModel> {

	@Override
	public String getId() {
		return "uk.ac.diamond.scisoft.analysis.processing.Normalisation2d";
	}

	@Override
	public OperationRank getInputRank() {
		return OperationRank.TWO;
	}

	@Override
	public OperationRank getOutputRank() {
		return OperationRank.TWO;
	}
}
