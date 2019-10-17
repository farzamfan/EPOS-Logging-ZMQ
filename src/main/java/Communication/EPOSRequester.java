package Communication;

import agent.Agent;
import config.Configuration;
import config.LiveConfiguration;
import data.Plan;
import data.Vector;
import loggers.EventLog;
import org.zeromq.ZMQ;
import pgpersist.PersistenceClient;
import protopeer.MainConfiguration;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class EPOSRequester {

    ZMQNetworkInterfaceFactory zmqNetworkInterfaceFactory;
    static Configuration config;
    String thisIP;
    static int numPeers;
    int maxNumRuns = 500;
    int maxSimulations = 500;
    int currentSim=0;
    ZMQNetworkInterface zmqNetworkInterface;
    transient PersistenceClient persistenceClient;

    public static void main(String[] args) throws UnknownHostException {
        String rootPath = System.getProperty("user.dir");
        String confPath = rootPath + File.separator + "conf" + File.separator + "epos.properties";
        config = Configuration.fromFile(confPath,false);
        numPeers = config.numAgents;

        EPOSRequester eposRequester = new EPOSRequester();
        eposRequester.bringUp();
        eposRequester.setUpPersistantClient();
        eposRequester.setUpEventLogger();
        eposRequester.listen();
        eposRequester.startSimulation();
    }

    public void startSimulation() {

        try {
            Runtime.getRuntime().exec("screen -S GateWay -d -m java -Xmx1024m -jar GateWay.jar");
            Runtime.getRuntime().exec("screen -S Users -d -m java -Xmx2048m -jar IEPOSUsers.jar");
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

        thisIP = "127.0.0.1";
        ZMQAddress zmqAddress = new ZMQAddress(thisIP,54321);
        System.out.println("zmqAddress : " + zmqAddress );

        zmqNetworkInterface=(ZMQNetworkInterface)zmqNetworkInterfaceFactory.createNewNetworkInterface(measurementLogger, zmqAddress);
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

            public void messageReceived(NetworkInterface networkInterface, NetworkAddress sourceAddress, Message message) throws InterruptedException {
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
                            EventLog.logEvent("EPOSRequester", "messageReceived", "SIMULATIONS Done" , eposRequestMessage.run+"-"+currentSim);
                            terminate();
                            TimeUnit.SECONDS.sleep(5);
                            System.exit(0);
                        }
                        EventLog.logEvent("EPOSRequester", "messageReceived", "SIMULATION Over" , eposRequestMessage.run+"-"+currentSim);
                        currentSim++;
                        terminate();
                        TimeUnit.SECONDS.sleep(5);
                        startSimulation();
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
        ZMQAddress destination = new ZMQAddress(thisIP ,12345);
//        System.out.println( "destination : " + destination );
        zmqNetworkInterface.sendMessage(destination, ERM);
    }

    public void terminate(){
        try {
            Runtime.getRuntime().exec("./killAll.sh");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void setUpEventLogger(){
        EventLog.setPeristenceClient(persistenceClient);
        EventLog.setPeerId(-100);
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
