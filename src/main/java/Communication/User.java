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
    private int finishedPeers=0;
    private int currentRun =0;
    private int usersWithassignedPeerRunning =0;
    private List<Integer> numUsersPerRun;
    private int joinLeaveRate = 20;
    private int newPlanProb = 19;
    private int userChangeProb = 2;

    public User(){
        String rootPath = System.getProperty("user.dir");
        String confPath = rootPath + File.separator + "conf" + File.separator + "epos.properties";
        config = Configuration.fromFile(confPath);
        numUsersPerRun = new ArrayList<Integer>();
        numUsersPerRun.add(config.numAgents);
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
                if (message instanceof InformUserMessage){
                    InformUserMessage informUserMessage = (InformUserMessage) message;
                    if (informUserMessage.status.equals("assignedPeerRunning")){
                        Users.get(informUserMessage.peerID).status = "assignedPeerRunning";
                        usersWithassignedPeerRunning++;
                    }
                    if (informUserMessage.status.equals("finished")) {
                        Users.get(informUserMessage.peerID).status = "finished";
                        usersHavingNewPlans(Users.get(informUserMessage.peerID));
//                        System.out.println("EPOS finished for user: " + informUser.peerID+" with selected plan ID: "+informUser.selectedPlanID);
                        finishedPeers++;
                        if (finishedPeers == numUsersPerRun.get(currentRun)){
                            System.out.println("---");
                            System.out.println("all users have received their final plans! EPOS finished! Run: "+ currentRun);
                            System.out.println("---");
                            currentRun++;
                            finishedPeers =0;
//                            System.exit(0);
                        }
                    }
                    if (informUserMessage.status.equals("checkNewPlans")) {
                        checkForNewPlans(informUserMessage);
                    }
                }
                else if (message instanceof UserRegisterMessage){
                    UserRegisterMessage userRegisterMessage = (UserRegisterMessage) message;
                    Users.get(userRegisterMessage.index).status = "peerAssigned";
                    Users.get(userRegisterMessage.index).assignedPeerAddress = userRegisterMessage.assignedPeerAddress;
                    sendPlans(userRegisterMessage.index,userRegisterMessage.assignedPeerAddress);
                    usersWithAssignedPeer++;
                }
                if (usersWithAssignedPeer == Users.size()){
                    System.out.println("---");
                    System.out.println("all users are assigned peers");
                    System.out.println("---");
                    usersWithAssignedPeer = 0;
                }
                if (usersWithassignedPeerRunning == Users.size()){
                    System.out.println("---");
                    System.out.println("all users have their assigned peers running");
                    System.out.println("---");
                    usersJoiningOrLeaving();
                    usersWithassignedPeerRunning = 0;
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
        Users = new ArrayList<UserStatus>(numUsersPerRun.get(currentRun));
        for (int i=0;i<numUsersPerRun.get(currentRun);i++){
            UserStatus user = new UserStatus(i,"initiated",thisAddress);
            Users.add(user);
        }
    }

    public void registerUsers(){
        for (UserStatus user: Users) {
            zmqNetworkInterface.sendMessage(gateWayAddress, new UserRegisterMessage(user.index, currentRun,user.status,user.userAddress));
        }
        System.out.println("---");
        System.out.println("user register message send for all of the users");
        System.out.println("---");
    }

    public void sendPlans(int idx, ZMQAddress address){

        PlanSetMessage psm = null;
        try {
            psm = createPlanMessage(config,idx);
            sendPlansMessage(psm,address);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }

    public PlanSetMessage createPlanMessage(Configuration conf, int Index) throws UnknownHostException {
        PlanSetMessage planSetMessage = new PlanSetMessage("setPlans");
        planSetMessage.possiblePlans = generatePlans(Index);
        return planSetMessage;
    }

    public void sendPlansMessage(PlanSetMessage planSetMessage, ZMQAddress destination){
        zmqNetworkInterface.sendMessage(destination, planSetMessage);
//        System.exit(0);
    }

    public List<Plan<Vector>> generatePlans(int peerIdx){
        //        List<Plan<Vector>> possiblePlans = conf.getDataset(Configuration.dataset).getPlans(Configuration.mapping.get(Index));
        Dataset gaussianDataset = new GaussianDataset(10,100,10,1,new Random(System.currentTimeMillis()));
        List<Plan<Vector>> possiblePlans = gaussianDataset.getPlans(peerIdx);
        return possiblePlans;
    }

    public void checkForNewPlans(InformUserMessage informUserMessage){
        if (Users.get(informUserMessage.peerID).status.equals("hasNewPlans")){
            PlanSetMessage planSetMessage = new PlanSetMessage("changePlans");
            planSetMessage.possiblePlans = generatePlans(informUserMessage.peerID);
            sendPlansMessage(planSetMessage,Users.get(informUserMessage.peerID).assignedPeerAddress);
        }
        else {
        zmqNetworkInterface.sendMessage(Users.get(informUserMessage.peerID).assignedPeerAddress, new PlanSetMessage("noNewPlans"));}
    }

    public void usersHavingNewPlans(UserStatus user){
        Random random = new Random(System.currentTimeMillis());
        if( (random.nextInt(newPlanProb) + 1) == 1){
            user.status = "hasNewPlans";
            System.out.println("user: "+user.index+ " has new plans.");
        }
    }

    public void addRemoveUsers(){
        Random random = new Random(System.currentTimeMillis());
        if (random.nextInt(1) == 0){
            System.out.println(Users.size()/joinLeaveRate+" users to be added");
            for (int r=0;r<Users.size()/joinLeaveRate;r++){
                UserStatus user = new UserStatus(Users.size(),"added",thisAddress);
                Users.add(user);
                zmqNetworkInterface.sendMessage(gateWayAddress, new UserJoinLeaveMessage(Users.size()-1,currentRun+1,"join",this.thisAddress));
            }
            numUsersPerRun.add(Users.size());
        }
//        else {
//            System.out.printf(Users.size()/joinLeaveRate+" users to be removed");
//            int[] leftUserIndices = new int[Users.size()/joinLeaveRate];
//            for (int r=0;r<Users.size()/joinLeaveRate;r++){
//                Users.remove(random.nextInt(Users.size()-1));
//                leftUserIndices[r] = random.nextInt(Users.size()-1);
//            }
//            numUsersPerRun.add(Users.size());
//            zmqNetworkInterface.sendMessage(gateWayAddress, new UserJoinLeaveMessage("leave",Users.size()/joinLeaveRate, leftUserIndices,currentRun));
//        }
    }

    public void usersJoiningOrLeaving(){
        Random random = new Random(System.currentTimeMillis());
        if( (random.nextInt(userChangeProb) + 1) == 1 && currentRun>0){
            System.out.println("There is a change in Users numbers for run: "+(currentRun+1));
            addRemoveUsers();
        }
        else {
            numUsersPerRun.add(Users.size());
            zmqNetworkInterface.sendMessage(gateWayAddress, new UserJoinLeaveMessage("noChange",currentRun));}
    }

}
