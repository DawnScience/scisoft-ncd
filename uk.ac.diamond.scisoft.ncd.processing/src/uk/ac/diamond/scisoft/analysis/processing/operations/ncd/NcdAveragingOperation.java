package uk.ac.diamond.scisoft.analysis.processing.operations.ncd;

import org.eclipse.dawnsci.analysis.api.dataset.IDataset;
import org.eclipse.dawnsci.analysis.api.dataset.ILazyDataset;
import org.eclipse.dawnsci.analysis.api.monitor.IMonitor;
import org.eclipse.dawnsci.analysis.api.processing.AbstractOperation;
import org.eclipse.dawnsci.analysis.api.processing.IExportOperation;
import org.eclipse.dawnsci.analysis.api.processing.OperationData;
import org.eclipse.dawnsci.analysis.api.processing.OperationException;
import org.eclipse.dawnsci.analysis.api.processing.OperationRank;
import org.eclipse.dawnsci.analysis.api.slice.SliceFromSeriesMetadata;
import org.eclipse.dawnsci.analysis.dataset.impl.AggregateDataset;
import org.eclipse.dawnsci.analysis.dataset.impl.Dataset;
import org.eclipse.dawnsci.analysis.dataset.impl.DatasetUtils;

import uk.ac.diamond.scisoft.analysis.processing.operations.EmptyModel;

public class NcdAveragingOperation extends AbstractOperation<EmptyModel, OperationData> implements IExportOperation {

	private Dataset[] sliceData;
	private ILazyDataset parent;
	private int counter;

	@Override
	public String getId() {
		return "uk.ac.diamond.scisoft.analysis.processing.NcdAveraging";
	}

	protected OperationData process(IDataset input, IMonitor monitor) throws OperationException {
		
		SliceFromSeriesMetadata ssm = getSliceSeriesMetadata(input);
		
		if (ssm == null) throw new OperationException(this, "Pipeline metadata not present!");
		
		if (parent != ssm.getSourceInfo().getParent()) {
			parent = ssm.getSourceInfo().getParent();
			sliceData = null;
			counter = 0;
		}
		
		
		Dataset d = DatasetUtils.cast(input,Dataset.FLOAT64);
		
		if (sliceData == null) {
			sliceData = new Dataset[ssm.getShapeInfo().getTotalSlices()];
			counter = 0;
		}
		
		//first accumulate all data
		sliceData[counter] = d;
		counter++;
		
		if (counter == ssm.getShapeInfo().getTotalSlices()) {

			boolean filteredAveraging = false;
			if (filteredAveraging) {
				//calculate Rg from GuinierPlotData - same as in GuinierOperation
				//filter using method from NcdSaxsDataStatsForkJoinTransformer
			}
			
			//after filtering (if done), do the averaging
			AggregateDataset aggregate = new AggregateDataset(true, sliceData);
			Dataset[] errorData = new Dataset[counter];
			boolean hasError = false;
			for (int i=0; i < counter; ++i) {
				Dataset slice = sliceData[i];
				if (slice.getErrorBuffer() != null) {
					hasError = true;
				}
				errorData[i] = slice.getErrorBuffer();
			}
			AggregateDataset aggregateErrors = new AggregateDataset(true, errorData);
			Dataset out = ((Dataset)aggregate.getSlice()).mean(false, 0);
			Dataset errorSum = null;
			if (hasError) {
				errorSum = (Dataset) ((Dataset)aggregateErrors).sum();
			}
			SliceFromSeriesMetadata outsmm = ssm.clone();
			for (int i = 0; i < ssm.getParent().getRank(); i++) {
				
				if (!outsmm.getShapeInfo().isDataDimension(i)) outsmm.reducedDimensionToSingular(i);
				
			}
			out.setMetadata(outsmm);
			if (hasError) {
				out.setErrorBuffer(errorSum.idivide(counter));
			}
			sliceData = null;
			counter = 0;
			return new OperationData(out);
		}
		
		return null;
	}

	@Override
	public OperationRank getInputRank() {
		return OperationRank.ONE;
	}

	@Override
	public OperationRank getOutputRank() {
		return OperationRank.ONE;
	}

}
