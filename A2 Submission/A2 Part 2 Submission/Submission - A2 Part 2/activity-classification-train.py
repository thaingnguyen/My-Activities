# -*- coding: utf-8 -*-
"""
Created on Wed Sep 21 16:02:58 2016

@author: cs390mb

Assignment 2 : Activity Recognition

This is the starter script used to train an activity recognition
classifier on accelerometer data.

See the assignment details for instructions. Basically you will train
a decision tree classifier and vary its parameters and evalute its
performance by computing the average accuracy, precision and recall
metrics over 10-fold cross-validation. You will then train another
classifier for comparison.

Once you get to part 4 of the assignment, where you will collect your
own data, change the filename to reference the file containing the
data you collected. Then retrain the classifier and choose the best
classifier to save to disk. This will be used in your final system.

Make sure to chek the assignment details, since the instructions here are
not complete.

"""

import os
import sys
import pickle
import numpy as np
import matplotlib.pyplot as plt
from sklearn import cross_validation
from sklearn.tree import DecisionTreeClassifier, export_graphviz
from sklearn.base import clone
from sklearn.svm import LinearSVC, SVC
from sklearn.metrics import confusion_matrix, accuracy_score, recall_score, precision_score, classification_report
from sklearn.cross_validation import train_test_split
from sklearn.model_selection import GridSearchCV
from features import extract_features # make sure features.py is in the same directory
from util import slidingWindow, reorient, reset_vars

# %%---------------------------------------------------------------------------
#
#		                 Load Data From Disk
#
# -----------------------------------------------------------------------------

print("Loading data...")
sys.stdout.flush()
data_file = os.path.join('data', 'activity-data.csv')
data = np.genfromtxt(data_file, delimiter=',')
print("Loaded {} raw labelled activity data samples.".format(len(data)))
sys.stdout.flush()

# %%---------------------------------------------------------------------------
#
#		                    Pre-processing
#
# -----------------------------------------------------------------------------

print("Reorienting accelerometer data...")
sys.stdout.flush()
reset_vars()
reoriented = np.asarray([reorient(data[i,1], data[i,2], data[i,3]) for i in range(len(data))])
reoriented_data_with_timestamps = np.append(data[:,0:1],reoriented,axis=1)
data = np.append(reoriented_data_with_timestamps, data[:,-1:], axis=1)


# %%---------------------------------------------------------------------------
#
#		                Extract Features & Labels
#
# -----------------------------------------------------------------------------

# you may want to play around with the window and step sizes
window_size = 20
step_size = 20

# sampling rate for the sample data should be about 25 Hz; take a brief window to confirm this
n_samples = 1000
time_elapsed_seconds = (data[n_samples,0] - data[0,0]) / 1000
sampling_rate = n_samples / time_elapsed_seconds

feature_names = [
    "mean X", "mean Y", "mean Z",
    "median X", "median Y", "median Z",
    "std X", "std Y", "std Z",
    "var X", "var Y", "var Z",
    "min X", "min Y", "min Z",
    "max X", "max Y", "max Z",
    "mean mag", "median mag", "std mag",
    "var mag", "min mag", "max mag",
    "entropy"
    ]

class_names = ["Stationary", "Walking"]
# class_names = ["Sitting", "Walking", "Running", "Jumping"]

print("Extracting features and labels for window size {} and step size {}...".format(window_size, step_size))
sys.stdout.flush()

n_features = len(feature_names)

X = np.zeros((0,n_features))
y = np.zeros(0,)

for i,window_with_timestamp_and_label in slidingWindow(data, window_size, step_size):
    # omit timestamp and label from accelerometer window for feature extraction:
    window = window_with_timestamp_and_label[:,1:-1]
    # extract features over window:
    x = extract_features(window)
    # append features:
    X = np.append(X, np.reshape(x, (1,-1)), axis=0)
    # append label:
    y = np.append(y, window_with_timestamp_and_label[10, -1])

print("Finished feature extraction over {} windows".format(len(X)))
print("Unique labels found: {}".format(set(y)))
sys.stdout.flush()

# %%---------------------------------------------------------------------------
#
#		                    Plot data points
#
# -----------------------------------------------------------------------------

# We provided you with an example of plotting two features.
# We plotted the mean X acceleration against the mean Y acceleration.
# It should be clear from the plot that these two features are alone very uninformative.
print("Plotting data points...")
sys.stdout.flush()
def plot_features(feature1, feature2, index1, index2):
    plt.figure()
    formats = ['bo', 'go']
    for i in range(0,len(y),10): # only plot 1/10th of the points, it's a lot of data!
        plt.plot(X[i,index1], X[i,index2], formats[int(y[i])])
    plt.title("Relationship between " + feature1 + " and " + feature2)
    plt.xlabel(feature1)
    plt.ylabel(feature2)
    plt.show()

# plot_features("Mean Z", "Median Z", 2, 5)
# plot_features("Std Z", "Var Z", 8, 11)
# plot_features("Min Z", "Max Z", 14, 17)

# %%---------------------------------------------------------------------------
#
#		                Train & Evaluate Classifier
#
# -----------------------------------------------------------------------------

X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, random_state=1234)
n = len(y_train)
n_classes = len(class_names)

def train_and_predict(name, model):
    accuracies = []
    precisions = []
    recalls = []
    cv = cross_validation.KFold(n, n_folds=10, shuffle=True, random_state=1234)

    for i, (train_indexes, test_indexes) in enumerate(cv):
        # print("Fold {}".format(i))
        curr_X_train = X_train[train_indexes]
        curr_X_val = X_train[test_indexes]
        curr_y_train = y_train[train_indexes]
        curr_y_val = y_train[test_indexes]

        clf = clone(model)
        clf.fit(curr_X_train, curr_y_train)
        curr_y_predict = clf.predict(curr_X_val)

        accuracies.append(accuracy_score(curr_y_val, curr_y_predict))
        precisions.append(precision_score(curr_y_val, curr_y_predict))
        recalls.append(recall_score(curr_y_val, curr_y_predict))

    print name
    # Comment out for extra credit assignment
    # if name.startswith("Decision Tree"):
    #     export_graphviz(clf, out_file=name + ".dot", feature_names = feature_names)
    print "Average accuracy: " + str(np.mean(accuracies))
    print "Average recall: " + str(np.mean(recalls))
    print "Average precision: " + str(np.mean(precisions))

# Comment out for extra credit parameter learning
# train_and_predict("Decision Tree Max Depth 5", DecisionTreeClassifier(criterion="entropy", max_depth=5))
# train_and_predict("Decision Tree Max Depth 10", DecisionTreeClassifier(criterion="entropy", max_depth=10))
# train_and_predict("Decision Tree Max Features 5", DecisionTreeClassifier(criterion="entropy", max_features=5))
# train_and_predict("Decision Tree Max Features 10", DecisionTreeClassifier(criterion="entropy", max_features=10))
# train_and_predict("Linear SVC", LinearSVC())

def parameter_learning(model, parameters):
    clf = GridSearchCV(model, tuned_parameters, cv=5, verbose=3)
    clf.fit(X_train, y_train)

    print("Best parameters set found on development set:")
    print()
    print(clf.best_params_)
    print()

    y_true, y_pred = y_test, clf.predict(X_test)
    print("Accuracy on held-out data: " + str(accuracy_score(y_true, y_pred)))
    print("Recall on held-out data: " + str(recall_score(y_true, y_pred)))
    print("Precision on held-out data: " + str(precision_score(y_true, y_pred)))

    print()

tuned_parameters = [{
    'kernel': ['rbf'],
    'gamma': [1e-2, 1e-3, 1e-4],
    'C': [0.1, 1, 10, 100, 1000]
}, {
    'kernel': ['linear'],
    'C': [0.1, 1, 10]
}]
parameter_learning(SVC(), tuned_parameters)
# tuned_parameters = [{
#     'criterion': ["gini", "entropy"],
#     'max_depth': range(3, 22, 3),
#     'max_features': range(3, 22, 3),
# }]
# parameter_learning(DecisionTreeClassifier(), tuned_parameters)

# Report accuracy on held-out data
# clf = DecisionTreeClassifier(criterion="entropy", max_depth=10)
# clf.fit(X_train, y_train)

# # Save model to disk
# best_classifier = DecisionTreeClassifier(criterion="entropy", max_depth=10)
# best_classifier.fit(X, y)
# with open('classifier.pickle', 'wb') as f: # 'wb' stands for 'write bytes'
#     pickle.dump(best_classifier, f)
