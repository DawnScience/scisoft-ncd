package uk.ac.diamond.scisoft.analysis.processing.operations.ncd;

import org.eclipse.dawnsci.analysis.api.processing.model.AbstractOperationModel;
import org.eclipse.dawnsci.analysis.api.processing.model.FileType;
import org.eclipse.dawnsci.analysis.api.processing.model.OperationModelField;

public class NcdBackgroundSubtractionModel extends AbstractOperationModel {

	@OperationModelField(hint="File containing background data",file = FileType.EXISTING_FILE, label = "Background file" )
	private String filePath = "";

	public String getFilePath() {
		return filePath;
	}

	public void setFilePath(String filePath) {
		firePropertyChange("filePath", this.filePath, this.filePath = filePath);
	}

	@OperationModelField(hint="Value to scale the background data", label = "Background scale" )
	private double bgScale;

	public double getBgScale() {
		return bgScale;
	}

	public void setBgScale(double bgScale) {
		this.bgScale = bgScale;
	}
}
