import algorithms.ApproxMCLSHOD;
import algorithms.ApproxMCOD;
import algorithms.LSHOD;
import algorithms.MCOD;
import core.DataObj;
import core.Outlier;
import core.Stream;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Set;

public class Executor {
    private int iMaxMemUsage = 0;
    private Long nTotalRunTime = 0L;
    private double nTimePerObj;
    private Long m_timePreObjSum;
    private int nProcessed;
    private static final int m_timePreObjInterval = 100;

    private String chosenAlgorithm;
    private int windowSize;
    private int slideSize;
    private double rParameter;
    private int kParameter;
    private String dataFile;
    private boolean containsClass;
    private String outliersFile;

    // ApproxMCOD additional parameters
    private int pdLimit;
    private double arFactor;

    private Stream stream;

    private MCOD mcodObj;
    private ApproxMCOD approxMCODObj;
    private LSHOD lshodObj;
    private ApproxMCLSHOD approxMCLSHODObj;


    public Executor(String[] args) {
        m_timePreObjSum = 0L;
        nProcessed = 0;
        nTimePerObj = 0L;

        readArguments(args);
        stream = new Stream();
    }

    private void readArguments(String[] args) {
        for (int i = 0; i < args.length; i++) {

            //check if arg starts with --
            String arg = args[i];
            if (arg.indexOf("--") == 0) {
                switch (arg) {
                    case "--algorithm":
                        this.chosenAlgorithm = args[i + 1];
                        break;
                    case "--W":
                        this.windowSize = Integer.parseInt(args[i + 1]);
                        break;
                    case "--slide":
                        this.slideSize = Integer.parseInt(args[i + 1]);
                        break;
                    case "--R":
                        this.rParameter = Double.parseDouble(args[i + 1]);
                        break;
                    case "--k":
                        this.kParameter = Integer.parseInt(args[i + 1]);
                        break;
                    case "--pdLimit":
                        this.pdLimit = Integer.parseInt(args[i + 1]);
                        break;
                    case "--arFactor":
                        this.arFactor = Double.parseDouble(args[i + 1]);
                        break;
                    case "--datafile":
                        this.dataFile = args[i + 1];
                        break;
                    case "--containsClass":
                        this.containsClass = Boolean.parseBoolean(args[i + 1]);
                        break;
                    case "--outliersFile":
                        this.outliersFile = args[i + 1];
                        break;
                }
            }
        }
    }

    public void performOutlierDetection() {
        // Load dataset file
        stream.loadFile(dataFile, containsClass);

        if (chosenAlgorithm.equals("MCOD")) {
            mcodObj = new MCOD(windowSize, slideSize, rParameter, kParameter);
        } else if (chosenAlgorithm.equals("ApproxMCOD")) {
            approxMCODObj = new ApproxMCOD(windowSize, slideSize, rParameter, kParameter, pdLimit, arFactor);
        } else if (chosenAlgorithm.equals("LSHOD")) {
            int dataDimensions = stream.getStreamDataDimensions();
            lshodObj = new LSHOD(windowSize, slideSize, rParameter, kParameter,
                    dataDimensions, 4, 10, (int)rParameter);
        } else if (chosenAlgorithm.equals("ApproxMCLSHOD")) {
            int dataDimensions = stream.getStreamDataDimensions();
            approxMCLSHODObj = new ApproxMCLSHOD(windowSize, slideSize, rParameter, kParameter,
                    dataDimensions, 5, 10, (int)(3 * rParameter / 2));
        }

        while (stream.hasNext()) {
            addNewStreamObjects();
        }

        // Evaluate the non-expired nodes still in the window in order to record
        // the nodes that are pure outliers.
        if (chosenAlgorithm.equals("MCOD")) {
            mcodObj.evaluateRemainingElemsInWin();
        } else if (chosenAlgorithm.equals("ApproxMCOD")) {
            approxMCODObj.evaluateRemainingElemsInWin();
        } else if (chosenAlgorithm.equals("LSHOD")) {
            lshodObj.evaluateRemainingElemsInWin();
        } else if (chosenAlgorithm.equals("ApproxMCLSHOD")) {
            approxMCLSHODObj.evaluateRemainingElemsInWin();
        }

        if (chosenAlgorithm.equals("MCOD")) {
            exportOutliersToFile(mcodObj.getOutliersFound(), outliersFile);
        } else if (chosenAlgorithm.equals("ApproxMCOD")) {
            exportOutliersToFile(approxMCODObj.getOutliersFound(), outliersFile);
        } else if (chosenAlgorithm.equals("LSHOD")) {
            exportOutliersToFile(lshodObj.getOutliersFound(), outliersFile);
        } else if (chosenAlgorithm.equals("ApproxMCLSHOD")) {
            exportOutliersToFile(approxMCLSHODObj.getOutliersFound(), outliersFile);
        }
    }

    public void addNewStreamObjects() {
        Long nsNow;

        if (chosenAlgorithm.equals("MCOD")) {
            nsNow = System.nanoTime();

            mcodObj.ProcessNewStreamObjects(stream.getIncomingData(slideSize));

            updateMaxMemUsage();
            nTotalRunTime += (System.nanoTime() - nsNow) / (1024 * 1024);

            // update process time per object
            nProcessed++;
            m_timePreObjSum += System.nanoTime() - nsNow;
            if (nProcessed % m_timePreObjInterval == 0) {
                nTimePerObj = ((double) m_timePreObjSum) / ((double) m_timePreObjInterval);
                // init
                m_timePreObjSum = 0L;
            }
        } else if (chosenAlgorithm.equals("ApproxMCOD")) {
            nsNow = System.nanoTime();

            approxMCODObj.ProcessNewStreamObjects(stream.getIncomingData(slideSize));

            updateMaxMemUsage();
            nTotalRunTime += (System.nanoTime() - nsNow) / (1024 * 1024);

            // update process time per object
            nProcessed++;
            m_timePreObjSum += System.nanoTime() - nsNow;
            if (nProcessed % m_timePreObjInterval == 0) {
                nTimePerObj = ((double) m_timePreObjSum) / ((double) m_timePreObjInterval);
                // init
                m_timePreObjSum = 0L;
            }
        } else if (chosenAlgorithm.equals("LSHOD")) {
            nsNow = System.nanoTime();

            lshodObj.processNewStreamObjects(stream.getIncomingData(slideSize));

            updateMaxMemUsage();
            nTotalRunTime += (System.nanoTime() - nsNow) / (1024 * 1024);

            // update process time per object
            nProcessed++;
            m_timePreObjSum += System.nanoTime() - nsNow;
            if (nProcessed % m_timePreObjInterval == 0) {
                nTimePerObj = ((double) m_timePreObjSum) / ((double) m_timePreObjInterval);
                // init
                m_timePreObjSum = 0L;
            }
        } else if (chosenAlgorithm.equals("ApproxMCLSHOD")) {
            nsNow = System.nanoTime();

            approxMCLSHODObj.processNewStreamObjects(stream.getIncomingData(slideSize));

            updateMaxMemUsage();
            nTotalRunTime += (System.nanoTime() - nsNow) / (1024 * 1024);

            // update process time per object
            nProcessed++;
            m_timePreObjSum += System.nanoTime() - nsNow;
            if (nProcessed % m_timePreObjInterval == 0) {
                nTimePerObj = ((double) m_timePreObjSum) / ((double) m_timePreObjInterval);
                // init
                m_timePreObjSum = 0L;
            }
        }
    }

    private <T extends DataObj<T>> void exportOutliersToFile(Set<Outlier<T>> outliersDetected, String targetFile) {
        try {
            BufferedWriter bw = new BufferedWriter(new FileWriter(targetFile));

            for (Outlier<T> outlier : outliersDetected) {
                bw.write(Long.toString(outlier.id));
                bw.newLine();
            }

            bw.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public HashMap<String, Integer> getResults() {
        HashMap<String, Integer> results = null;
        if (chosenAlgorithm.equals("MCOD")) {
            results = mcodObj.getResults();
        } else if (chosenAlgorithm.equals("ApproxMCOD")) {
            results = approxMCODObj.getResults();
        } else if (chosenAlgorithm.equals("LSHOD")) {
            results = lshodObj.getResults();
        } else if (chosenAlgorithm.equals("ApproxMCLSHOD")) {
            results = approxMCLSHODObj.getResults();
        }

        return results;
    }

    public void printResults() {
        HashMap<String, Integer> results = getResults();

        int nBothInlierOutlier = results.get("nBothInlierOutlier");
        int nOnlyInlier = results.get("nOnlyInlier");
        int nOnlyOutlier = results.get("nOnlyOutlier");
        int nRangeQueriesExecuted = results.get("nRangeQueriesExecuted");

        System.out.println("Statistics:\n\n");
        int sum = nBothInlierOutlier + nOnlyInlier + nOnlyOutlier;
        if (sum > 0) {
            System.out.println(String.format("  Nodes always inlier: %d (%.1f%%)\n", nOnlyInlier, (100 * nOnlyInlier) / (double)sum));
            System.out.println(String.format("  Nodes always outlier: %d (%.1f%%)\n", nOnlyOutlier, (100 * nOnlyOutlier) / (double)sum));
            System.out.println(String.format("  Nodes both inlier and outlier: %d (%.1f%%)\n", nBothInlierOutlier, (100 * nBothInlierOutlier) / (double)sum));

            System.out.println("  (Sum: " + sum + ")\n");
        }

        System.out.println("\n  Total range queries: " + nRangeQueriesExecuted + "\n");
        System.out.println("  Max memory usage: " + iMaxMemUsage + " MB\n");
        System.out.println("  Total process time: " + String.format("%.2f ms", nTotalRunTime / 1000.0) + "\n");
    }

    private void updateMaxMemUsage() {
        int x = GetMemoryUsage();
        if (iMaxMemUsage < x) iMaxMemUsage = x;
    }
    private int GetMemoryUsage() {
        int iMemory = (int) ((Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024));
        return iMemory;
    }

    public static void main(String[] args) {
        Executor executor = new Executor(args);
        executor.performOutlierDetection();
        executor.printResults();
    }
}
