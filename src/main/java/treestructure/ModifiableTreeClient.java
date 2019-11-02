package treestructure;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;

import Communication.*;
import agent.Agent;
import agent.IterativeTreeAgent;
import agent.MultiObjectiveIEPOSAgent;
import agent.TreeAgent;
import com.sun.source.tree.Tree;
import dsutil.protopeer.FingerDescriptor;
import dsutil.protopeer.services.topology.trees.DescriptorType;
import dsutil.protopeer.services.topology.trees.TreeMiddlewareInterface;
import dsutil.protopeer.services.topology.trees.TreeProviderInterface;
import loggers.EventLog;
import org.apache.regexp.RE;
import protopeer.BasePeerlet;
import protopeer.Peer;
import protopeer.network.Message;
import protopeer.network.NetworkAddress;
import protopeer.servers.bootstrap.PeerIdentifierGenerator;
import tree.centralized.TreeViewReply;
import tree.centralized.TreeViewRequest;

import javax.swing.*;

/**
 * This class allows the tree structure to be changed dynamically during runtime. 
 * Still, this is the class that communicates with TreeServer:
 *  - requests a view from the server by sending request message
 *  - handling reply from the server
 *  - passes received information to implementer of TreeProviderInterface
 * 
 * @author jovan
 *
 */
public class ModifiableTreeClient extends BasePeerlet implements TreeMiddlewareInterface {
	
	private Logger logger = Logger.getLogger(ModifiableTreeClient.class.getName());
	
	public enum ClientState {
        INIT,
        WAITING,
        COMPLETED
    }
	
    private ClientState 				state;
    private FingerDescriptor 			localDescriptor;
    private PeerIdentifierGenerator 	idGenerator;
    private NetworkAddress 				bootstrapServerAddress;
    private double 						rank;
    private int 						dMax;
    
    
    public ModifiableTreeClient(NetworkAddress bootstrapServerAddress, 
    		                    PeerIdentifierGenerator idGenerator, 
    		                    double rank, 
    		                    int dMax) {
        this.bootstrapServerAddress = bootstrapServerAddress;
        this.idGenerator = idGenerator;
        this.rank = rank;
        this.dMax = dMax;
        this.state = ClientState.INIT;
    }
    
    //TODO:
    // 1. Should rank be changed when new TreeView is requested? If so, then FingerDescriptor must be changed as well!
    //    - Rank is changed by shuffling method in the server, not necessary to update these from the client
    // 2. Degree should stay the same.
    
    private FingerDescriptor createFingerDescriptor(){
        this.localDescriptor = new FingerDescriptor(getPeer().getFinger());
        localDescriptor.addDescriptor(DescriptorType.RANK, rank);
        localDescriptor.addDescriptor(DescriptorType.NODE_DEGREE, dMax);
        return localDescriptor;
    }
    
    /**
     * Returns the peerlet that implements TreeProviderInterface.
     * @return
     */
    private TreeProviderInterface getTreeProvider(){
        return (TreeProviderInterface) this.getPeer().getPeerletOfType(TreeProviderInterface.class);
    }
    
    @Override
    /**
     * Invokes init() method inherited from BasePeerlet and sets ID to the peer.
     * ID is generated based on the IP address of the peer via idGenerator passed via constructor.
     * @param peer
     */
    public void init(Peer peer) {
        super.init(peer);
        this.getPeer().setIdentifier(idGenerator.generatePeerIdentifier(this.getPeer().getNetworkAddress()));
    }
    
    @Override
    /**
     * Invokes start() method inherited from BasePeerlet and then requests new Tree View.
     * Note that there is no Bootstrapping period!
     */
    public void start() {
        super.start();
        this.createFingerDescriptor();
//        this.requestNewTreeView();
    }
    
    /**
     * Returns the state if the ModifiableTreeClient.
     * If the state is WAITING, then client awaits for a parent and children.
     * If the state is COMPLETED, then client is fully equipped with all info.
     * @return state if the client.
     */
    public ClientState getState() {
    	return this.state;
    }
    
    /**
     * Sends request for a new Tree View (parent and children).
     * The request is sent to the TreeServer. 
     */
    public void requestNewTreeView() {
        ExtendedTreeViewRequest requestMsg = new ExtendedTreeViewRequest();
    	requestMsg.sourceDescriptor = this.localDescriptor;
    	requestMsg.peerID = getPeer().getIndexNumber();
    	this.sendTreeViewRequest(requestMsg);
    }
    
    /**
     * Does actual sending of Request Message.
     * 
     * @param requestMsg request Message with Descriptor already set!
     */
    private void sendTreeViewRequest(ExtendedTreeViewRequest requestMsg) {
    	this.getPeer().sendMessage(this.bootstrapServerAddress, requestMsg);
    	this.state=ClientState.WAITING;
        System.out.println("BootstrapHello sent to: "+this.bootstrapServerAddress+" by: "+this.getPeer().getNetworkAddress());
    }
    
    //																							ACTIVE STATE
    /////////////////////////////////////////////////////////////////////////////////////////////////////////
    //																							PASSIVE STATE
    
    @Override
    /**
     * Only messages of <code>TreeViewReply</code> type are handled.
     */
    public void handleIncomingMessage(Message message) {
        if (message instanceof TreeViewReply) {
            System.out.println("treeViewReply received by: "+getPeer().getNetworkAddress());
            this.runPassiveState((TreeViewReply) message);
        }
        if (message instanceof PlanSetMessage){
            PlanSetMessage planSetMessage = (PlanSetMessage) message;
//            System.out.println("Message received from: "+planSetMessage.getSourceAddress()+ " message: "+ planSetMessage + " messageSize: " + planSetMessage.status);
            if (planSetMessage.status.equals("setPlans")) {
                ((Agent) this.getPeer().getPeerletOfType(Agent.class)).addPlans(((PlanSetMessage) message).possiblePlans);
                ((Agent) this.getPeer().getPeerletOfType(Agent.class)).userAddress = message.getSourceAddress();
                getPeer().sendMessage(message.getSourceAddress(), new PlanSetMessage("plansSet"));
            }
            if (planSetMessage.status.equals("hasNewPlans")) {
                ((Agent) this.getPeer().getPeerletOfType(Agent.class)).plansAreSet = false;
                System.out.println("peer: "+this.getPeer().getIndexNumber()+" has new plans, next run: "+String.valueOf(((Agent) this.getPeer().getPeerletOfType(Agent.class)).activeRun));
            }
            if (planSetMessage.status.equals("changePlans")) {
                ((Agent) this.getPeer().getPeerletOfType(Agent.class)).addPlans(((PlanSetMessage) message).possiblePlans);
                EventLog.logEvent("ModifiableTreeClient", "handleIncomingMessage", "changePlans" , String.valueOf(((Agent) this.getPeer().getPeerletOfType(Agent.class)).activeRun));
            }
            if (planSetMessage.status.equals("noUserChanges")) {
                ((TreeAgent) this.getPeer().getPeerletOfType(TreeAgent.class)).treeViewIsSet = true;
            }
        }
        if (message instanceof WeightSetMessage){
            WeightSetMessage weightSetMessage = (WeightSetMessage) message;
            if (weightSetMessage.status.equals("hasNewWeights")){
                ((Agent) this.getPeer().getPeerletOfType(Agent.class)).weightsAreSet = false;
            }
            if (weightSetMessage.status.equals("setNewWeights")){
                ((MultiObjectiveIEPOSAgent) this.getPeer().getPeerletOfType(MultiObjectiveIEPOSAgent.class)).setUnfairnessWeight(((WeightSetMessage) message).alpha);
                ((MultiObjectiveIEPOSAgent) this.getPeer().getPeerletOfType(MultiObjectiveIEPOSAgent.class)).setLocalCostWeight(((WeightSetMessage) message).beta);
                System.out.println("weights for peer: "+getPeer().getIndexNumber()+" has changed");
                ((Agent) this.getPeer().getPeerletOfType(Agent.class)).weightsAreSet = true;
                EventLog.logEvent("ModifiableTreeClient", "handleIncomingMessage", "hasNewWeights" , String.valueOf(((Agent) this.getPeer().getPeerletOfType(Agent.class)).activeRun));

            }
            if (weightSetMessage.status.equals("noNewWeights")){
                ((Agent) this.getPeer().getPeerletOfType(Agent.class)).weightsAreSet = true;
            }
        }
        if (message instanceof ReadyToRunMessage){
            ReadyToRunMessage readyToRunMessage = (ReadyToRunMessage) message;
            System.out.println("ready to run message received for: "+getPeer().getNetworkAddress()+" run:"+readyToRunMessage.run);
            ((Agent) this.getPeer().getPeerletOfType(Agent.class)).setReadyToRun();
        }
        if (message instanceof TreeViewChangeMessage){
            System.out.println("new tree view requested for: " + getPeer().getNetworkAddress());
            TreeViewChangeMessage treeViewChangeMessage = (TreeViewChangeMessage) message;
            if (treeViewChangeMessage.status.equals("deactivate")) {
                EventLog.logEvent("ModifiableTreeClient", "handleIncomingMessage", "deactivate" , String.valueOf(((Agent) this.getPeer().getPeerletOfType(Agent.class)).activeRun));
                System.exit(0);
            }
            if (treeViewChangeMessage.status.equals("requestNewTreeView")) {
                this.requestNewTreeView();
//                EventLog.logEvent("ModifiableTreeClient", "handleIncomingMessage", "requestNewTreeView" , String.valueOf(((Agent) this.getPeer().getPeerletOfType(Agent.class)).activeRun));
            }
        }
    }
    
    /**
     * Handles reply from Tree Server. The reply should contain
     * new parent and new set of children for this node.
     * 
     * @param reply Reply from Tree Server containing new parent and children
     */
    private void runPassiveState(TreeViewReply reply) {
    	//this.logger.log(Level.FINE, "NODE: " + this.getPeer().getIndexNumber() + " TreeViewReply received!");
        this.state = ClientState.COMPLETED;
        this.deliverTreeView(reply.parent, reply.children);
    }
    
    @Override
	/**
	 * From TreeMiddlewareInterface:
	 * Delivers new parents and children to implementer of TreeApplicationInterface.
	 * Parents and Children are received from Tree Server as a response to a request
	 *  sent by this agent.
	 */
	public void deliverTreeView(FingerDescriptor parent, List<FingerDescriptor> children) {
    	if(parent == null && (children == null || (children != null && children.isEmpty()))) {
    		this.logger.log(Level.SEVERE, "No parent and no children, node " + this.getPeer().getIndexNumber() + " is disconnected!");
    	} else {
    		this.logger.log(Level.FINER, "TREE VIEW - NODE: " + this.getPeer().getIndexNumber() + " " + ModifiableTreeClient.printParent(parent) + "   " + ModifiableTreeClient.printChildren(children));
    	}
        System.out.println("I am: "+getPeer().getNetworkAddress()+" parent: "+printParent(parent)+" children: "+printChildren(children));
        EventLog.logEvent("ModifiableTreeClient", "deliverTreeView", "parent-children" , printParent(parent)+"-"+printChildren(children));
    	this.getTreeProvider().provideTreeView(parent, children);
	}
    
    private static String printParent(FingerDescriptor parent) {
    	if(parent == null) {
    		return "no parent";
    	}
    	return "Parent: " + parent.getNetworkAddress();
    }
    
    public static String printChildren(List<FingerDescriptor> children) {
    	if(children == null || (children != null && children.isEmpty())) {
    		return "no children";
    	}
    	
    	StringBuilder sb = new StringBuilder();
    	sb.append("Children: ");    	
    	IntStream.range(0, children.size()).forEach(i -> {
    		sb.append(children.get(i).getNetworkAddress());
    		if(i < children.size()-1) {
    			sb.append(", ");
    		}
    	});
    	return sb.toString();
    }

	@Override
	/**
	 * From TreeMiddlewareInterface:
	 */
	public void deliverChildren(List<FingerDescriptor> arg0) {	}

	@Override
	/**
	 * From TreeMiddlewareInterface:
	 */
	public void deliverParent(FingerDescriptor arg0) {	}

	@Override
	/**
	 * From TreeMiddlewareInterface:
	 * Returns local descriptor as is.
	 */
	public FingerDescriptor getMyLocalDescriptor() {
		return this.localDescriptor;
	}

}
