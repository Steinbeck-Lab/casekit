package casekit.nmr.elucidation.lsd;

import casekit.nmr.elucidation.Constants;
import casekit.nmr.elucidation.model.Detections;
import casekit.nmr.elucidation.model.ElucidationOptions;
import casekit.nmr.elucidation.model.Grouping;
import casekit.nmr.elucidation.model.MolecularConnectivity;
import casekit.nmr.model.DataSet;
import casekit.nmr.model.nmrium.Correlations;
import casekit.nmr.utils.Statistics;
import casekit.nmr.utils.Utils;

import java.text.SimpleDateFormat;
import java.util.*;

public class PyLSDInputFileBuilder {

    private static String buildHeader() {
        final StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("; PyLSD input file created by casekit (https://github.com/michaelwenk/casekit)\n");
        final SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd 'at' HH:mm:ss z");
        final Date date = new Date(System.currentTimeMillis());
        stringBuilder.append("; ")
                     .append(formatter.format(date));

        return stringBuilder.toString();
    }

    private static String buildFORM(final String mf, final Map<String, Integer> elementCounts) {
        final StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("; Molecular Formula: ")
                     .append(mf)
                     .append("\n");
        stringBuilder.append("FORM ");
        elementCounts.forEach((elem, count) -> stringBuilder.append(elem)
                                                            .append(" ")
                                                            .append(count)
                                                            .append(" "));

        return stringBuilder.toString();
    }

    private static String buildPIEC() {
        return "PIEC 1";
    }

    private static String buildELIM(final int elimP1, final int elimP2) {
        return "ELIM "
                + elimP1
                + " "
                + elimP2;
    }

    private static String buildPossibilitiesString(final Collection<Integer> possibilities) {
        final StringBuilder possibilitiesStringBuilder = new StringBuilder();
        if (possibilities.size()
                > 1) {
            possibilitiesStringBuilder.append("(");
        }
        int counter = 0;
        for (final int possibility : possibilities) {
            possibilitiesStringBuilder.append(possibility);
            if (counter
                    < possibilities.size()
                    - 1) {
                possibilitiesStringBuilder.append(" ");
            }
            counter++;
        }
        if (possibilities.size()
                > 1) {
            possibilitiesStringBuilder.append(")");
        }

        return possibilitiesStringBuilder.toString();
    }

    private static Map<String, StringBuilder> buildStringBuilderMap(
            final Map<Integer, List<MolecularConnectivity>> molecularConnectivityMap) {
        final Map<String, StringBuilder> stringBuilderMap = new HashMap<>();
        final Map<String, List<String>> stringListMap = new HashMap<>();
        stringListMap.put("MULT", new ArrayList<>());
        stringListMap.put("HSQC", new ArrayList<>());
        stringListMap.put("HMBC", new ArrayList<>());
        stringListMap.put("COSY", new ArrayList<>());
        stringListMap.put("BOND", new ArrayList<>());
        stringListMap.put("SHIX", new ArrayList<>());
        stringListMap.put("SHIH", new ArrayList<>());
        StringBuilder stringBuilder, hybridizationStringBuilder, attachedProtonsCountStringBuilder;
        List<String> stringList;
        int counter, firstOfEquivalenceIndexPyLSD;
        Set<Integer> groupMembers; // use as a Set to remove the actual value and not at a list index
        MolecularConnectivity molecularConnectivityGroupMember, molecularConnectivityHeavyAtom;
        final Map<Integer, Set<Integer>> addedBONDPairs = new HashMap<>();
        final Set<Integer> addedKeysSHIH = new HashSet<>();
        for (final int correlationIndex : molecularConnectivityMap.keySet()) {
            firstOfEquivalenceIndexPyLSD = -1;
            for (final MolecularConnectivity molecularConnectivity : molecularConnectivityMap.get(correlationIndex)) {
                if (firstOfEquivalenceIndexPyLSD
                        == -1) {
                    firstOfEquivalenceIndexPyLSD = molecularConnectivity.getIndex();
                }
                if (!molecularConnectivity.getAtomType()
                                          .equals("H")) {

                    hybridizationStringBuilder = new StringBuilder();
                    if (molecularConnectivity.getHybridizations()
                                             .size()
                            > 1) {
                        hybridizationStringBuilder.append("(");
                    }
                    counter = 0;
                    for (final int hybrid : molecularConnectivity.getHybridizations()) {
                        hybridizationStringBuilder.append(hybrid);
                        if (counter
                                < molecularConnectivity.getHybridizations()
                                                       .size()
                                - 1) {
                            hybridizationStringBuilder.append(" ");
                        }
                        counter++;
                    }
                    if (molecularConnectivity.getHybridizations()
                                             .size()
                            > 1) {
                        hybridizationStringBuilder.append(")");
                    }
                    attachedProtonsCountStringBuilder = new StringBuilder();
                    if (molecularConnectivity.getProtonCounts()
                                             .size()
                            > 1) {
                        attachedProtonsCountStringBuilder.append("(");
                    }
                    counter = 0;
                    for (final int protonCount : molecularConnectivity.getProtonCounts()) {
                        attachedProtonsCountStringBuilder.append(protonCount);
                        if (counter
                                < molecularConnectivity.getProtonCounts()
                                                       .size()
                                - 1) {
                            attachedProtonsCountStringBuilder.append(" ");
                        }
                        counter++;
                    }
                    if (molecularConnectivity.getProtonCounts()
                                             .size()
                            > 1) {
                        attachedProtonsCountStringBuilder.append(")");
                    }
                    // MULT section
                    stringList = stringListMap.get("MULT");
                    stringBuilder = new StringBuilder();
                    stringBuilder.append("MULT ")
                                 .append(molecularConnectivity.getIndex())
                                 .append(" ")
                                 .append(Constants.defaultAtomLabelMap.get(molecularConnectivity.getAtomType()))
                                 .append(" ")
                                 .append(hybridizationStringBuilder)
                                 .append(" ")
                                 .append(attachedProtonsCountStringBuilder);
                    stringBuilder.append("; ")
                                 .append(buildShiftString(molecularConnectivityMap, molecularConnectivity));
                    if (molecularConnectivityMap.get(correlationIndex)
                                                .size()
                            > 1
                            && molecularConnectivity.getIndex()
                            != firstOfEquivalenceIndexPyLSD) {
                        stringBuilder.append("; equivalent to ")
                                     .append(firstOfEquivalenceIndexPyLSD);
                    }
                    stringBuilder.append("\n");
                    stringList.add(stringBuilder.toString());
                    // HSQC section
                    if (molecularConnectivity.getHsqc()
                            != null) {
                        stringList = stringListMap.get("HSQC");
                        stringBuilder = new StringBuilder();
                        stringBuilder.append("HSQC ")
                                     .append(molecularConnectivity.getIndex())
                                     .append(" ")
                                     .append(molecularConnectivity.getHsqc()
                                                                  .get(0))
                                     .append(buildShiftsComment(molecularConnectivityMap, molecularConnectivity,
                                                                casekit.nmr.elucidation.Utilities.findMolecularConnectivityByIndex(
                                                                        molecularConnectivityMap, "H", false,
                                                                        molecularConnectivity.getHsqc()
                                                                                             .get(0))))
                                     .append("\n");
                        stringList.add(stringBuilder.toString());
                    }
                    // HMBC section
                    if (molecularConnectivity.getHmbc()
                            != null) {
                        stringList = stringListMap.get("HMBC");
                        for (final int protonIndexInPyLSD : molecularConnectivity.getHmbc()
                                                                                 .keySet()) {
                            // filter out group members which are directly bonded to that proton
                            groupMembers = new HashSet<>(molecularConnectivity.getGroupMembers());
                            for (final int groupMemberIndex : new ArrayList<>(groupMembers)) {
                                molecularConnectivityGroupMember = casekit.nmr.elucidation.Utilities.findMolecularConnectivityByIndex(
                                        molecularConnectivityMap, molecularConnectivity.getAtomType(), false,
                                        groupMemberIndex);
                                if (molecularConnectivityGroupMember.getHsqc()
                                        != null
                                        && molecularConnectivityGroupMember.getHsqc()
                                                                           .contains(protonIndexInPyLSD)) {
                                    groupMembers.remove(groupMemberIndex);
                                }
                            }
                            if (!groupMembers.isEmpty()) {
                                stringBuilder = new StringBuilder();
                                stringBuilder.append("HMBC ")
                                             .append(buildPossibilitiesString(groupMembers))
                                             .append(" ")
                                             .append(protonIndexInPyLSD)
                                             .append(" ")
                                             .append(molecularConnectivity.getHmbc()
                                                                          .get(protonIndexInPyLSD)[0])
                                             .append(" ")
                                             .append(molecularConnectivity.getHmbc()
                                                                          .get(protonIndexInPyLSD)[1])
                                             .append(buildShiftsComment(molecularConnectivityMap, molecularConnectivity,
                                                                        casekit.nmr.elucidation.Utilities.findMolecularConnectivityByIndex(
                                                                                molecularConnectivityMap, "H", false,
                                                                                protonIndexInPyLSD)))
                                             .append("\n");
                                if (!stringList.contains(stringBuilder.toString())) {
                                    stringList.add(stringBuilder.toString());
                                }
                            }
                        }
                    }
                    // BOND section
                    if (molecularConnectivity.getFixedNeighbors()
                            != null) {
                        stringList = stringListMap.get("BOND");
                        for (final int bondedIndexInPyLSD : molecularConnectivity.getFixedNeighbors()) {
                            if (!addedBONDPairs.containsKey(molecularConnectivity.getIndex())
                                    || (addedBONDPairs.containsKey(molecularConnectivity.getIndex())
                                    && !addedBONDPairs.get(molecularConnectivity.getIndex())
                                                      .contains(bondedIndexInPyLSD))) {
                                stringBuilder = new StringBuilder();
                                stringBuilder.append("BOND ")
                                             .append(molecularConnectivity.getIndex())
                                             .append(" ")
                                             .append(bondedIndexInPyLSD)
                                             .append(buildShiftsComment(molecularConnectivityMap, molecularConnectivity,
                                                                        casekit.nmr.elucidation.Utilities.findMolecularConnectivityByIndex(
                                                                                molecularConnectivityMap, "H", true,
                                                                                bondedIndexInPyLSD)))
                                             .append("\n");
                                if (!stringList.contains(stringBuilder.toString())) {
                                    stringList.add(stringBuilder.toString());
                                }
                                addedBONDPairs.putIfAbsent(molecularConnectivity.getIndex(), new HashSet<>());
                                addedBONDPairs.get(molecularConnectivity.getIndex())
                                              .add(bondedIndexInPyLSD);
                                addedBONDPairs.putIfAbsent(bondedIndexInPyLSD, new HashSet<>());
                                addedBONDPairs.get(bondedIndexInPyLSD)
                                              .add(molecularConnectivity.getIndex());
                            }
                        }
                    }
                } else if (molecularConnectivity.getAtomType()
                                                .equals("H")) {
                    // COSY section
                    if (molecularConnectivity.getCosy()
                            != null) {
                        stringList = stringListMap.get("COSY");
                        for (final int protonIndexInPyLSD : molecularConnectivity.getCosy()
                                                                                 .keySet()) {
                            groupMembers = new HashSet<>(molecularConnectivity.getGroupMembers());
                            // 1) use only one attached proton of a CH2 group (optional)
                            final Set<Integer> alreadyFoundHeavyAtomIndex = new HashSet<>();
                            for (final int groupMemberIndex : new ArrayList<>(groupMembers)) {
                                molecularConnectivityHeavyAtom = casekit.nmr.elucidation.Utilities.getHeavyAtomMolecularConnectivity(
                                        molecularConnectivityMap, groupMemberIndex);
                                if (molecularConnectivityHeavyAtom
                                        == null
                                        || alreadyFoundHeavyAtomIndex.contains(
                                        molecularConnectivityHeavyAtom.getIndex())) {
                                    groupMembers.remove(groupMemberIndex);
                                } else {
                                    alreadyFoundHeavyAtomIndex.add(molecularConnectivityHeavyAtom.getIndex());
                                }
                            }
                            // 2) filter out group members which would direct to itself when using COSY correlation
                            molecularConnectivityHeavyAtom = casekit.nmr.elucidation.Utilities.getHeavyAtomMolecularConnectivity(
                                    molecularConnectivityMap, protonIndexInPyLSD);
                            if (molecularConnectivityHeavyAtom
                                    != null) {
                                for (final int groupMemberIndex : new HashSet<>(groupMembers)) {
                                    if (molecularConnectivityHeavyAtom.getHsqc()
                                                                      .contains(groupMemberIndex)) {
                                        groupMembers.remove(groupMemberIndex);
                                    }
                                }
                                if (!groupMembers.isEmpty()) {
                                    stringBuilder = new StringBuilder();
                                    stringBuilder.append("COSY ")
                                                 .append(buildPossibilitiesString(groupMembers))
                                                 .append(" ")
                                                 .append(protonIndexInPyLSD)
                                                 .append(" ")
                                                 .append(molecularConnectivity.getCosy()
                                                                              .get(protonIndexInPyLSD)[0])
                                                 .append(" ")
                                                 .append(molecularConnectivity.getCosy()
                                                                              .get(protonIndexInPyLSD)[1])
                                                 .append(buildShiftsComment(molecularConnectivityMap,
                                                                            molecularConnectivity,
                                                                            casekit.nmr.elucidation.Utilities.findMolecularConnectivityByIndex(
                                                                                    molecularConnectivityMap, "H",
                                                                                    false, protonIndexInPyLSD)))
                                                 .append("\n");
                                    if (!stringList.contains(stringBuilder.toString())) {
                                        stringList.add(stringBuilder.toString());
                                    }
                                }
                            }
                        }
                    }
                }
                // SHIH/SHIX section
                if (molecularConnectivity.getSignal()
                        != null) {
                    stringList = stringListMap.get(molecularConnectivity.getAtomType()
                                                                        .equals("H")
                                                   ? "SHIH"
                                                   : "SHIX");
                    stringBuilder = new StringBuilder();
                    stringBuilder.append(molecularConnectivity.getAtomType()
                                                              .equals("H")
                                         ? "SHIH"
                                         : "SHIX")
                                 .append(" ")
                                 .append(molecularConnectivity.getIndex())
                                 .append(" ")
                                 .append(Statistics.roundDouble(molecularConnectivity.getSignal()
                                                                                     .getShift(0), 5))
                                 .append("\n");
                    if (!stringList.contains(stringBuilder.toString())) {
                        if (!molecularConnectivity.getAtomType()
                                                  .equals("H")) {
                            stringList.add(stringBuilder.toString());
                        } else if (!addedKeysSHIH.contains(molecularConnectivity.getIndex())) {
                            stringList.add(stringBuilder.toString());
                            addedKeysSHIH.add(molecularConnectivity.getIndex());
                        }
                    }
                }
            }
        }
        // put sections into stringBuilderMap
        for (final String sectionKey : stringListMap.keySet()) {
            stringBuilder = new StringBuilder();
            for (final String sectionLine : stringListMap.get(sectionKey)) {
                stringBuilder.append(sectionLine);
            }
            stringBuilderMap.put(sectionKey, stringBuilder);
        }

        return stringBuilderMap;
    }

    private static String buildShiftString(final Map<Integer, List<MolecularConnectivity>> molecularConnectivityMap,
                                           final MolecularConnectivity molecularConnectivity) {
        if (molecularConnectivity
                == null
                || molecularConnectivity.getSignal()
                == null) {
            return "?";
        }

        final String heavyAtomShiftString = "";
        //        if (molecularConnectivity.getAtomType()
        //                                 .equals("H")) {
        //            MolecularConnectivity heavyAtomMolecularConnectivity = null;
        //            boolean found = false;
        //            for (final int correlationIndex : molecularConnectivityMap.keySet()) {
        //                for (final MolecularConnectivity molecularConnectivityTemp : molecularConnectivityMap.get(
        //                        correlationIndex)) {
        //                    if (molecularConnectivityTemp.getHsqc()
        //                            != null
        //                            && molecularConnectivityTemp.getHsqc()
        //                                                        .contains(molecularConnectivity.getIndex())) {
        //                        heavyAtomMolecularConnectivity = molecularConnectivityTemp;
        //                        found = true;
        //                        break;
        //                    }
        //                }
        //                if (found) {
        //                    break;
        //                }
        //            }
        //            if (heavyAtomMolecularConnectivity
        //                    != null) {
        //                heavyAtomShiftString = " ("
        //                        + buildShiftString(molecularConnectivityMap, heavyAtomMolecularConnectivity)
        //                        + ")";
        //            }
        //        }

        return Statistics.roundDouble(molecularConnectivity.getSignal()
                                                           .getShift(0), 3)
                + heavyAtomShiftString;
    }

    private static String buildShiftsComment(final Map<Integer, List<MolecularConnectivity>> molecularConnectivityMap,
                                             final MolecularConnectivity molecularConnectivity1,
                                             final MolecularConnectivity molecularConnectivity2) {
        return "; "
                + molecularConnectivity1.getAtomType()
                + ": "
                + buildShiftString(molecularConnectivityMap, molecularConnectivity1)
                + " -> "
                + molecularConnectivity2.getAtomType()
                + ": "
                + buildShiftString(molecularConnectivityMap, molecularConnectivity2);
    }

    private static String buildLISTsAndPROPs(final Map<Integer, List<MolecularConnectivity>> molecularConnectivityMap,
                                             final Map<String, Integer> elementCounts,
                                             final boolean allowHeteroHeteroBonds) {
        final StringBuilder stringBuilder = new StringBuilder();
        // list key -> [list name, size]
        final Map<String, Object[]> listMap = new HashMap<>();

        // LIST and PROP for hetero hetero bonds to disallow in case hetero atoms are present
        final boolean containsHeteroAtoms = elementCounts.keySet()
                                                         .stream()
                                                         .anyMatch(atomType -> !atomType.equals("C")
                                                                 && !atomType.equals("H"));
        if (containsHeteroAtoms
                && !allowHeteroHeteroBonds) {
            LISTAndPROPUtilities.insertNoHeteroHeteroBonds(stringBuilder, listMap);
        }
        // insert LIST for each heavy atom type in MF
        LISTAndPROPUtilities.insertGeneralLISTs(stringBuilder, listMap, molecularConnectivityMap,
                                                elementCounts.keySet());
        // insert list combinations of carbon and hybridization states
        LISTAndPROPUtilities.insertHeavyAtomCombinationLISTs(stringBuilder, listMap, molecularConnectivityMap);
        // insert forbidden connection lists and properties
        LISTAndPROPUtilities.insertConnectionLISTsAndPROPs(stringBuilder, listMap, molecularConnectivityMap, "forbid");
        // insert set connection lists and properties
        LISTAndPROPUtilities.insertConnectionLISTsAndPROPs(stringBuilder, listMap, molecularConnectivityMap, "allow");

        return stringBuilder.toString();
    }

    private static String buildDEFFs(final String[] filterPaths, final String[] pathsToNeighborsFiles) {
        final StringBuilder stringBuilder = new StringBuilder();
        // DEFF -> add filters
        final Map<String, String> filters = new LinkedHashMap<>();
        int counter = 1;
        for (final String filterPath : filterPaths) {
            filters.put("F"
                                + counter, filterPath);
            counter++;
        }
        for (final String pathToNeighborsFiles : pathsToNeighborsFiles) {
            filters.put("F"
                                + counter, pathToNeighborsFiles);
            counter++;
        }

        if (!filters.isEmpty()) {
            stringBuilder.append("; externally defined filters\n");
            filters.forEach((label, filePath) -> stringBuilder.append("DEFF ")
                                                              .append(label)
                                                              .append(" \"")
                                                              .append(filePath)
                                                              .append("\"\n"));
            stringBuilder.append("\n");
        }

        return stringBuilder.toString();
    }

    private static String buildFEXP(final Map<String, Boolean> fexpMap) {
        final StringBuilder stringBuilder = new StringBuilder();

        if (!fexpMap.isEmpty()) {
            stringBuilder.append("FEXP \"");
            int counter = 0;
            for (final String label : fexpMap.keySet()) {
                if (!fexpMap.get(label)) {
                    stringBuilder.append("NOT ");
                }
                stringBuilder.append(label);
                if (counter
                        < fexpMap.keySet()
                                 .size()
                        - 1) {
                    stringBuilder.append(" AND ");
                }
                counter++;
            }
            stringBuilder.append("\"\n");
        }

        return stringBuilder.toString();
    }

    private static String buildDEFFsAndFEXP(final ElucidationOptions elucidationOptions, final Detections detections) {
        final StringBuilder stringBuilder = new StringBuilder();
        final Map<String, Boolean> fexpMap = new HashMap<>();
        for (int i = 0; i
                < elucidationOptions.getFilterPaths().length; i++) {
            fexpMap.put("F"
                                + (i
                    + 1), false);
        }
        //        // build and write neighbors files
        //        final List<String> pathsToNeighborsFilesToUse = new ArrayList<>();
        //        if (Utilities.writeNeighborsFile(elucidationOptions.getPathsToNeighborsFiles()[0], correlationList, indicesMap,
        //                                         forbiddenNeighbors)) {
        //            fexpMap.put("F"
        //                                + (fexpMap.size()
        //                    + 1), false);
        //            pathsToNeighborsFilesToUse.add(elucidationOptions.getPathsToNeighborsFiles()[0]);
        //        }
        //        if (Utilities.writeNeighborsFile(elucidationOptions.getPathsToNeighborsFiles()[1], correlationList, indicesMap,
        //                                         setNeighbors)) {
        //            fexpMap.put("F"
        //                                + (fexpMap.size()
        //                    + 1), true);
        //            pathsToNeighborsFilesToUse.add(elucidationOptions.getPathsToNeighborsFiles()[1]);
        //        }

        // build and write fragments files
        final List<String> pathsToFragmentFilesToUse = new ArrayList<>();
        String pathToFragmentFile;
        DataSet fragmentDataSet;
        for (int i = 0; i
                < detections.getFragments()
                            .size(); i++) {
            fragmentDataSet = detections.getFragments()
                                        .get(i);
            if (fragmentDataSet.getAttachment()
                    == null
                    || !((boolean) fragmentDataSet.getAttachment()
                                                  .get("include"))) {
                continue;
            }
            pathToFragmentFile = elucidationOptions.getPathToFragmentFiles()
                    + "_"
                    + i
                    + ".deff";
            if (Utilities.writeFragmentFile(pathToFragmentFile, fragmentDataSet)) {
                fexpMap.put("F"
                                    + (fexpMap.size()
                        + 1), true);
                pathsToFragmentFilesToUse.add(pathToFragmentFile);
            }
        }

        // build DEFFs
        stringBuilder.append(
                             buildDEFFs(elucidationOptions.getFilterPaths(), pathsToFragmentFilesToUse.toArray(String[]::new)))
                     .append("\n");
        // build FEXP
        stringBuilder.append(buildFEXP(fexpMap))
                     .append("\n");

        return stringBuilder.toString();
    }


    public static List<String> buildPyLSDInputFileContentList(final Correlations correlations, final String mf,
                                                              final Detections detections, final Grouping grouping,
                                                              final ElucidationOptions elucidationOptions,
                                                              final Map<String, Integer[]> defaultBondDistances) {
        if (mf
                == null
                || mf.isEmpty()) {
            return new ArrayList<>();
        }
        final List<String> inputFilesContentList = new ArrayList<>();
        // build different combinations
        final List<Map<Integer, List<MolecularConnectivity>>> molecularConnectivityMapCombinationList = casekit.nmr.elucidation.Utilities.buildMolecularConnectivityMapCombinationList(
                correlations.getValues(), detections, grouping, defaultBondDistances);
        // for each combination insert an input file for PyLSD
        for (final Map<Integer, List<MolecularConnectivity>> molecularConnectivityMap : molecularConnectivityMapCombinationList) {
            inputFilesContentList.add(
                    buildPyLSDInputFileContent(molecularConnectivityMap, mf, elucidationOptions, detections));
        }

        return inputFilesContentList;
    }

    public static String buildPyLSDInputFileContent(
            final Map<Integer, List<MolecularConnectivity>> molecularConnectivityMap, final String mf,
            final ElucidationOptions elucidationOptions, final Detections detections) {

        final Map<String, Integer> elementCounts = new LinkedHashMap<>(Utils.getMolecularFormulaElementCounts(mf));
        final StringBuilder stringBuilder = new StringBuilder();
        // create header
        stringBuilder.append(buildHeader())
                     .append("\n\n");
        // FORM
        stringBuilder.append(buildFORM(mf, elementCounts))
                     .append("\n\n");
        // PIEC
        stringBuilder.append(buildPIEC())
                     .append("\n\n");
        // ELIM
        if (elucidationOptions.isUseElim()) {
            stringBuilder.append(buildELIM(elucidationOptions.getElimP1(), elucidationOptions.getElimP2()))
                         .append("\n\n");
        }

        final Map<String, StringBuilder> stringBuilderMap = buildStringBuilderMap(molecularConnectivityMap);
        stringBuilder.append(stringBuilderMap.get("MULT")
                                             .toString())
                     .append("\n");
        stringBuilder.append(stringBuilderMap.get("HSQC")
                                             .toString())
                     .append("\n");

        stringBuilder.append(stringBuilderMap.get("BOND")
                                             .toString())
                     .append("\n");
        stringBuilder.append(stringBuilderMap.get("HMBC")
                                             .toString())
                     .append("\n");
        stringBuilder.append(stringBuilderMap.get("COSY")
                                             .toString())
                     .append("\n");
        stringBuilder.append(stringBuilderMap.get("SHIX")
                                             .toString())
                     .append("\n");
        stringBuilder.append(stringBuilderMap.get("SHIH")
                                             .toString())
                     .append("\n");

        // LIST PROP for certain limitations or properties of atoms in lists, e.g. hetero hetero bonds allowance
        stringBuilder.append(buildLISTsAndPROPs(molecularConnectivityMap, elementCounts,
                                                elucidationOptions.isAllowHeteroHeteroBonds()))
                     .append("\n");
        // DEFF and FEXP as filters (good/bad lists)
        stringBuilder.append(buildDEFFsAndFEXP(elucidationOptions, detections))
                     .append("\n");

        return stringBuilder.toString();
    }
}
