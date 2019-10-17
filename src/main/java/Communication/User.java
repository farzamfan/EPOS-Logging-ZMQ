package Communication;

import agent.dataset.Dataset;
import agent.dataset.GaussianDataset;
import com.sun.scenario.effect.impl.sw.sse.SSEBlend_SRC_OUTPeer;
import config.Configuration;
import config.LiveConfiguration;
import data.Plan;
import data.Vector;
import loggers.EventLog;
import org.zeromq.ZMQ;
import pgpersist.PersistenceClient;
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
import java.util.*;

import static org.apache.commons.math3.util.Precision.round;

public class User {

    private ZMQNetworkInterfaceFactory zmqNetworkInterfaceFactory;
    transient PersistenceClient persistenceClient;
    private ZMQNetworkInterface zmqNetworkInterface;
    private String thisIP;
    private ZMQAddress thisAddress;
    private ZMQAddress gateWayAddress;
    private Configuration config;
    private List<UserStatus> Users;
    private int usersWithAssignedPeer;
    private int finishedPeers=0;
    private int currentRun =0;
    private int finishedRun=-1;
    private int usersWithassignedPeerRunning =0;
    private int maxNumRuns=10000;
    private List<Integer> numUsersPerRun;
    private int joinLeaveRate = 9;
    private int maxNumPeers = 150;
    private int minNumPeers = 50;
    private int newPlanProb = 9;
    private int newWeightProb = 9;
    private int userChangeProb = 9;
    private boolean userChangeProcessed = false;
    private int userPort = 15545;
    private int gateWayPort = 12345;
    // TODO: 17.10.19
    int dataSetSize = 100;
    List<Integer> numberList;
    List<Integer> userDatasetIndices;

    public User(){
        String rootPath = System.getProperty("user.dir");
        String confPath = rootPath + File.separator + "conf" + File.separator + "epos.properties";
        config = Configuration.fromFile(confPath,true);
        numUsersPerRun = new ArrayList<Integer>();

        numberList = new ArrayList<>();
        for(int i=1; i<=dataSetSize; i++){ numberList.add(i); }

        userDatasetIndices = new ArrayList<Integer>();
        numUsersPerRun = new ArrayList<Integer>(Collections.nCopies(maxNumRuns, 0));
        numUsersPerRun.set(0,config.numAgents);
    }

    public static void main(String[] args) {
        User user = new User();
        user.setUpPersistantClient();
        user.setUpEventLogger();
        user.createInterface();
        user.selectUsers(user.numUsersPerRun.get(0));
        user.initiateUsers();
        user.registerUsers();
    }

    public void createInterface(){
        RealClock clock=new RealClock();
        MeasurementLogger measurementLogger=new MeasurementLogger(clock);
        zmqNetworkInterfaceFactory=new ZMQNetworkInterfaceFactory(measurementLogger);

        thisIP = "127.0.0.1";
        thisAddress = new ZMQAddress(thisIP,userPort);
        System.out.println("user entity address : " + thisAddress);

        gateWayAddress = new ZMQAddress("127.0.0.1",gateWayPort);
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
                synchronized (this) {
                    if (message instanceof InformUserMessage) {
                        InformUserMessage informUserMessage = (InformUserMessage) message;
                        if (informUserMessage.status.equals("assignedPeerRunning")) {
                            Users.get(informUserMessage.peerID).status = "assignedPeerRunning";
                            usersWithassignedPeerRunning++;
                            if (usersWithassignedPeerRunning == numUsersPerRun.get(currentRun)) {
                                usersWithassignedPeerRunning = 0;
                                numUsersPerRun.set(currentRun + 1, numUsersPerRun.get(currentRun));
                                System.out.println("all users have their assigned peers running for run: " + currentRun + " numPeers: " + numUsersPerRun.get(currentRun));

                                if (!userChangeProcessed) {
                                    userChangeProcessed = true;
                                    usersJoiningOrLeaving();
                                }
                            }
                        }
                        if (informUserMessage.status.equals("finished")) {
                            checkCorrectRun(informUserMessage);
                            if (finishedPeers == numUsersPerRun.get(currentRun)) {
                                finishedPeers = 0;
                                System.out.println("---");
                                System.out.println("EPOS FINISHED! Run: " + currentRun + " numPeers: " + numUsersPerRun.get(currentRun));
                                System.out.println("---");
                                finishedRun = currentRun;
                                currentRun++;
                                userChangeProcessed = false;
                                resetPerRun();
                            }
                        }
                        if (informUserMessage.status.equals("checkNewPlans")) {
                            checkForNewPlans(informUserMessage);
                        }
                        if (informUserMessage.status.equals("checkNewWeights")) {
                            checkForNewWeights(informUserMessage);
                        }
                    } else if (message instanceof UserRegisterMessage) {
                        UserRegisterMessage userRegisterMessage = (UserRegisterMessage) message;
//                    System.out.println("peer assigned for: "+userRegisterMessage.index+" at run: "+userRegisterMessage.currentRun);
                        Users.get(userRegisterMessage.index).status = "peerAssigned";
                        Users.get(userRegisterMessage.index).assignedPeerAddress = userRegisterMessage.assignedPeerAddress;
                        sendPlans(userRegisterMessage.index, userRegisterMessage.assignedPeerAddress);
                        usersWithAssignedPeer++;
                        if (usersWithAssignedPeer == numUsersPerRun.get(currentRun)) {
                            usersWithAssignedPeer = 0;
                            System.out.println("all peers are assigned treeView: " + currentRun + " numPeers: " + numUsersPerRun.get(currentRun));
                        }
                    }
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

    public void selectUsers(int size){
        Collections.shuffle(this.numberList);
        for(int j=0; j<size; j++){
            userDatasetIndices.add(this.numberList.get(j));
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
//                List<Plan<Vector>> possiblePlans = config.getDataset(Configuration.dataset).getPlans(Configuration.mapping.get(userDatasetIndices.get(peerIdx)));
        List<Plan<Vector>> possiblePlans = config.getDataset(Configuration.dataset).getPlans(userDatasetIndices.get(peerIdx));
//        Dataset gaussianDataset = new GaussianDataset(10,100,10,1,new Random(Double.doubleToLongBits(Math.random())));
//        List<Plan<Vector>> possiblePlans = gaussianDataset.getPlans(peerIdx);
        return possiblePlans;
    }


    public void usersHavingNewPlans(UserStatus user){
        Random random = new Random(Double.doubleToLongBits(Math.random()));
        if( (random.nextInt(newPlanProb) + 1) == 1){
            user.planStatus = "hasNewPlans";
        }
    }

    public void checkForNewPlans(InformUserMessage informUserMessage){
        if (Users.get(informUserMessage.peerID).planStatus.equals("hasNewPlans")){
            PlanSetMessage planSetMessage = new PlanSetMessage("changePlans");
            // generates new plans for the user
            Random randomGenerator = new Random();
            int randomInt = randomGenerator.nextInt(dataSetSize) + 1;
            while (userDatasetIndices.contains(randomInt)){
                randomInt = randomGenerator.nextInt(dataSetSize) + 1;
            }
            userDatasetIndices.set(informUserMessage.peerID,randomInt);
            planSetMessage.possiblePlans = generatePlans(informUserMessage.peerID);
            sendPlansMessage(planSetMessage,Users.get(informUserMessage.peerID).assignedPeerAddress);

            Users.get(informUserMessage.peerID).planStatus = "noNewPlans";
        }
        else {
            zmqNetworkInterface.sendMessage(Users.get(informUserMessage.peerID).assignedPeerAddress, new PlanSetMessage("noNewPlans"));}
    }

    public void usersHavingNewWeights(UserStatus user){
        Random random = new Random(Double.doubleToLongBits(Math.random()));
        if( (random.nextInt(newWeightProb) + 1) == 1){
            user.weightStatus = "hasNewWeights";
        }
    }

    public void checkForNewWeights(InformUserMessage informUserMessage){
        if (Users.get(informUserMessage.peerID).weightStatus.equals("hasNewWeights")){
            boolean val = new java.util.Random(Double.doubleToLongBits(Math.random())).nextInt(2)==0;
            double oldAlpha = informUserMessage.alpha;
            double oldBeta = informUserMessage.beta;
            if (val){
                // increase alpha
                double newAlpha = round(( new java.util.Random(Double.doubleToLongBits(Math.random())).nextInt(20)*(0.05) ),2);
                if ( !( (newAlpha+oldBeta) < 1) ){
                    double newBeta = round(oldBeta - Math.abs(1-(newAlpha+oldBeta)),2);
                    zmqNetworkInterface.sendMessage(Users.get(informUserMessage.peerID).assignedPeerAddress, new WeightSetMessage("hasNewWeights",newAlpha,newBeta));
//                    System.out.println("(inc alpha) user: "+informUserMessage.peerID+ " has new weights of alpha: "+newAlpha+" beta: "+newBeta);
                }
                else if ( ( (newAlpha+oldBeta) < 1)) {
                    zmqNetworkInterface.sendMessage(Users.get(informUserMessage.peerID).assignedPeerAddress, new WeightSetMessage("hasNewWeights",newAlpha,oldBeta));
//                    System.out.println("(inc alpha) user: "+informUserMessage.peerID+ " has new weights of alpha: "+newAlpha+" beta: "+oldBeta);
                }
            }
            else {
                // increase beta
                double newBeta = round(( new java.util.Random(Double.doubleToLongBits(Math.random())).nextInt(20)*(0.05) ),2);
                if ( !( (oldAlpha+newBeta) < 1) ){
                    double newAlpha = round(oldAlpha - Math.abs(1-(oldAlpha+newBeta)),2);
                    zmqNetworkInterface.sendMessage(Users.get(informUserMessage.peerID).assignedPeerAddress, new WeightSetMessage("hasNewWeights",newAlpha,newBeta));
//                    System.out.println("(inc beta) user: "+informUserMessage.peerID+ " has new weights of alpha: "+newAlpha+" beta: "+newBeta);
                }
                else if ( (oldAlpha+newBeta) < 1) {
                    zmqNetworkInterface.sendMessage(Users.get(informUserMessage.peerID).assignedPeerAddress, new WeightSetMessage("hasNewWeights", oldAlpha, newBeta));
//                    System.out.println("(inc beta) user: " + informUserMessage.peerID + " has new weights of alpha: " + oldAlpha + " beta: " + newBeta);
                }
            }
            Users.get(informUserMessage.peerID).weightStatus = "noNewWeights";
        }
        else {
            zmqNetworkInterface.sendMessage(Users.get(informUserMessage.peerID).assignedPeerAddress, new WeightSetMessage("noNewWeights"));}
    }

    public void addRemoveUsers(){
        Random random = new java.util.Random(Double.doubleToLongBits(Math.random()));
        if (random.nextInt(2) == 0 && numUsersPerRun.get(currentRun) < maxNumPeers){
            int countJoined=0;
            for (int r=0;r<numUsersPerRun.get(currentRun)/joinLeaveRate;r++){
                UserStatus user = new UserStatus(Users.size(),currentRun+1,"added",thisAddress);
                Users.add(user);

                Random randomGenerator = new Random();
                int randomInt = randomGenerator.nextInt(dataSetSize) + 1;
                while (userDatasetIndices.contains(randomInt)){randomInt = randomGenerator.nextInt(dataSetSize) + 1; }
                userDatasetIndices.add(randomInt);

                zmqNetworkInterface.sendMessage(gateWayAddress, new UserJoinLeaveMessage(Users.size()-1,currentRun+1,"join",this.thisAddress));
                System.out.println("users: "+(Users.size()-1)+" will join the system at run: "+(currentRun+1));
                countJoined++;
                EventLog.logEvent("User", "addRemoveUsers", "userJoin" , (Users.size()-1)+"-"+currentRun);
            }
            numUsersPerRun.set(currentRun+1,numUsersPerRun.get(currentRun)+countJoined);
        }
        else if (numUsersPerRun.get(currentRun) > minNumPeers) {
            int countLeft=0;
            Set<Integer> indices = new HashSet<Integer>();
            Random newRand = new java.util.Random(Double.doubleToLongBits(Math.random()));
            for (int r=0;r<numUsersPerRun.get(currentRun)/joinLeaveRate;r++){
                indices.add(newRand.nextInt(Users.size()-1));
            }
            Iterator<Integer> it = indices.iterator();
            while (it.hasNext()){
                int index = it.next();
                if (index !=0 && (currentRun < Users.get(index).leaveRun) ){
                    Users.get(index).run = currentRun+1;
                    Users.get(index).leaveRun = currentRun+1;
                    userDatasetIndices.set(index,-1);
                    zmqNetworkInterface.sendMessage(gateWayAddress, new UserJoinLeaveMessage(index, currentRun+1,"leave",this.thisAddress));
                    System.out.println("users: "+index+" will leave the system at run: "+(currentRun+1));
                    countLeft++;
                    EventLog.logEvent("User", "addRemoveUsers", "userLeave" , index+"-"+currentRun);
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
            zmqNetworkInterface.sendMessage(gateWayAddress, new UserJoinLeaveMessage("noChange",currentRun));}
    }

    public void resetPerRun(){
        finishedPeers=0;
        usersWithassignedPeerRunning =0;
        usersWithAssignedPeer=0;
    }

    public void checkCorrectRun(InformUserMessage informUserMessage){
        if (informUserMessage.run == finishedRun+1){
            Users.get(informUserMessage.peerID).status = "finished";
            Users.get(informUserMessage.peerID).run = informUserMessage.run;
            usersHavingNewPlans(Users.get(informUserMessage.peerID));
            usersHavingNewWeights(Users.get(informUserMessage.peerID));
            finishedPeers++;
        }
        else {
            EventLog.logEvent("User", "checkCorrectRun", "incorrectFinish" , informUserMessage.peerID+"-"+currentRun);
            System.out.println("incorrect finish message received from peer"+informUserMessage.peerID+
                    " reported run: "+informUserMessage.run+" numPeers for incorrect run: "+numUsersPerRun.get(informUserMessage.run));
            System.out.println("current run: "+currentRun+" correct numPeers: "+numUsersPerRun.get(currentRun));
            if (numUsersPerRun.get(currentRun) != numUsersPerRun.get(informUserMessage.run)) {System.exit(1);}
        }
    }

    public void setUpEventLogger(){
        EventLog.setPeristenceClient(persistenceClient);
        EventLog.setPeerId(-200);
        EventLog.setDIASNetworkId(0);
    }

    public void setUpPersistantClient(){
        LiveConfiguration liveConf = new LiveConfiguration();
        ZMQ.Context zmqContext = ZMQ.context(1);
        String[] args = new String[2];
        args[0] = String.valueOf(0);
        args[1]= String.valueOf(0);
        liveConf.readConfiguration(args);
        int persistenceClientOutputQueueSize = 1000;
        String daemonConnectString = "tcp://" + liveConf.persistenceDaemonIP + ":" + liveConf.persistenceDaemonPort;
        persistenceClient = new PersistenceClient( zmqContext, daemonConnectString, persistenceClientOutputQueueSize );
        System.out.println( "persistenceClient created" );
    }

}
