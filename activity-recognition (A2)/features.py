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

def _compute_magnitude_features(window):
    magnitudes = []
    for data_point in window:
        magnitude = 0
        for axis in data_point:
            magnitude += axis ** 2
        magnitudes.append(math.sqrt(magnitude))
    return np.asarray(magnitudes)

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
    return x
