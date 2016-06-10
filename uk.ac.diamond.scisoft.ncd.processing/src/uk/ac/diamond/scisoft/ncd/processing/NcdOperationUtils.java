/*
 * Copyright (c) 2014 Diamond Light Source Ltd.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */

package uk.ac.diamond.scisoft.ncd.processing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.dawnsci.analysis.api.dataset.DatasetException;
import org.eclipse.dawnsci.analysis.api.dataset.IDataset;
import org.eclipse.dawnsci.analysis.api.dataset.ILazyDataset;
import org.eclipse.dawnsci.analysis.api.dataset.Slice;
import org.eclipse.dawnsci.analysis.api.metadata.AxesMetadata;
import org.eclipse.dawnsci.analysis.api.processing.IOperation;
import org.eclipse.dawnsci.analysis.dataset.impl.AbstractDataset;
import org.eclipse.dawnsci.analysis.dataset.impl.Dataset;
import org.eclipse.dawnsci.analysis.dataset.impl.DatasetFactory;
import org.eclipse.dawnsci.analysis.dataset.impl.DatasetUtils;
import org.eclipse.dawnsci.analysis.dataset.impl.IndexIterator;
import org.eclipse.dawnsci.analysis.dataset.impl.Maths;
import org.eclipse.dawnsci.analysis.dataset.slicer.SliceFromSeriesMetadata;

import uk.ac.diamond.scisoft.analysis.fitting.Fitter;
import uk.ac.diamond.scisoft.analysis.fitting.functions.StraightLine;
import uk.ac.diamond.scisoft.analysis.processing.operations.utils.ProcessingUtils;
import uk.ac.diamond.scisoft.ncd.core.data.plots.GuinierPlotData;
import uk.ac.diamond.scisoft.ncd.core.data.stats.ClusterOutlierRemoval;
import uk.ac.diamond.scisoft.ncd.core.data.stats.FilterData;
import uk.ac.diamond.scisoft.ncd.core.data.stats.SaxsAnalysisStats;
import uk.ac.diamond.scisoft.ncd.core.data.stats.SaxsAnalysisStatsParameters;
import uk.ac.diamond.scisoft.ncd.core.data.stats.SaxsStatsData;

public class NcdOperationUtils {
	/**
	 * Add a dimension of 1 to account for slicing of original data in the Processing pipeline
	 * @param set
	 * @return
	 */
	public static int[] addDimension(int[] set) {
		int[] dataShape = new int[set.length + 1];
		dataShape[0] = 1;
		int index = 1;
		for (int dimension: set) {
			dataShape[index++] = dimension;
		}
		return dataShape;
	}
	
	/**
	 * Check existing paths list, then /entry/result/data in case this is a file from created from the Processing pipeline.
	 * @param fileToRead
	 * @return
	 * @throws Exception
	 */
	public static ILazyDataset getDataset(IOperation<?, ?> op, String fileToRead, List<String> dataPathsToTry) throws Exception {
		dataPathsToTry.add("/entry/result/data");
		ILazyDataset toReturn = null;
		for (String location : dataPathsToTry) {
			toReturn = ProcessingUtils.getLazyDataset(op, fileToRead, location);
			if (toReturn != null) {
				break;
			}
		}
		return toReturn;
	}
	

	public static Object[] getGuinierPlotParameters(IDataset slice) throws Exception {
		GuinierPlotData guinier = new GuinierPlotData();
		
		@SuppressWarnings("unused")
		IDataset dataSlice = slice.getSliceView();
		ILazyDataset axisSlice;
		try {
			axisSlice = slice.getMetadata(AxesMetadata.class).get(0).getAxes()[0];
			if (axisSlice == null) {
				throw new Exception("No axes found");
			}
		} catch (Exception e) {
			throw new Exception("problem while getting axis metadata", e);
		}
		Object[] params = guinier.getGuinierPlotParameters(slice, (IDataset)axisSlice);
		return params;
	}
	
	public static Dataset getSaxsAnalysisStats(Dataset data, SaxsAnalysisStatsParameters statType) {
		SaxsAnalysisStats selectedSaxsStat = statType.getSelectionAlgorithm();
		SaxsStatsData statsData = selectedSaxsStat.getSaxsAnalysisStatsObject();
		if (statsData instanceof FilterData) {
			((FilterData)statsData).setConfigenceInterval(statType.getSaxsFilteringCI());
		}
		if (statsData instanceof ClusterOutlierRemoval) {
			((ClusterOutlierRemoval)statsData).setDbSCANClustererEpsilon(statType.getDbSCANClustererEpsilon());
			((ClusterOutlierRemoval)statsData).setDbSCANClustererMinPoints(statType.getDbSCANClustererMinPoints());
		}

		statsData.setReferenceData(data);
		Dataset mydata = statsData.getStatsData();
		return mydata;
	}
	
	/**
	 * Provide an easy way to get the errorBuffer because only error may be defined
	 * @param data
	 * @return
	 */
	public static Dataset getErrorBuffer(Dataset data) {
		if (data.getErrorBuffer() == null) {
			Dataset error = data.getError() == null ? data.clone() : data.getError();
			return error.ipower(2);
		}
		return data.getErrorBuffer();
	}
	
	public static Dataset getBackgroundSlice(SliceFromSeriesMetadata ssm, IDataset slice, ILazyDataset background) throws Exception {
		Dataset bgSlice;

		//if the background image is the same shape as the sliced image, then do simple subtraction on the background
		if (Arrays.equals(AbstractDataset.squeezeShape(slice.getShape(), false), AbstractDataset.squeezeShape(background.getShape(), false))) {
			bgSlice = DatasetUtils.sliceAndConvertLazyDataset(background);
		}
		else {
			//if number of images between background and parent dataset are the same, subtract each BG from corresponding data slice
			int backgroundImages = getNumberOfImages(background, ssm, slice);
			int sampleImages = getNumberOfSliceImages(ssm);
			if (backgroundImages == sampleImages) {
				bgSlice = DatasetUtils.convertToDataset(ssm.getMatchingSlice(background));
			}
			else {
				throw new IllegalArgumentException("Background data not compatible with subtraction from data - consider averaging the background data before background subtraction");
			}
		}
		if (bgSlice ==  null) {
			throw new Exception("Background slice is null. Make sure a selection of background image(s) has been specified.");
		}
		//data slice must not be larger than BG data slice! we have not done enough reduction on slices in this case!
		if (slice.getShape().length > bgSlice.getShape().length) {
			throw new Exception("Slice should not have bigger dimensionality than the background data");
		}

		Dataset backgroundErrors = NcdOperationUtils.getErrorBuffer(bgSlice.getSlice());
		if (backgroundErrors == null) {
			backgroundErrors = bgSlice.getSlice();
		}
		bgSlice.setErrorBuffer(backgroundErrors);
		return bgSlice;
	}
	
	private static int getNumberOfImages(ILazyDataset backgroundToProcess2, SliceFromSeriesMetadata ssm, IDataset slice) {
		//find location of data dimensions in origin, see if we have them in background
		ILazyDataset origin = ssm.getParent();
		List<Integer>backgroundDataDims = new ArrayList<Integer>();
		List<Integer> dimensionList = new ArrayList<Integer>();
		if (origin.getRank()== backgroundToProcess2.getRank()) {
			for (int dataDim : ssm.getDataDimensions()) {
				dimensionList.add(origin.getShape()[dataDim]);
			}
		}
		else {
			for (int i = 0; i < slice.getShape().length; ++i) {
				dimensionList.add(slice.getShape()[i]);
			}
		}
		
		for (int i = 0; i < backgroundToProcess2.getShape().length; ++i) {
			if (dimensionList.contains(backgroundToProcess2.getShape()[i])) {
				backgroundDataDims.add(i);
			}
		}
		
		int backgroundImages = 1;
		for (int i=0; i < backgroundToProcess2.getShape().length; ++i) {
			int backgroundDim = backgroundToProcess2.getShape()[i];
			if (backgroundDim != 1 && !backgroundDataDims.contains(i)) {
				backgroundImages *= backgroundDim;
			}
		}
		return backgroundImages;
	}

	private static int getNumberOfSliceImages(SliceFromSeriesMetadata ssm) {
		int totalSize = 1;
		for (int i = 0; i < ssm.getSubSampledShape().length; ++i) {
			boolean isADataDimension = false;
			for (int j=0; j < ssm.getDataDimensions().length; ++j) {
				if (ssm.getDataDimensions()[j] == i) {
					isADataDimension = true;
					break;
				}
			}
			if (!isADataDimension) {
				totalSize *= ssm.getSubSampledShape()[i];
			}
		}
		return totalSize;
	}
	
	public static Dataset convertListOfDatasetsToDataset(List<Dataset> ag) throws Exception {
		Dataset first = null;
		for (Dataset dataset : ag) {
			if (first == null) {
				first = dataset;
			}
			if (dataset.getSize() != first.getSize()) {
				throw new Exception("all component datasets must be the same size");
			}
		}
		Dataset newDataset = DatasetFactory.zeros(new int[] {ag.size(), first.getSize()}, first.getDtype());
		for (int i=0; i < ag.size(); ++i) {
			Dataset dataset = ag.get(i);
			for (int j=0; j < dataset.getSize(); ++j) {
				newDataset.set(dataset.getObject(j), i, j);
			}
		}
		return newDataset;
	}

	/**
	 * Class to hold the three parameters from fitting the Porod constant
	 * @author Timothy Spain, timothy.spain@diamond.ac.uk
	 *
	 */
	public static class PorodParameters {
		public double qMin, qMax, gradient, porodConstant;
	}

	public static class KratkyParameters {
		public double qMin, gradient, intercept;
		public Dataset cubicData;
	}
	
	public static PorodParameters fitPorodConstant(Dataset intensity) throws DatasetException {
		PorodParameters params = new PorodParameters();
		
		Dataset q = DatasetUtils.convertToDataset(intensity.getFirstMetadata(AxesMetadata.class).getAxis(0)[0].getSlice());
		
		// Calculate the independent and dependent variables of the Porod
		// background plot
		Dataset x = Maths.square(Maths.square(q));
		Dataset y = Maths.multiply(x, intensity);
		
		// First Derivative. Smooth over 4 elements
		Dataset dy_dx = Maths.derivative(x, y, 4);
		
		// Search for the extrema of the data
		List<Double> zeros = findDatasetZeros(dy_dx);
		
		if (zeros.size() < 3) {
			params.porodConstant = -1;
		}
		
		// Take the Porod range to be 80% of the gap between the second and
		// third zeros
		int iMin = (int) Math.floor(zeros.get(1)),
				 iMax = (int) Math.ceil(zeros.get(2)),
				 iDiff = iMax-iMin;
		
		int margin = iDiff/10;
		iMin += margin;
		iMax -= margin;
		
		params.qMin = q.getElementDoubleAbs(iMin);
		params.qMax = q.getElementDoubleAbs(iMax);
		
		Slice linearRegion = new Slice(iMin, iMax, 1);

		// Perform a linear fit over the region of interest slice
		StraightLine porodFit = new StraightLine();
		try {
			Fitter.llsqFit(new Dataset[] {x.getSlice(linearRegion)}, y.getSlice(linearRegion), porodFit);
		} catch (Exception e) {
			System.err.println("Exception performing linear fit in fitPorodConstant(): " + e.toString());
			return new PorodParameters();
		}

		params.gradient = porodFit.getParameterValue(0);
		params.porodConstant = porodFit.getParameterValue(1);

		return params;
	}

	public static KratkyParameters fitKratkyLine(Dataset intensity) throws DatasetException {
		KratkyParameters parameters = new KratkyParameters();

		Dataset q = DatasetUtils.convertToDataset(intensity.getFirstMetadata(AxesMetadata.class).getAxis(0)[0].getSlice());
		
		// Calculate the independent and dependent variables of the Kratky plot
		Dataset x = q;
		Dataset y = Maths.multiply(Maths.square(q), intensity);

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

		// Search for the extrema of the data
		List<Double> extrema = findDatasetZeros(dy_dx),
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

		List<Double> inflections = findDatasetZeros(Maths.derivative(q, dy_dx, smoothing));
		// Get the last inflection before peakIndex
		double prePeakInflection = 0;
		for (Double inflection : inflections)
			if (inflection < peakIndex && 
					inflections.indexOf(inflection) < inflections.size()-1 &&
					inflections.get(inflections.indexOf(inflection)+1) > peakIndex)
				prePeakInflection = inflection;
		
		double f = prePeakInflection % 1;
		int i = (int) Math.floor(prePeakInflection);
		// End of the linearly interpolated region
		parameters.qMin = q.getDouble(i) * (1-f) + q.getDouble(i+1) * f;
		// Linear interpolation of the gradient
		parameters.gradient = dy_dx.getDouble(i) * (1-f) + dy_dx.getDouble(i+1) * f;
		// The intercept
		parameters.intercept = y.getDouble(i) * (1-f) + y.getDouble(i+1) * f - parameters.qMin * parameters.gradient;

		return parameters;
	}
		
	/**
	 * Returns the floating point indices of the zeros of a Dataset
	 * @param y
	 * 			dependent variable of the data
	 * @return List of values of the independent variable at the zeros of the data
	 */
	public static List<Double> findDatasetZeros(Dataset y) {
		List<Double> zeros = new ArrayList<>(); 
		IndexIterator searchStartIterator = y.getIterator(), searchingIterator = searchStartIterator;
		if (!searchStartIterator.hasNext())
			return zeros;
		double startValue = y.getElementDoubleAbs(searchStartIterator.index);
		
		while(searchingIterator.hasNext()) {
			double searchValue = y.getElementDoubleAbs(searchingIterator.index);
			if (searchValue == 0) {
				// restart the search from the next point
				if (!searchingIterator.hasNext()) break;
				searchStartIterator = searchingIterator;
				startValue = y.getElementDoubleAbs(searchStartIterator.index);
			}
			if (Math.signum(searchValue) != Math.signum(startValue)) {
				// linear interpolation to get the zero
				double y1 = y.getElementDoubleAbs(searchingIterator.index-1),
						y2 = y.getElementDoubleAbs(searchingIterator.index);
				zeros.add(searchingIterator.index - y2/(y2-y1));
				
				// restart the search from the searchValue point
				searchStartIterator = searchingIterator;
				startValue = searchValue;
			}
		}
		return zeros;
	}
	
	
}