package Communication;

import config.Configuration;
import config.LiveConfiguration;
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
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;

public class EPOSRequester {

    ZMQNetworkInterfaceFactory zmqNetworkInterfaceFactory;
    ZMQNetworkInterface zmqNetworkInterface;
    transient PersistenceClient persistenceClient;
    static String rootPath = System.getProperty("user.dir");
    static String confPath = rootPath + File.separator + "conf" + File.separator + "epos.properties";
    static Configuration config;
    static String EPOSRequesterIP;
    static int EPOSRequesterPort;
    static int EPOSRequesterPeerID;
    static String GateWayIP;
    static int GateWayPort;
    static int maxSimulations;
    int currentSim = 0;
    static int maxNumRuns;
    static int numPeers;
    static int persistenceClientOutputQueueSize;
    static int sleepSecondBetweenRuns;

    public static void main(String[] args) throws UnknownHostException {

        EPOSRequester eposRequester = new EPOSRequester();
        eposRequester.readConfig();
        eposRequester.bringUp();
        eposRequester.setUpPersistantClient();
        eposRequester.setUpEventLogger();
        eposRequester.listen();
        eposRequester.startSimulation();
    }

    public void readConfig(){
        config = Configuration.fromFile(confPath,false);

        EPOSRequesterIP = config.EPOSRequesterIP;
        EPOSRequesterPort = config.EPOSRequesterPort;
        EPOSRequesterPeerID = config.EPOSRequesterPeerID;
        GateWayIP = config.GateWayIP;
        GateWayPort = config.GateWayPort;

        maxSimulations = config.maxSimulations;
        maxNumRuns = config.maxNumRuns;
        numPeers = config.numAgents;

        sleepSecondBetweenRuns = config.sleepSecondBetweenRuns;
        persistenceClientOutputQueueSize = config.persistenceClientOutputQueueSize;
    }

    public void startSimulation() {

        try {
            Runtime.getRuntime().exec("screen -S GateWay -d -m java -Xmx1024m -jar GateWay.jar");
            Runtime.getRuntime().exec("screen -S Users -d -m java -Xmx2048m -jar IEPOSUsers.jar "+currentSim);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public EPOSRequestMessage createMessage(Configuration conf, int numNodes) throws UnknownHostException {
        EPOSRequestMessage eposRequestMessage = new EPOSRequestMessage(1,numNodes,"EPOSRunRequested", maxNumRuns, currentSim);
        return eposRequestMessage;
    }

    public void bringUp(){
        RealClock clock=new RealClock();
        MeasurementLogger measurementLogger=new MeasurementLogger(clock);
        zmqNetworkInterfaceFactory=new ZMQNetworkInterfaceFactory(measurementLogger);

        ZMQAddress EPOSRequesterAddress = new ZMQAddress(EPOSRequesterIP,EPOSRequesterPort);
        System.out.println("zmqAddress : " + EPOSRequesterAddress );

        zmqNetworkInterface=(ZMQNetworkInterface)zmqNetworkInterfaceFactory.createNewNetworkInterface(measurementLogger, EPOSRequesterAddress);
    }

    public void listen(){
        zmqNetworkInterface.addNetworkListener(new NetworkListener()
        {
            public void exceptionHappened(NetworkInterface networkInterface, NetworkAddress remoteAddress, Message message, Throwable cause) {
                System.out.println( "ZmqTestClient::exceptionHappened" + cause );
                cause.printStackTrace();
            }

            public void interfaceDown(NetworkInterface networkInterface) {
                System.out.println( "ZmqTestClient::interfaceDown" );
            }

            public void messageReceived(NetworkInterface networkInterface, NetworkAddress sourceAddress, Message message) {
//                System.out.println("Message received from: + " +message.getSourceAddress() + " message: "+ message + " messageSize: " + message.getMessageSize());
                if (message instanceof EPOSRequestMessage){
                    EPOSRequestMessage eposRequestMessage = (EPOSRequestMessage) message;
                    if (eposRequestMessage.status.equals("usersRegistered")){
                        System.out.println("EPOS users registered! Run: "+eposRequestMessage.run+" simulation: "+currentSim);
                        try {
                            EventLog.logEvent("EPOSRequester", "messageReceived", "usersRegistered" , eposRequestMessage.run+"-"+currentSim);
                            requestEPOS(createMessage(config, numPeers));
                        } catch (UnknownHostException e) {
                            e.printStackTrace();
                        }
                    }
                    if (eposRequestMessage.status.equals("finished")){
                        System.out.println("EPOS finished successfully! Run: "+eposRequestMessage.run+" simulation: "+currentSim);
                        EventLog.logEvent("EPOSRequester", "messageReceived", "EPOS finished" , eposRequestMessage.run+"-"+currentSim);
                    }
                    if (eposRequestMessage.status.equals("maxRunReached")){
                        System.out.println("---");
                        System.out.println("SIMULATION: "+currentSim+" Over");
                        System.out.println("---");
                        if (currentSim == maxSimulations){
                            EventLog.logEvent("EPOSRequester", "messageReceived", "ALL SIMULATIONS Done" , eposRequestMessage.run+"-"+currentSim);
                            try {
                                terminate();
                                TimeUnit.SECONDS.sleep(sleepSecondBetweenRuns);
                                System.exit(0);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                        EventLog.logEvent("EPOSRequester", "messageReceived", "SIMULATION Over" , eposRequestMessage.run+"-"+currentSim);
                        currentSim++;
                        try {
                            terminate();
                            checkConfigChanges();
                            TimeUnit.SECONDS.sleep(sleepSecondBetweenRuns);
                            startSimulation();
                        } catch (InterruptedException | IOException e) {
                            e.printStackTrace();
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

    public void requestEPOS(EPOSRequestMessage ERM){
        ZMQAddress destination = new ZMQAddress(GateWayIP,GateWayPort);
//        System.out.println( "destination : " + destination );
        zmqNetworkInterface.sendMessage(destination, ERM);
    }

    public void checkConfigChanges() throws IOException {
        if (currentSim%3 == 0) {
            config.changeConfig(confPath,"globalCostFunction","RMSE");
            config.changeConfig(confPath,"userChangeProb","9");
            config.changeConfig(confPath,"GCFChangeProb","9");
            config.changeConfig(confPath,"newWeightProb","9");
            config.changeConfig(confPath,"newPlanProb","9");
        }
        if (currentSim%3 == 1) {
            config.changeConfig(confPath,"globalCostFunction","RMSE");
            config.changeConfig(confPath,"userChangeProb","4");
            config.changeConfig(confPath,"GCFChangeProb","4");
            config.changeConfig(confPath,"newWeightProb","4");
            config.changeConfig(confPath,"newPlanProb","4");
        }
        if (currentSim%3 == 2) {
            config.changeConfig(confPath,"globalCostFunction","RMSE");
            config.changeConfig(confPath,"userChangeProb","2");
            config.changeConfig(confPath,"GCFChangeProb","2");
            config.changeConfig(confPath,"newWeightProb","2");
            config.changeConfig(confPath,"newPlanProb","2");
        }
        readConfig();
    }

    public void terminate(){
        try {
            Runtime.getRuntime().exec("./killAll.sh");
            EventLog.logEvent("EPOSRequester", "terminate", "terminate", String.valueOf(currentSim));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void setUpEventLogger(){
        EventLog.setPeristenceClient(persistenceClient);
        EventLog.setPeerId(EPOSRequesterPeerID);
        EventLog.setDIASNetworkId(0);
    }

    public void setUpPersistantClient(){
        ZMQ.Context zmqContext = ZMQ.context(1);
        String daemonConnectString = "tcp://" + config.persistenceDaemonIP + ":" + config.persistenceDaemonPort;
        persistenceClient = new PersistenceClient( zmqContext, daemonConnectString, persistenceClientOutputQueueSize );
        System.out.println( "persistenceClient created" );
    }
}
