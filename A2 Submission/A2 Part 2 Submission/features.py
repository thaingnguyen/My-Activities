# -*- coding: utf-8 -*-
"""
Created on Tue Sep 27 13:08:49 2016

@author: cs390mb

This file is used for extracting features over windows of tri-axial accelerometer
data. We recommend using helper functions like _compute_mean_features(window) to
extract individual features.

As a side note, the underscore at the beginning of a function is a Python
convention indicating that the function has private access (although in reality
it is still publicly accessible).

"""

import numpy as np
import math

def _compute_statistical_features(window):
    statistical_features = []
    statistical_features = np.append(statistical_features, np.mean(window, axis=0))
    statistical_features = np.append(statistical_features, np.median(window, axis=0))
    statistical_features = np.append(statistical_features, np.std(window, axis=0))
    statistical_features = np.append(statistical_features, np.var(window, axis=0))
    statistical_features = np.append(statistical_features, np.amin(window, axis=0))
    statistical_features = np.append(statistical_features, np.amax(window, axis=0))
    return statistical_features

def _compute_magnitude(data_point):
    magnitude = 0
    for axis in data_point:
        magnitude += axis ** 2
    return math.sqrt(magnitude)

def _compute_magnitude_features(window):
    magnitudes = np.asarray(map(_compute_magnitude, window))

    magnitude_features = []
    magnitude_features = np.append(magnitude_features, np.mean(magnitudes))
    magnitude_features = np.append(magnitude_features, np.median(magnitudes))
    magnitude_features = np.append(magnitude_features, np.std(magnitudes))
    magnitude_features = np.append(magnitude_features, np.var(magnitudes))
    magnitude_features = np.append(magnitude_features, np.amin(magnitudes))
    magnitude_features = np.append(magnitude_features, np.amax(magnitudes))

    return magnitude_features

def _compute_entropy_features(window):
    hist = np.histogram(window, bins = 5)[0]
    entropy = 0
    for v in hist:
        if v > 0:
            entropy += -v * np.log(v)
    return np.asarray([entropy])

def extract_features(window):
    """
    Here is where you will extract your features from the data over
    the given window. We have given you an example of computing
    the mean and appending it to the feature matrix X.

    Make sure that X is an N x d matrix, where N is the number
    of data points and d is the number of features.

    """
    x = []
    x = np.append(x, _compute_statistical_features(window))
    x = np.append(x, _compute_magnitude_features(window))
    x = np.append(x, _compute_entropy_features(window))
    return x
