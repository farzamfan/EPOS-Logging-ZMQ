package Communication;

import config.Configuration;
import config.LiveConfiguration;
import experiment.LiveRun;
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
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

public class GatewayServer {

    private boolean allNodesReady = false;
    private int readyPeers=0;
    private int innerNode=0;
    private int innerNodeRunning=0;
    private int finishedPeers=0;
    private int peersWithPlansSet=0;
    private int peersWithTreeViewSet=0;
    private int numPeers;
    private int numRuns=0;
    private ZMQNetworkInterface zmqNetworkInterface;
    private List<EPOSPeerStatus> PeersStatus;
    private List<UserStatus> UsersStatus;
    private int registeredUsers=0;
    private ZMQAddress EPOSRequesterAddress;
    private int bootstrapPort;

    public GatewayServer(){
        String rootPath = System.getProperty("user.dir");
        String confPath = rootPath + File.separator + "conf" + File.separator + "epos.properties";
        Configuration config = Configuration.fromFile(confPath);
        numPeers=config.numAgents;

        LiveConfiguration liveConfiguration = new LiveConfiguration();
        bootstrapPort = liveConfiguration.bootstrapPort;

        PeersStatus = new ArrayList<EPOSPeerStatus>(numPeers);
        UsersStatus = new ArrayList<UserStatus>(numPeers);

        RealClock clock=new RealClock();
        MeasurementLogger measurementLogger=new MeasurementLogger(clock);
        ZMQNetworkInterfaceFactory zmqNetworkInterfaceFactory=new ZMQNetworkInterfaceFactory(measurementLogger);

        String		thisIP = "127.0.0.1";
        ZMQAddress zmqAddress = new ZMQAddress(thisIP,12345);
        System.out.println("gateway address : " + zmqAddress );

        zmqNetworkInterface = (ZMQNetworkInterface)zmqNetworkInterfaceFactory.createNewNetworkInterface(measurementLogger,zmqAddress);

    }

    public static void main(String[] args) {
        GatewayServer gatewayServer = new GatewayServer();
        gatewayServer.listen();
    }

    public void listen(){

        zmqNetworkInterface.addNetworkListener(new NetworkListener()
        {

            public void exceptionHappened(NetworkInterface networkInterface, NetworkAddress remoteAddress,
                                          Message message, Throwable cause)
            {
                System.out.println( "ZmqTestServer::exceptionHappened" + cause );
                cause.printStackTrace();
            }

            public void interfaceDown(NetworkInterface networkInterface)
            {
                System.out.println( "ZmqTestServer::interfaceDown" );

            }

            public void messageReceived(NetworkInterface networkInterface, NetworkAddress sourceAddress,
                                        Message message)
            {
//                System.out.println("Message received: + " +message.getSourceAddress() + " message: "+ message.getClass());
                if (message instanceof EPOSRequestMessage){
                    EPOSRequestMessage eposRequestMessage = (EPOSRequestMessage) message;
                    numPeers = eposRequestMessage.numPeers;
                    EPOSRequesterAddress = (ZMQAddress) eposRequestMessage.getSourceAddress();
                    if (eposRequestMessage.numPeers>1 && UsersStatus.size()>0){
                        System.out.println("---");
                        System.out.println("initiating the boostrap server with address: "+"127.0.0.1:"+(bootstrapPort + UsersStatus.get(0).index));
                        System.out.println("---");
                        ZMQAddress peerAddress = new ZMQAddress("127.0.0.1",(bootstrapPort + UsersStatus.get(0).index));
                        String command = "screen -S peer"+UsersStatus.get(0).index+" -d -m java -Xmx1024m -jar tutorial.jar "+ String.valueOf(UsersStatus.get(0).index) + " " + String.valueOf(bootstrapPort + UsersStatus.get(0).index);
                        try {
                            Runtime.getRuntime().exec(command);
                            UsersStatus.get(0).assignedPeerAddress = peerAddress;
                            UsersStatus.get(0).status = "peerAssigned";
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    else if (UsersStatus.size() == 0) {
                        System.out.println("no user is initiated!");
                        System.exit(0);
                    }
                    else if (numPeers == 0){
                        System.out.println("only one peer is requested, optimisation is pointless");
                        System.exit(0);
                    }
                }
                else if (message instanceof InformGateway){
                    InformGateway informGateway = (InformGateway) message;
                    if (informGateway.status.equals("bootsrapPeerInitiated")) {
//                        System.out.println("peer: "+informGateway.peerID+" is "+ informGateway.status);
                        initiateRemainigPeers();
                    }
                    if (informGateway.status.equals("treeViewSet")) {
//                        System.out.println("peer: "+informGateway.peerID+" is "+ informGateway.status);
                        peersWithTreeViewSet++;
                    }
                    else if (informGateway.status.equals("plansSet")) {
//                        System.out.println("peer: "+informGateway.peerID+" is "+ informGateway.status);
                        peersWithPlansSet++;
                    }
                    else if (informGateway.status.equals("ready")) {
                        EPOSPeerStatus peer = new EPOSPeerStatus(informGateway.peerID,informGateway.run,informGateway.status,informGateway.isLeaf,informGateway.getSourceAddress());
                        PeersStatus.add(peer);
//                        System.out.println("peer: "+informGateway.peerID+" is "+ informGateway.status);
                        if (informGateway.isLeaf == false) {innerNode++;}
                        readyPeers++;
                    }
                    else if (informGateway.status.equals("innerRunning")) {
//                        System.out.println("peer: "+informGateway.peerID+" is "+ informGateway.status);
                        innerNodeRunning++;
                    }
                    else if (informGateway.status.equals("finished")) {
//                        System.out.println("peer: "+informGateway.peerID+" is "+ informGateway.status);
                        finishedPeers++;
                    }
                }
                else if (message instanceof UserRegisterMessage){
                    UserRegisterMessage userRegisterMessage = (UserRegisterMessage) message;
                    UserStatus userStatus = new UserStatus(registeredUsers,userRegisterMessage.status,(ZMQAddress) userRegisterMessage.getSourceAddress());
                    UsersStatus.add(userStatus);
//                    System.out.println("users "+userRegisterMessage.index+" registered");
                    registeredUsers++;
                }
                checkStatus();
            }

            public void messageSent(NetworkInterface networkInterface, NetworkAddress destinationAddress, Message message)
            {
//                System.out.println("Message sent: + " +destinationAddress + " message: "+ message);
            }

            public void interfaceUp(NetworkInterface networkInterface)
            {
                System.out.println( "ZmqTestServer::interfaceUp" );

            }

        });
        zmqNetworkInterface.bringUp();
        System.out.println( "ZmqTestServer: milestone 1" );
    }

    public void checkStatus(){
        if (peersWithTreeViewSet == numPeers){
            System.out.println("---");
            System.out.println("all peers have their treeView set");
            System.out.println("---");
            for (UserStatus user:UsersStatus) {
                informUser(user);
            }
            peersWithTreeViewSet=0;
        }
        if (peersWithPlansSet == numPeers){
            System.out.println("---");
            System.out.println("all peers have their plans set");
            System.out.println("---");
            peersWithPlansSet=0;
        }
        if (finishedPeers == numPeers){
            System.out.println("---");
            System.out.println("EPOS Successfully executed for run: "+numRuns);
            System.out.println("---");
            zmqNetworkInterface.sendMessage(EPOSRequesterAddress, new EPOSRequestMessage(numRuns,numPeers,"finished"));
            resetAll();
            numRuns++;
//            terminate();
        }
        if (readyPeers == numPeers){
            System.out.println("---");
            System.out.println("all nodes are ready, sending run message to inner nodes");
            System.out.println("---");
            for (EPOSPeerStatus peer : PeersStatus) {
                if (peer.isleaf == false){
                    zmqNetworkInterface.sendMessage(peer.address, new ReadyToRunMessage(peer.index));
                }
            }
            allNodesReady = true;
            readyPeers=0;
        }
        if (innerNodeRunning == innerNode & allNodesReady & innerNodeRunning != 0){
            System.out.println("---");
            System.out.println("all inner nodes are running, sending run message to leafs");
            System.out.println("---");
            for (EPOSPeerStatus peer : PeersStatus) {
                if (peer.isleaf == true && peer.run == numRuns){
                    zmqNetworkInterface.sendMessage(peer.address, new ReadyToRunMessage(peer.index));
                }
            }
            innerNodeRunning = 0;
            allNodesReady = false;
        }
    }

    public void initiateRemainigPeers(){
            System.out.println("---");
            System.out.println("inititating rest of the peers");
            System.out.println("---");
            for (int j=1;j<UsersStatus.size();j++) {
                System.out.println("liveNode " + UsersStatus.get(j).index + " initiated");
                ZMQAddress peerAddress = new ZMQAddress("127.0.0.1", (bootstrapPort + UsersStatus.get(j).index));
                String command = "screen -S peer"+UsersStatus.get(j).index+" -d -m java -Xmx1024m -jar tutorial.jar "+ String.valueOf(UsersStatus.get(j).index) + " " + String.valueOf(bootstrapPort + UsersStatus.get(j).index);
                try {
                    Runtime.getRuntime().exec(command);
                    UsersStatus.get(j).assignedPeerAddress = peerAddress;
                    UsersStatus.get(j).status = "peerAssigned";
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
    }

    public void informUser(UserStatus user){
        zmqNetworkInterface.sendMessage(user.userAddress, new UserRegisterMessage(user.index,user.status,user.userAddress,user.assignedPeerAddress));
    }

    public void terminate(){
        try {
            Runtime.getRuntime().exec("./killAll.sh");
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.exit(0);
    }

    public void resetAll(){
        allNodesReady = false;
        readyPeers=0;
        innerNode=0;
        innerNodeRunning=0;
        finishedPeers=0;
    }
}
