package algorithms;

import core.mcodbase.ISBIndex;
import core.mcodbase.MTreeMicroClusters;
import core.mcodbase.MicroCluster;
import core.OutlierDetector;
import core.mcodbase.ISBIndex.ISBEntry;
import core.mcodbase.ISBIndex.ISBEntry.EntryType;

import java.util.HashMap;
import java.util.TreeSet;
import java.util.Vector;

public class MCODBase extends OutlierDetector<ISBEntry> {
    protected static class EventItem implements Comparable<EventItem> {
        public ISBEntry entry;
        public Long timeStamp;

        public EventItem(ISBEntry entry, Long timeStamp) {
            this.entry = entry;
            this.timeStamp = timeStamp;
        }

        @Override
        public int compareTo(EventItem t) {
            if (this.timeStamp > t.timeStamp) {
                return +1;
            } else if (this.timeStamp < t.timeStamp) {
                return -1;
            } else {
                if (this.entry.id > t.entry.id)
                    return +1;
                else if (this.entry.id < t.entry.id)
                    return -1;
            }
            return 0;
        }
    }

    protected static class EventQueue {
        public TreeSet<EventItem> setEvents;

        public EventQueue() {
            setEvents = new TreeSet<EventItem>();
        }

        public void insert(ISBEntry entry, Long expTime) {
            setEvents.add(new EventItem(entry, expTime));
        }

        public EventItem findMin() {
            if (setEvents.size() > 0) {
                // events are sorted ascenting by expiration time
                return setEvents.first();
            }
            return null;
        }

        public EventItem extractMin() {
            EventItem e = findMin();
            if (e != null) {
                setEvents.remove(e);
                return e;
            }
            return null;
        }
    }

    protected class SearchResultMC {
        public MicroCluster mc;
        public double distance;

        public SearchResultMC(MicroCluster mc, double distance) {
            this.mc = mc;
            this.distance = distance;
        }
    }

    protected int nRangeQueriesExecuted = 0;

    // object identifier increments with each new data stream object
    protected Long objId;
    protected EventQueue eventQueue;
    // MTree index of micro-clusters
    protected MTreeMicroClusters mtreeMC;
    // set of micro-clusters (for trace)
    protected TreeSet<MicroCluster> setMC;
    // Entries treated as new entries when a mc removed
    protected TreeSet<ISBEntry> entriesReinsert;
    // index of objects not in any micro-cluster
    protected ISBIndex ISB_PD;

    protected double m_radius;
    protected int m_k;
    protected double m_theta = 1.0;

    // statistics
    public int m_nBothInlierOutlier;
    public int m_nOnlyInlier;
    public int m_nOnlyOutlier;

    public MCODBase(int windowSize, int slideSize, double radius, int k) {
        super(windowSize, slideSize);

        m_radius = radius;
        m_k = k;

        objId = FIRST_OBJ_ID; // init object identifier
        // create ISB
        ISB_PD = new ISBIndex(m_radius, m_k);
        // create helper sets for micro-cluster management
        setMC = new TreeSet<MicroCluster>();
        // micro-cluster index
        mtreeMC = new MTreeMicroClusters();
        // create event queue
        eventQueue = new EventQueue();

        // init statistics
        m_nBothInlierOutlier = 0;
        m_nOnlyInlier = 0;
        m_nOnlyOutlier = 0;
    }

    protected void setEntryType(ISBEntry entry, EntryType type) {
        entry.entryType = type;
        // update statistics
        if (type == EntryType.OUTLIER)
            entry.nOutlier++;
        else
            entry.nInlier++;
    }

    protected void addToEventQueue(ISBEntry x, ISBEntry entryMinExp) {
        if (entryMinExp != null) {
            Long expTime = getExpirationTime(entryMinExp);
            eventQueue.insert(x, expTime);
        }
    }

    protected Long getExpirationTime(ISBEntry entry) {
        return entry.id + windowSize + 1;
    }

    protected int getEntrySlide(ISBEntry entry) {
        // Since entry IDs begin from 1, we subtract 1 from the id so that the integer division
        // operation always returns the correct slide the entry belongs to.
        long adjustedID = entry.id - 1;

        // The result is incremented by 1 since the slide index starts from 1.
        return (int)(adjustedID / slideSize) + 1;

    }

    protected void doSlide() {
        windowStart += slideSize;
        windowEnd += slideSize;
    }

    protected boolean isSafeInlier(ISBEntry entry) {
        return entry.count_after >= m_k;
    }

    protected void addEntry(ISBEntry entry) {
        windowElements.add(entry);
    }

    protected void removeEntry(ISBEntry entry) {
        windowElements.remove(entry);
        // update statistics
        updateStatistics(entry);
        // Check whether the entry should be recorded as a pure outlier
        // by the outlier detector
        evaluateAsOutlier(entry);
    }

    protected void addMicroCluster(MicroCluster mc) {
        mtreeMC.add(mc);
        setMC.add(mc);
    }

    protected class CorruptedDataStateException extends Exception {
        public CorruptedDataStateException(String errorMessage) {
            super(errorMessage);
        }
    }

    protected void removeMicroCluster(MicroCluster mc) throws CorruptedDataStateException {
        boolean mtreeRemoval = mtreeMC.remove(mc);
        boolean setMCRemoval = setMC.remove(mc);

        if (mtreeRemoval != setMCRemoval) {
            throw new CorruptedDataStateException("The target mc was removed from setMC but was not found in M-Tree");
        }
    }

    protected void updateStatistics(ISBEntry entry) {
        if ((entry.nInlier > 0) && (entry.nOutlier > 0))
            m_nBothInlierOutlier++;
        else if (entry.nInlier > 0)
            m_nOnlyInlier++;
        else
            m_nOnlyOutlier++;
    }

    public HashMap<String, Integer> getResults() {
        // get counters of expired entries
        int nBothInlierOutlier = m_nBothInlierOutlier;
        int nOnlyInlier = m_nOnlyInlier;
        int nOnlyOutlier = m_nOnlyOutlier;

        // add counters of non expired entries still in window
        for (ISBEntry entry : windowElements) {
            if ((entry.nInlier > 0) && (entry.nOutlier > 0))
                nBothInlierOutlier++;
            else if (entry.nInlier > 0)
                nOnlyInlier++;
            else
                nOnlyOutlier++;
        }

        HashMap<String, Integer> results = new HashMap<>();
        results.put("nBothInlierOutlier", nBothInlierOutlier);
        results.put("nOnlyInlier", nOnlyInlier);
        results.put("nOnlyOutlier", nOnlyOutlier);
        results.put("nRangeQueriesExecuted", nRangeQueriesExecuted);
        return results;
    }

    protected double getEuclideanDist(ISBEntry n1, ISBEntry n2)
    {
        double diff;
        double sum = 0;
        int d = n1.obj.dimensions();
        for (int i = 0; i < d; i++) {
            diff = n1.obj.get(i) - n2.obj.get(i);
            sum += Math.pow(diff, 2);
        }
        return Math.sqrt(sum);
    }

    protected Vector<SearchResultMC> RangeSearchMC(ISBEntry newEntry, double radius) {
        Vector<SearchResultMC> results = new Vector<SearchResultMC>();
        // create a dummy mc in order to search w.r.t. newEntry
        MicroCluster dummy = new MicroCluster(newEntry);
        // query results are returned ascending by distance
        MTreeMicroClusters.Query query = mtreeMC.getNearestByRange(dummy, radius);
        for (MTreeMicroClusters.ResultItem q : query) {
            results.add(new SearchResultMC(q.data, q.distance));
        }
        return results;
    }
}
