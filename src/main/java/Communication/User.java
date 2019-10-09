package Communication;

import agent.dataset.Dataset;
import agent.dataset.GaussianDataset;
import com.sun.scenario.effect.impl.sw.sse.SSEBlend_SRC_OUTPeer;
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
import java.util.Collections;
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
    private int maxNumRuns=5000;
    private List<Integer> numUsersPerRun;
    private int joinLeaveRate = 9;
    private int maxNumPeers = 200;
    private int minNumPeers = 100;
    private int newPlanProb = 9;
    private int userChangeProb = 9;
    private boolean userChangeProcessed = false;

    public User(){
        String rootPath = System.getProperty("user.dir");
        String confPath = rootPath + File.separator + "conf" + File.separator + "epos.properties";
        config = Configuration.fromFile(confPath);
        numUsersPerRun = new ArrayList<Integer>();
        numUsersPerRun = new ArrayList<Integer>(Collections.nCopies(maxNumRuns, 0));
        numUsersPerRun.set(0,config.numAgents);
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
//                        System.out.println("peer: "+informUserMessage.peerID+" is finished at run: "+(informUserMessage.run));
                        Users.get(informUserMessage.peerID).status = "finished";
                        Users.get(informUserMessage.peerID).run = informUserMessage.run;
                        usersHavingNewPlans(Users.get(informUserMessage.peerID));
                        finishedPeers++;
                        if (finishedPeers == numUsersPerRun.get(currentRun)){
                            System.out.println("---");
                            System.out.println("EPOS FINISHED! Run: "+ currentRun+" numPeers: "+numUsersPerRun.get(currentRun));
                            System.out.println("---");
                            currentRun++;
                            finishedPeers =0;
                            userChangeProcessed = false;
//                            System.exit(0);
                        }
                    }
                    if (informUserMessage.status.equals("checkNewPlans")) {
                        checkForNewPlans(informUserMessage);
                    }
                }
                else if (message instanceof UserRegisterMessage){
                    UserRegisterMessage userRegisterMessage = (UserRegisterMessage) message;
//                    System.out.println("peer assigned for: "+userRegisterMessage.index+" at run: "+userRegisterMessage.currentRun);
                    Users.get(userRegisterMessage.index).status = "peerAssigned";
                    Users.get(userRegisterMessage.index).assignedPeerAddress = userRegisterMessage.assignedPeerAddress;
                    sendPlans(userRegisterMessage.index,userRegisterMessage.assignedPeerAddress);
                    usersWithAssignedPeer++;
                }
                if (usersWithAssignedPeer == numUsersPerRun.get(currentRun)){
                    System.out.println("all users are assigned peers for run: "+currentRun+" numPeers: "+numUsersPerRun.get(currentRun));
                    usersWithAssignedPeer = 0;
                }
                if (usersWithassignedPeerRunning == numUsersPerRun.get(currentRun)){
                    System.out.println("all users have their assigned peers running for run: "+currentRun+" numPeers: "+numUsersPerRun.get(currentRun));
                    if (!userChangeProcessed) {
                        userChangeProcessed = true;
                        usersJoiningOrLeaving();
                    }
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
            UserStatus user = new UserStatus(i,0,"initiated",thisAddress);
            Users.add(user);
        }
    }

    public void registerUsers(){
        for (UserStatus user: Users) {
            zmqNetworkInterface.sendMessage(gateWayAddress, new UserRegisterMessage(user.index, currentRun,user.status,user.userAddress));
        }
        System.out.println("user register message send for all of the users: "+currentRun);
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
        Dataset gaussianDataset = new GaussianDataset(10,100,10,1,new Random(Double.doubleToLongBits(Math.random())));
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
        Random random = new Random(Double.doubleToLongBits(Math.random()));
        if( (random.nextInt(newPlanProb) + 1) == 1){
            user.status = "hasNewPlans";
            System.out.println("user: "+user.index+ " has new plans.");
        }
    }

    public void addRemoveUsers(){
        Random random = new java.util.Random(Double.doubleToLongBits(Math.random()));
        if (random.nextInt(2) == 0 && numUsersPerRun.get(currentRun) < maxNumPeers){
            int countJoined=0;
            for (int r=0;r<numUsersPerRun.get(currentRun)/joinLeaveRate;r++){
                UserStatus user = new UserStatus(Users.size(),currentRun+1,"added",thisAddress);
                Users.add(user);
                zmqNetworkInterface.sendMessage(gateWayAddress, new UserJoinLeaveMessage(Users.size()-1,currentRun+1,"join",this.thisAddress));
                System.out.println("users: "+(Users.size()-1)+" will join the system at run: "+(currentRun+1));
                countJoined++;
            }
            numUsersPerRun.set(currentRun+1,numUsersPerRun.get(currentRun)+countJoined);
        }
        else if (numUsersPerRun.get(currentRun) > minNumPeers) {
            int countLeft=0;
            for (int r=0;r<numUsersPerRun.get(currentRun)/joinLeaveRate;r++){
                Random newRand = new java.util.Random(Double.doubleToLongBits(Math.random()));
                int index = newRand.nextInt(Users.size()-1);
                if (index !=0 && (currentRun < Users.get(index).leaveRun) ){
                    Users.get(index).run = currentRun+1;
                    Users.get(index).leaveRun = currentRun+1;
                    zmqNetworkInterface.sendMessage(gateWayAddress, new UserJoinLeaveMessage(index, currentRun+1,"leave",this.thisAddress));
                    System.out.println("users: "+index+" will leave the system at run: "+(currentRun+1));
                    countLeft++;
                }
            }
            numUsersPerRun.set(currentRun+1,numUsersPerRun.get(currentRun)-countLeft);
        }
    }

    public void usersJoiningOrLeaving(){
        boolean val = new java.util.Random(Double.doubleToLongBits(Math.random())).nextInt(userChangeProb)==0;
        if( val && currentRun>0){
            addRemoveUsers();
        }
        else {
            numUsersPerRun.set(currentRun+1,numUsersPerRun.get(currentRun));
            zmqNetworkInterface.sendMessage(gateWayAddress, new UserJoinLeaveMessage("noChange",currentRun));}
    }

}
