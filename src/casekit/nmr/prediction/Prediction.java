/*
 * The MIT License
 *
 * Copyright 2019 Michael Wenk [https://github.com/michaelwenk].
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package casekit.nmr.prediction;


import casekit.nmr.analysis.MultiplicitySectionsBuilder;
import casekit.nmr.elucidation.model.Detections;
import casekit.nmr.filterandrank.FilterAndRank;
import casekit.nmr.fragments.model.ConnectionTree;
import casekit.nmr.fragments.model.ConnectionTreeNode;
import casekit.nmr.hose.HOSECodeBuilder;
import casekit.nmr.model.*;
import casekit.nmr.utils.Statistics;
import casekit.nmr.utils.Utils;
import casekit.threading.MultiThreading;
import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.interfaces.IAtom;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.silent.SilentChemObjectBuilder;
import org.openscience.cdk.smiles.SmilesGenerator;
import org.openscience.cdk.tools.CDKHydrogenAdder;
import org.openscience.cdk.tools.manipulator.AtomContainerManipulator;
import org.openscience.nmrshiftdb.util.AtomUtils;
import org.openscience.nmrshiftdb.util.ExtendedHOSECodeGenerator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/**
 * @author Michael Wenk [https://github.com/michaelwenk]
 */
public class Prediction {

    private final static ExtendedHOSECodeGenerator extendedHOSECodeGenerator = new ExtendedHOSECodeGenerator();

    /**
     * Diastereotopic distinctions are not provided yet.
     *
     * @param hoseCodeShiftStatistics
     * @param structure
     * @param solvent
     * @param nucleus
     *
     * @return
     */
    public static DataSet predict1D(final Map<String, Map<String, Double[]>> hoseCodeShiftStatistics,
                                    final IAtomContainer structure, final String nucleus, final String solvent) {
        final int minMatchingSphere = 1;
        final Spectrum spectrum = new Spectrum();
        spectrum.setNuclei(new String[]{nucleus});
        spectrum.addMetaInfo("solvent", solvent);
        spectrum.setSignals(new ArrayList<>());
        final Assignment assignment = new Assignment();
        assignment.setNuclei(spectrum.getNuclei());
        assignment.initAssignments(0);

        final CDKHydrogenAdder hydrogenAdder = CDKHydrogenAdder.getInstance(SilentChemObjectBuilder.getInstance());
        String hoseCode, atomTypeSpectrum;
        Signal signal;
        Double shift;
        Integer addedSignalIndex;
        ConnectionTree connectionTree;

        try {
            AtomContainerManipulator.percieveAtomTypesAndConfigureAtoms(structure);
            Utils.convertExplicitToImplicitHydrogens(structure);
            hydrogenAdder.addImplicitHydrogens(structure);
            Utils.convertImplicitToExplicitHydrogens(structure);
            Utils.setAromaticityAndKekulize(structure);

            for (int i = 0; i
                    < structure.getAtomCount(); i++) {
                atomTypeSpectrum = Utils.getAtomTypeFromNucleus(nucleus);
                if (structure.getAtom(i)
                             .getSymbol()
                             .equals(atomTypeSpectrum)) {
                    connectionTree = HOSECodeBuilder.buildConnectionTree(structure, i, null);
                    shift = null;
                    for (int s = connectionTree.getMaxSphere(true); s
                            >= minMatchingSphere; s--) {
                        hoseCode = HOSECodeBuilder.buildHOSECode(structure, i, s, false);
                        if (hoseCodeShiftStatistics.containsKey(hoseCode)
                                && hoseCodeShiftStatistics.get(hoseCode)
                                                          .containsKey(solvent)) {
                            shift = hoseCodeShiftStatistics.get(hoseCode)
                                                           .get(solvent)[3]; // take median value
                            break;
                        }
                    }
                    signal = new Signal();
                    signal.setNuclei(spectrum.getNuclei());
                    signal.setEquivalencesCount(1);
                    if (atomTypeSpectrum.equals("C")) {
                        signal.setMultiplicity(Utils.getMultiplicityFromProtonsCount(
                                AtomContainerManipulator.countHydrogens(structure, structure.getAtom(i))));
                    }

                    signal.setKind("signal");
                    signal.setShifts(new Double[]{shift});
                    addedSignalIndex = spectrum.addSignal(signal);
                    if (addedSignalIndex
                            == null
                            || addedSignalIndex
                            >= assignment.getSetAssignmentsCount(0)) {
                        assignment.addAssignment(0, new int[]{i});
                    } else {
                        assignment.addAssignmentEquivalence(0, addedSignalIndex, i);
                    }
                }
            }
        } catch (final CDKException e) {
            e.printStackTrace();
            return null;
        }

        return new DataSet(structure, spectrum, assignment, new HashMap<>(), new HashMap<>());
    }

    /**
     * Predicts a 2D spectrum from two 1D spectra. Each 1D spectra needs to contain the same solvent information.
     * Diastereotopic distinctions are not provided yet ({@link #predict1D(Map, IAtomContainer, String, String)}).
     *
     * @param hoseCodeShiftStatistics HOSE code shift statistics
     * @param structure               structure to use for prediction
     * @param nuclei                  nuclei for 2D spectrum to predict
     * @param solvent                 solvent
     * @param minPathLength           minimal path length
     * @param maxPathLength           maximal path length
     *
     * @return
     *
     * @see #predict1D(Map, IAtomContainer, String, String)
     * @see #predict2D(IAtomContainer, Spectrum, Spectrum, Assignment, Assignment, int, int)
     */
    public static DataSet predict2D(final Map<String, Map<String, Double[]>> hoseCodeShiftStatistics,
                                    final IAtomContainer structure, final String[] nuclei, final String solvent,
                                    final int minPathLength, final int maxPathLength) {
        final DataSet predictionDim1 = predict1D(hoseCodeShiftStatistics, structure, nuclei[0], solvent);
        final DataSet predictionDim2 = predict1D(hoseCodeShiftStatistics, structure, nuclei[1], solvent);
        return Prediction.predict2D(structure, predictionDim1.getSpectrum()
                                                             .toSpectrum(), predictionDim2.getSpectrum()
                                                                                          .toSpectrum(),
                                    predictionDim1.getAssignment(), predictionDim2.getAssignment(), minPathLength,
                                    maxPathLength);
    }

    /**
     * Predicts a 2D spectrum from two 1D spectra. <br>
     * Each 1D spectra needs to contain the same solvent information. <br>
     * Note: If 1H is used then it needs to be in first dimension, e.g. 1H, 13C.
     *
     * @param structure      structure to use for prediction
     * @param spectrumDim1   1D spectrum of first dimension
     * @param spectrumDim2   1D spectrum of second dimension
     * @param assignmentDim1 1D assignment of first dimension
     * @param assignmentDim2 1D assignment of second dimension
     * @param minPathLength  minimal path length
     * @param maxPathLength  maximal path length
     *
     * @return
     */
    public static DataSet predict2D(final IAtomContainer structure, final Spectrum spectrumDim1,
                                    final Spectrum spectrumDim2, final Assignment assignmentDim1,
                                    final Assignment assignmentDim2, final int minPathLength, final int maxPathLength) {
        if (!spectrumDim1.getMeta()
                         .get("solvent")
                         .equals(spectrumDim2.getMeta()
                                             .get("solvent"))) {
            return null;
        }
        final String[] nuclei2D = new String[]{spectrumDim1.getNuclei()[0], spectrumDim2.getNuclei()[0]};
        final String atomTypeDim1 = casekit.nmr.utils.Utils.getAtomTypeFromNucleus(spectrumDim1.getNuclei()[0]);
        final String atomTypeDim2 = casekit.nmr.utils.Utils.getAtomTypeFromNucleus(spectrumDim2.getNuclei()[0]);

        final Spectrum predictedSpectrum2D = new Spectrum();
        predictedSpectrum2D.setNuclei(nuclei2D);
        predictedSpectrum2D.setSignals(new ArrayList<>());
        predictedSpectrum2D.addMetaInfo("solvent", spectrumDim1.getMeta()
                                                               .get("solvent"));
        final Assignment assignment2D = new Assignment();
        assignment2D.setNuclei(predictedSpectrum2D.getNuclei());
        assignment2D.initAssignments(0);

        Signal signal2D;
        IAtom atom;
        Double shiftDim1, shiftDim2;
        int addedSignalIndex;
        ConnectionTree connectionTree;
        List<ConnectionTreeNode> nodesInSphere;
        List<Integer> signalIndicesDim1, signalIndicesDim2;
        for (int i = 0; i
                < structure.getAtomCount(); i++) {
            atom = structure.getAtom(i);
            if (atom.getSymbol()
                    .equals(atomTypeDim1)) {
                connectionTree = HOSECodeBuilder.buildConnectionTree(structure, i, maxPathLength);
                for (int s = minPathLength; s
                        <= connectionTree.getMaxSphere(false); s++) {
                    nodesInSphere = connectionTree.getNodesInSphere(s, false);
                    for (final ConnectionTreeNode nodeInSphere : nodesInSphere) {
                        if (nodeInSphere.getAtom()
                                        .getSymbol()
                                        .equals(atomTypeDim2)) {
                            signal2D = new Signal();
                            signal2D.setNuclei(nuclei2D);
                            signal2D.setKind("signal");
                            signal2D.setEquivalencesCount(1);
                            // on first axis go through all possible assignments, i.e. in case of 1H
                            signalIndicesDim1 = assignmentDim1.getIndices(0, i);
                            for (final int signalIndexDim1 : signalIndicesDim1) {
                                shiftDim1 = spectrumDim1.getShift(signalIndexDim1, 0);
                                // on second axis go through all possible assignments, i.e. in case of 1H
                                signalIndicesDim2 = assignmentDim2.getIndices(0, nodeInSphere.getKey());
                                for (final int signalIndexDim2 : signalIndicesDim2) {
                                    shiftDim2 = spectrumDim2.getShift(signalIndexDim2, 0);
                                    signal2D.setShifts(new Double[]{shiftDim1, shiftDim2});
                                    // add 2D signal
                                    addedSignalIndex = predictedSpectrum2D.addSignal(signal2D);
                                    if (addedSignalIndex
                                            >= assignment2D.getSetAssignmentsCount(0)) {
                                        assignment2D.addAssignment(0, new int[]{i});
                                        assignment2D.addAssignment(1, new int[]{nodeInSphere.getKey()});
                                    } else {
                                        assignment2D.addAssignmentEquivalence(0, addedSignalIndex, i);
                                        assignment2D.addAssignmentEquivalence(1, addedSignalIndex,
                                                                              nodeInSphere.getKey());
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return new DataSet(structure, predictedSpectrum2D, assignment2D, new HashMap<>(), new HashMap<>());
    }

    public static DataSet predictHSQC(final IAtomContainer structure, final Spectrum spectrumDim1,
                                      final Spectrum spectrumDim2, final Assignment assignmentDim1,
                                      final Assignment assignmentDim2) {
        return predict2D(structure, spectrumDim1, spectrumDim2, assignmentDim1, assignmentDim2, 1, 1);
    }

    public static DataSet predictHSQCEdited(final IAtomContainer structure, final Spectrum spectrumDim1,
                                            final Spectrum spectrumDim2, final Assignment assignmentDim1,
                                            final Assignment assignmentDim2) {
        final DataSet dataSet = predictHSQC(structure, spectrumDim1, spectrumDim2, assignmentDim1, assignmentDim2);
        final Spectrum spectrum = dataSet.getSpectrum()
                                         .toSpectrum();

        final String atomTypeDim2 = Utils.getAtomTypeFromSpectrum(spectrumDim2, 0);
        IAtom atom;
        int explicitHydrogensCount;
        for (int i = 0; i
                < spectrum.getSignalCount(); i++) {
            atom = structure.getAtom(dataSet.getAssignment()
                                            .getAssignment(1, i, 0));
            if (!atom.getSymbol()
                     .equals(atomTypeDim2)) {
                continue;
            }
            explicitHydrogensCount = AtomContainerManipulator.countExplicitHydrogens(structure, atom);
            if (explicitHydrogensCount
                    == 2) {
                spectrum.getSignal(i)
                        .setPhase(-1);
            } else if (explicitHydrogensCount
                    == 1
                    || explicitHydrogensCount
                    == 3) {
                spectrum.getSignal(i)
                        .setPhase(1);
            }
        }

        return dataSet;
    }

    public static List<DataSet> predict1DByStereoHOSECodeAndFilter(final Spectrum querySpectrum,
                                                                   final double shiftTolerance,
                                                                   final double maximumAverageDeviation,
                                                                   final boolean checkMultiplicity,
                                                                   final boolean checkEquivalencesCount,
                                                                   final boolean allowLowerEquivalencesCount,
                                                                   final int maxSphere,
                                                                   final List<IAtomContainer> structureList,
                                                                   final Map<String, Map<String, Double[]>> hoseCodeShiftStatistics,
                                                                   final Map<String, int[]> multiplicitySectionsSettings,
                                                                   final int nThreads) {


        return predict1DByStereoHOSECodeAndFilter(querySpectrum, shiftTolerance, maximumAverageDeviation,
                                                  checkMultiplicity, checkEquivalencesCount,
                                                  allowLowerEquivalencesCount, null, maxSphere, structureList,
                                                  hoseCodeShiftStatistics, multiplicitySectionsSettings, nThreads);
    }

    public static List<DataSet> predict1DByStereoHOSECodeAndFilter(final Spectrum querySpectrum,
                                                                   final double shiftTolerance,
                                                                   final double maximumAverageDeviation,
                                                                   final boolean checkMultiplicity,
                                                                   final boolean checkEquivalencesCount,
                                                                   final boolean allowLowerEquivalencesCount,
                                                                   final Detections detections, final int maxSphere,
                                                                   final List<IAtomContainer> structureList,
                                                                   final Map<String, Map<String, Double[]>> hoseCodeShiftStatistics,
                                                                   final Map<String, int[]> multiplicitySectionsSettings,
                                                                   final int nThreads) {
        final MultiplicitySectionsBuilder multiplicitySectionsBuilder = new MultiplicitySectionsBuilder();
        multiplicitySectionsBuilder.setMinLimit(multiplicitySectionsSettings.get(querySpectrum.getNuclei()[0])[0]);
        multiplicitySectionsBuilder.setMaxLimit(multiplicitySectionsSettings.get(querySpectrum.getNuclei()[0])[1]);
        multiplicitySectionsBuilder.setStepSize(multiplicitySectionsSettings.get(querySpectrum.getNuclei()[0])[2]);

        List<DataSet> dataSetList = new ArrayList<>();
        try {
            final ConcurrentLinkedQueue<DataSet> dataSetConcurrentLinkedQueue = new ConcurrentLinkedQueue<>();
            final List<Callable<DataSet>> callables = new ArrayList<>();
            for (final IAtomContainer structure : structureList) {
                callables.add(
                        () -> predict1DByStereoHOSECodeAndFilter(structure, querySpectrum, maxSphere, shiftTolerance,
                                                                 maximumAverageDeviation, checkMultiplicity,
                                                                 checkEquivalencesCount, allowLowerEquivalencesCount,
                                                                 detections, hoseCodeShiftStatistics,
                                                                 multiplicitySectionsBuilder));
            }
            final Consumer<DataSet> consumer = (dataSet) -> {
                if (dataSet
                        != null) {
                    dataSetConcurrentLinkedQueue.add(dataSet);
                }
            };
            MultiThreading.processTasks(callables, consumer, nThreads, 5);
            dataSetList = new ArrayList<>(dataSetConcurrentLinkedQueue);
        } catch (final Exception e) {
            e.printStackTrace();
        }

        return dataSetList;
    }

    private static DataSet predict1DByStereoHOSECodeAndFilter(final IAtomContainer structure,
                                                              final Spectrum querySpectrum, final int maxSphere,
                                                              final double shiftTolerance,
                                                              final double maxAverageDeviation,
                                                              final boolean checkMultiplicity,
                                                              final boolean checkEquivalencesCount,
                                                              final boolean allowLowerEquivalencesCount,
                                                              final Detections detections,
                                                              final Map<String, Map<String, Double[]>> hoseCodeShiftStatistics,
                                                              final MultiplicitySectionsBuilder multiplicitySectionsBuilder) {
        final String nucleus = querySpectrum.getNuclei()[0];
        final DataSet dataSet = predict1DByStereoHOSECode(structure, nucleus, maxSphere, hoseCodeShiftStatistics);
        if (dataSet
                != null) {
            return FilterAndRank.checkDataSet(dataSet, querySpectrum, shiftTolerance, maxAverageDeviation,
                                              checkMultiplicity, checkEquivalencesCount, allowLowerEquivalencesCount,
                                              multiplicitySectionsBuilder, true, detections);
        }

        return null;
    }

    public static DataSet predict1DByStereoHOSECode(final IAtomContainer structure, final String nucleus,
                                                    final int maxSphere,
                                                    final Map<String, Map<String, Double[]>> hoseCodeShiftStatistics) {

        final String atomType = Utils.getAtomTypeFromNucleus(nucleus);

        final Assignment assignment;
        Signal signal;
        Map<String, Double[]> hoseCodeObjectValues;
        double predictedShift;
        String hoseCode;
        Double[] statistics;
        int signalIndex, sphere, count;
        Double min, max;
        List<Double> medians;

        try {
            Utils.placeExplicitHydrogens(structure);
            Utils.setAromaticityAndKekulize(structure);

            final DataSet dataSet = Utils.atomContainerToDataSet(structure, false);

            final Spectrum predictedSpectrum = new Spectrum();
            predictedSpectrum.setNuclei(new String[]{nucleus});
            predictedSpectrum.setSignals(new ArrayList<>());

            final Map<Integer, List<Integer>> assignmentMap = new HashMap<>();
            final Map<Integer, Double[]> predictionMeta = new HashMap<>();
            for (int i = 0; i
                    < structure.getAtomCount(); i++) {
                if (!structure.getAtom(i)
                              .getSymbol()
                              .equals(atomType)) {
                    continue;
                }
                medians = new ArrayList<>();
                sphere = maxSphere;
                count = 0;
                min = null;
                max = null;
                while (sphere
                        >= 1) {
                    try {
                        hoseCode = extendedHOSECodeGenerator.getHOSECode(structure, structure.getAtom(i), sphere);
                        hoseCodeObjectValues = hoseCodeShiftStatistics.get(hoseCode);
                        if (hoseCodeObjectValues
                                != null) {
                            for (final Map.Entry<String, Double[]> solventEntry : hoseCodeObjectValues.entrySet()) {
                                statistics = hoseCodeObjectValues.get(solventEntry.getKey());
                                medians.add(statistics[3]);
                                count += statistics[0].intValue();
                                min = min
                                              == null
                                      ? statistics[1]
                                      : Double.min(min, statistics[1]);
                                max = max
                                              == null
                                      ? statistics[4]
                                      : Double.max(max, statistics[4]);
                            }
                            break;
                        }
                    } catch (final Exception ignored) {
                    }
                    sphere--;
                }
                if (medians.isEmpty()) {
                    continue;
                }
                predictedShift = Statistics.getMean(medians);
                signal = new Signal();
                signal.setNuclei(new String[]{nucleus});
                signal.setShifts(new Double[]{predictedShift});
                signal.setMultiplicity(Utils.getMultiplicityFromProtonsCount(
                        AtomUtils.getHcount(structure, structure.getAtom(i)))); // counts explicit H
                signal.setEquivalencesCount(1);

                signalIndex = predictedSpectrum.addSignal(signal);

                assignmentMap.putIfAbsent(signalIndex, new ArrayList<>());
                assignmentMap.get(signalIndex)
                             .add(i);

                if (!predictionMeta.containsKey(signalIndex)) {
                    predictionMeta.put(signalIndex, new Double[]{(double) sphere, (double) count, min, max});
                }
            }

            Utils.convertExplicitToImplicitHydrogens(structure);
            dataSet.setStructure(new StructureCompact(structure));
            dataSet.addMetaInfo("smiles", SmilesGenerator.generic()
                                                         .create(structure));

            dataSet.setSpectrum(new SpectrumCompact(predictedSpectrum));
            assignment = new Assignment();
            assignment.setNuclei(predictedSpectrum.getNuclei());
            assignment.initAssignments(predictedSpectrum.getSignalCount());

            for (final Map.Entry<Integer, List<Integer>> entry : assignmentMap.entrySet()) {
                for (final int atomIndex : entry.getValue()) {
                    assignment.addAssignmentEquivalence(0, entry.getKey(), atomIndex);
                }
            }
            dataSet.setAssignment(assignment);

            dataSet.addAttachment("predictionMeta", predictionMeta);

            return dataSet;
        } catch (final Exception e) {
            e.printStackTrace();
        }

        return null;
    }
}
