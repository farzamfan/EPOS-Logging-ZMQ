/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package agent;

import Communication.InformGateway;
import data.Plan;
import dsutil.protopeer.services.topology.trees.TreeApplicationInterface;
import func.CostFunction;
import func.PlanCostFunction;
import agent.logging.AgentLoggingProvider;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import protopeer.Finger;
import data.DataType;
import protopeer.MainConfiguration;
import protopeer.network.zmq.ZMQAddress;
import protopeer.time.Timer;
import protopeer.time.TimerListener;
import protopeer.util.quantities.Time;

/**
 * An agent that performs combinatorial optimization in a tree network.
 * 
 * @author Peter
 * @param <V> the type of the data this agent should handle
 */
public abstract class TreeAgent<V extends DataType<V>> extends Agent<V> implements TreeApplicationInterface, java.io.Serializable {

    // tree properties
    Finger 					parent 			= 	null;
    final List<Finger> 		children 		= 	new ArrayList<>();
    public boolean treeViewIsSet = false;

    /**
     * Initializes the agent with the given combinatorial optimization problem
     * definition
     *
     * @param possiblePlans the possible plans of this agent
     * @param globalCostFunc the global cost function
     * @param localCostFunc the local cost function
     * @param loggingProvider the logger for the experiment
     */
    public TreeAgent(List<Plan<V>> possiblePlans, CostFunction<V> globalCostFunc, PlanCostFunction<V> localCostFunc, AgentLoggingProvider<? extends TreeAgent<V>> loggingProvider) {
        super(globalCostFunc, localCostFunc, loggingProvider);
    }

    /**
     * Initializes the agent with the given combinatorial optimization problem
     * definition
     *  @param globalCostFunc the global cost function
     * @param localCost the local cost function
     * @param loggingProvider the logger for the experiment
     * @param seed the seed for the RNG used by this agent
     */
    public TreeAgent(CostFunction<V> globalCostFunc, PlanCostFunction<V> localCost, AgentLoggingProvider<? extends TreeAgent<V>> loggingProvider, long seed) {
        super(globalCostFunc, localCost, loggingProvider, seed);
    }

    /**
     * Root is the agent without parent and always has at least 1 child
     * @return
     */
    public boolean isRoot() {
        return parent == null && !children.isEmpty();
    }

    /**
     * Leaf is the agent that has a parent and has no children
     * @return
     */
    public boolean isLeaf() {
        return parent != null && children.isEmpty();
    }
    
    /**
     * Inner node is the agent that has a parent and has at least 1 child
     * @return
     */
    public boolean isInnerNode() {
        return parent != null && !children.isEmpty();
    }

    /**
     * Node is considered disconnected iff it has no parent and has no children
     * @return
     */
    public boolean isDisconnected() {
        return parent == null && children.isEmpty();
    }

    @Override
    public void setParent(Finger parent) {
        if (parent != null) {
            this.parent = parent;
        }
        else {
        }
    }

    @Override
    public void setChildren(List<Finger> list) {
        children.addAll(list);
    }

    @Override
    public void setTreeView(Finger parent, List<Finger> children) {
    	this.setParent(parent);
        this.setChildren(children);
        System.out.println("treeViewIsSet for:"+this.getPeer().getNetworkAddress());
        treeViewIsSet = true;
        ZMQAddress dest = new ZMQAddress(MainConfiguration.getSingleton().peerZeroIP, 12345);
        getPeer().sendMessage(dest, new InformGateway(MainConfiguration.getSingleton().peerPort - 3000, "treeViewSet", isLeaf()));
    }
    
    void treeViewIsSet() {
    }

    public List<Finger> getChildren() {
        return children;
    }

    @Override
    /**
     * Agent is 'representative' iff it is root.
     */
    public boolean isRepresentative() {
        return isRoot();
    }
    
    @Override
    public void reset() {
    	super.reset();
    }
}
