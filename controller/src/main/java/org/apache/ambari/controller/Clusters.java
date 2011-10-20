/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ambari.controller;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.StringTokenizer;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import org.apache.ambari.common.rest.entities.Blueprint;
import org.apache.ambari.common.rest.entities.ClusterDefinition;
import org.apache.ambari.common.rest.entities.ClusterInformation;
import org.apache.ambari.common.rest.entities.ClusterState;
import org.apache.ambari.common.rest.entities.Node;
import org.apache.ambari.common.rest.entities.RoleToNodes;
import org.apache.ambari.resource.statemachine.ClusterFSM;
import org.apache.ambari.resource.statemachine.StateMachineInvoker;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class Clusters {
    private static Log LOG = LogFactory.getLog(Clusters.class);
    
    /*
     * Operational clusters include both active and inactive clusters
     */
    protected ConcurrentHashMap<String, Cluster> operational_clusters = new ConcurrentHashMap<String, Cluster>();
    
    /*
     * Operational clusters ID to name map
     */
    protected ConcurrentHashMap<String, String> operational_clusters_id_to_name = new ConcurrentHashMap<String, String>();
    
    /*
     * List of clusters to be deleted when they get into ATTIC state 
     * When cluster state is switched to ATTIC, if it is present in the tobe_deleted_list, 
     * it should be deleted from the persistence list and inmemory list. 
     */
    ConcurrentHashMap<String, String> tobe_deleted_clusters = new ConcurrentHashMap<String, String>();
    
    private static Clusters ClustersTypeRef=null;
        
    private Clusters() {
        /*
         * TODO: Check if blueprint in cluster definition AND its parents already exists
         */
        Blueprints.getInstance().createDummyBlueprint("cluster123-site-site-site-blueprint", "0", null, null);
        Blueprints.getInstance().createDummyBlueprint("cluster123-site-site-blueprint", "0", "cluster123-site-site-site-blueprint", "0");
        Blueprints.getInstance().createDummyBlueprint("cluster123-site-blueprint", "0", "cluster123-site-site-blueprint", "0");
        Blueprints.getInstance().createDummyBlueprint("cluster123-blueprint", "0", "cluster123-site-blueprint", "0");
        Blueprints.getInstance().createDummyBlueprint("cluster124-site-blueprint", "0", null, null);
        Blueprints.getInstance().createDummyBlueprint("cluster124-blueprint", "0", "cluster124-site-blueprint", "0");
        
        /*
         * Cluster definition 
         */
        ClusterDefinition cluster123 = new ClusterDefinition();
        
        cluster123.setName("blue.dev.Cluster123");
        cluster123.setBlueprintName("cluster123-blueprint");
        cluster123.setBlueprintRevision("0");
        cluster123.setDescription("cluster123 - development cluster");
        cluster123.setGoalState(ClusterState.CLUSTER_STATE_ATTIC);
        List<String> activeServices = new ArrayList<String>();
        activeServices.add("hdfs");
        activeServices.add("mapred");
        cluster123.setActiveServices(activeServices);
        
        String nodes = "jt-nodex,nn-nodex,hostname-1x,hostname-2x,hostname-3x,"+
                       "hostname-4x,node-2x,node-3x,node-4x";  
        cluster123.setNodes(nodes);
        
        List<RoleToNodes> rnm = new ArrayList<RoleToNodes>();
        
        RoleToNodes rnme = new RoleToNodes();
        rnme.setRoleName("jobtracker-role");
        rnme.setNodes("jt-nodex");
        rnm.add(rnme);
        
        rnme = new RoleToNodes();
        rnme.setRoleName("namenode-role");
        rnme.setNodes("nn-nodex");
        rnm.add(rnme);
        
        rnme = new RoleToNodes();
        rnme.setRoleName("slaves-role");
        rnme.setNodes("hostname-1x,hostname-2x,hostname-3x,"+
                       "hostname-4x,node-2x,node-3x,node-4x");
        rnm.add(rnme);
        
        cluster123.setRoleToNodesMap(rnm);
        
        /*
         * Cluster definition 
         */
        ClusterDefinition cluster124 = new ClusterDefinition();
        cluster124.setName("blue.research.Cluster124");
        cluster124.setBlueprintName("cluster124-blueprint");
        cluster124.setBlueprintRevision("0");
        cluster124.setDescription("cluster124 - research cluster");
        cluster124.setGoalState(ClusterState.CLUSTER_STATE_INACTIVE);
        activeServices = new ArrayList<String>();
        activeServices.add("hdfs");
        activeServices.add("mapred");
        cluster124.setActiveServices(activeServices);
        
        nodes = "jt-node,nn-node,hostname-1,hostname-2,hostname-3,hostname-4,"+
                "node-2,node-3,node-4";  
        cluster124.setNodes(nodes);
        
        rnm = new ArrayList<RoleToNodes>();
        
        rnme = new RoleToNodes();
        rnme.setRoleName("jobtracker-role");
        rnme.setNodes("jt-node");
        rnm.add(rnme);
        
        rnme = new RoleToNodes();
        rnme.setRoleName("namenode-role");
        rnme.setNodes("nn-node");
        rnm.add(rnme);
        
        rnme = new RoleToNodes();
        rnme.setRoleName("slaves-role");
        rnme.setNodes("hostname-1,hostname-2,hostname-3,hostname-4,"+
                      "node-2,node-3,node-4");
        rnm.add(rnme);
        
        cluster124.setRoleToNodesMap(rnm);
        
        try {
            addCluster(cluster123, false);
            addCluster(cluster124, false);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public static synchronized Clusters getInstance() {
        if(ClustersTypeRef == null) {
                ClustersTypeRef = new Clusters();
        }
        return ClustersTypeRef;
    }

    public Object clone() throws CloneNotSupportedException {
        throw new CloneNotSupportedException();
    }

    /*
     * Rename the cluster
     */
    public void renameCluster(String clusterName, String new_name) throws Exception {
        /*
         * 
         */
        if (!this.operational_clusters.containsKey(clusterName)) {
            String msg = "Cluster ["+clusterName+"] does not exist";
            throw new WebApplicationException((new ExceptionResponse(msg, Response.Status.NOT_FOUND)).get());
        }
        
        if (new_name == null || new_name.equals("")) {
            String msg = "New name of the cluster should be specified as query parameter, (?new_name=xxxx)";
            throw new WebApplicationException((new ExceptionResponse(msg, Response.Status.BAD_REQUEST)).get());
        }
        
        synchronized (operational_clusters) {
            /*
             * Check if cluster state is ATTAIC, If yes update the name
             * don't make new revision of cluster definition as it is in ATTIC state
             */
            if (!this.operational_clusters.get(clusterName).getClusterState().getState().equals(ClusterState.CLUSTER_STATE_ATTIC)) {
                String msg = "Cluster state is not ATTIC. Cluster is only allowed to be renamed in ATTIC state";
                throw new WebApplicationException((new ExceptionResponse(msg, Response.Status.NOT_ACCEPTABLE)).get());
            }
            
            Cluster x = this.operational_clusters.get(clusterName);
            x.getLatestClusterDefinition().setName(new_name);
            this.operational_clusters.remove(clusterName);
            this.operational_clusters.put(new_name, x);
            this.operational_clusters_id_to_name.remove(x.getID());
            this.operational_clusters_id_to_name.put(x.getID(), new_name);
        }
     
    }
    
    /* 
     * Add new Cluster to cluster list 
     * Validate the cluster definition
     * Lock the cluster list
     *   -- Check if cluster with given name already exist?
     *   -- Set the cluster state and timestamps 
     *   -- Reserve the nodes. i.e. add the cluster and role referenes to Node
     *   -- Throw exception, if some nodes are already preallocated to other cluster.
     *   -- Persist the cluster definition as revision 0 and list of node names against cluster & service:role 
     *   -- Background daemon should trigger the agent installation on the new nodes (UNREGISTERED), if not done already. 
     *      (daemon can keep track of which nodes agent is already installed or check it by ssh to nodes, if nodes added
     *       are in UNREGISTERED state).  
     */   
    public ClusterDefinition addCluster(ClusterDefinition cdef, boolean dry_run) throws Exception {

        /*
         * TODO: Validate the cluster definition and set the default
         */
        validateClusterDefinition(cdef);
        
        synchronized (operational_clusters) {
            /* 
             * Check if cluster already exist
             */
            if (operational_clusters.containsKey(cdef.getName())) {
                String msg = "Cluster ["+cdef.getName()+"] already exist";
                throw new WebApplicationException((new ExceptionResponse(msg, Response.Status.CONFLICT)).get());
            }
 
            /*
             * Create new cluster object
             */
            Date requestTime = new Date();
            Cluster cls = new Cluster();
            ClusterState clsState = new ClusterState();
            clsState.setCreationTime(requestTime);
            clsState.setLastUpdateTime(requestTime);
            clsState.setDeployTime((Date)null);          
            if (cdef.getGoalState().equals(ClusterDefinition.GOAL_STATE_ATTIC)) {
                clsState.setState(ClusterState.CLUSTER_STATE_ATTIC);
            } else {
                clsState.setState(ClusterDefinition.GOAL_STATE_INACTIVE);
            }
            cls.setID(UUID.randomUUID().toString());
            cls.addClusterDefinition(cdef);
            cls.setClusterState(clsState);
            
            /*
             * If dry run then update roles to nodes map, if not specified explicitly
             * and return
             */
            if (dry_run) {
                List<RoleToNodes> role2NodesList = generateRoleToNodesListBasedOnNodeAttributes (cdef);
                cdef.setRoleToNodesMap(role2NodesList);
                return cdef;
            }
            
            /*
             * Update cluster nodes reservation. 
             */
            if (cdef.getNodes() != null 
                && !cdef.getGoalState().equals(ClusterDefinition.GOAL_STATE_ATTIC)) {
                updateClusterNodesReservation (cls.getID(), cdef);
            }
            
            /*
             * Update the Node to Roles association, if specified
             * If role is not explicitly associated w/ any node, then assign it w/ default role
             * If RoleToNodes list is not specified then derive it based on the node attributes  
             */
            if (!cdef.getGoalState().equals(ClusterDefinition.GOAL_STATE_ATTIC)) {
                if (cdef.getRoleToNodes() == null) {
                    /*
                     * TODO: Derive the role to nodes map based on nodes attributes
                     * then populate the node to roles association.
                     */
                    List<RoleToNodes> role2NodesList = generateRoleToNodesListBasedOnNodeAttributes (cdef);
                    cdef.setRoleToNodesMap(role2NodesList);
                }
                updateNodeToRolesAssociation(cdef.getNodes(), cdef.getRoleToNodes());
            }
            
            /*
             * TODO: Persist the cluster definition to data store as a initial version r0. 
             *          Persist reserved nodes against the cluster & service/role
             */
                
            // Add the cluster to the list, after definition is persisted
            this.operational_clusters.put(cdef.getName(), cls);
            this.operational_clusters_id_to_name.put(cls.getID(), cdef.getName());
            
            /*
             * Activate the cluster if the goal state is ACTIVE
             * TODO: What to do if activate fails ??? 
            */
            if(cdef.getGoalState().equals(ClusterDefinition.GOAL_STATE_ACTIVE)) {          
                org.apache.ambari.resource.statemachine.ClusterFSM cs = 
                    StateMachineInvoker.createCluster(cls.getID(),cdef,cls.getClusterState());
            }
        }
        return cdef;
    } 
    
    /*
     * Create RoleToNodes list based on node attributes
     * TODO: For now just pick some nodes randomly
     */
    public List<RoleToNodes> generateRoleToNodesListBasedOnNodeAttributes (ClusterDefinition cdef) {
        List<RoleToNodes> role2NodesList = new ArrayList<RoleToNodes>();
        return role2NodesList;
    }
    
    /*
     * Validates the cluster definition
     * TODO: Validate each role has enough nodes associated with it. 
     */
    private void validateClusterDefinition (ClusterDefinition cdef) throws Exception {
        /*
         * Check if name is not empty or null
         */
        if (cdef.getName() == null ||  cdef.getName().equals("")) {
            String msg = "Cluster Name must be specified and must be non-empty string";
            throw new WebApplicationException((new ExceptionResponse(msg, Response.Status.BAD_REQUEST)).get());
        }
        
        if (cdef.getNodes() == null || cdef.getNodes().equals("")) {
            String msg = "Cluster node range must be specified and must be non-empty string";
            throw new WebApplicationException((new ExceptionResponse(msg, Response.Status.BAD_REQUEST)).get());
        }
        
        if (cdef.getBlueprintName() == null || cdef.getBlueprintName().equals("")) {
            String msg = "Cluster blueprint must be specified and must be non-empty string";
            throw new WebApplicationException((new ExceptionResponse(msg, Response.Status.BAD_REQUEST)).get());
        }
        
        if (cdef.getBlueprintRevision() == null || cdef.getBlueprintRevision().equals("")) {
            String msg = "Cluster blueprint revision must be specified";
            throw new WebApplicationException((new ExceptionResponse(msg, Response.Status.BAD_REQUEST)).get());
        }
        
        /*
         * Check if the cluster blueprint and its parents exist
         * getBlueprint would throw exception if it does not find the blueprint
         */
        Blueprint bp = Blueprints.getInstance()
                       .getBlueprint(cdef.getBlueprintName(), Integer.parseInt(cdef.getBlueprintRevision()));
        while (bp.getParentName() != null) {
            if (bp.getParentRevision() == null) {
                bp = Blueprints.getInstance()
                    .getBlueprint(bp.getParentName(), -1);
            } else {
                bp = Blueprints.getInstance()
                .getBlueprint(bp.getParentName(), Integer.parseInt(bp.getParentRevision()));
            }
        }
        
        /* 
         * Populate the input cluster definition w/ default values
         */
        if (cdef.getDescription() == null) { cdef.setDescription("Ambari cluster : "+cdef.getName());
        }
        if (cdef.getGoalState() == null) { cdef.setGoalState(cdef.GOAL_STATE_INACTIVE);
        }
        
        // TODO: Add the list of active services by querying pluging component.
        if (cdef.getActiveServices() == null) {
            List<String> services = new ArrayList<String>();
            services.add("ALL");
            cdef.setActiveServices(services);
        }
        
        /*
         * Check if all the nodes explicitly specified in the RoleToNodesMap belong the cluster node range specified 
         */
        List<String> cluster_node_range = new ArrayList<String>();
        cluster_node_range.addAll(getHostnamesFromRangeExpressions(cdef.getNodes()));
        if (cdef.getRoleToNodes() != null) {
            List<String> nodes_specified_using_role_association = new ArrayList<String>();
            for (RoleToNodes e : cdef.getRoleToNodes()) {
                List<String> hosts = getHostnamesFromRangeExpressions(e.getNodes());
                nodes_specified_using_role_association.addAll(hosts);
                // TODO: Remove any duplicate nodes from nodes_specified_using_role_association
            }
            
            nodes_specified_using_role_association.removeAll(cluster_node_range);
            if (!nodes_specified_using_role_association.isEmpty()) {
                String msg = "Some nodes explicityly associated with roles using RoleToNodesMap do not belong in the " +
                             "golbal node range specified for the cluster : ["+nodes_specified_using_role_association+"]";
                throw new WebApplicationException((new ExceptionResponse(msg, Response.Status.BAD_REQUEST)).get());
            }
        }
    }
    
    /*
     * Update the nodes associated with cluster
     */
    private synchronized void updateClusterNodesReservation (String clusterID, ClusterDefinition clsDef) throws Exception {
                
        ConcurrentHashMap<String, Node> all_nodes = Nodes.getInstance().getNodes();
        List<String> cluster_node_range = new ArrayList<String>();
        cluster_node_range.addAll(getHostnamesFromRangeExpressions(clsDef.getNodes()));
       
        /*
         * Reserve the nodes as specified in the node range expressions
         * -- throw exception if any nodes are pre-associated with other cluster
         */    
        List<String> nodes_currently_allocated_to_cluster = new ArrayList<String>();
        for (Node n : Nodes.getInstance().getNodes().values()) {
            if ( n.getNodeState().getClusterID() != null &&
                 n.getNodeState().getClusterID().equals(clusterID)) {
                nodes_currently_allocated_to_cluster.add(n.getName());
            }
        }
        
        List<String> nodes_to_allocate = new ArrayList<String>(cluster_node_range);
        nodes_to_allocate.removeAll(nodes_currently_allocated_to_cluster);
        List<String> nodes_to_deallocate = new ArrayList<String>(nodes_currently_allocated_to_cluster);
        nodes_to_deallocate.removeAll(cluster_node_range);
        
        /*
         * Check for any nodes that are allocated to other cluster
         */
        List<String> preallocatedhosts = new ArrayList<String>();
        for (String n : nodes_to_allocate) {
            if (all_nodes.containsKey(n) && 
                    (all_nodes.get(n).getNodeState().getClusterID() != null || 
                     all_nodes.get(n).getNodeState().getAllocatedToCluster()
                    )
                ) {
                preallocatedhosts.add(n);
            }
        }
        
        /* 
         * Throw exception, if some of the hosts are already allocated to other cluster
         */
        if (!preallocatedhosts.isEmpty()) {
            /*
             * TODO: Return invalid request code and return list of preallocated nodes as a part of
             *       response element
             */
            String msg = "Some of the nodes specified for the cluster roles are allocated to other cluster: ["+preallocatedhosts+"]";
            throw new WebApplicationException((new ExceptionResponse(msg, Response.Status.CONFLICT)).get());
        }
        
        /*
         * Allocate nodes to given cluster
         */    
        for (String node_name : nodes_to_allocate) {
            if (all_nodes.containsKey(node_name)) { 
                // Set the cluster name in the node 
                synchronized (all_nodes.get(node_name)) {
                    all_nodes.get(node_name).reserveNodeForCluster(clusterID, true);
                }    
            } else {
                Date epoch = new Date(0);
                Nodes.getInstance().checkAndUpdateNode(node_name, epoch);
                Node node = Nodes.getInstance().getNode(node_name);
                /*
                 * TODO: Set agentInstalled = true, unless controller uses SSH to setup the agent
                 */
                node.reserveNodeForCluster(clusterID, true);
            }
        }
        
        /*
         * deallocate nodes from a given cluster
         * TODO: Node agent would asynchronously clean up the node and notify it through heartbeat which 
         * would reset the clusterID associated with node
         */
        for (String node_name : nodes_to_deallocate) {
            if (all_nodes.containsKey(node_name)) {
                synchronized (all_nodes.get(node_name)) {
                    all_nodes.get(node_name).releaseNodeFromCluster();
                }
            }
        }
    }

    /*
     * This function disassociate the node from the cluster. The clsuterID associated w/
     * cluster will be reset by heart beat when node reports all clean.
     */
    public synchronized void releaseClusterNodes (String clusterID) throws Exception {
        String clusterName = this.getClusterByID(clusterID).getLatestClusterDefinition().getName();
        for (Node clusterNode : Nodes.getInstance().getClusterNodes (clusterName, "", "")) {
            clusterNode.releaseNodeFromCluster();     
        }
    }
    
    private synchronized void updateNodeToRolesAssociation (String clusterNodes, List<RoleToNodes> roleToNodesList) throws Exception {
        /*
         * Associate roles list with node
         */
        if (roleToNodesList == null) {
            return;
        }
        
        /*
         * Add list of roles to Node
         * If node is not explicitly associated with any role then assign it w/ default role
         */
        for (RoleToNodes e : roleToNodesList) {
            List<String> hosts = getHostnamesFromRangeExpressions(e.getNodes());
            for (String host : hosts) {
              if (Nodes.getInstance().getNodes().get(host).getNodeState().getNodeRoleNames() == null) {
                Nodes.getInstance().getNodes().get(host).getNodeState().setNodeRoleNames((new ArrayList<String>()));
              } 
              Nodes.getInstance().getNodes().get(host).getNodeState().getNodeRoleNames().add(e.getRoleName());
            }
        }
        
        
        /*
         * Get the list of specified global node list for the cluster and any nodes NOT explicitly specified in the
         * role to nodes map, assign them with default role 
         */
        List<String> specified_node_range = new ArrayList<String>();
        specified_node_range.addAll(getHostnamesFromRangeExpressions(clusterNodes));
        for (String host : specified_node_range) {
            if (Nodes.getInstance().getNodes().get(host).getNodeState().getNodeRoleNames() == null) {
                Nodes.getInstance().getNodes().get(host).getNodeState().setNodeRoleNames((new ArrayList<String>()));
                String cid = Nodes.getInstance().getNodes().get(host).getNodeState().getClusterID();
                Nodes.getInstance().getNodes().get(host).getNodeState().getNodeRoleNames().add(getDefaultRoleName(cid));
            } 
        }
    }

    /*
     * Get Cluster blueprint
     */
    public Blueprint getClusterBlueprint(String clusterName, boolean expanded) throws Exception {
        if (!this.operational_clusters.containsKey(clusterName)) {
            String msg = "Cluster ["+clusterName+"] does not exist";
            throw new WebApplicationException((new ExceptionResponse(msg, Response.Status.NOT_FOUND)).get());
        }
        
        Cluster cls = this.operational_clusters.get(clusterName);
        String blueprintName = cls.getLatestClusterDefinition().getBlueprintName();
        int blueprintRevision = Integer.parseInt(cls.getLatestClusterDefinition().getBlueprintRevision());
        
        Blueprint bp;
        if (!expanded) {
            bp = Blueprints.getInstance().getBlueprint(blueprintName, blueprintRevision);
        } else {
            // TODO: Get the derived/expanded blueprint
            bp = Blueprints.getInstance().getBlueprint(blueprintName, blueprintRevision);
        }
        return bp;
    }
    
    /* 
     * Update cluster definition 
     * TODO: As nodes or role to node association changes, validate key services nodes are not removed
     * TODO: Don't allow update of blueprint name/version if cluster is in ACTIVE/INACTIVE state
    */
    public ClusterDefinition updateCluster(String clusterName, ClusterDefinition c, boolean dry_run) throws Exception {
        
        /*
         * Validate cluster definition
         */
        if (c.getName() == null || c.getName().equals("") || !c.getName().equals(clusterName)) {
            String msg = "Cluster name in resource URI ["+clusterName+"] does not match with one specified in update request element";
            throw new WebApplicationException((new ExceptionResponse(msg, Response.Status.BAD_REQUEST)).get());
        }
        
        /*
         * Check if cluster already exist.  
         */
        if (!this.operational_clusters.containsKey(clusterName)) {
            String msg = "Cluster ["+clusterName+"] does not exist";
            throw new WebApplicationException((new ExceptionResponse(msg, Response.Status.NOT_FOUND)).get());
        }
        
        Cluster cls = this.operational_clusters.get(clusterName);
        /*
         * Time being we will keep entire updated copy as new revision
         */
        ClusterDefinition newcd = new ClusterDefinition ();
        
        synchronized (cls.getClusterDefinitionRevisionsList()) {
            newcd.setName(clusterName);
            if (c.getBlueprintName() != null) {
                newcd.setBlueprintName(c.getBlueprintName());
            } else {
                newcd.setBlueprintName(cls.getLatestClusterDefinition().getBlueprintName());
            }
            if (c.getBlueprintRevision() != null) {
                newcd.setBlueprintRevision(c.getBlueprintRevision());
            } else {
                newcd.setBlueprintRevision(cls.getLatestClusterDefinition().getBlueprintRevision());
            }
            if (c.getDescription() != null) {
                newcd.setDescription(c.getDescription());
            } else {
                newcd.setDescription(cls.getLatestClusterDefinition().getDescription());
            }
            if (c.getGoalState() != null) {
                newcd.setGoalState(c.getGoalState());
            } else {
                newcd.setGoalState(cls.getLatestClusterDefinition().getGoalState());
            }
            if (c.getActiveServices() != null) {
                newcd.setActiveServices(c.getActiveServices());
            } else {
                newcd.setActiveServices(cls.getLatestClusterDefinition().getActiveServices());
            }
            
            /*
             * TODO: What if controller is crashed after updateClusterNodesReservation 
             * before updating and adding new revision of cluster definition?
             */
            boolean updateNodesReservation = false;
            boolean updateNodeToRolesAssociation = false;
            if (c.getNodes() != null) {
                newcd.setNodes(c.getNodes());
                updateNodesReservation = true;
                
            } else {
                newcd.setNodes(cls.getLatestClusterDefinition().getNodes());
            }
            if (c.getRoleToNodes() != null) {
                newcd.setRoleToNodesMap(c.getRoleToNodes());
                updateNodeToRolesAssociation = true;
                
            }  
            
            /*
             * if Cluster goal state is ATTIC then no need to take any action other than
             * updating the cluster definition.
             */
            if (newcd.getGoalState().equals(ClusterState.CLUSTER_STATE_ATTIC)) {
                cls.getClusterState().setLastUpdateTime(new Date());
                cls.addClusterDefinition(newcd);
                /*
                 * TODO: Persist the latest cluster definition under new revision
                 */
                return cls.getLatestClusterDefinition();
            }
            
            /*
             * Validate the updated cluster definition
             */
            validateClusterDefinition(newcd);
            
            /*
             * TODO: If dry_run then return the newcd at this point
             */
            if (dry_run) {
                System.out.println ("Dry run for update cluster..");
                return newcd;
            }
            
            /*
             * Update the nodes reservation and node to roles association 
             */
            if (updateNodesReservation) {
                updateClusterNodesReservation (cls.getID(), c);   
            }
            if (updateNodeToRolesAssociation) {
                updateNodeToRolesAssociation(newcd.getNodes(), c.getRoleToNodes());
            }
            
            /*
             *  Update the last update time & revision
             */
            cls.getClusterState().setLastUpdateTime(new Date());
            cls.addClusterDefinition(newcd);
            
            /*
             * TODO: Persist the latest cluster definition under new revision
             */
            
            /*
             * Invoke state machine event
             */
            ClusterFSM clusterFSM = StateMachineInvoker.
                getStateMachineClusterInstance(cls.getID());
            if(c.getGoalState().equals(ClusterState.CLUSTER_STATE_ACTIVE)) {
              clusterFSM.activate();
            } else if(c.getGoalState().
                equals(ClusterState.CLUSTER_STATE_INACTIVE)) {
              clusterFSM.deactivate();
            } else if(c.getGoalState().
                equals(ClusterState.CLUSTER_STATE_ATTIC)) {
              clusterFSM.deactivate();
              clusterFSM.terminate();
            }
        }
        return cls.getLatestClusterDefinition();
    }
    
    /*
     * Delete Cluster 
     * Delete operation will bring the cluster to ATTIC state and then remove the
     * cluster definition from the controller 
     * When cluster state transitions to ATTIC, it should check if the cluster definition is 
     * part of tobe_deleted_clusters map and then delete the definition.
     * TODO: Delete definition from both operational_clusters and operational_clusters_id_name map and to_be_deleted 
     * clusters list.
     */
    public void deleteCluster(String clusterName) throws Exception { 
        synchronized (this.operational_clusters) {
            for (Cluster cls : this.operational_clusters.values()) {
                if (cls.getLatestClusterDefinition().getName().equals(clusterName)) {
                    synchronized (cls) {
                        ClusterDefinition cdf = new ClusterDefinition();
                        cdf.setName(clusterName);
                        cdf.setGoalState(ClusterState.CLUSTER_STATE_ATTIC);
                        updateCluster(clusterName, cdf, false);
                        this.tobe_deleted_clusters.put(clusterName, "null");                    
                    }
                } 
            }
        }
    }   
     
    /* 
     * Get the cluster by name
     */
    public Cluster getClusterByName(String clusterName) {
        return this.operational_clusters.get(clusterName);
    }
    
    /* 
     * Get the cluster by ID
     */
    public Cluster getClusterByID(String clusterID) {
        String clusterName = this.operational_clusters_id_to_name.get(clusterID);
        if (clusterName != null) {
            return this.getClusterByName(clusterName);
        } else {
            return null;
        }
    }
    
    /*
     * Get cluster ID given cluster Name
     */
    public String getClusterIDByName (String clusterName) {
        if (this.operational_clusters.containsKey(clusterName)) {
            return this.operational_clusters.get(clusterName).getID();
        } else {
            return null;
        }
    }
    
    /*
     * Get the latest cluster definition
     */
    public ClusterDefinition getLatestClusterDefinition(String clusterName) {
        return this.operational_clusters.get(clusterName).getLatestClusterDefinition();
    }
    
    /*
     * Get Cluster Definition given name and revision
     */
    public ClusterDefinition getClusterDefinition(String clusterName, long revision) {
        return this.operational_clusters.get(clusterName).getClusterDefinition(revision);
    }
    
    /* 
     * Get the cluster Information by name
     */
    public ClusterInformation getClusterInformation (String clusterName) throws Exception  {
        if (!this.operational_clusters.containsKey(clusterName)) {
            String msg = "Cluster ["+clusterName+"] does not exist";
            throw new WebApplicationException((new ExceptionResponse(msg, Response.Status.NOT_FOUND)).get());
        }
        ClusterInformation clsInfo = new ClusterInformation();
        clsInfo.setDefinition(this.getLatestClusterDefinition(clusterName));
        clsInfo.setState(this.operational_clusters.get(clusterName).getClusterState());
        return clsInfo;
    }
    
    
    /* 
     * Get the cluster state
    */
    public ClusterState getClusterState(String clusterName) throws WebApplicationException {
        if (!this.operational_clusters.containsKey(clusterName)) {
            String msg = "Cluster ["+clusterName+"] does not exist";
            throw new WebApplicationException((new ExceptionResponse(msg, Response.Status.NOT_FOUND)).get());
        }
        return this.operational_clusters.get(clusterName).getClusterState();
    }
    
    
    /*
     * Get Cluster Information list i.e. cluster definition and cluster state
     */
    public List<ClusterInformation> getClusterInformationList(String state) {
      List<ClusterInformation> list = new ArrayList<ClusterInformation>();
      for (Cluster cls : this.operational_clusters.values()) {
        if (state.equals("ALL")) {
          ClusterInformation clsInfo = new ClusterInformation();
          clsInfo.setDefinition(cls.getLatestClusterDefinition());
          clsInfo.setState(cls.getClusterState());
          list.add(clsInfo);
        } else {
          if (cls.getClusterState().getState().equals(state)) {
              ClusterInformation clsInfo = new ClusterInformation();
              clsInfo.setDefinition(cls.getLatestClusterDefinition());
              clsInfo.setState(cls.getClusterState());
              list.add(clsInfo);
          }
        }
      }
      return list;
    }
    
    /*
     * Get the list of clusters
     * TODO: Get the synchronized snapshot of each cluster definition? 
     */
    public List<Cluster> getClustersList(String state) {
        List<Cluster> list = new ArrayList<Cluster>();
        if (state.equals("ALL")) {
          list.addAll(this.operational_clusters.values());
        } else {
          for (Cluster cls : this.operational_clusters.values()) {
            if (cls.getClusterState().getState().equals(state)) {
              list.add(cls);
            }
          }
        }
        return list;
    }
    
    /* 
     * UTIL methods on entities
     */
    
    /*
     * Get the list of role names associated with node
     */
    public List<String> getAssociatedRoleNames(String hostname) {
      return Nodes.getInstance().getNodes().get(hostname).getNodeState().getNodeRoleNames();
    }
    
    /*
     *  Return the default role name to be associated with specified cluster node that 
     *  has no specific role to nodes association specified in the cluster definition
     *  Throw exception if node is not associated to with any cluster
     */
    public String getDefaultRoleName(String clusterID) throws Exception {
        Cluster c = Clusters.getInstance().getClusterByID(clusterID);
        // TODO: find the default role from the clsuter blueprint 
        return "slaves-role";
    }
    
  /*
   * TODO: Implement proper range expression
   * TODO: Remove any duplicate nodes from the derived list
   */
  public List<String> getHostnamesFromRangeExpressions (String nodeRangeExpression) throws Exception {
      List<String> list = new ArrayList<String>();
      StringTokenizer st = new StringTokenizer(nodeRangeExpression, ",");
      while (st.hasMoreTokens()) {
        list.add(st.nextToken());
      }
      return list;
  }
}
