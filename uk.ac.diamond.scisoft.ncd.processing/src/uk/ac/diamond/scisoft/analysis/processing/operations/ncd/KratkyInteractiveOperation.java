package uk.ac.diamond.scisoft.analysis.processing.operations.ncd;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.dawnsci.analysis.api.dataset.IDataset;
import org.eclipse.dawnsci.analysis.api.metadata.AxesMetadata;
import org.eclipse.dawnsci.analysis.api.monitor.IMonitor;
import org.eclipse.dawnsci.analysis.api.processing.OperationData;
import org.eclipse.dawnsci.analysis.api.processing.OperationException;
import org.eclipse.dawnsci.analysis.api.processing.OperationRank;
import org.eclipse.dawnsci.analysis.api.processing.PlotAdditionalData;
import org.eclipse.dawnsci.analysis.dataset.impl.Dataset;
import org.eclipse.dawnsci.analysis.dataset.impl.DatasetUtils;
import org.eclipse.dawnsci.analysis.dataset.impl.DoubleDataset;
import org.eclipse.dawnsci.analysis.dataset.impl.IndexIterator;
import org.eclipse.dawnsci.analysis.dataset.impl.Maths;
import org.eclipse.dawnsci.analysis.dataset.metadata.AxesMetadataImpl;
import org.eclipse.dawnsci.analysis.dataset.operations.AbstractOperation;

import uk.ac.diamond.scisoft.ncd.processing.NcdOperationUtils;
import uk.ac.diamond.scisoft.ncd.processing.NcdOperationUtils.KratkyParameters;
import uk.ac.diamond.scisoft.ncd.processing.TParameterMetadata;

@PlotAdditionalData(onInput = false, dataName = "Kratky fit")
public class KratkyInteractiveOperation extends
		AbstractOperation<KratkyInteractiveModel, OperationData> {

	@Override
	public String getId() {
		// TODO Auto-generated method stub
		return "uk.ac.diamond.scisoft.ncd.processing.KratkyInteractiveOperation";
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
	protected OperationData process(IDataset input, IMonitor monitor)
			throws OperationException {

		Dataset q = DatasetUtils.convertToDataset(input.getFirstMetadata(AxesMetadata.class).getAxis(0)[0].getSlice());
	
		Dataset x = q;
		Dataset y = DatasetUtils.convertToDataset(input);
		
		// Get the smoothing size to use on the derivative. This is the
		// distance between 0.025 per angstrom and 0.05 per angstrom
		int lowIndex = 0, highIndex = 0;
		final double lowLimit = 0.025, highLimit = 0.05;
		IndexIterator iter = q.getIterator();
		iter.hasNext(); // step along
		while (iter.hasNext()) {
			double thisQ = q.getElementDoubleAbs(iter.index);
			double prevQ = q.getElementDoubleAbs(iter.index-1);
			if (thisQ >= lowLimit && prevQ < lowLimit)
				lowIndex = iter.index;
			if (thisQ >= highLimit && prevQ < highLimit)
				highIndex = iter.index;
		}
		int smoothing = (int) Math.ceil((highIndex - lowIndex)/10.0)*10;

		// First Derivative. Smooth over 0.025 or so to get the general shape of
		// the curve
		Dataset dy_dx = Maths.derivative(x, y, smoothing);

		// Index of the point from which the linear interpolation toward zero will be taken.
		double prePeakInflection = 0;
		
		// Get the T parameter metadata or create a new one
		TParameterMetadata tparam = input.getFirstMetadata(TParameterMetadata.class);
		if (tparam == null)
			tparam = new TParameterMetadata();

		// Get limits of the fit, either automatically, or from the user
		if (model.isAutoFit() || model.getKratkyRange() == null) {

			// Search for the extrema of the data
			List<Double> extrema = NcdOperationUtils.findDatasetZeros(dy_dx),
					removingExtrema = new ArrayList<Double>();
			// remove zeros that are likely below the peak
			for (Double extremum : extrema)
				if (extremum < lowIndex)
					removingExtrema.add(extremum);
			extrema.removeAll(removingExtrema);
			
			// Assume that the principal peak is now the first negative-going zero-
			// crossing in the derivative
			double peakIndex = extrema.get(0);
			for (Double extremum : extrema) {
				if (dy_dx.getElementDoubleAbs((int) Math.floor(extremum)) > dy_dx.getDouble((int) Math.ceil(extremum))) {
					peakIndex = extremum;
					break;
				}
			}

			List<Double> inflections = NcdOperationUtils.findDatasetZeros(Maths.derivative(q, dy_dx, smoothing));
			// Get the last inflection before peakIndex

			for (Double inflection : inflections)
				if (inflection < peakIndex && 
						inflections.indexOf(inflection) < inflections.size()-1 &&
						inflections.get(inflections.indexOf(inflection)+1) > peakIndex)
					prePeakInflection = inflection;
			
			// Set the limits of valid data
			double validUpper = q.getDouble(q.getSize()-1);
			if (tparam != null)
				validUpper = tparam.getqPorodMin();
			model.setKratkyRange(new double[] {q.getDouble((int) Math.floor(prePeakInflection)), validUpper});
			
		} else {
			prePeakInflection = DatasetUtils.findIndexGreaterThanOrEqualTo(q, model.getKratkyRange()[0]);
		}

		KratkyParameters parameters = new KratkyParameters();

		double f = prePeakInflection % 1;
		int i = (int) Math.floor(prePeakInflection);
		// End of the linearly interpolated region
		parameters.qMin = q.getDouble(i) * (1-f) + q.getDouble(i+1) * f;
		// Linear interpolation of the gradient
		parameters.gradient = dy_dx.getDouble(i) * (1-f) + dy_dx.getDouble(i+1) * f;
		// The intercept
		parameters.intercept = y.getDouble(i) * (1-f) + y.getDouble(i+1) * f - parameters.qMin * parameters.gradient;

		System.err.println("Kratky parameters: qmin = " + parameters.qMin + ", gradient = " + parameters.gradient + ", intercept = " + parameters.intercept);

		// Create an additional output dataset of the linear fit
		Dataset fitQ = DoubleDataset.createRange(Double.MIN_NORMAL, parameters.qMin, parameters.qMin/20);
		Dataset kratkyCurve = Maths.add(Maths.multiply(parameters.gradient, fitQ), parameters.intercept);
		AxesMetadataImpl kratkyAxes = new AxesMetadataImpl(1);
		kratkyAxes.addAxis(0, fitQ);
		kratkyCurve.addMetadata(kratkyAxes);
		kratkyCurve.setName("Kratky fit");


			
		tparam.setqKratkyMin(parameters.qMin);
		tparam.setKratkyIntegral(parameters.qMin * (parameters.intercept + parameters.gradient * parameters.qMin));
		
		// Don't add metadata to a Dataset that already has it
		if (input.getFirstMetadata(TParameterMetadata.class) == null)
			input.addMetadata(tparam);
			
		
		return new OperationData(input, kratkyCurve);

	
		
		
	}

}
