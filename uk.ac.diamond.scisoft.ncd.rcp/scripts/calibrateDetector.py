from calibrationMethods import performCalibration
from uk.ac.diamond.scisoft.analysis.plotserver import GuiParameters
from uk.ac.diamond.scisoft.analysis.plotserver import CalibrationResultsBean
import scisoftpy as dnp
from scisoftpy import plot

def calibrate(saxswaxs, plotName, redsetup, peaks=[], stdspacing=[67.0], wavelength=None, pixelSize=None, n=10, disttobeamstop=None):
    [func, calPeaks, camlength] = performCalibration(peaks, stdspacing, wavelength, pixelSize*1000, n, disttobeamstop)
    if func is None:
        raise "Something went wrong"
    plot_results(plotName, func)
    if redsetup is not None:
        redsetup.ncdredconf(saxswaxs, slope=func.func.getParameterValue(0), intercept=func.func.getParameterValue(1), cameralength=(camlength/1000))
    function_to_GUIBean(saxswaxs, plotName, func, calPeaks, camlength)
        
def function_to_GUIBean(saxswaxs, plotName, fittingReturn, calPeaks, cameraDist):
    new_bean = plot.getbean(plotName)
    ncdbean = new_bean[GuiParameters.CALIBRATIONFUNCTIONNCD]
    if not isinstance(ncdbean, CalibrationResultsBean):
        new_bean[GuiParameters.CALIBRATIONFUNCTIONNCD] = CalibrationResultsBean(saxswaxs,fittingReturn.func,calPeaks,cameraDist)
    else:
        ncdbean.putCalibrationResult(saxswaxs,fittingReturn.func,calPeaks,cameraDist)
    plot.setbean(new_bean, plotName)
    
def plot_results(plotName, function):
     d = function.coords
     d1 = dnp.zeros(d[0].shape[0]+1)
     d1[1:] = d[0]
     d2 = dnp.toList(d1)
     
     data = function.data
     data2 = dnp.zeros(data.shape[0]+1)
     data2[0] = function.func.getParameterValue(1)
     data2[1:] = data
     
     func = dnp.fit.fitcore.fitresult(function.func, d2, data2)  
     func.plot(plotName)