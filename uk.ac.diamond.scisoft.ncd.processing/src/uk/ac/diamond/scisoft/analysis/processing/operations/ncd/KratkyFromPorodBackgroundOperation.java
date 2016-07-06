package uk.ac.diamond.scisoft.analysis.processing.operations.ncd;

import org.eclipse.dawnsci.analysis.api.processing.OperationData;
import org.eclipse.dawnsci.analysis.api.processing.OperationException;
import org.eclipse.dawnsci.analysis.api.processing.OperationRank;
import org.eclipse.dawnsci.analysis.api.processing.model.EmptyModel;
import org.eclipse.dawnsci.analysis.dataset.operations.AbstractOperation;
import org.eclipse.january.IMonitor;
import org.eclipse.january.dataset.Dataset;
import org.eclipse.january.dataset.DatasetException;
import org.eclipse.january.dataset.DatasetUtils;
import org.eclipse.january.dataset.IDataset;
import org.eclipse.january.dataset.Maths;
import org.eclipse.january.metadata.AxesMetadata;
import org.eclipse.january.metadata.internal.AxesMetadataImpl;

import uk.ac.diamond.scisoft.ncd.processing.TParameterMetadata;

public class KratkyFromPorodBackgroundOperation extends
		AbstractOperation<EmptyModel, OperationData> {

	@Override
	public String getId() {
		return "uk.ac.diamond.scisoft.ncd.processing.KratkyFromPorodBackgroundOperation";
	}

	@Override
	public OperationRank getInputRank() {
		return OperationRank.ONE;
	}

	@Override
	public OperationRank getOutputRank() {
		return OperationRank.ONE;
	}

	@Override
	protected OperationData process(IDataset porodInput, IMonitor monitor)
			throws OperationException {

		// The axis is the fourth power of q
		Dataset q4;
		try {
			q4 = DatasetUtils.convertToDataset(porodInput.getFirstMetadata(AxesMetadata.class).getAxis(0)[0].getSlice());
		} catch (DatasetException e) {
			throw new OperationException(this, e);
		}
		Dataset porodData = DatasetUtils.convertToDataset(porodInput);

		// Porod: (q^4, I q^4), Kratky (q, I q^2)
		Dataset q2 = Maths.sqrt(q4);
		Dataset q = Maths.sqrt(q2);
		Dataset kratkyData = Maths.divide(porodData, q2);
		
		// Create the new axes
		AxesMetadataImpl kratkyAxes = new AxesMetadataImpl(1);
		kratkyAxes.addAxis(0, q);
		kratkyData.addMetadata(kratkyAxes);

		// Copy the metadata over
		TParameterMetadata tparam = porodInput.getFirstMetadata(TParameterMetadata.class);
		kratkyData.addMetadata(tparam);
		
		return new OperationData(kratkyData);
	}

}
