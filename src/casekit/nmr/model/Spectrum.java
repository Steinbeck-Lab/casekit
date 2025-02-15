/*
 * This class was adopted and modified from an earlier version by Christoph Steinbeck
 */


/*
 * The MIT License
 *
 * Copyright 2018 Michael Wenk [https://github.com/michaelwenk].
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
package casekit.nmr.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Michael Wenk [https://github.com/michaelwenk]
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class Spectrum {

    private String[] nuclei;
    private Map<String, String> meta;
    private List<Signal> signals;
    private int signalCount;

    public void addMetaInfo(final String key, final String value) {
        if (this.meta
                == null) {
            this.meta = new HashMap<>();
        }
        this.meta.put(key, value);
    }

    public void removeMetaInfo(final String key) {
        this.meta.remove(key);
    }

    public int getNDim() {
        return this.getNuclei().length;
    }

    public boolean containsDim(final int dim) {
        return dim
                >= 0
                && dim
                < this.getNDim();
    }

    public boolean compareNuclei(final String[] nuclei) {
        return Arrays.equals(this.getNuclei(), nuclei);
    }

    public int getSignalCountWithEquivalences() {
        int sum = 0;
        for (final Signal signal : this.getSignals()) {
            sum += signal.getEquivalencesCount();
        }
        return sum;
    }

    /**
     * Simply adds a signal to the end of the signal list without equivalence checks.
     *
     * @param signal signal to add
     *
     * @return
     */
    public boolean addSignalWithoutEquivalenceSearch(final Signal signal) {
        if ((signal
                == null)
                || !this.compareNuclei(signal.getNuclei())) {
            return false;
        }
        // add signal at the end of signal list
        this.signals.add(signal);
        this.signalCount++;

        return true;
    }

    /**
     * Adds a signal to this spectrum with pickPrecision of 0 and enabled multiplicity check for equivalence search.
     *
     * @param signal signal to add
     *
     * @return
     *
     * @see #addSignal(Signal, double[], boolean)
     */
    public Integer addSignal(final Signal signal) {
        final double[] pickPrecisions = new double[signal.getNDim()];
        for (int dim = 0; dim
                < signal.getNDim(); dim++) {
            pickPrecisions[dim] = 0.0;
        }
        return this.addSignal(signal, pickPrecisions, true);
    }

    /**
     * Checks for equivalent signals in all dimensions.
     *
     * @param signal            signal
     * @param pickPrecisions    picking precision per dimension
     * @param checkMultiplicity whether multiplicity has to be checked too
     *
     * @return
     */
    public List<Integer> checkForEquivalences(final Signal signal, final double[] pickPrecisions,
                                              final boolean checkMultiplicity) {
        if (signal.getShift(0)
                == null) {
            return null;
        }
        // check for equivalent signals in all dimensions
        final List<Integer> closestSignalIndexList = this.pickByClosestShift(signal.getShift(0), 0, pickPrecisions[0]);
        for (int dim = 1; dim
                < this.getNDim(); dim++) {
            closestSignalIndexList.retainAll(this.pickByClosestShift(signal.getShift(dim), dim, pickPrecisions[dim]));
        }
        if (checkMultiplicity) {
            closestSignalIndexList.retainAll(this.pickByMultiplicity(signal.getMultiplicity()));
        }

        return closestSignalIndexList;
    }

    /**
     * Adds a signal to this spectrum and stores an equivalent signal index.
     *
     * @param signal            signal to add
     * @param pickPrecisions    precisions per dimension to find equivalent signals to store in
     * @param checkMultiplicity indicates whether to compare the multiplicity of signal
     *                          to add while searching for equivalences
     *
     * @return
     */
    public Integer addSignal(final Signal signal, final double[] pickPrecisions, final boolean checkMultiplicity) {
        if ((signal
                == null)
                || !this.compareNuclei(signal.getNuclei())) {
            return null;
        }

        final List<Integer> closestSignalIndexList = this.checkForEquivalences(signal, pickPrecisions,
                                                                               checkMultiplicity);
        if (closestSignalIndexList
                == null) {
            return null;
        }
        // if no equivalent signal was found then just add as new signal
        if (closestSignalIndexList.isEmpty()) {
            this.addSignalWithoutEquivalenceSearch(signal);
            return this.getSignalCount()
                    - 1;
        }
        // otherwise store as equivalence (in first hit only)
        final Signal closestSignal = this.getSignal(closestSignalIndexList.get(0));
        closestSignal.setEquivalencesCount(closestSignal.getEquivalencesCount()
                                                   + signal.getEquivalencesCount());
        return closestSignalIndexList.get(0);
    }

    public boolean removeSignal(final Signal signal) {
        return this.removeSignal(this.getSignalIndex(signal));
    }

    public boolean removeSignal(final int signalIndex) {
        if (!this.checkSignalIndex(signalIndex)) {
            return false;
        }
        if (this.signals.remove(signalIndex)
                != null) {
            this.signalCount--;

            return true;
        }

        return false;
    }

    private boolean checkSignalIndex(final Integer signalIndex) {
        return (signalIndex
                != null)
                && (signalIndex
                >= 0)
                && (signalIndex
                < this.getSignalCount());
    }

    /**
     * Returns a NMR Signal at position number in the signal list
     *
     * @param signalIndex
     *
     * @return
     */
    public Signal getSignal(final int signalIndex) {
        if (!this.checkSignalIndex(signalIndex)) {
            return null;
        }

        return this.signals.get(signalIndex);
    }

    /**
     * Sets a NMR Signal at position number in the signal list
     *
     * @param signalIndex signal index in list
     * @param signal      signal
     *
     * @return
     */
    public boolean setSignal(final int signalIndex, final Signal signal) {
        if (!this.checkSignalIndex(signalIndex)
                || !this.compareNuclei(signal.getNuclei())) {
            return false;
        }

        this.signals.set(signalIndex, signal);

        return true;
    }

    public Double getShift(final int signalIndex, final int dim) {
        if (!this.checkSignalIndex(signalIndex)) {
            return null;
        }

        return this.getSignal(signalIndex)
                   .getShift(dim);
    }

    public List<Double> getShifts(final int dim) {
        return this.getSignals()
                   .stream()
                   .map(signal -> signal.getShift(dim))
                   .collect(Collectors.toList());
    }

    public String getMultiplicity(final int signalIndex) {
        if (!this.checkSignalIndex(signalIndex)) {
            return null;
        }

        return this.getSignal(signalIndex)
                   .getMultiplicity();
    }

    public Boolean hasEquivalences(final int signalIndex) {
        if (!this.checkSignalIndex(signalIndex)) {
            return null;
        }

        return this.getEquivalencesCount(signalIndex)
                > 1;
    }

    public Integer getEquivalencesCount(final int signalIndex) {
        if (!this.checkSignalIndex(signalIndex)) {
            return null;
        }

        return this.getSignal(signalIndex)
                   .getEquivalencesCount();
    }

    public List<Integer> getEquivalencesCounts() {
        return this.getSignals()
                   .stream()
                   .map(Signal::getEquivalencesCount)
                   .collect(Collectors.toList());
    }

    /**
     * Returns the position of an NMRSignal the List
     *
     * @param signal
     *
     * @return
     */
    public int getSignalIndex(final Signal signal) {
        for (int s = 0; s
                < this.signals.size(); s++) {
            if (this.signals.get(s)
                    == signal) {
                return s;
            }
        }
        return -1;
    }

    /**
     * Returns the indices of signals with same multiplicity (even null values).
     *
     * @param multiplicity multiplicity to search for
     *
     * @return
     */
    public List<Integer> pickByMultiplicity(final String multiplicity) {
        final List<Integer> matchIndices = new ArrayList<>();
        for (int s = 0; s
                < this.getSignalCount(); s++) {
            if ((this.getSignal(s)
                     .getMultiplicity()
                    == null
                    && multiplicity
                    == null)
                    || (this.getSignal(s)
                            .getMultiplicity()
                    != null
                    && this.getSignal(s)
                           .getMultiplicity()
                           .equals(multiplicity))) {
                matchIndices.add(s);
            }
        }

        return matchIndices;
    }

    /**
     * Returns the signal index (or indices) closest to the given shift. If no signal is found within the interval
     * defined by {@code pickPrecision}, an empty list is returned.
     *
     * @param shift         query shift
     * @param dim           dimension in spectrum to look in
     * @param pickPrecision tolerance value for search window
     *
     * @return
     */
    public List<Integer> pickByClosestShift(final double shift, final int dim, final double pickPrecision) {
        final List<Integer> matchIndices = new ArrayList<>();
        if (!this.containsDim(dim)) {
            return matchIndices;
        }
        double minDiff = pickPrecision;
        // detect the minimal difference between a signal shift to the given query shift
        for (int s = 0; s
                < this.getSignalCount(); s++) {
            if (Math.abs(this.getShift(s, dim)
                                 - shift)
                    < minDiff) {
                minDiff = Math.abs(this.getShift(s, dim)
                                           - shift);
            }
        }
        for (int s = 0; s
                < this.getSignalCount(); s++) {
            if (Math.abs(this.getShift(s, dim)
                                 - shift)
                    == minDiff) {
                matchIndices.add(s);
            }
        }

        return matchIndices;
    }

    /**
     * Returns a list of signal indices within the interval defined by
     * pickPrecision. That list is sorted by the distances to the query shift.
     * If none is found an empty ArrayList is returned.
     *
     * @param shift         query shift
     * @param dim           dimension in spectrum to look in
     * @param pickPrecision tolerance value for search window
     *
     * @return
     */
    public List<Integer> pickSignals(final Double shift, final int dim, final double pickPrecision) {
        final List<Integer> pickedSignals = new ArrayList<>();
        if (!this.containsDim(dim)) {
            return pickedSignals;
        }
        for (int s = 0; s
                < this.getSignalCount(); s++) {
            if (Math.abs(this.getShift(s, dim)
                                 - shift)
                    <= pickPrecision) {
                pickedSignals.add(s);
            }
        }
        // sort signal indices by distance to query shift
        pickedSignals.sort(Comparator.comparingDouble(pickedSignalIndex -> Math.abs(shift
                                                                                            - this.getShift(
                pickedSignalIndex, dim))));

        return pickedSignals;
    }

    public Spectrum buildClone() {
        final Spectrum clone = new Spectrum();
        clone.setNuclei(this.getNuclei()
                            .clone());
        clone.setSignals(new ArrayList<>());
        for (int i = 0; i
                < this.getSignalCount(); i++) {
            clone.addSignal(this.getSignal(i)
                                .buildClone());
        }
        clone.setMeta(new HashMap<>(this.getMeta()));

        return clone;
    }

    @Override
    public String toString() {
        return "Spectrum{"
                + "nuclei="
                + Arrays.toString(this.nuclei)
                + ", meta="
                + this.meta
                + ", signals="
                + this.signals
                + ", signalCount="
                + this.signalCount
                + '}';
    }
}
