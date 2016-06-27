package uk.ac.diamond.scisoft.analysis.processing.operations.ncd;

import org.eclipse.dawnsci.analysis.api.dataset.IDataset;
import org.eclipse.dawnsci.analysis.api.monitor.IMonitor;
import org.eclipse.dawnsci.analysis.api.processing.OperationData;
import org.eclipse.dawnsci.analysis.api.processing.OperationException;
import org.eclipse.dawnsci.analysis.api.processing.OperationRank;
import org.eclipse.dawnsci.analysis.api.processing.model.EmptyModel;
import org.eclipse.dawnsci.analysis.dataset.impl.ComplexDoubleDataset;
import org.eclipse.dawnsci.analysis.dataset.impl.DatasetUtils;
import org.eclipse.dawnsci.analysis.dataset.impl.FFT;
import org.eclipse.dawnsci.analysis.dataset.impl.Maths;
import org.eclipse.dawnsci.analysis.dataset.operations.AbstractOperation;

public class FFTOperation extends AbstractOperation<EmptyModel, OperationData> {

	@Override
	public String getId() {
		// TODO Auto-generated method stub
		return "uk.ac.diamond.scisoft.analysis.processing.operations.ncd.FFTOperation";
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
	protected OperationData process(IDataset input, IMonitor monitor) throws OperationException {
		
		ComplexDoubleDataset fourier  = (ComplexDoubleDataset) FFT.fft(DatasetUtils.convertToDataset(input));
		return new OperationData(Maths.sqrt(Maths.add(Maths.square(fourier.real()), Maths.square(fourier.imag()))));
	}

}
