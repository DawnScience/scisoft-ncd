import sys
import math

from uk.ac.diamond.scisoft.analysis.plotserver import CalibrationPeak
import scisoftpy as dnp

  
def combinations(iterable, r):
    '''
    This is combinations implementation from itertools module in Python2.6
    It is included here until Jython is upgraded to version 2.6 
    '''
    pool = tuple(iterable)
    n = len(pool)
    if r > n:
        return
    indices = range(r)
    yield tuple(pool[i] for i in indices)
    while True:
        for i in reversed(range(r)):
            if indices[i] != i + n - r:
                break
        else:
            return
        indices[i] += 1
        for j in range(i+1, r):
            indices[j] = indices[j-1] + 1
        yield tuple(pool[i] for i in indices)

        
def twoThetaAngles(n, wavelength, standardDSpacing):
    '''
    Calculates the bragg angle for a given reflection at a given wavelength.
    The order of the bragg scattering is also calculated
    '''
    twoTheta = []
    for (idx, d) in standardDSpacing:
        if (n >= max(idx)):
            x = (wavelength / (2 * d))
            if x > 1:
                x = 1 #cant scatter beyond pi/2 as beyond resolution limit
            twoTheta.append([2 * math.asin(x), d, idx])
    return twoTheta

   
def indexPeaks(peaksOnDetector, braggAngle):
    '''
    Uses a trivial trigonometric function to calculate the 
    distance from the sample to the detector. It is assumed that
    the detector is orthogonal to the detector and will attempt
    to fit all of the available scatters to the fitted peaks
    '''
      
    minStd = sys.maxint
    probablematch = []
    
    braggAngle.sort()
    peaksOnDetector.sort()
    
    for s in combinations(range(len(braggAngle)), len(peaksOnDetector)):
        distance = []
        result = []
        for idx, i in enumerate(s):
            twoTheta, d, n = braggAngle[i]
            if twoTheta <= 0:
                break
            distance.append(peaksOnDetector[idx] / math.tan(twoTheta))
            result.append([peaksOnDetector[idx], twoTheta, d, n])

        std = dnp.array(distance).std()
        if(std < minStd):
            probablematch = result
            minStd = std
    print "Probable match: ", probablematch
    return probablematch

    
def fitFunctionToData(probablematch):
    #plot q vs pixel
    q = []
    calPeaks = []
    for i, (peak, ttheta, d, n) in enumerate(probablematch): 
        qVal = 2 * math.pi / d
        print "Peak " + str(i+1) + " has a d spacing of " + str(d) + " and an 'n' value of " + str(n) + " with a q value of " + str(qVal)
        q.append(qVal)
        calPeaks.append(CalibrationPeak(peak,ttheta,d, n))

    XData = dnp.asDataset([p[0] for p in probablematch])
    YData = dnp.asDataset(q)
    
    ymax = YData[len(YData) - 1]
    gradient = (ymax - YData[0]) / (XData[len(XData) - 1] - XData[0])
    
    return dnp.fit.fit(dnp.fit.function.linear, XData, YData, [gradient, 0.0], 
                       bounds=[(gradient * 0.8, gradient * 1.2), (-ymax, ymax)], seed=123, optimizer='global'), calPeaks

                       
def cameraLength(match, pixelSize):
    cameraLen = []
    for i, (peak_i,ttheta_i,_,_) in enumerate(match): 
        for j in range(i+1, len(match)):
            (peak_j,ttheta_j,_,_) = match[j]
            if pixelSize != None:
                cameraLen.append((peak_j-peak_i)*pixelSize/(ttheta_j-ttheta_i))
            else:
                cameraLen.append((peak_j-peak_i)/(ttheta_j-ttheta_i))
                
    result = dnp.array(cameraLen)
    meanCameraLength = result.mean()
    stdCameraLength = result.std()
    print "Camera length: ", meanCameraLength, "+/-", stdCameraLength, " mm"
    return meanCameraLength


def performCalibration(peaks, spacing, wavelength, pixelSize, n, disttobeamstop): 
    braggAngle = twoThetaAngles(n, wavelength, spacing)
    braggAngle.sort()
    
    if(len(peaks) < 1):
        raise "Cannot calibrate with this number of peaks"   
        
    peaksOnDetector = []
    if disttobeamstop != None:
        peaksOnDetector = [(p + disttobeamstop) * pixelSize for p in peaks]
    else:
        peaksOnDetector = peaks

    match = indexPeaks(peaksOnDetector, braggAngle)
    function, calPeaks = fitFunctionToData(match)
    camlength = cameraLength(match, None if (disttobeamstop != None) else pixelSize)
    
    print "Linear fit results"
    print "    Gradient: ", function.func.getParameterValue(0)
    print "    Intercept: ", function.func.getParameterValue(1)
    
    return [function, calPeaks, camlength]