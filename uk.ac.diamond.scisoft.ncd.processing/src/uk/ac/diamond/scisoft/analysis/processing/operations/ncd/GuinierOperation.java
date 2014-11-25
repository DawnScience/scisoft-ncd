package uk.ac.diamond.scisoft.analysis.processing.operations.ncd;

import java.io.Serializable;

import javax.measure.quantity.Dimensionless;

import org.eclipse.dawnsci.analysis.api.dataset.IDataset;
import org.eclipse.dawnsci.analysis.api.dataset.ILazyDataset;
import org.eclipse.dawnsci.analysis.api.metadata.AxesMetadata;
import org.eclipse.dawnsci.analysis.api.monitor.IMonitor;
import org.eclipse.dawnsci.analysis.api.processing.AbstractOperation;
import org.eclipse.dawnsci.analysis.api.processing.OperationData;
import org.eclipse.dawnsci.analysis.api.processing.OperationException;
import org.eclipse.dawnsci.analysis.api.processing.OperationRank;
import org.eclipse.dawnsci.analysis.dataset.impl.DoubleDataset;
import org.jscience.physics.amount.Amount;

import uk.ac.diamond.scisoft.analysis.processing.operations.EmptyModel;
import uk.ac.diamond.scisoft.ncd.core.data.plots.GuinierPlotData;

public class GuinierOperation extends AbstractOperation<EmptyModel, OperationData> {

	@Override
	public String getId() {
		return "uk.ac.diamond.scisoft.analysis.processing.Guinier";
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
	public OperationData process(IDataset slice, IMonitor monitor) throws OperationException {
		GuinierPlotData guinier = new GuinierPlotData();
		
		@SuppressWarnings("unused")
		IDataset dataSlice = slice.getSliceView();
		ILazyDataset axisSlice;
		try {
			axisSlice = slice.getMetadata(AxesMetadata.class).get(0).getAxes()[0];
			if (axisSlice == null) {
				throw new OperationException(this, new Exception("No axes found"));
			}
		} catch (Exception e) {
			throw new OperationException(this, new Exception("problem while getting axis metadata"));
		}
		Object[] params = guinier.getGuinierPlotParameters(slice, (IDataset)axisSlice);

		@SuppressWarnings("unchecked")
		DoubleDataset i0 = new DoubleDataset(new double[]{((Amount<Dimensionless>)params[0]).getEstimatedValue()}, new int[]{1});
		i0.setName("I0");
		i0.squeeze();
		@SuppressWarnings("unchecked")
		DoubleDataset rG = new DoubleDataset(new double[]{((Amount<Dimensionless>)params[1]).getEstimatedValue()}, new int[]{1});
		rG.setName("Rg");
		rG.squeeze();
		DoubleDataset rGLow = new DoubleDataset(new double[]{(double) params[2]}, new int[]{1});
		rGLow.setName("RgLow");
		rGLow.squeeze();
		DoubleDataset rGUpper = new DoubleDataset(new double[]{(double) params[3]}, new int[]{1});
		rGUpper.setName("RgUpper");
		rGUpper.squeeze();
		return new OperationData(slice, new Serializable[]{i0, rG, rGLow, rGUpper});
	}
}
