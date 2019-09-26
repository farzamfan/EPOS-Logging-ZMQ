package Communication;

import agent.dataset.Dataset;
import agent.dataset.GaussianDataset;
import config.Configuration;
import data.Plan;
import data.Vector;
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
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class User {

    private ZMQNetworkInterfaceFactory zmqNetworkInterfaceFactory;
    private ZMQNetworkInterface zmqNetworkInterface;
    private String thisIP;
    private ZMQAddress thisAddress;
    private ZMQAddress gateWayAddress;
    private Configuration config;
    private List<UserStatus> Users;
    private int usersWithAssignedPeer;


    public User(){
        String rootPath = System.getProperty("user.dir");
        String confPath = rootPath + File.separator + "conf" + File.separator + "epos.properties";
        config = Configuration.fromFile(confPath);
    }

    public static void main(String[] args) {
        User user = new User();
        user.createInterface();
        user.initiateUsers();
        user.registerUsers();
    }

    public void createInterface(){
        RealClock clock=new RealClock();
        MeasurementLogger measurementLogger=new MeasurementLogger(clock);
        zmqNetworkInterfaceFactory=new ZMQNetworkInterfaceFactory(measurementLogger);

        thisIP = "127.0.0.1";
        thisAddress = new ZMQAddress(thisIP,15545);
        System.out.println("user entity address : " + thisAddress);

        gateWayAddress = new ZMQAddress("127.0.0.1",12345);
        System.out.println("gateway address : " + thisAddress);

        zmqNetworkInterface = (ZMQNetworkInterface) zmqNetworkInterfaceFactory.createNewNetworkInterface(measurementLogger, thisAddress);
        zmqNetworkInterface.addNetworkListener(new NetworkListener()
        {
            public void exceptionHappened(NetworkInterface networkInterface, NetworkAddress remoteAddress,
                                          Message message, Throwable cause) {
                System.out.println( "ZmqTestClient::exceptionHappened" + cause );
                cause.printStackTrace();
            }

            public void interfaceDown(NetworkInterface networkInterface) {
                System.out.println( "ZmqTestClient::interfaceDown" );
            }

            public void messageReceived(NetworkInterface networkInterface, NetworkAddress sourceAddress, Message message) {
                if (message instanceof InformUser){
                    InformUser informUser = (InformUser) message;
                    if (informUser.status.equals("finished")) {
                        System.out.println("EPOS finished for user: " + informUser.peerID+" with selected plan ID: "+informUser.selectedPlanID);
                    }
                }
                else if (message instanceof UserRegisterMessage){
                    UserRegisterMessage userRegisterMessage = (UserRegisterMessage) message;
                    Users.get(userRegisterMessage.index).status = "peerAssigned";
                    Users.get(userRegisterMessage.index).assignedPeerAddress = userRegisterMessage.assignedPeerAddress;
                    usersWithAssignedPeer++;
                }
                if (usersWithAssignedPeer == Users.size()){
                    System.out.println("---");
                    System.out.println("all users are assigned peers");
                    System.out.println("---");
                    sendPlans();
                    usersWithAssignedPeer = 0;
                }

            }

            public void messageSent(NetworkInterface networkInterface, NetworkAddress destinationAddress, Message message) {
//                System.out.println("Message sent: + " +destinationAddress + " message: "+ message + " messageSize: " + message.getMessageSize());
            }


            public void interfaceUp(NetworkInterface networkInterface) {
                System.out.println( "ZmqTestClient::interfaceUp" );
            }
        });
        zmqNetworkInterface.bringUp();
    }

    public void initiateUsers(){
        Users = new ArrayList<UserStatus>(config.numAgents);
        for (int i=0;i<config.numAgents;i++){
            UserStatus user = new UserStatus(i,"initiated",thisAddress);
            Users.add(user);
        }
    }

    public void registerUsers(){
        for (UserStatus user: Users) {
            zmqNetworkInterface.sendMessage(gateWayAddress, new UserRegisterMessage(user.index,user.status,user.userAddress));
        }
        System.out.println("---");
        System.out.println("user register message send for all of the users");
        System.out.println("---");
    }

    public void sendPlans(){
        for (UserStatus user: Users) {
            try {
                PlanSetMessage psm = getPlans(config,user.index);
                sendPlansMessage(psm,user.assignedPeerAddress);
            } catch (UnknownHostException e) {
                e.printStackTrace();
            }
        }
        System.out.println("---");
        System.out.println("all users have sent their plans");
        System.out.println("---");
    }

    public PlanSetMessage getPlans(Configuration conf, int Index) throws UnknownHostException {
//        List<Plan<Vector>> possiblePlans = conf.getDataset(Configuration.dataset).getPlans(Configuration.mapping.get(Index));

        Dataset gaussianDataset = new GaussianDataset(10,100,10,1,new Random(123));
        List<Plan<Vector>> possiblePlans = gaussianDataset.getPlans(Index);

        PlanSetMessage planSetMessage = new PlanSetMessage();
        planSetMessage.possiblePlans = possiblePlans;
        return planSetMessage;
    }

    public void sendPlansMessage(PlanSetMessage planSetMessage, ZMQAddress destination){
        zmqNetworkInterface.sendMessage(destination, planSetMessage);
//        System.exit(0);
    }

}
