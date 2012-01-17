###
# Copyright 2011 Diamond Light Source Ltd.
# 
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
# 
#   http://www.apache.org/licenses/LICENSE-2.0
# 
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
###

from calibrationMethods import performCalibration
from uk.ac.diamond.scisoft.analysis.plotserver import GuiParameters
from uk.ac.diamond.scisoft.analysis.plotserver import CalibrationResultsBean
import scisoftpy as dnp
from scisoftpy import plot

def calibrate(saxswaxs, plotName, redsetup, peaks=[], stdspacing=[67.0], wavelength=None, pixelSize=None, n=10, disttobeamstop=None, unit="nm^{-1}"):
    [func, calPeaks, camlength] = performCalibration(peaks, stdspacing, wavelength, pixelSize*1000, n, disttobeamstop)
    if func is None:
        raise "Something went wrong"
    plot_results(plotName, func)
    if redsetup is not None:
        redsetup.ncdredconf(saxswaxs, slope=func.func.getParameterValue(0), intercept=func.func.getParameterValue(1), cameralength=(camlength/1000))
    function_to_GUIBean(saxswaxs, plotName, func, calPeaks, camlength, unit)
        
def function_to_GUIBean(saxswaxs, plotName, fittingReturn, calPeaks, cameraDist, unit):
    new_bean = plot.getbean(plotName)
    ncdbean = new_bean[GuiParameters.CALIBRATIONFUNCTIONNCD]
    if not isinstance(ncdbean, CalibrationResultsBean):
        new_bean[GuiParameters.CALIBRATIONFUNCTIONNCD] = CalibrationResultsBean(saxswaxs,fittingReturn.func,calPeaks,cameraDist, unit)
    else:
        ncdbean.putCalibrationResult(saxswaxs,fittingReturn.func,calPeaks,cameraDist, unit)
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
