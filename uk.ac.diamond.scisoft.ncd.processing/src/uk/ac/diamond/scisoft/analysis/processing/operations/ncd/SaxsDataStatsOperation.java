package uk.ac.diamond.scisoft.analysis.processing.operations.ncd;

import org.apache.commons.lang.math.NumberUtils;
import org.eclipse.dawnsci.analysis.api.dataset.IDataset;
import org.eclipse.dawnsci.analysis.api.monitor.IMonitor;
import org.eclipse.dawnsci.analysis.api.processing.AbstractOperation;
import org.eclipse.dawnsci.analysis.api.processing.OperationData;
import org.eclipse.dawnsci.analysis.api.processing.OperationException;
import org.eclipse.dawnsci.analysis.api.processing.OperationRank;
import org.eclipse.dawnsci.analysis.dataset.impl.Dataset;

import uk.ac.diamond.scisoft.ncd.core.data.stats.ClusterOutlierRemoval;
import uk.ac.diamond.scisoft.ncd.core.data.stats.FilterData;
import uk.ac.diamond.scisoft.ncd.core.data.stats.SaxsAnalysisStats;
import uk.ac.diamond.scisoft.ncd.core.data.stats.SaxsAnalysisStatsParameters;
import uk.ac.diamond.scisoft.ncd.core.data.stats.SaxsStatsData;

public class SaxsDataStatsOperation extends AbstractOperation<SaxsDataStatsModel, OperationData> {

	@Override
	public String getId() {
		return "uk.ac.diamond.scisoft.analysis.processing.SaxsDataStats";
	}

	@Override
	public OperationRank getInputRank() {
		return OperationRank.ONE;
	}

	@Override
	public OperationRank getOutputRank() {
		return OperationRank.ONE;
	}

	public OperationData process(IDataset slice, IMonitor monitor) throws OperationException {
		//need to get the Rg from the previous calculation - how do I access the aux data?
		//The following code copied from DataReductionHandler and NcdSaxsDataStatsForkJoinTransformer
		String saxsSelectionAlgorithm = "Data Filter"; //TODO these parameters are from preferences in Irakli's GUI
		String strDBSCANClustererEps = "0.1";
		int dbSCANClustererMinPoints = 0;
		String strSaxsFilteringCI = "0.999";
		
		SaxsAnalysisStatsParameters saxsAnalysisStatParams = new SaxsAnalysisStatsParameters();
		saxsAnalysisStatParams.setSelectionAlgorithm(SaxsAnalysisStats.forName(saxsSelectionAlgorithm));
		if (NumberUtils.isNumber(strDBSCANClustererEps)) {
			saxsAnalysisStatParams.setDbSCANClustererEpsilon(Double.valueOf(strDBSCANClustererEps));
		}
		saxsAnalysisStatParams.setDbSCANClustererMinPoints(dbSCANClustererMinPoints);
		if (NumberUtils.isNumber(strDBSCANClustererEps)) {
			saxsAnalysisStatParams.setSaxsFilteringCI(Double.valueOf(strSaxsFilteringCI));
		}
		
		SaxsAnalysisStats selectedSaxsStat = saxsAnalysisStatParams.getSelectionAlgorithm();
		SaxsStatsData statsData = selectedSaxsStat.getSaxsAnalysisStatsObject();
		if (statsData instanceof FilterData) {
			((FilterData)statsData).setConfigenceInterval(saxsAnalysisStatParams.getSaxsFilteringCI());
		}
		if (statsData instanceof ClusterOutlierRemoval) {
			((ClusterOutlierRemoval)statsData).setDbSCANClustererEpsilon(saxsAnalysisStatParams.getDbSCANClustererEpsilon());
			((ClusterOutlierRemoval)statsData).setDbSCANClustererMinPoints(saxsAnalysisStatParams.getDbSCANClustererMinPoints());
		}

		statsData.setReferenceData((Dataset) slice);
		Dataset mydata = statsData.getStatsData();
		return new OperationData(mydata);
	}
}
