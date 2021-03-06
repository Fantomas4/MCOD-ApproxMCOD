/*
 *    MCODBase.java
 *    Copyright (C) 2013 Aristotle University of Thessaloniki, Greece
 *    @author D. Georgiadis, A. Gounaris, A. Papadopoulos, K. Tsichlas, Y. Manolopoulos
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 *
 */

package algorithms;


import core.ISBIndex.ISBNode;
import core.ISBIndex.ISBSearchResult;
import core.ISBIndex.ISBNode.NodeType;
import core.MicroCluster;
import core.StreamObj;

import java.util.*;

public class MCOD extends MCODBase {
    // DIAG ONLY -- DELETE
    int diagExactMCCount = 0;
    int diagDiscardedMCCount = 0;
    int diagAdditionsToMC = 0;
    int diagAdditionsToPD = 0;
    int diagSafeInliersCount = 0;

    public MCOD(int windowSize, int slideSize, double radius, int k) {
        super(windowSize, slideSize, radius, k);
    }

    void AddNeighbor(ISBNode node, ISBNode q, boolean bUpdateState) {
        // check if q still in window
        if (IsNodeIdInWin(q.id) == false) {
            return;
        }

        if (q.id < node.id) {
            node.AddPrecNeigh(q);
        } else {
            node.count_after++;
        }

        if (bUpdateState) {
            // check if node inlier or outlier
            int count = node.count_after + node.CountPrecNeighs(windowStart);
            if ((node.nodeType == NodeType.OUTLIER) && (count >= m_k)) {
                // remove node from outliers
                RemoveOutlier(node);
                // add node to inlier set PD
                SetNodeType(node, NodeType.INLIER_PD);
                // insert node to event queue
                ISBNode nodeMinExp = node.GetMinPrecNeigh(windowStart);
                AddToEventQueue(node, nodeMinExp);
            }
        }
    }

    void ProcessNewNode(ISBNode nodeNew, boolean bNewNode) {
        // Perform 3R/2 range query to cluster centers w.r.t new node
        Vector<SearchResultMC> resultsMC;
        // results are sorted ascenting by distance
        resultsMC = RangeSearchMC(nodeNew, 1.5 * m_radius);

        // Get closest micro-cluster
        MicroCluster mcClosest = null;
        if (resultsMC.size() > 0) {
            mcClosest = resultsMC.get(0).mc;
        }

        // check if nodeNew can be inserted to closest micro-cluster
        boolean bFoundMC = false;
        if (mcClosest != null) {
            double d = GetEuclideanDist(nodeNew, mcClosest.mcc);
            if (d <= m_radius / 2) {
                bFoundMC = true;
            }
        }

        if (bFoundMC) {
            // Add new node to micro-cluster
            // DIAG ONLY -- DELETE
            diagAdditionsToMC++;
            nodeNew.mc = mcClosest;
            SetNodeType(nodeNew, NodeType.INLIER_MC);
            mcClosest.AddNode(nodeNew);

            // Update neighbors of set PD
            Vector<ISBNode> nodes;
            nodes = ISB_PD.GetAllNodes();
            for (ISBNode q : nodes) {
                if (q.Rmc.contains(mcClosest)) {
                    if (GetEuclideanDist(q, nodeNew) <= m_radius) {
                        if (bNewNode) {
                            // update q.count_after and its' outlierness
                            AddNeighbor(q, nodeNew, true);
                        } else {
                            if (nodesReinsert.contains(q)) {
                                // update q.count_after or q.nn_before and its' outlierness
                                AddNeighbor(q, nodeNew, true);
                            }
                        }
                    }
                }
            }
        }
        else {
            // No close enough micro-cluster found.
            // Perform 3R/2 range query to nodes in set PD.
            nRangeQueriesExecuted++;
            // create helper sets for micro-cluster management
            ArrayList<ISBNode> setNC = new ArrayList<ISBNode>();
            ArrayList<ISBNode> setNNC = new ArrayList<ISBNode>();
            Vector<ISBSearchResult> resultNodes;
            resultNodes = ISB_PD.RangeSearch(nodeNew, 1.5 * m_radius); // 1.5 ###
            for (ISBSearchResult sr : resultNodes) {
                ISBNode q = sr.node;
                if (sr.distance <= m_radius) {
                    // add q to neighs of nodeNew
                    AddNeighbor(nodeNew, q, false);
                    if (bNewNode) {
                        // update q.count_after and its' outlierness
                        AddNeighbor(q, nodeNew, true);
                    } else {
                        if (nodesReinsert.contains(q)) {
                            // update q.count_after or q.nn_before and its' outlierness
                            AddNeighbor(q, nodeNew, true);
                        }
                    }
                }

                if (sr.distance <= m_radius / 2.0) {
                    setNC.add(q);
                } else {
                    setNNC.add(q);
                }
            }

            // check if size of set NC big enough to create cluster
            if (setNC.size() >= m_theta * m_k) {
                // DIAG ONLY -- DELETE
                diagExactMCCount ++;

                // create new micro-cluster with center nodeNew
                MicroCluster mcNew = new MicroCluster(nodeNew);
                AddMicroCluster(mcNew);
                nodeNew.mc = mcNew;
                // DIAG ONLY -- DELETE
                diagAdditionsToMC++;
                SetNodeType(nodeNew, NodeType.INLIER_MC);

                // Add to new mc nodes within range R/2
                for (ISBNode q : setNC) {
                    q.mc = mcNew;
                    // DIAG ONLY -- DELETE
                    diagAdditionsToMC++;
                    mcNew.AddNode(q);
                    // move q from set PD to set inlier-mc
                    SetNodeType(q, NodeType.INLIER_MC);
                    ISB_PD.Remove(q);
                    RemoveOutlier(q); // needed? ###
                }

                // Update Rmc lists of nodes of PD in range 3R/2 from mcNew
                for (ISBNode q : setNNC) {
                    q.Rmc.add(mcNew);
                }
            } else {
                // Add to nodeNew neighs nodes of near micro-clusters
                for (SearchResultMC sr : resultsMC) {
                    for (ISBNode q : sr.mc.nodes) {
                        if (GetEuclideanDist(q, nodeNew) <= m_radius) {
                            // add q to neighs of nodeNew
                            AddNeighbor(nodeNew, q, false);
                        }
                    }
                }

                ISB_PD.Insert(nodeNew);
                diagAdditionsToPD++;

                // check if nodeNew is an inlier or outlier
                // use both nn_before and count_after for case bNewNode=false
                int count = nodeNew.CountPrecNeighs(windowStart) + nodeNew.count_after;
                if (count >= m_k) {
                    // nodeNew is an inlier
                    SetNodeType(nodeNew, NodeType.INLIER_PD);
                    // insert nodeNew to event queue
                    ISBNode nodeMinExp = nodeNew.GetMinPrecNeigh(windowStart);
                    AddToEventQueue(nodeNew, nodeMinExp);
                } else {
                    // nodeNew is an outlier
                    SetNodeType(nodeNew, NodeType.OUTLIER);
                    SaveOutlier(nodeNew);
                }

                // Update nodeNew.Rmc
                for (SearchResultMC sr : resultsMC) {
                    nodeNew.Rmc.add(sr.mc);
                }
            }
        }
    }

    void ProcessEventQueue(ISBNode nodeExpired) {
        // DIAG ONLY -- DELETE
        diagSafeInliersCount = 0;

        EventItem e = eventQueue.FindMin();
        while ((e != null) && (e.timeStamp <= windowEnd)) {
            e = eventQueue.ExtractMin();
            ISBNode x = e.node;
            // node x must be in window and not in any micro-cluster
            boolean bValid = ( IsNodeIdInWin(x.id) && (x.mc == null) );
            if (bValid) {
                // remove nodeExpired from x.nn_before
                x.RemovePrecNeigh(nodeExpired);
                // get amount of neighbors of x
                int count = x.count_after + x.CountPrecNeighs(windowStart);
                if (count < m_k) {
                    // x is an outlier
                    SetNodeType(x, NodeType.OUTLIER);
                    SaveOutlier(x);
                } else {
                    // x is an inlier, add to event queue
                    // DIAG ONLY -- DELETE
                    if (x.count_after >= m_k) diagSafeInliersCount++;

                    // get oldest preceding neighbor of x
                    ISBNode nodeMinExp = x.GetMinPrecNeigh(windowStart);
                    // add x to event queue
                    AddToEventQueue(x, nodeMinExp);
                }
            }
            e = eventQueue.FindMin();
        }
    }

    void ProcessExpiredNodes(ArrayList<ISBNode> expiredNodes) {
        for (ISBNode expiredNode : expiredNodes) {
            MicroCluster mc = expiredNode.mc;
            if (mc != null) {
                mc.RemoveNode(expiredNode);
                if (mc.GetNodesCount() < m_k) {
                    // DIAG ONLY -- DELETE
                    diagDiscardedMCCount ++;

                    // remove micro-cluster mc
                    RemoveMicroCluster(mc);

                    // insert nodes of mc to set nodesReinsert
                    nodesReinsert = new TreeSet<ISBNode>();
                    System.out.println("mc.nodes size 1: " + mc.nodes.size());
                    for (ISBNode q : mc.nodes) {
                        nodesReinsert.add(q);
                    }

                    System.out.println("mc.nodes size 2: " + mc.nodes.size());
                    System.out.println("MPHKAAAAAAAAAAAAAAAAAAAAA");
                    System.out.println("mc.nodes size 3: " + mc.nodes.size());
                    // treat each node of mc as new node
                    for (ISBNode q : mc.nodes) {
                        System.out.println("MPHKAAAA2222222222222222222222222");
                        System.out.println("mc.nodes size 4: " + mc.nodes.size());
                        q.InitNode();
                        System.out.println("mc.nodes size 5: " + mc.nodes.size());
                        ProcessNewNode(q, false);
                        System.out.println("mc.nodes size 6: " + mc.nodes.size());
                    }
                }
            } else {
                // expiredNode belongs to set PD
                // remove expiredNode from PD index
                ISB_PD.Remove(expiredNode);
            }

            RemoveNode(expiredNode);
            ProcessEventQueue(expiredNode);
        }
    }

    public void ProcessNewStreamObjects(ArrayList<StreamObj> streamObjs) {
        if (windowNodes.size() == windowSize) {
            // If the window is full, perform a slide
            doSlide();
            // Process expired nodes
            ProcessExpiredNodes(GetExpiredNodes());
        }

        // Process new nodes
        for (StreamObj streamObj : streamObjs) {
            ISBNode nodeNew = new ISBNode(streamObj, objId); // create new ISB node
            AddNode(nodeNew); // add nodeNew to window
            ProcessNewNode(nodeNew, true);

            objId++; // update object identifier
        }


        // DIAG ONLY -- DELETE
        System.out.println("-------------------- MCOD baseline --------------------");
        System.out.println("DIAG - Current stream object: " + (objId - 1));
        System.out.println("DIAG - Total Exact MCs count: " + diagExactMCCount);
        System.out.println("DIAG - Total Discarded MCs: " + diagDiscardedMCCount);
        System.out.println("DIAG - #Times a point was added to an MC: " + diagAdditionsToMC);
        System.out.println("DIAG - #Times a point was added to PD: " + diagAdditionsToPD);
        System.out.println("DIAG - #Safe inliers detected: " + diagSafeInliersCount);
        System.out.println("DIAG - Total -ACTIVE- MCs: " + setMC.size());
        System.out.println("DIAG - Total -ACTIVE- PD List Population: " + ISB_PD.GetSize());
        System.out.println("DIAG - TEMP OUTLIER SET SIZE: " + GetOutliersFound().size());
        System.out.println("DIAG - TEMP Window size is: " + windowNodes.size());
        System.out.println("-------------------------------------------------------");
    }

    private ArrayList<ISBNode> GetExpiredNodes() {
        ArrayList<ISBNode> expiredNodes = new ArrayList<>();
        for (ISBNode node : windowNodes) {
            // check if node has expired
            if (node.id < windowStart) {
                expiredNodes.add(node);
            } else {
                break;
            }
        }
        return expiredNodes;
    }

    void AddMicroCluster(MicroCluster mc) {
        mtreeMC.add(mc);
        setMC.add(mc);
    }

    void RemoveMicroCluster(MicroCluster mc) {
        System.out.println(mtreeMC.remove(mc));
        System.out.println(setMC.remove(mc));
    }
}