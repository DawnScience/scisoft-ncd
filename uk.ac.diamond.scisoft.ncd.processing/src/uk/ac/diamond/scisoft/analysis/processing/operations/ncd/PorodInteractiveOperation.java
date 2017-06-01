package uk.ac.diamond.scisoft.analysis.processing.operations.ncd;

import java.util.List;

import org.eclipse.dawnsci.analysis.api.processing.OperationData;
import org.eclipse.dawnsci.analysis.api.processing.OperationException;
import org.eclipse.dawnsci.analysis.api.processing.OperationRank;
import org.eclipse.dawnsci.analysis.api.processing.PlotAdditionalData;
import org.eclipse.dawnsci.analysis.dataset.operations.AbstractOperation;
import org.eclipse.january.DatasetException;
import org.eclipse.january.IMonitor;
import org.eclipse.january.MetadataException;
import org.eclipse.january.dataset.Dataset;
import org.eclipse.january.dataset.DatasetFactory;
import org.eclipse.january.dataset.DatasetUtils;
import org.eclipse.january.dataset.DoubleDataset;
import org.eclipse.january.dataset.IDataset;
import org.eclipse.january.dataset.Maths;
import org.eclipse.january.dataset.Slice;
import org.eclipse.january.metadata.AxesMetadata;
import org.eclipse.january.metadata.MetadataFactory;

import uk.ac.diamond.scisoft.analysis.fitting.Fitter;
import uk.ac.diamond.scisoft.analysis.fitting.functions.StraightLine;
import uk.ac.diamond.scisoft.analysis.optimize.ApacheOptimizer;
import uk.ac.diamond.scisoft.analysis.optimize.ApacheOptimizer.Optimizer;
import uk.ac.diamond.scisoft.ncd.processing.NcdOperationUtils;
import uk.ac.diamond.scisoft.ncd.processing.NcdOperationUtils.PorodParameters;
import uk.ac.diamond.scisoft.ncd.processing.TParameterMetadata;

@PlotAdditionalData(onInput = false, dataName = "Porod fit")
public class PorodInteractiveOperation extends
		AbstractOperation<PorodInteractiveModel, OperationData> {

	@Override
	public String getId() {
		return "uk.ac.diamond.scisoft.ncd.processing.PorodInteractiveOperation";
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

		PorodParameters params = new PorodParameters();

		// The axis is the fourth power of q
		Dataset q4;
		try {
			q4 = DatasetUtils.convertToDataset(input.getFirstMetadata(AxesMetadata.class).getAxis(0)[0].getSlice());
		} catch (DatasetException e1) {
			throw new OperationException(this, e1);
		}
		Dataset dInput = DatasetUtils.convertToDataset(input);
		
		// Get limits of the fit, either automatically, or from the user
		if (model.isAutoFit() || model.getPorodRange() == null) {
			// First Derivative. Smooth over 4 elements
			Dataset dy_dx = Maths.derivative(q4, dInput, 4);
			
			// Search for the extrema of the data
			List<Double> zeros = NcdOperationUtils.findDatasetZeros(dy_dx);
			// Take the Porod range to be 80% of the gap between the second and
			// third zeros
			int iMin = (int) Math.floor(zeros.get(1));
			int iMax = (int) Math.ceil(zeros.get(2));
			int iDiff = iMax-iMin;
			
			int margin = iDiff/10;
			iMin += margin;
			iMax -= margin;

			params.qMin = Math.sqrt(Math.sqrt(q4.getElementDoubleAbs(iMin)));
			params.qMax = Math.sqrt(Math.sqrt(q4.getElementDoubleAbs(iMax)));
			model.setPorodRange(new double[] {Math.pow(params.qMin, 4), Math.pow(params.qMax, 4)});
			
		} else {
			double[] q4Range = model.getPorodRange();
			params.qMin = Math.sqrt(Math.sqrt(q4Range[0]));
			params.qMax = Math.sqrt(Math.sqrt(q4Range[1]));
			
			if (params.qMin > params.qMax) {
				double tempus = params.qMin;
				params.qMin = params.qMax;
				params.qMax = tempus;
			}
		}

		// Get the region of the fit from the qMax and qMin
		Dataset q = Maths.sqrt(Maths.sqrt(q4));
		int iMin = DatasetUtils.findIndexGreaterThanOrEqualTo(q, params.qMin);
		int iMax = DatasetUtils.findIndexGreaterThan(q, params.qMax) - 1;
		
		Slice linearRegion = new Slice(iMin, iMax, 1);

		// Perform a linear fit over the region of interest slice
		StraightLine porodFit = new StraightLine();
		try {
			ApacheOptimizer opt = new ApacheOptimizer(Optimizer.LEVENBERG_MARQUARDT);
			opt.optimize(new Dataset[] {q4.getSlice(linearRegion)}, dInput.getSlice(linearRegion), porodFit);

			params.gradient = porodFit.getParameterValue(0);
			params.porodConstant = porodFit.getParameterValue(1);
		} catch (Exception e) {
			System.err.println("Exception performing linear fit in fitPorodConstant(): " + e.toString());
		}

		// Porod fit
		Dataset fitQ = DatasetFactory.createRange(DoubleDataset.class, params.qMin, params.qMax, (params.qMax-params.qMin)/20);
		Dataset fitQ4 = Maths.square(Maths.square(fitQ));
		Dataset porodCurve = Maths.add(params.porodConstant, Maths.multiply(params.gradient, fitQ4));
		AxesMetadata porodAxes;
		try {
			porodAxes = MetadataFactory.createMetadata(AxesMetadata.class, 1);
		} catch (MetadataException e) {
			throw new OperationException(this, e);
		}
		porodAxes.addAxis(0, fitQ4);
		porodCurve.addMetadata(porodAxes);
		porodCurve.setName("Porod fit");
		
		System.err.println("Porod data: region = [" + params.qMin + ", " + params.qMax + "], B = " + params.gradient + ", constant = " + params.porodConstant);

		// Set the metadata
		TParameterMetadata tparam = input.getFirstMetadata(TParameterMetadata.class);
		if (tparam == null)
			tparam = new TParameterMetadata();
		
		tparam.setqPorodMin(params.qMin);
		tparam.setPorodConstant(params.porodConstant);
		
		Dataset baselined = dInput.copy(DoubleDataset.class);
		if (model.isSubtractBackground()) {
			baselined.isubtract(Maths.multiply(params.gradient, q4));
			porodCurve.isubtract(Maths.multiply(params.gradient, fitQ4));
		}
		
		baselined.addMetadata(tparam);

		
		return new OperationData(baselined, porodCurve);
	}
	
}
