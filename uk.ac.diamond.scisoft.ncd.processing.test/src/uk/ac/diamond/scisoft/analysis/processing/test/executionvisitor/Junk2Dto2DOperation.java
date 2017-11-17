package uk.ac.diamond.scisoft.analysis.processing.test.executionvisitor;

import org.eclipse.dawnsci.analysis.api.processing.Atomic;
import org.eclipse.dawnsci.analysis.api.processing.OperationData;
import org.eclipse.dawnsci.analysis.api.processing.OperationException;
import org.eclipse.dawnsci.analysis.api.processing.OperationRank;
import org.eclipse.dawnsci.analysis.dataset.operations.AbstractOperation;
import org.eclipse.january.IMonitor;
import org.eclipse.january.MetadataException;
import org.eclipse.january.dataset.Dataset;
import org.eclipse.january.dataset.DatasetFactory;
import org.eclipse.january.dataset.IDataset;
import org.eclipse.january.dataset.ShortDataset;
import org.eclipse.january.metadata.AxesMetadata;
import org.eclipse.january.metadata.MetadataFactory;

@Atomic
public class Junk2Dto2DOperation extends AbstractOperation<Junk2Dto2Dmodel, OperationData> implements ITestOperation {

	private boolean withAxes = true;
	private int nAxes = 1;
	
	@Override
	public String getId() {
		return "uk.ac.diamond.scisoft.analysis.processing.test.executionvisitor.Junk2Dto2DOperation";
	}
	
	@Override
	public String getName(){
		return "Junk2Dto2DOperation";
	}

	@Override
	public OperationRank getInputRank() {
		return OperationRank.TWO;
	}

	@Override
	public OperationRank getOutputRank() {
		return OperationRank.TWO;
	}
	
	public void setWithAxes(boolean withAxes) {
		this.withAxes = withAxes;
	}
	
	public void setNumberOfAxes(int nAxes) {
		this.nAxes = nAxes;
	}

	protected OperationData process(IDataset input, IMonitor monitor) throws OperationException {
		
		return getTestData();
	}

	public OperationData getTestData() {
		int x = model.getxDim();
		int y = model.getyDim();
		
		IDataset out = DatasetFactory.createRange(ShortDataset.class, x*y);
		out.setShape(new int[]{x,y});;
		out.setName("Junk2Dto2Dout");
		
		if (withAxes) {
			AxesMetadata am;
			try {
				am = MetadataFactory.createMetadata(AxesMetadata.class, 2);
			} catch (MetadataException e) {
				throw new OperationException(this, e);
			}
			for (int i = 0; i < nAxes; i++) {
				Dataset ax1 = DatasetFactory.createRange(ShortDataset.class, 0, x, 1);
				ax1.iadd(i);
				ax1.setShape(new int[]{x,1});
				if (i == 0) ax1.setName("Junk2Dto2DAx1");
				else ax1.setName("Junk2Dto2DAx1"+"_"+Integer.toString(i));
				Dataset ax2 = DatasetFactory.createRange(ShortDataset.class, 0, y, 1);
				ax2.setShape(new int[]{1,y});
				ax2.setName("Junk2Dto2DAx2");
				if (i == 0) ax2.setName("Junk2Dto2DAx2");
				else ax2.setName("Junk2Dto2DAx2"+"_"+Integer.toString(i));
				ax2.iadd(i);
				
				am.addAxis(0, ax1);
				am.addAxis(1, ax2);
			}
			
			out.setMetadata(am);
		}
		
		
		return new OperationData(out);
	}
	
	public Class<Junk2Dto2Dmodel> getModelClass() {
		return Junk2Dto2Dmodel.class;
	}
}
