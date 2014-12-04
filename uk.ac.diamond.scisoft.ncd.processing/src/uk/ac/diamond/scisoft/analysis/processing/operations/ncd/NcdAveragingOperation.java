package uk.ac.diamond.scisoft.analysis.processing.operations.ncd;

import java.util.ArrayList;
import java.util.List;

import javax.measure.quantity.Dimensionless;

import org.apache.commons.lang.math.NumberUtils;
import org.eclipse.core.runtime.Platform;
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
import org.eclipse.dawnsci.analysis.dataset.impl.DoubleDataset;
import org.jscience.physics.amount.Amount;

import uk.ac.diamond.scisoft.analysis.processing.operations.EmptyModel;
import uk.ac.diamond.scisoft.ncd.core.data.stats.SaxsAnalysisStats;
import uk.ac.diamond.scisoft.ncd.core.data.stats.SaxsAnalysisStatsParameters;
import uk.ac.diamond.scisoft.ncd.preferences.NcdPreferences;
import uk.ac.diamond.scisoft.ncd.processing.NcdOperationUtils;

public class NcdAveragingOperation extends AbstractOperation<EmptyModel, OperationData> implements IExportOperation {

	private Dataset[] sliceData;
	private ILazyDataset parent;
	private int counter;

	@Override
	public String getId() {
		return "uk.ac.diamond.scisoft.analysis.processing.NcdAveraging";
	}

	@SuppressWarnings("unchecked")
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
				double[] rG = new double[counter];
				for (int i=0; i < counter; ++i) {
					try {
						Object[] guinierParams = NcdOperationUtils.getGuinierPlotParameters(sliceData[i]);
						rG[i] = ((Amount<Dimensionless>)guinierParams[1]).getEstimatedValue();
					} catch (Exception e) {
						throw new OperationException(this, "Exception during Guinier calculation" + e);
					}
					
				}
				//filter using method from NcdSaxsDataStatsForkJoinTransformer - this part from NcdDataReductionTransformer
				String saxsSelectionAlgorithm = Platform.getPreferencesService().getString("uk.ac.diamond.scisoft.ncd.rcp",
						NcdPreferences.SAXS_SELECTION_ALGORITHM,
						SaxsAnalysisStatsParameters.DEFAULT_SELECTION_METHOD.getName(), null);
				String strDBSCANClustererEps = Platform.getPreferencesService().getString("uk.ac.diamond.scisoft.ncd.rcp",
						NcdPreferences.DBSCANClusterer_EPSILON, Double.toString(SaxsAnalysisStatsParameters.DBSCAN_CLUSTERER_EPSILON), null);
				int dbSCANClustererMinPoints = Platform.getPreferencesService().getInt("uk.ac.diamond.scisoft.ncd.rcp",
						NcdPreferences.DBSCANClusterer_MINPOINTS, SaxsAnalysisStatsParameters.DBSCAN_CLUSTERER_MINPOINTS, null);
				String strSaxsFilteringCI = Platform.getPreferencesService().getString("uk.ac.diamond.scisoft.ncd.rcp",
						NcdPreferences.SAXS_FILTERING_CI, Double.toString(SaxsAnalysisStatsParameters.SAXS_FILTERING_CI), null);
				SaxsAnalysisStatsParameters saxsAnalysisStatParams = new SaxsAnalysisStatsParameters();
				saxsAnalysisStatParams.setSelectionAlgorithm(SaxsAnalysisStats.forName(saxsSelectionAlgorithm));
				if (NumberUtils.isNumber(strDBSCANClustererEps)) {
					saxsAnalysisStatParams.setDbSCANClustererEpsilon(Double.valueOf(strDBSCANClustererEps));
				}
				saxsAnalysisStatParams.setDbSCANClustererMinPoints(dbSCANClustererMinPoints);
				if (NumberUtils.isNumber(strDBSCANClustererEps)) {
					saxsAnalysisStatParams.setSaxsFilteringCI(Double.valueOf(strSaxsFilteringCI));
				}
				
				DoubleDataset rgDataset = new DoubleDataset(rG);
				Dataset filter = NcdOperationUtils.getSaxsAnalysisStats(rgDataset, saxsAnalysisStatParams);
				
				List<Dataset> filteredDataset = new ArrayList<Dataset>();
				for (int i=0; i < counter; ++i) {
					if (filter.getBoolean(i) == true) {
						filteredDataset.add(sliceData[i]);
					}
				}
				sliceData = new Dataset[filteredDataset.size()];
				sliceData = filteredDataset.toArray(sliceData);
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

			Dataset errorSum = null;
			if (hasError) {
				AggregateDataset aggregateErrors = new AggregateDataset(true, errorData);
				errorSum = (Dataset) ((Dataset)aggregateErrors).sum();
			}

			Dataset out = ((Dataset)aggregate.getSlice()).mean(false, 0);
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
