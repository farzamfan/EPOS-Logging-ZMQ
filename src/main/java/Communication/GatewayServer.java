package Communication;

import com.sun.security.auth.PolicyFile;
import config.Configuration;
import config.LiveConfiguration;
import loggers.EventLog;
import org.zeromq.ZMQ;
import pgpersist.PersistenceClient;
import protopeer.Peer;
import protopeer.measurement.MeasurementLogger;
import protopeer.network.Message;
import protopeer.network.NetworkAddress;
import protopeer.network.NetworkInterface;
import protopeer.network.NetworkListener;
import protopeer.network.zmq.ZMQAddress;
import protopeer.network.zmq.ZMQNetworkInterface;
import protopeer.network.zmq.ZMQNetworkInterfaceFactory;
import protopeer.time.RealClock;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.*;
import java.util.stream.IntStream;

public class GatewayServer {
    private ZMQNetworkInterface zmqNetworkInterface;
    transient PersistenceClient persistenceClient;
    Configuration config;

    private String GateWayIP;
    private int GateWayPort;
    private ZMQAddress GateWayAddress;
    private int GateWayPeerID;
    private String EPOSRequesterIP;
    private int EPOSRequesterPort;
    private ZMQAddress EPOSRequesterAddress;
    private static String peerIP;

    private boolean bootstrapInformed = new Boolean(false);
    private boolean allNodesReady = new Boolean(false);
    private int readyPeers=0;
    private int innerNode=0;
    private int innerNodeRunning=0;
    private int finishedPeers=0;
    private int peersWithPlansSet=0;
    private int peersWithTreeViewSet=0;
    private int currentRun =0;
    private int registeredUsers=0;

    private List<UserStatus> UsersStatus;
    private List<EPOSPeerStatus> PeersStatus;
    private List<Integer> numUsersPerRun;

    private int bootstrapPort;
    private int maxNumRuns;
    private int currentSim=0;

    int persistenceClientOutputQueueSize;

    public GatewayServer(){
        /* constructor, does the following:
        - reads the epos config file
        - set up the address for itself
        - sets up the bootstrap port
        - initiates the peersStatus and usersStatus
        - creates the ZMQ interface
         */
        String rootPath = System.getProperty("user.dir");
        String confPath = rootPath + File.separator + "conf" + File.separator + "epos.properties";
        config = Configuration.fromFile(confPath,false);

        GateWayIP = config.GateWayIP;
        GateWayPort = config.GateWayPort;
        GateWayPeerID = config.GateWayPeerID;
        EPOSRequesterPort = config.EPOSRequesterPort;
        GateWayAddress = new ZMQAddress(GateWayIP,GateWayPort);
        System.out.println("gateway address : " + GateWayAddress );
        EPOSRequesterIP = config.EPOSRequesterIP;
        EPOSRequesterAddress = new ZMQAddress(EPOSRequesterIP,EPOSRequesterPort);
        peerIP = config.UserIP;

        bootstrapPort = config.bootstrapPort;
        maxNumRuns = config.maxNumRuns;
        PeersStatus = new ArrayList<EPOSPeerStatus>();
        UsersStatus = new ArrayList<UserStatus>();
        numUsersPerRun = new ArrayList<Integer>(Collections.nCopies(maxNumRuns+2, 0));
        numUsersPerRun.set(0,config.numAgents);

        RealClock clock=new RealClock();
        MeasurementLogger measurementLogger=new MeasurementLogger(clock);
        ZMQNetworkInterfaceFactory zmqNetworkInterfaceFactory=new ZMQNetworkInterfaceFactory(measurementLogger);
        zmqNetworkInterface = (ZMQNetworkInterface)zmqNetworkInterfaceFactory.createNewNetworkInterface(measurementLogger,GateWayAddress);

        persistenceClientOutputQueueSize = config.persistenceClientOutputQueueSize;
    }

    public static void main(String[] args) {
        /*
        - initialises a new object and starts listening
         */
        GatewayServer gatewayServer = new GatewayServer();
        gatewayServer.setUpPersistantClient();
        gatewayServer.setUpEventLogger();
        gatewayServer.listen();
    }

    public void listen() {
        synchronized (this) {
            zmqNetworkInterface.addNetworkListener(new NetworkListener() {

                public void exceptionHappened(NetworkInterface networkInterface, NetworkAddress remoteAddress,
                                              Message message, Throwable cause) {
                    System.out.println("ZmqTestServer::exceptionHappened" + cause);
                    cause.printStackTrace();
                }

                public void interfaceDown(NetworkInterface networkInterface) {
                    System.out.println("ZmqTestServer::interfaceDown");

                }

                public void messageReceived(NetworkInterface networkInterface, NetworkAddress sourceAddress,
                                            Message message) {
//                System.out.println("message received from: "+message.getSourceAddress()+" of type: "+message.getClass());
                    if (message instanceof EPOSRequestMessage) {
                    /*
                    - The epos request message arrives once (todo make it more recurring, such as changing goal function, ...)
                     */
                        EPOSRequestMessage eposRequestMessage = (EPOSRequestMessage) message;
                        EPOSRequesterAddress = (ZMQAddress) eposRequestMessage.getSourceAddress();
                        currentSim = eposRequestMessage.currentSim;
                        numUsersPerRun.set(0, eposRequestMessage.numPeers);
                        maxNumRuns = eposRequestMessage.maxRuns;
                        if (eposRequestMessage.numPeers > 1 && UsersStatus.size() > 0) {
                        /*
                        - records the EPOS requester address
                        - creates the bootstrap server (peer0)
                        - sets initial numUsersPerRun
                         */
                            System.out.println("initiating the boostrap server with address: " + peerIP+":" + (bootstrapPort + UsersStatus.get(0).index));
                            ZMQAddress peerAddress = new ZMQAddress(peerIP, (bootstrapPort + UsersStatus.get(0).index));
                            // idx, port, numAgent, initRun, initSim
                            String command = "screen -S peer" + UsersStatus.get(0).index + " -d -m java -Xmx1024m -jar IEPOSNode.jar " + UsersStatus.get(0).index +
                                    " " + (bootstrapPort + UsersStatus.get(0).index) + " " + numUsersPerRun.get(currentRun) + " " + 0 + " " + currentSim;
                            try {
//                            System.out.println(command);
                                Runtime.getRuntime().exec(command);
                            /*
                            - initiates the bootstrap server (peer0) and records its status
                            - records the changes in the peerStatus
                             */
                                UsersStatus.get(0).assignedPeerAddress = peerAddress;
                                UsersStatus.get(0).status = "peerAssigned";
                                PeersStatus.get(0).address = peerAddress;
                                PeersStatus.get(0).peerPort = bootstrapPort;
                                PeersStatus.get(0).status = "initiated";

                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            EventLog.logEvent("GateWay", "EPOSRequestMessageReceived", "initiatingBootstrap", String.valueOf(currentRun));
                        } else if (UsersStatus.size() == 0) {
                            System.out.println("no user is initiated!");
                            System.exit(0);
                        } else if (eposRequestMessage.numPeers == 0) {
                            System.out.println("only one peer is requested, optimisation is pointless");
                            System.exit(0);
                        }
                    } else if (message instanceof InformGatewayMessage) {
                    /*
                    - listens for various updates from peers, and runs the appropriate command
                     */
                        InformGatewayMessage informGatewayMessage = (InformGatewayMessage) message;
                        if (informGatewayMessage.status.equals("bootsrapPeerInitiated")) {
                        /*
                        - the bootstrap server (peer0) is online
                        - initiates the rest of the peers
                        - updates the user and peer status
                        */
                            initiatePeers(1, UsersStatus.size() - 1, currentRun, true);
                            EventLog.logEvent("GateWay", "InformGatewayMessageReceived", "initiatingRest", (UsersStatus.size() - 1) + "-" + currentRun);
                        }
                        if (informGatewayMessage.status.equals("treeViewSet")) {
                            // all peers have sent their bootstrap hello and received the treeView from the server
//                            System.out.println("tree view set for: " + informGatewayMessage.getSourceAddress() + " at run: " + informGatewayMessage.run);
                            PeersStatus.get(informGatewayMessage.peerID).status = informGatewayMessage.status;
                            PeersStatus.get(informGatewayMessage.peerID).isleaf = informGatewayMessage.isLeaf;
                            peersWithTreeViewSet++;
                        } else if (informGatewayMessage.status.equals("plansSet")) {
                            // all peers have received their plans from the corresponding user
//                            System.out.println("plan set for: " + informGatewayMessage.getSourceAddress() + " at run: " + informGatewayMessage.run);
                            peersWithPlansSet++;
                        } else if (informGatewayMessage.status.equals("ready") && informGatewayMessage.run == currentRun) {
                            // update peer status based on the ready message received
//                            System.out.println("ready message received for: " + informGatewayMessage.getSourceAddress() + " at run: " + informGatewayMessage.run);
                            PeersStatus.get(informGatewayMessage.peerID).status = informGatewayMessage.status;
                            PeersStatus.get(informGatewayMessage.peerID).address = informGatewayMessage.getSourceAddress();
                            // the peer has it treeView and plans set, and is ready to start epos process
                            if (informGatewayMessage.isLeaf == false) {
                                // records the number of inner (non-leaf) peers
                                innerNode++;
                            }
                            readyPeers++;
                        } else if (informGatewayMessage.status.equals("innerRunning")) {
                            // the inner peer has executed the "initIteration" and are listening to the leafs (its children)
//                        System.out.println("innerRunning message received for: "+informGatewayMessage.getSourceAddress()+" at run: "+informGatewayMessage.run);
                            innerNodeRunning++;
                        } else if (informGatewayMessage.status.equals("finished")) {
                            // the peer has finished its run (numIteration)
//                        System.out.println("finished message received for: "+informGatewayMessage.getSourceAddress()+" at run: "+informGatewayMessage.run);
                            finishedPeers++;
//                            if (numUsersPerRun.get(currentRun + 1) != numUsersPerRun.get(currentRun) && bootstrapInformed == false) {
                            if (bootstrapInformed == false) {
                                bootstrapInformed = true;
                                treeViewShouldChange();
                            }
                        }
                        if (informGatewayMessage.status.equals("checkUserChanges")) {
                            checkUserChanges(informGatewayMessage);
                        }
                    } else if (message instanceof UserRegisterMessage) {
                    /*
                    - receives the user register message from each user
                    - creates a userStatus entry for each user and sets the status as "registered"
                    - creates a EPOSPeerStatus entry for each user and sets the status as "registered"
                     */
                        UserRegisterMessage userRegisterMessage = (UserRegisterMessage) message;
                        registerUser(userRegisterMessage.index, "registered", (ZMQAddress) userRegisterMessage.getSourceAddress());
                        registerPeer(userRegisterMessage.index, currentRun, "registered");
                        registeredUsers++;
                        if (registeredUsers == numUsersPerRun.get(0)) {
                            registeredUsers = 0;
                            // tells the epos requester that the users are registered
                            zmqNetworkInterface.sendMessage(EPOSRequesterAddress, new EPOSRequestMessage(currentRun, UsersStatus.size(), "usersRegistered"));
                        }
                    } else if (message instanceof UserJoinLeaveMessage) {
                        UserJoinLeaveMessage userJoinLeaveMessage = (UserJoinLeaveMessage) message;
                        if (userJoinLeaveMessage.joinLeaveStatus.equals("join")) {
                            registerUser(userJoinLeaveMessage.userIndex, "registered", userJoinLeaveMessage.userAddress);
                            registerPeer(userJoinLeaveMessage.userIndex, userJoinLeaveMessage.currentRun, "registered");
                            numUsersPerRun.set(userJoinLeaveMessage.currentRun, numUsersPerRun.get(userJoinLeaveMessage.currentRun) + 1);
                            System.out.println("peer: " + userJoinLeaveMessage.userIndex + " will join at run: " + userJoinLeaveMessage.currentRun + " current run:" + currentRun);
                            EventLog.logEvent("GateWay", "UserJoinLeaveMessage", "userJoin", userJoinLeaveMessage.userIndex + "-" + currentRun);
                        } else if (userJoinLeaveMessage.joinLeaveStatus.equals("leave")) {
                            UsersStatus.get(userJoinLeaveMessage.userIndex).status = "left";
                            PeersStatus.get(userJoinLeaveMessage.userIndex).status = "left";
                            PeersStatus.get(userJoinLeaveMessage.userIndex).leaveRun = userJoinLeaveMessage.currentRun;
                            numUsersPerRun.set(userJoinLeaveMessage.currentRun, numUsersPerRun.get(userJoinLeaveMessage.currentRun) - 1);
                            System.out.println("peer: " + userJoinLeaveMessage.userIndex + " will leave at run: " + userJoinLeaveMessage.currentRun + " current run:" + currentRun);
                            EventLog.logEvent("GateWay", "UserJoinLeaveMessage", "userLeave", userJoinLeaveMessage.userIndex + "-" + currentRun);
                        }
                        if (userJoinLeaveMessage.joinLeaveStatus.equals("noChange")) {
                            // no action needed
                        }
                    }
                    checkStatus();
                }

                public void messageSent(NetworkInterface networkInterface, NetworkAddress destinationAddress, Message message) {
//                System.out.println("Message sent: + " +destinationAddress + " message: "+ message);
                }

                public void interfaceUp(NetworkInterface networkInterface) {
                    System.out.println("ZmqTestServer::interfaceUp");
                }

            });
            zmqNetworkInterface.bringUp();
        }
    }

    public void checkStatus(){
        /*
        whenever a new message is received, the gateway checks the status to see if any action is needed
         */
        if (peersWithTreeViewSet == numUsersPerRun.get(currentRun)){
            /*
            - all peers have their tree view set
            - informs the users about this, for them to send the plans to their assigned peer
            - updates the status of peers to "treeViewSet
             */
            System.out.println("all peers have their treeView set at run: "+currentRun+" numPeers: "+numUsersPerRun.get(currentRun));
            for (UserStatus user:UsersStatus) {
                if (PeersStatus.get(user.index).leaveRun > currentRun)
                {informUserTreeSet(user);}
            }
            for (EPOSPeerStatus eposPeerStatus: PeersStatus){
                if (eposPeerStatus.leaveRun > currentRun){
                    eposPeerStatus.status = "treeViewSet";
                    eposPeerStatus.run = currentRun;}
            }
            peersWithTreeViewSet=0;
        }
        if (peersWithPlansSet == numUsersPerRun.get(currentRun)){
            /*
            - all peers have their plans set
            - change the status of peers to "plansSet"
             */
            System.out.println("all peers have their plans set at run: "+currentRun+" numPeers: "+numUsersPerRun.get(currentRun));
            for (EPOSPeerStatus eposPeerStatus: PeersStatus){
                eposPeerStatus.status = "plansSet";
            }
            peersWithPlansSet=0;
        }
        if (finishedPeers == numUsersPerRun.get(currentRun)){
            /*
            - all peers have finished epos for the given run
            - informs EPOSRequester about the end of the run
            - updates peer status and user status to finished
            - if maxRuns is reached, terminates
             */
            System.out.println("---");
            System.out.println("EPOS Successfully executed for run: "+ currentRun+" numPeers: "+numUsersPerRun.get(currentRun));
            System.out.println("---");
            zmqNetworkInterface.sendMessage(EPOSRequesterAddress, new EPOSRequestMessage(currentRun,UsersStatus.size(),"finished"));
            for (EPOSPeerStatus eposPeerStatus: PeersStatus){
                eposPeerStatus.status = "finished";
                eposPeerStatus.run = currentRun+1;
            }
            for (UserStatus user:UsersStatus) {
                user.status = "finished";
            }
            if (currentRun == maxNumRuns){
                System.out.println("---------------");
                System.out.println("MAX NUM RUN REACHED: "+ currentRun+" numPeers: "+numUsersPerRun.get(currentRun));
                System.out.println("---------------");
                zmqNetworkInterface.sendMessage(EPOSRequesterAddress, new EPOSRequestMessage(currentRun,UsersStatus.size(),"maxRunReached"));
            }
            // resets the local variables for checking per run status
            resetPerRun();
            currentRun++;
        }
        if (readyPeers == numUsersPerRun.get(currentRun)){
            /*
            - all peers are ready
            - send message to the innerNodes to execute the initIteration and listen to the leafs
            - update the status of peer and user to "assignedPeerRunning"
             */
            System.out.println("sending run message to inner nodes at run: "+currentRun+" numPeers: "+numUsersPerRun.get(currentRun));
            for (EPOSPeerStatus peer : PeersStatus) {
                if (peer.isleaf == false && peer.run == currentRun && peer.leaveRun > currentRun){
//                    zmqNetworkInterface.sendMessage(peer.address, new ReadyToRunMessage(peer.index, currentRun));
                }
            }
            for (UserStatus user : UsersStatus){
                if (PeersStatus.get(user.index).leaveRun > currentRun){
                    zmqNetworkInterface.sendMessage(user.userAddress, new InformUserMessage(user.index, currentRun,"assignedPeerRunning"));
                    user.status = "assignedPeerRunning";}
            }
            allNodesReady = new Boolean(true);;;
            readyPeers=0;
            // already setting up the next run parameters
            numUsersPerRun.set(currentRun+1,numUsersPerRun.get(currentRun));
        }
        if (innerNodeRunning == innerNode & allNodesReady & innerNodeRunning != 0){
            /*
            - all inner peers have their initIteration done and listening to the leafs
            - sending message the the leafs to start the iterations
            - updates users status to "peerRunning"
             */
            System.out.println("sending run message to leafs at run: "+currentRun+" numPeers: "+numUsersPerRun.get(currentRun));
            for (EPOSPeerStatus peer : PeersStatus) {
                if (peer.isleaf == true && peer.run == currentRun && peer.leaveRun > currentRun){
                    zmqNetworkInterface.sendMessage(peer.address, new ReadyToRunMessage(peer.index, currentRun));
//                    System.out.println("ready leaf message send to: "+peer.address);
                }
            }
            for (UserStatus user:UsersStatus) {
                user.status = "peerRunning";
            }
            innerNodeRunning = 0;
            allNodesReady = new Boolean(false);;;
        }
    }

    public void registerUser(int idx, String status, ZMQAddress userAddr){
        UserStatus userStatus = new UserStatus(idx,currentRun,status,userAddr);
        UsersStatus.add(userStatus);
    }

    public void registerPeer(int idx, int run, String status){
//        System.out.println("new peer registered, id: "+idx+" run: "+run+" status: "+status);
        EPOSPeerStatus peer = new EPOSPeerStatus(idx,run,status,false,null,-1);
        PeersStatus.add(peer);
    }

    public void initiatePeers(int beginRange, int numPeers, int initRun, boolean init) {
        int peerPort = -1;
        for (int j = beginRange; j < (beginRange + numPeers); j++) {
            System.out.println("liveNode " + UsersStatus.get(j).index + " initiated");
            if (init) {
                peerPort = (bootstrapPort + UsersStatus.get(j).index);
            } else {
                peerPort = findFreePort();
                while (!checkFreePort(peerPort)) {
                    peerPort = findFreePort();
                }
            }
            ZMQAddress peerAddress = new ZMQAddress(peerIP, peerPort);
            // idx, port, numAgent, initRun, initSim
            String command = "screen -S peer" + UsersStatus.get(j).index + " -d -m java -Xmx1024m -jar IEPOSNode.jar " + UsersStatus.get(j).index +
                    " " + peerPort + " " + numUsersPerRun.get(currentRun) + " " + initRun + " " + currentSim;
            UsersStatus.get(j).assignedPeerAddress = peerAddress;
            UsersStatus.get(j).status = "peerAssigned";
            PeersStatus.get(j).address = peerAddress;
            PeersStatus.get(j).peerPort = peerPort;
            PeersStatus.get(j).status = "initiated";
            try {
//                System.out.println(command);
                Runtime.getRuntime().exec(command);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void informUserTreeSet(UserStatus user){
        // informs user that the assigned peer has the tree set
        zmqNetworkInterface.sendMessage(user.userAddress, new UserRegisterMessage(user.index,currentRun,user.status,user.userAddress,user.assignedPeerAddress));
    }

    public void checkUserChanges(InformGatewayMessage informGatewayMessage){
        // checking to see if there are any new users in the network
        if (PeersStatus.get(informGatewayMessage.peerID).leaveRun == informGatewayMessage.run){
            zmqNetworkInterface.sendMessage(informGatewayMessage.getSourceAddress(), new TreeViewChangeMessage(currentRun,"deactivate"));
        }
//        else if (numUsersPerRun.get(informGatewayMessage.run) == numUsersPerRun.get(informGatewayMessage.run-1)){
//            zmqNetworkInterface.sendMessage(informGatewayMessage.getSourceAddress(), new PlanSetMessage("noUserChanges"));
//        }
        else { zmqNetworkInterface.sendMessage(informGatewayMessage.getSourceAddress(), new TreeViewChangeMessage(currentRun,"requestNewTreeView"));}
    }

    public void treeViewShouldChange(){
        List<Integer> activePeers = new ArrayList<>();
        activePeers = findActivePeers(activePeers);
        zmqNetworkInterface.sendMessage(UsersStatus.get(0).assignedPeerAddress,new InformBootstrap(currentRun, "informBootstrap",numUsersPerRun.get(currentRun+1),activePeers));
        for (EPOSPeerStatus peer: PeersStatus){
            if (peer.run == currentRun+1){
                initiatePeers(peer.index,1,peer.run,false);
            }
        }
        System.out.println("informing the treeGateway ("+UsersStatus.get(0).assignedPeerAddress+") " +
                "of the users change. New number of users: "+numUsersPerRun.get(currentRun+1)+" for run: "+(currentRun+1));
    }

    public void terminate(){
        try {
            Runtime.getRuntime().exec("./killAll.sh");
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.exit(0);
    }

    public void resetPerRun(){
        allNodesReady = false;
        readyPeers=0;
        innerNode=0;
        innerNodeRunning=0;
        finishedPeers=0;
        bootstrapInformed = false;
    }

    public List<Integer> findActivePeers(List<Integer> actPeers){
        for (EPOSPeerStatus peer:PeersStatus) {
            if (peer.leaveRun > (currentRun+1)){
                actPeers.add(peer.index);
            }
        }
        return actPeers;
    }

    public int findFreePort() {
        int toReturn = -1;
        ServerSocket socket = null;
        try {
            socket = new ServerSocket(0);
            socket.setReuseAddress(true);
            int port = socket.getLocalPort();
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return port;
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        throw new IllegalStateException("Could not find a free TCP/IP port to start peer on");
    }

    public boolean checkFreePort(int port){
        boolean flag = new Boolean(true);
        for (EPOSPeerStatus peer:PeersStatus) {
            if (peer.peerPort == port && peer.leaveRun < currentRun){}
            else {flag = false;}
        }
        EventLog.logEvent("GateWay", "checkFreePort", "checkFreePort", "port: "+port+"-"+flag);
        return flag;
    }

    public void setUpEventLogger(){
        EventLog.setPeristenceClient(persistenceClient);
        EventLog.setPeerId(GateWayPeerID);
        EventLog.setDIASNetworkId(0);
    }

    public void setUpPersistantClient(){
        ZMQ.Context zmqContext = ZMQ.context(1);
        String daemonConnectString = "tcp://" + config.persistenceDaemonIP + ":" + config.persistenceDaemonPort;
        persistenceClient = new PersistenceClient( zmqContext, daemonConnectString, persistenceClientOutputQueueSize );
        System.out.println( "persistenceClient created" );
    }
}