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
package casekit.NMR;


import casekit.NMR.model.Signal;
import casekit.NMR.model.Spectrum;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import org.w3c.dom.Document;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.commons.lang3.StringUtils;
import org.openscience.cdk.CDKConstants;
import org.openscience.cdk.aromaticity.Aromaticity;
import org.openscience.cdk.aromaticity.ElectronDonation;
import org.openscience.cdk.depict.DepictionGenerator;
import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.graph.CycleFinder;
import org.openscience.cdk.graph.Cycles;
import org.openscience.cdk.interfaces.IAtom;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.interfaces.IAtomType;
import org.openscience.cdk.interfaces.IBond;
import org.openscience.cdk.interfaces.IMolecularFormula;
import org.openscience.cdk.io.SDFWriter;
import org.openscience.cdk.io.iterator.IteratingSDFReader;
import org.openscience.cdk.qsar.descriptors.atomic.AtomValenceDescriptor;
import org.openscience.cdk.silent.SilentChemObjectBuilder;
import org.openscience.cdk.tools.manipulator.AtomContainerManipulator;
import org.openscience.cdk.tools.manipulator.MolecularFormulaManipulator;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 *
 * @author Michael Wenk [https://github.com/michaelwenk]
 */
public class Utils {
        
    /**
     * Splits an SDF into single molecular files and converts each of them into the LSD substructure format. 
     * Therefore, the mol2ab executable provided by LSD is required.
     * 
     * @param pathSDF path to SDF to split
     * @param pathOut path to directory which should contain the splitted and converted structure files
     * @param pathMol2ab path to mol2ab executable provided by LSD
     * @throws FileNotFoundException
     * @throws CDKException
     * @throws IOException
     */
    public static void SDFtoLSD(final String pathSDF, final String pathOut, final String pathMol2ab) throws FileNotFoundException, CDKException, IOException{
        
        
        System.out.println("Conversion from SDF format to LSD format... ");
        

        IAtomContainer ac;
        
        IteratingSDFReader iterator = new IteratingSDFReader(
                new FileReader(pathSDF),
                SilentChemObjectBuilder.getInstance()
        );

        
        File fout;
        FileOutputStream fos;
        BufferedWriter bw;
        File foutPilot = new File(pathOut + "/pilot");
        FileOutputStream fosPilot = new FileOutputStream(foutPilot);
        BufferedWriter bwPilot = new BufferedWriter(new OutputStreamWriter(fosPilot));
        
        
        int i = 0;
        while (iterator.hasNext()) {
            i++;
            ac = iterator.next();
            String molID = String.valueOf(i);//(String) ac.getProperties().get("cdk:Remark");
//            molID = molID.replace(" ", "_");
            fout = new File(pathOut + "/" + molID + ".sdf");
            fos = new FileOutputStream(fout);
            bw = new BufferedWriter(new OutputStreamWriter(fos));
            
            SDFWriter wtr = new SDFWriter(bw);
//            Properties sdfWriterProps = new Properties();
//            sdfWriterProps.put("WriteAromaticBondTypes", "true");
//            wtr.addChemObjectIOListener(new PropertiesListener(sdfWriterProps));
//            wtr.customizeJob();
            
            wtr.write(ac);
            wtr.close();
            bw.close();
            
            bwPilot.write(molID + " " + fout.getPath());
            bwPilot.newLine();
            
        }
       
        iterator.close();
        bwPilot.close();
        System.out.println("Input file contained " + i + " molecules!\nSingle files created!");
         
        
        // should be replaced by e.g. the process command because:
        // 1. for very long files the program ends long before the conversion process (command) ends
        // 2. no control or output here
        Runtime.getRuntime().exec(pathMol2ab + "/mol2ab " + pathOut + " " + foutPilot.getPath());
        
        
        System.out.println("Conversion from SDF format to LSD format... DONE!");
        
    }
    
    
    /**
     * Returns a hashmap constisting of lists of atom indices in an atom container.
     * This is done for all atom types (e.g. C or Br) in given atom container.
     *
     * @param ac IAtomContainer to look in 
     * @return
     * @see #getAtomTypeIndicesByElement(org.openscience.cdk.interfaces.IAtomContainer, java.lang.String) 
     */
    public static HashMap<String, ArrayList<Integer>> getAtomTypeIndices(final IAtomContainer ac) {
        
        final HashMap<String, ArrayList<Integer>> atomTypeIndices = new HashMap<>();
        final HashSet<String> atomTypes = new HashSet<>();
        for (final IAtom heavyAtom : AtomContainerManipulator.getHeavyAtoms(ac)) {
            atomTypes.add(heavyAtom.getSymbol());
        }
        for (final String atomType : atomTypes) {
            atomTypeIndices.put(atomType, Utils.getAtomTypeIndicesByElement(ac, atomType));
        }
        
        return atomTypeIndices;
    }
    
    
    /**
     * Returns a list of atom indices in an atom container for a given atom 
     * type (e.g. C or Br)
     *
     * @param ac IAtomContainer to use for search
     * @param atomType Atom type to find in atom container 
     * @return
     */
    public static ArrayList<Integer> getAtomTypeIndicesByElement(final IAtomContainer ac, final String atomType){
        
        final ArrayList<Integer> indices = new ArrayList<>();
        for (int i = 0; i < ac.getAtomCount(); i++) {
            if(ac.getAtom(i).getSymbol().equals(atomType)){
                indices.add(i);
            }
        }
        
        return indices;
    }
    
    
    /**
     * Reads a specific column of a NMR peak table and stores it into an
     * ArrayList object.
     *
     * @param pathToCSV path to NMR peak table in CSV file format
     * @param column column index to select in peak table
     * @return ArrayList of Double shift values
     * @throws IOException
     */
    public static ArrayList<Double> parseCSV(final String pathToCSV, final int column) throws IOException {

        final ArrayList<Double> shifts = new ArrayList<>();
        String line;
        String[] tokens;
        BufferedReader fileReader = new BufferedReader(new FileReader(pathToCSV));
        while ((line = fileReader.readLine()) != null) {
            tokens = line.split(",");
            // get shift value 
            if (tokens[column].trim().matches("^[+|-]{0,1}\\d+\\.{0,1}\\d*")) {
                shifts.add(Double.parseDouble(tokens[column].trim()));
            }
        }
        fileReader.close();

        return shifts;
    }
    

    /**
     * Reads specific columns of one NMR peak table to obtain a Spectrum class
     * object and set intensitiy values.
     * The number of columns and atom types has to be the same and defines the
     * dimension of the returning spectrum.
     *
     * @param pathToCSV path to NMR peak table in CSV file format
     * @param columns column indices to select in peak table
     * @param atomTypes atom types (element) for each dimension
     * @param intensityColumnIndex column index for intensity values
     * @return Spectrum class object containing the peak lists
     * @throws IOException
     */
    public static Spectrum CSVtoSpectrum(final String pathToCSV, final int[] columns, final String[] atomTypes, final int intensityColumnIndex) throws IOException {
        
        // assumes the same number of selected columns (dimensions) and atom types
        if(columns.length != atomTypes.length){
            return null;
        }
        final String[] nuclei = new String[columns.length];
        for (int col = 0; col < columns.length; col++) {
            nuclei[col] = Utils.getIsotopeIdentifier(atomTypes[col]);
        }
        final Spectrum spectrum = new Spectrum(nuclei);
        ArrayList<Double> shiftList;
        for (int col = 0; col < columns.length; col++) {
            shiftList = Utils.parseCSV(pathToCSV, columns[col]);
            if(col == 0){
                for (int i = 0; i < shiftList.size(); i++) {
                    spectrum.addSignal(new Signal(spectrum.getNuclei()));
                }
            }
            if(!spectrum.setShifts(shiftList, col)){
                return null;
            }
        }
        spectrum.setIntensities(parseCSV(pathToCSV, intensityColumnIndex));
 
        return spectrum;
     }
    
    
    /**
     * Reads a NMR peak XML file and returns one attribute of nodes (column) into an
     * ArrayList object.
     * The XML file must be in Bruker's TopSpin format.
     *
     * @param pathToXML Path to XML file
     * @param dim number of dimensions of given data 1 (1D) or 2 (2D)
     * @param attribute which attribute index in XML peak nodes should be used: 
     * 1 (shift of 1st dimension), 2 (shift of 2nd dimension if 2D data, 
     * intensity if 1D data) or 3 (intensity if 2D data)
     * 
     * @return ArrayList of Double shift values
     * @throws IOException
     * @throws javax.xml.parsers.ParserConfigurationException
     * @throws org.xml.sax.SAXException
     */
    public static ArrayList<Double> parseXML(final String pathToXML, final int dim, final int attribute) throws IOException, ParserConfigurationException, SAXException {

        // assumes a attribute value between 1 and 3
        if(attribute < 1 || attribute > 3){
            return null;
        }
        
        final ArrayList<Double> shifts = new ArrayList<>();
        final DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
        final DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
        final Document doc = docBuilder.parse(new File(pathToXML));

        // normalize text representation
        doc.getDocumentElement().normalize();
        final NodeList peakLists = doc.getElementsByTagName("Peak" + dim + "D");
        for (int i = 0; i < peakLists.getLength(); i++) {
            shifts.add(Double.parseDouble(peakLists.item(i).getAttributes().item(attribute - 1).getNodeValue()));
        }

        return shifts;
    }
    
    
    /**
     * Reads specific columns of NMR XML files to obtain a Spectrum class
     * object.
     * The XML file must be in Bruker's TopSpin format.
     *
     * @param pathToXML path to NMR XML file in Bruker's TopSpin XML file format
     * @param ndim number of dimensions: 1 (1D) or 2 (2D)
     * @param attributes which attribute indices in XML peak nodes should be used: 
     * 1 (shift of 1st dimension), 2 (shift of 2nd dimension if 2D data)
     * @param atomTypes atom types (element) for each dimension
     * @return Spectrum class object containing the selected peak lists
     * @throws IOException
     * @throws javax.xml.parsers.ParserConfigurationException
     * @throws org.xml.sax.SAXException
     */
    public static Spectrum XMLtoSpectrum(final String pathToXML, final int ndim, final int[] attributes, final String[] atomTypes) throws IOException, ParserConfigurationException, SAXException {
        
        // assumes the same number of dims, attributes and atom types and a maximum number of dims of 2
        if((ndim != attributes.length) || (ndim != atomTypes.length) || (attributes.length != atomTypes.length)
                || (ndim < 1 || ndim > 2)){
            return null;
        }
        final String[] nuclei = new String[ndim];
        for (int dim = 0; dim < ndim; dim++) {
            nuclei[dim] = Utils.getIsotopeIdentifier(atomTypes[dim]);
        }
        final Spectrum spectrum = new Spectrum(nuclei);
        ArrayList<Double> shiftList;
        for (int dim = 0; dim < ndim; dim++) {
            shiftList = Utils.parseXML(pathToXML, ndim, attributes[dim]);
            if(dim == 0){
                for (int i = 0; i < shiftList.size(); i++) {
                    spectrum.addSignal(new Signal(spectrum.getNuclei()));
                }
            }
            if(!spectrum.setShifts(shiftList, dim)){
                return null;
            }
        }
        spectrum.setIntensities(Utils.parseXML(pathToXML, ndim, ndim + 1));
        
        return spectrum;
     }
    
    public static String getAtomTypeFromSpectrum(final Spectrum spectrum, final int dim){
        if(spectrum.checkDimension(dim)){
            return Utils.getElementIdentifier(spectrum.getNuclei()[dim]);
        }
        
        return null;
    }
    
    public static int getDifferenceSpectrumSizeAndMolecularFormulaCount(final Spectrum spectrum, final IMolecularFormula molformula){
        final String atomType = Utils.getAtomTypeFromSpectrum(spectrum, 0);
        int atomsInMolFormula = 0;
        if(molformula != null){
            atomsInMolFormula = MolecularFormulaManipulator.getElementCount(molformula, atomType);
        }
        return atomsInMolFormula - spectrum.getSignalCount();
    }
    
    public static void editSignalsInSpectrum(final Spectrum spectrum, final IMolecularFormula molFormula) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in)); int n;
        final ArrayList<Integer> validIndices = new ArrayList<>();
        int diff = Utils.getDifferenceSpectrumSizeAndMolecularFormulaCount(spectrum, molFormula);
        // walk through all signals in spectrum add missing or to remove signals
        while (diff != 0) {
            // display all selectable signal indices in spectrum
            if(diff > 0){
                System.out.println("\n" + diff + " " + spectrum.getNuclei()[0] + " signals are missing!\nWhich signal is not unique?");
            } else {
                System.out.println("\n" + (-1 * diff) + " " + spectrum.getNuclei()[0] + " signals are to be removed!\nWhich signal is to remove?");
            }
            for (int s = 0; s < spectrum.getSignalCount(); s++) {
                System.out.print("index: " + s);
                for (int d = 0; d < spectrum.getDimCount(); d++) {
                    System.out.print(", shift dim " + (d+1) + ": " + spectrum.getShift(s, d));
                }
                System.out.println("");
                validIndices.add(s);
            }
            // get selected index by user input
            n = -1;
            while(!validIndices.contains(n)){
                System.out.println("Enter the index: ");
                n = Integer.parseInt(br.readLine());
            }
            // add/remove signals in spectrum
            if(diff > 0){
                spectrum.addSignal(spectrum.getSignal(validIndices.indexOf(n)).getClone());
                spectrum.setEquivalence(spectrum.getSignalCount() - 1, validIndices.indexOf(n));
            } else {
                spectrum.removeSignal(validIndices.indexOf(n));
            }
            diff = Utils.getDifferenceSpectrumSizeAndMolecularFormulaCount(spectrum, molFormula);
        }
    }
    
    /**
     * Corrects a match list regarding a given shift list and an atom container.
     * This is useful when two ore more shift values (e.g. DEPT shifts) match
     * with the same atom in the atom container. So the purpose here is to
     * enable more unambiguous matches. This method first looks for unambiguous
     * matches and calculates the median of the difference values between the
     * shift list values and the shifts of atom container. Then, all shift list
     * values are adjusted (+/-) with this median value.
     *
     * @param ac IAtomContainer to search
     * @param shifts Shift value list to match
     * @param matches Match list to correct
     * @param tol Tolerance value
     * @param atomType Element name (e.g. "C") which also occurrs in 
     * {@link Utils#getNMRShiftConstant(java.lang.String)}
     * @return
     */
    public static ArrayList<Integer> correctShiftMatches(final IAtomContainer ac, final ArrayList<Double> shifts, final ArrayList<Integer> matches, final double tol, final String atomType) {

        int matchIndex;
        // get differences of unique matches between query shift and ac shifts
        ArrayList<Double> diffs = new ArrayList<>();
        final HashSet<Integer> uniqueMatchIndicesSet = new HashSet<>(matches);
        for (Integer matchIndexAtomContainer : uniqueMatchIndicesSet) {
            if (Collections.frequency(matches, matchIndexAtomContainer) == 1) {
                matchIndex = matches.indexOf(matchIndexAtomContainer);
                if (matches.get(matchIndex) >= 0) {
                    diffs.add(shifts.get(matchIndex) - Double.parseDouble(ac.getAtom(matches.get(matchIndex)).getProperty(casekit.NMR.Utils.getNMRShiftConstant(atomType)).toString()));
                }
            }
        }
        // calculate the median of found unique match differences
        if (diffs.size() > 0) {
            final double median = casekit.NMR.Utils.getMedian(diffs);
            // add or subtract the median of the differences to all shift list values (input) and match again then
            for (int i = 0; i < shifts.size(); i++) {
                shifts.set(i, shifts.get(i) - median);
            }
            // rematch
            return casekit.NMR.Utils.findShiftMatches(ac, shifts, tol, atomType);
        } 
        
        return matches;
    }
    
    
    /**
     * Corrects a match list regarding a given shift list and an atom container.
     * This is useful when two ore more shift values (e.g. DEPT shifts) match
     * with the same atom in the atom container. So the purpose here is to
     * enable more unambiguous matches. This method first looks for unambiguous
     * matches and calculates the median of the difference values between the
     * shift list values and the shifts of atom container. Then, all shift list
     * values are adjusted (+/-) with this median value.
     *
     * @param shiftList1 Shift value list to search in
     * @param shiftList2 Shift value list to match in shiftList1
     * @param matchesInshiftList1 Match list to correct
     * @param tol Tolerance value
     * @return
     */
    public static ArrayList<Integer> correctShiftMatches(final ArrayList<Double> shiftList1, final ArrayList<Double> shiftList2, final ArrayList<Integer> matchesInshiftList1, final double tol) {

        int matchIndex;
        // get differences of unique matches between query shift and ac shifts
        ArrayList<Double> diffs = new ArrayList<>();
        final HashSet<Integer> uniqueMatchIndicesSet = new HashSet<>(matchesInshiftList1);
        for (final int uniqueMatchIndex : uniqueMatchIndicesSet) {
            if (Collections.frequency(matchesInshiftList1, uniqueMatchIndex) == 1) {
                matchIndex = matchesInshiftList1.indexOf(uniqueMatchIndex);
                if (matchesInshiftList1.get(matchIndex) >= 0) {
                    diffs.add(shiftList2.get(matchIndex) - shiftList1.get(matchesInshiftList1.get(matchIndex)));
                }
            }
        }
        // calculate the median of found unique match differences
        if (diffs.size() > 0) {
            final double median = casekit.NMR.Utils.getMedian(diffs);
            // add or subtract the median of the differences to all shift list values (input) and match again then
            for (int i = 0; i < shiftList2.size(); i++) {
                shiftList2.set(i, shiftList2.get(i) - median);
            }
            // rematch
            return casekit.NMR.Utils.findShiftMatches(shiftList1, shiftList2, tol);
        } 
        
        return matchesInshiftList1;
    }
    

    /**
     * Finds the matches with the lowest deviations between a given shift value
     * set and the atoms of an atom container. A tolerance value and NMRSHIFT
     * constant must be set.
     *
     * @param ac IAtomContainer to search
     * @param shiftList shift value list to match
     * @param tol Tolerance value [ppm]
     * @param atomType Element name (e.g. "C") which also occurrs in 
     * {@link Utils#getNMRShiftConstant(java.lang.String)}
     * @return List of match indices for every query shift within the IAtomContainer
     */
    public static ArrayList<Integer> findShiftMatches(final IAtomContainer ac, final ArrayList<Double> shiftList, final double tol, final String atomType) {

        final ArrayList<Integer> matches = new ArrayList<>();
        for (int i = 0; i < shiftList.size(); i++) {
            matches.add(casekit.NMR.Utils.findSingleShiftMatch(ac, shiftList.get(i), tol, atomType));
        }

        return matches;
    }

    /**
     * Finds the match with the lowest deviation between a given shift value and
     * the atoms of an atom container. A tolerance value and NMRSHIFT constant
     * must be set.
     *
     * @param ac IAtomContainer to search
     * @param shift Shift value to match [ppm]
     * @param tol Tolerance value [ppm]
     * @param atomType Element name (e.g. "C") which also occurrs in 
     * {@link Utils#getNMRShiftConstant(java.lang.String)}
     * @return Match index of a query shift within the IAtomContainer
     */
    public static int findSingleShiftMatch(final IAtomContainer ac, final double shift, final double tol, final String atomType) {

        int matchIndex = -1;
        double minDiff = tol, acShift;
        for (int k = 0; k < ac.getAtomCount(); k++) {
            // skip other atom types than given
            if (ac.getAtom(k).getProperty(casekit.NMR.Utils.getNMRShiftConstant(atomType)) == null) {
                continue;
            }
            // figure out the atom with lowest shift deviation 
            acShift = Double.parseDouble(ac.getAtom(k).getProperty(casekit.NMR.Utils.getNMRShiftConstant(atomType)).toString());
            if ((shift - tol <= acShift) && (acShift <= shift + tol) && (Math.abs(shift - acShift) < minDiff)) {
                minDiff = Math.abs(shift - acShift);
                matchIndex = k;
            }
        }

        return matchIndex;
    }
    
    
    /**
     * Finds the matches with the lowest deviations between two given shift value
     * lists. 
     *
     * @param shiftList1 shift value list to search in
     * @param shiftList2 shift value list to match in shiftList1
     * @param tol Tolerance value [ppm]
     * @return List of match indices within shiftList1
     */
    public static ArrayList<Integer> findShiftMatches(final ArrayList<Double> shiftList1, final ArrayList<Double> shiftList2, final double tol) {

        final ArrayList<Integer> matchesInShiftList1 = new ArrayList<>();
        for (int i = 0; i < shiftList2.size(); i++) {
            matchesInShiftList1.add(casekit.NMR.Utils.findSingleShiftMatch(shiftList1, shiftList2.get(i), tol));
        }

        return matchesInShiftList1;
    }
    
    
    /**
     * Finds the match with the lowest deviation between a given shift value and
     * a shift list. 
     *
     * @param shiftList Shift list to search in
     * @param shift Shift value [ppm] to find in ShiftList
     * @param tol Tolerance value [ppm]
     * @return Match index of a query shift within shiftList
     */
    public static int findSingleShiftMatch(final ArrayList<Double> shiftList, final double shift, final double tol) {

        int matchIndex = -1;
        double minDiff = tol;
        for (int k = 0; k < shiftList.size(); k++) {
            // figure out the shift with lowest deviation 
            if ((shift - tol <= shiftList.get(k)) && (shiftList.get(k) <= shift + tol) && (Math.abs(shift - shiftList.get(k)) < minDiff)) {
                minDiff = Math.abs(shift - shiftList.get(k));
                matchIndex = k;
            }
        }

        return matchIndex;
    }
    

    /**
     * Finds match indices between a given shift list from a peak table and an atom container.
     * Wrapper function for {@link #parsePeakTable(String, int)},
     * {@link #findShiftMatches(IAtomContainer, ArrayList, double, String)}
     * and
     * {@link #correctShiftMatches(IAtomContainer, ArrayList, ArrayList, double, String)}.
     *
     * @param ac IAtomContainer to search for matches
     * @param pathToPeakList Path to peak table
     * @param atomType Element name (e.g. "C") which also occurrs in 
     * {@link Utils#getNMRShiftConstant(java.lang.String)}
     * @param tol Tolerance value [ppm]
     * @param column Column number of shift values in peak table
     * @return Indices of matches for each shift within the IAtomContainer 
     * @throws IOException
     */
    public static ArrayList<Integer> matchShiftsFromPeakTable(final IAtomContainer ac, final String pathToPeakList, final String atomType, final double tol, final int column) throws IOException {

        final ArrayList<Double> shiftsAtomType = casekit.NMR.Utils.parseCSV(pathToPeakList, column);
        ArrayList<Integer> matchesAtomType = casekit.NMR.Utils.findShiftMatches(ac, shiftsAtomType, tol, atomType);
        matchesAtomType = casekit.NMR.Utils.correctShiftMatches(ac, shiftsAtomType, matchesAtomType, tol, atomType);

        return matchesAtomType;
    }
    
    
    /**
     * Finds match indices between a given shift list from a XML file and an
     * atom container. Wrapper function for {@link #parseXML(String, int)},
     * {@link #findShiftMatches(IAtomContainer, ArrayList, double, String)} and
     * {@link #correctShiftMatches(IAtomContainer, ArrayList, ArrayList, double, String)}.
     *
     * @param ac IAtomContainer to search for matches
     * @param pathToXML
     * @param atomType Element name (e.g. "C") which also occurrs in 
     * {@link Utils#getNMRShiftConstant(java.lang.String)}
     * @param tol Tolerance value [ppm]
     * @param ndim number of dimensions of given data 1 (1D) or 2 (2D)
     * @param attribute which attribute index in XML peak nodes should be used: 
     * 1 (shift of 1st dimension), 2 (shift of 2nd dimension if 2D data, 
     * intensity if 1D data) or 3 (intensity if 2D data)
     * @return Indices of matches for each shift within the IAtomContainer
     * @throws IOException
     * @throws javax.xml.parsers.ParserConfigurationException
     * @throws org.xml.sax.SAXException
     */
    public static ArrayList<Integer> matchShiftsFromXML(final IAtomContainer ac, final String pathToXML, final String atomType, final double tol, final int ndim, final int attribute) throws IOException, ParserConfigurationException, SAXException {

        final ArrayList<Double> shiftsAtomType = casekit.NMR.Utils.parseXML(pathToXML, ndim, attribute);
        ArrayList<Integer> matchesAtomType = casekit.NMR.Utils.findShiftMatches(ac, shiftsAtomType, tol, atomType);
        matchesAtomType = casekit.NMR.Utils.correctShiftMatches(ac, shiftsAtomType, matchesAtomType, tol, atomType);

        return matchesAtomType;
    }
    
    
    /**
     * Creates a two dimensional array of a given NMRShiftDB NMR entry 
     * with all shift values and atom indices.
     *
     * @param shiftsString
     * @return two dimensional array: 
     * 1. dimension: shift entry (row); 
     * 2. dimension: shift value (column 1), atom index in atom container (column 2)
     */
    public static String[][] parseShiftsInNMRShiftDBEntry(final String shiftsString){
        
        if(shiftsString.trim().length() == 0){
            return new String[][]{};
        }
        
        String[] signalSplit;
        final String[] shiftsSplit = shiftsString.split("\\|");
        final String[][] values = new String[shiftsSplit.length][3];
        for (int i = 0; i < shiftsSplit.length; i++) {
            signalSplit = shiftsSplit[i].split(";");
            values[i][0] = signalSplit[0];
            values[i][1] = signalSplit[1];
            values[i][2] = signalSplit[2];
        }
        
        return values;
    }
    
    
    public static void assignNMRShiftDBShiftsToAtomContainer(final IAtomContainer ac, final String spectrumPropertyString){
                
        // property string has to be changed for general case and not only "Spectrum 13C 0"
        if (ac.getProperty(spectrumPropertyString) == null) {
            return;
        }
        final String[][] spectrumStringArray = Utils.parseShiftsInNMRShiftDBEntry(ac.getProperty(spectrumPropertyString));
        
        int atomIndexSpectrumDB;
        Double shiftDB;
        for (int k = 0; k < ac.getAtomCount(); k++) {  
            shiftDB = null;
            for (int i = 0; i < spectrumStringArray.length; i++) {
                atomIndexSpectrumDB = Integer.parseInt(spectrumStringArray[i][2]);                
                if (atomIndexSpectrumDB == k) {
                    shiftDB = Double.parseDouble(spectrumStringArray[i][0]);
                    break;
                }
            }
            ac.getAtom(k).setProperty(Utils.getNMRShiftConstant(ac.getAtom(k).getSymbol()), shiftDB);
        }        
    }
    
    
    /**
     * Returns the NMR shift constant value for a given element. As far as 
     * it is defined, the value from CDKConstants.NMRSHIFT_* (e.g.
     * {@link org.openscience.cdk.CDKConstants#NMRSHIFT_CARBON}) will be used.
     * Otherwise the same format is used for other atom types.
     * Elements defined so far: C, H, N, P, F, D, O, S, Si, B, Pt.
     * @param element element's symbol (e.g. "C")
     * @return
     */
    public static String getNMRShiftConstant(final String element){
        switch(element){
            case "C": return CDKConstants.NMRSHIFT_CARBON;
            case "H": return CDKConstants.NMRSHIFT_HYDROGEN;
            case "N": return CDKConstants.NMRSHIFT_NITROGEN;
            case "P": return CDKConstants.NMRSHIFT_PHOSPORUS;
            case "F": return CDKConstants.NMRSHIFT_FLUORINE;
            case "S": return CDKConstants.NMRSHIFT_SULFUR;
            default:
                return null;
        }
    }
    
    
    public static int getElectronNumberByBondOrder(final IBond.Order order) {
        switch (order) {
            case SINGLE:
                return 1;
            case DOUBLE:
                return 2;
            case TRIPLE:
                return 3;
            case QUADRUPLE:
                return 4;    
            case QUINTUPLE:
                return 5;
            case SEXTUPLE:
                return 6;    
            default:
                return 0;
        }
    }
    
    
    /**
     * Returns the NMR isotope identifier for a given element, e.g. C -> 13C. 
     * Elements defined so far: C, H, N, P, F, D, O, S, Si, B, Pt.
     *
     * @param element element's symbol (e.g. "C")
     * @return
     */
    public static String getIsotopeIdentifier(final String element) {
        switch(element){
            case "C": return "13C";
            case "H": return "1H";
            case "N": return "15N";
            case "P": return "31P";
            case "F": return "19F";
            case "O": return "17O";
            case "S": return "33S";
            case "Si": return "29Si";
            case "B": return "11B";
            case "Pt": return "195Pt";
            default:
                return element;
        }
    }
    
    /**
     * Returns the element identifier for a given isotope, e.g. 13C -> C.
     * Elements defined so far: C, H, N, P, F, D, O, S, Si, B, Pt.
     *
     * @param isotope isotope's symbol (e.g. "13C")
     * @return
     */
    public static String getElementIdentifier(final String isotope) {
        switch (isotope) {
            case "13C": return "C";
            case "1H":  return "H";
            case "15N": return "N";
            case "31P": return "P";
            case "19F": return "F";
            case "17O": return "O";
            case "33S": return "S";
            case "29Si": return "Si";
            case "11B": return "B";
            case "195Pt": return "Pt";
            default:
                return null;
        }
    }
    
    
    public static HashSet<String> getAtomTypesInAtomContainer(final IAtomContainer ac) {
        final HashSet<String> atomTypes = new HashSet<>();
        for (IAtom atom : ac.atoms()) {
            atomTypes.add(atom.getSymbol());
        }
        
        return atomTypes;
    }
    
    
    public static int[] getNeighborhoodBondsCount(final IAtomContainer ac, final int indexAC, final String[] bondsSet, final ArrayList<String> neighborElems){
        final int[] counts = new int[neighborElems.size() * bondsSet.length];
        String foundBonds;
        // for all given neighbor element types
        for (int n = 0; n < neighborElems.size(); n++) {
            foundBonds = "";
            // for all next neighbors of a specific element
            for (IAtom neighborAtom : ac.getConnectedAtomsList(ac.getAtom(indexAC))) {
                // skip if not the right neighborhood element or bond type is unknown/unset
                if ((!neighborAtom.getSymbol().equals(neighborElems.get(n))) || (casekit.NMR.Utils.getStringFromBondOrder(ac.getBond(ac.getAtom(indexAC), neighborAtom).getOrder()) == null)) {
                    continue;
                }
                foundBonds += casekit.NMR.Utils.getStringFromBondOrder(ac.getBond(ac.getAtom(indexAC), neighborAtom).getOrder());
            }
            for (int k = 0; k < bondsSet.length; k++) {
                counts[n * bondsSet.length + k] = 0;
                if (casekit.NMR.Utils.sortString(foundBonds).equals(casekit.NMR.Utils.sortString(bondsSet[k]))) {
                    counts[n * bondsSet.length + k] = 1;
                    break;
                }
            }
        }
        
        return counts;
    }
    
    
    public static void writeNeighborhoodBondsCountMatrix(final String pathToOutput, final int[][] m, final String[] bondsSet, final String elem, final ArrayList<String> neighborElems, final int min, final int max, final int stepSize) throws IOException{
        
        if(stepSize < 1){
            System.err.println("stepSize < 1 not allowed!!!");
            return;
        }
        final StringBuilder sb = new StringBuilder();
        sb.append("shift [" + elem + "] (" + stepSize + "),nTotal,inRing,isArom,q" + elem + "," + elem + "H," + elem + "H2," + elem + "H3,");
        for (int i = 0; i < neighborElems.size(); i++) {
            for (int j = 0; j < bondsSet.length; j++) {
                sb.append(bondsSet[j] + "[" + neighborElems.get(i) + "]");
                if (j < bondsSet.length - 1) {
                    sb.append(",");
                }
            }
            if (i < neighborElems.size() - 1) {
                sb.append(",");
            }
        }
        sb.append("\n");
        for (int i = 0; i < stepSize * (max - min) + 1; i++) {
            sb.append((i + min) + ",");
            for (int j = 0; j < 3 + 4 + neighborElems.size() * bondsSet.length; j++) {
                sb.append(m[i][j]);
                if (j < 3 + 4 + neighborElems.size() * bondsSet.length - 1) {
                    sb.append(",");
                }
            }
            sb.append("\n");
        }
        
        final FileWriter writer = new FileWriter(pathToOutput);
        writer.append(sb.toString());
        writer.flush();
        writer.close();
    }
    
    
    public static String sortString(final String s) {
        final char[] c = s.toCharArray();
        Arrays.sort(c);
        return new String(c);
    }
    
    
    public static ArrayList<ArrayList<IBond.Order>> getBondOrderSets(final String[] valences) {
        
        final ArrayList<ArrayList<IBond.Order>> bondOrderSets = new ArrayList<>();
        for (int i = 0; i < valences.length; i++) {
            bondOrderSets.add(new ArrayList<>());
            for (int k = 0; k < StringUtils.countMatches(valences[i], "-"); k++) {
                bondOrderSets.get(i).add(IBond.Order.SINGLE);
            }
            for (int k = 0; k < StringUtils.countMatches(valences[i], "="); k++) {
                bondOrderSets.get(i).add(IBond.Order.DOUBLE);
            }
            for (int k = 0; k < StringUtils.countMatches(valences[i], "%"); k++) {
                bondOrderSets.get(i).add(IBond.Order.TRIPLE);
            }
        }
        
        return bondOrderSets;
    }
    
    
    public static String getStringFromBondOrder(final IBond.Order order) {
        switch (order) {
            case SINGLE:
                return "-";
            case DOUBLE:
                return "=";
            case TRIPLE:
                return "%";
            default:
                return null;
        }
    }
    
    
    public static IBond.Order getBondOrderFromString(final String order){
        switch(order){
            case "-": return IBond.Order.SINGLE;
            case "=": return IBond.Order.DOUBLE;
            case "%": return IBond.Order.TRIPLE;
            default:  return null;
        }
    }
    
    
    public static void writeTextFile(final String pathToOutput, final String content) throws IOException {
        FileWriter fr = new FileWriter(new File(pathToOutput));
        BufferedWriter br = new BufferedWriter(fr);
        br.write(content);
        br.close();
    }
    
    /**
     * Simple function without any settings to generate a picture from a structure 
     * given as IAtomcontainer.
     *
     * @param ac Atom container
     * @param path Path to file for storing
     * @throws IOException
     * @throws CDKException
     */
    public static void generatePicture(IAtomContainer ac, String path) throws IOException, CDKException {        
        final DepictionGenerator dg = new DepictionGenerator().withSize(1200, 1200).withAtomColors().withAtomValues().withFillToFit();
        dg.depict(ac).writeTo(path);
    }
    
    
    /**
     *
     * @param input
     * @return
     */
    public static ArrayList<Integer> getOutliers(ArrayList<Integer> input) {
        final ArrayList<Integer> outliers = new ArrayList<>();
        if(input.size() <= 1){
            return outliers;
        }
        Collections.sort(input);
        final List<Integer> data1 = input.subList(0, input.size() / 2);
        final List<Integer> data2;
        if (input.size() % 2 == 0) {
            data2 = input.subList(input.size() / 2, input.size());
        } else {
            data2 = input.subList(input.size() / 2 + 1, input.size());
        }
        final double q1 = getMedian(data1);
        final double q3 = getMedian(data2);
        final double iqr = q3 - q1;
        final double lowerFence = q1 - 1.5 * iqr;
        final double upperFence = q3 + 1.5 * iqr;
        for (int i = 0; i < input.size(); i++) {
            if ((input.get(i) < lowerFence) || (input.get(i) > upperFence)) {
                outliers.add(input.get(i));
            }
        }
//        System.out.println("input size: " + input.size());
//        System.out.println("output size: " + outliers.size());
        return outliers;
    }

    /**
     *
     * @param data
     * @return
     */
    public static double getMedian(final List<Integer> data) {
        if(data.size() == 1){
            return data.get(0);
        }
        Collections.sort(data);
        if (data.size() % 2 == 1) {
            return data.get(data.size() / 2);
        } else {
            return (data.get(data.size() / 2 - 1) + data.get(data.size() / 2)) / 2.0;
        }
    }
    
    
    /**
     *
     * @param data
     * @return
     */
    public static double getMedian(final ArrayList<Double> data) {
        if(data.size() == 1){
            return data.get(0);
        }
        Collections.sort(data);
        if (data.size() % 2 == 1) {
            return data.get(data.size() / 2);
        } else {
            return (data.get(data.size() / 2 - 1) + data.get(data.size() / 2)) / 2.0;
        }
    }
    
    
    /**
     *
     * @param data
     * @return
     */
    public static double getRMS(final ArrayList<Double> data) {
        if(data.size() == 1){
            return data.get(0);
        }
        double qSum = 0;
        for (final Double d : data) {
            qSum += d*d;
        }
        
        return Math.sqrt(qSum/data.size());
    }
    
    
    /**
     *
     * @param data
     * @return
     */
    public static double getMean(final ArrayList<Double> data) {
        double sum = 0;
        for (Double d : data) {
            sum += d;
        }
        return sum/data.size();
    }
    
    
    /**
     * Tests whether two array lists of integers are equal which also means
     * bidirectional values to each other.
     *
     * @param shiftMatches1
     * @param shiftMatches2
     * @return
     */
    public static boolean isBidirectional(final ArrayList<Integer> shiftMatches1, final ArrayList<Integer> shiftMatches2) {
        final ArrayList<Integer> temp1 = new ArrayList<>(shiftMatches1);
        final ArrayList<Integer> temp2 = new ArrayList<>(shiftMatches2);
        Collections.sort(temp1);
        Collections.sort(temp2);

        return temp1.equals(temp2);
    }

    /**
     *
     * @param ac
     * @param shiftMatches1
     * @param shiftMatches2
     * @param prop
     * @deprecated 
     */
    public static void setBidirectionalLinks(final IAtomContainer ac, final ArrayList<Integer> shiftMatches1, final ArrayList<Integer> shiftMatches2, final String prop) {

        ArrayList<Integer> propList1, propList2;
        for (int i = 0; i < shiftMatches1.size(); i++) {
            if (shiftMatches1.get(i) >= 0 && shiftMatches2.get(i) >= 0) {
                if (ac.getAtom(shiftMatches1.get(i)).getProperty(prop) == null) {
                    ac.getAtom(shiftMatches1.get(i)).setProperty(prop, new ArrayList<>());
                }
                if (ac.getAtom(shiftMatches2.get(i)).getProperty(prop) == null) {
                    ac.getAtom(shiftMatches2.get(i)).setProperty(prop, new ArrayList<>());
                }
                propList1 = ac.getAtom(shiftMatches1.get(i)).getProperty(prop);
                propList2 = ac.getAtom(shiftMatches2.get(i)).getProperty(prop);
                if (!propList1.contains(shiftMatches2.get(i))) {
                    propList1.add(shiftMatches2.get(i));
                }
                if (!propList2.contains(shiftMatches1.get(i))) {
                    propList2.add(shiftMatches1.get(i));
                }
            }
        }
    }
    
    
    /**
     *
     * @param pathToFile
     * @return
     */
    public static String getFileFormat(final String pathToFile) {

        if(pathToFile == null || pathToFile.trim().isEmpty()){
            return "";
        }        
        final String[] split = pathToFile.split("\\.");

        return split[split.length - 1];
    }
   
    
    
    
    /**
     *
     * @param lookup
     * @return
     */
    public static HashMap<String, Double> getRMS(final HashMap<String, ArrayList<Double>> lookup){
        
        final HashMap<String, Double> rms = new HashMap<>();
        for (final String key : lookup.keySet()) {
            rms.put(key, casekit.NMR.Utils.getRMS(lookup.get(key)));
//            System.out.println("count: " + lookup.get(key).size() + ", mean: " + NMR.Utils.getMean(lookup.get(key)) + ", rms: " +  rms.get(key) + ", median: " + NMR.Utils.getMedian(lookup.get(key)));
        }
      
        return rms;
    }
    
    public static IAtomContainer setAromaticitiesInAtomContainer(final IAtomContainer ac, final int maxCycleSize) throws CDKException {
        
        AtomContainerManipulator.percieveAtomTypesAndConfigureAtoms(ac);
        final ElectronDonation model = ElectronDonation.cdkAllowingExocyclic();
        final CycleFinder cycles = Cycles.or(Cycles.all(), Cycles.all(maxCycleSize));
        final Aromaticity aromaticity = new Aromaticity(model, cycles);
        aromaticity.apply(ac);
        
        return ac;
    }
    
    
    /**
     * Removes atoms from a given atom type from an atom container.
     *
     * @param ac IAtomContainer object where to remove the atoms
     * @param atomType Atom type (element's name, e.g. C or Br)
     * @return IAtomContainer where the atoms were removed
     */
    public static IAtomContainer removeAtoms(final IAtomContainer ac, final String atomType) {

        final ArrayList<IAtom> toRemoveList = new ArrayList<>();
        for (IAtom atomA : ac.atoms()) {
            if (atomA.getSymbol().equals(atomType)) {// detect wether the current atom A is a from the given atom type 
                toRemoveList.add(atomA);
            }
        }
        for (IAtom iAtom : toRemoveList) {
            ac.removeAtom(iAtom);
        }

        return ac;
    }
    
    
    public static ArrayList<Integer> ArrayToArrayList(final int[] array){
        
        final ArrayList<Integer> list = new ArrayList<>();
        for (int i = 0; i < array.length; i++) {
            list.add(array[i]);
        }
        
        return list;
    }
    
    
    public static String getSpectrumNucleiAsString(final Spectrum spectrum){
        String specID = "";
        for (int i = 0; i < spectrum.getDimCount(); i++) {
            specID += spectrum.getNuclei()[i];
            if(i < spectrum.getDimCount()-1){
                specID += "-";
            }
        }
        
        return specID;
    }
    
    
    // ########################################################################################################
    // test functions -> not ready to use
    
    
    
    
     /**
     * Returns 
     *
     * @param values
     * @return
     */
    public static HashMap<Integer, Double> getValueFrequencies(final ArrayList<Integer> values) {
        
        final HashMap<Integer, Double> freqs = new HashMap<>();
        final HashSet<Integer> valueLevels = new HashSet<>(values);
        int sum = 0;
        for (int value : valueLevels) {
            sum += Collections.frequency(values, value);
        }
        for (int value : valueLevels) {            
            freqs.put(value, (Collections.frequency(values, value) / (double) sum));
        }
        
        return freqs;
    }
    

        /**
     * Returns a list of open bonds of an atom.
     *
     * @param ac atom container
     * @param atomIndex index of the atom to test
     * @return
     */
    public static ArrayList<IBond.Order> getOpenBonds(final IAtomContainer ac, final int atomIndex) {

        final IAtom atom = ac.getAtom(atomIndex);
        if (atom.getHybridization() == null) {
            return null;
        }
        final ArrayList<IBond.Order> bondOrderList = new ArrayList<>();
        final AtomValenceDescriptor valenceDesc = new AtomValenceDescriptor();
        final int valence = Integer.valueOf(valenceDesc.calculate(atom, ac).getValue().toString());
        int electronsLeft = (8 - (valence + atom.getImplicitHydrogenCount()));

        if (electronsLeft == 0) {
//            System.out.println(atom.getSymbol() + ": " + atomIndex + " (" + atom.getHybridization() + "): " + bondOrderList);
            return bondOrderList;
        }
        // only one single bond left; possible at SP1, SP2 and SP3
        if (electronsLeft == 1) {
            bondOrderList.add(IBond.Order.SINGLE);
//            System.out.println(atom.getSymbol() + ": " + atomIndex + " (" + atom.getHybridization() + "): " + bondOrderList);
            return bondOrderList;
        }
        // with SP3 are only single bonds possible
        if (atom.getHybridization().equals(IAtomType.Hybridization.SP3)) {
            // subtract the single bonded neighbor number
            electronsLeft -= ac.getConnectedAtomsList(atom).size();
            for (int k = 0; k < electronsLeft; k++) {
                bondOrderList.add(IBond.Order.SINGLE);
            }
//            System.out.println(atom.getSymbol() + ": " + atomIndex + " (" + atom.getHybridization() + "): " + bondOrderList);
            return bondOrderList;
        }

        if (atom.getHybridization().equals(IAtomType.Hybridization.SP2)) {
            switch (atom.getSymbol()) {
                case "O":
                case "S":
                    bondOrderList.add(IBond.Order.DOUBLE);
                    return bondOrderList;
                case "C":
                    bondOrderList.add(IBond.Order.SINGLE);
                    bondOrderList.add(IBond.Order.SINGLE);
                    bondOrderList.add(IBond.Order.DOUBLE);
                    break;
                case "N":
                    bondOrderList.add(IBond.Order.SINGLE);
                    bondOrderList.add(IBond.Order.DOUBLE);
                    break;
                default:
                    break;
            }
        } else if (atom.getHybridization().equals(IAtomType.Hybridization.SP1)) {
            switch (atom.getSymbol()) {
                case "C":
                    bondOrderList.add(IBond.Order.DOUBLE);
                    bondOrderList.add(IBond.Order.DOUBLE);
                    // or
                    bondOrderList.add(IBond.Order.SINGLE);
                    bondOrderList.add(IBond.Order.TRIPLE);
                    break;
                case "N":
                    bondOrderList.add(IBond.Order.TRIPLE);
                    break;
                default:
                    break;
            }
        }
        for (IAtom neighbor : ac.getConnectedAtomsList(atom)) {
            bondOrderList.remove(ac.getBond(atom, neighbor).getOrder());
            electronsLeft -= casekit.NMR.Utils.getElectronNumberByBondOrder(ac.getBond(atom, neighbor).getOrder());
        }

        int theoCounter = 0;
        for (IBond.Order order : bondOrderList) {
            theoCounter += casekit.NMR.Utils.getElectronNumberByBondOrder(order);
        }

        switch (Math.abs(theoCounter - electronsLeft)) {
            case 1:
                bondOrderList.remove(IBond.Order.SINGLE);
                theoCounter -= 1;
                break;
            case 2:

                break;
            case 3:

                break;
        }

//        System.out.println(atom.getSymbol() + ": " + atomIndex + " (" + atom.getHybridization() + "): " + bondOrderList + " -> e: " + theoCounter + " (theo) vs. " + electronsLeft + " (real), bond counter: " + ac.getConnectedAtomsList(atom).size() + " (+" + atom.getImplicitHydrogenCount() + "H)");
        return bondOrderList;
    }
  
        /**
     * Returns a bond type for two bond atoms from its hybridization.
     * CURRENTLY ONLY SINGLE BOND DETECTION POSSIBLE!!!
     * This function detects single, double and triple bonds and returns a
     * bond order from {@link org.openscience.cdk.interfaces.IBond.Order}.
     * If no bond type could be detected then
     * {@link org.openscience.cdk.interfaces.IBond.Order#UNSET} will be
     * returned.
     * For single and double bond detection, the following elements are defined
     * so far: C, O, N, S.
     * For triple bond detection, the following elements are defined so far: C,
     * N.
     *
     *
     * @param atom1
     * @param atom2
     * @return
     */
    public static IBond.Order getBondTypeFromHybridizations(final IAtom atom1, final IAtom atom2) {

//        final String atomType1 = atom1.getSymbol();
        final IAtomType.Hybridization hybridization1 = atom1.getHybridization();
//        final String atomType2 = atom2.getSymbol();
        final IAtomType.Hybridization hybridization2 = atom2.getHybridization();

        if (hybridization1 == null && hybridization2 == null) {
            return IBond.Order.UNSET;
        }        
        
//        IBond.Order bondOrder1 = IBond.Order.UNSET;
//        IBond.Order bondOrder2 = IBond.Order.UNSET;

        // single bond detection, the "3" means all SP3 hybrdidizations like SP3, SP3D2 or PLANAR3
        if ((hybridization1 != null) //&& (atomType1.equals("C") || atomType1.equals("O") || atomType1.equals("N") || atomType1.equals("S"))
                && hybridization1.toString().contains("3")) {
            return IBond.Order.SINGLE;
        }
        if ((hybridization2 != null) //&& (atomType2.equals("C") || atomType2.equals("O") || atomType2.equals("N") || atomType2.equals("S"))
                && hybridization2.toString().contains("3")) {
            return IBond.Order.SINGLE;
        }
//        // double bond detection
//        if ((atomType1.equals("C") && (hybridization1.equals(IAtomType.Hybridization.SP1) || hybridization1.equals(IAtomType.Hybridization.SP2)))
//                || ((atomType1.equals("O") || atomType1.equals("N") || atomType1.equals("S")) && (hybridization1.equals(IAtomType.Hybridization.SP2)))) {
//            bondOrder1 = IBond.Order.DOUBLE;
//        }
//        if ((atomType2.equals("C") && (hybridization2.equals(IAtomType.Hybridization.SP1) || hybridization2.equals(IAtomType.Hybridization.SP2)))
//                || ((atomType2.equals("O") || atomType2.equals("N") || atomType2.equals("S")) && hybridization2.equals(IAtomType.Hybridization.SP2))) {
//            bondOrder2 = IBond.Order.DOUBLE;
//        }
//        // triple bond detection
//        if ((atomType1.equals("C") && (hybridization1.equals(IAtomType.Hybridization.SP1)))
//                && (atomType2.equals("N") && hybridization2.equals(IAtomType.Hybridization.SP1))) {
//            bondOrder1 = IBond.Order.TRIPLE;
//        }
//        if ((atomType2.equals("N") && (hybridization2.equals(IAtomType.Hybridization.SP1)))
//                && (atomType1.equals("C") && hybridization1.equals(IAtomType.Hybridization.SP1))) {
//            bondOrder2 = IBond.Order.TRIPLE;
//        }

//        if (bondOrder1.equals(bondOrder2)) {
//            return bondOrder1;
//        }

        return IBond.Order.UNSET;
    }
}
