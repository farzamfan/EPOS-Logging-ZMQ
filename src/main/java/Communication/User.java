package Communication;

import agent.dataset.Dataset;
import agent.dataset.GaussianDataset;
import config.Configuration;
import data.Plan;
import data.Vector;
import protopeer.BasePeerlet;
import protopeer.measurement.MeasurementLogger;
import protopeer.network.Message;
import protopeer.network.NetworkAddress;
import protopeer.network.NetworkInterface;
import protopeer.network.NetworkListener;
import protopeer.network.zmq.ZMQAddress;
import protopeer.network.zmq.ZMQNetworkInterface;
import protopeer.network.zmq.ZMQNetworkInterfaceFactory;
import protopeer.time.RealClock;

import javax.tools.Diagnostic;
import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Random;

public class User {

    ZMQNetworkInterfaceFactory zmqNetworkInterfaceFactory;
    ZMQNetworkInterface zmqNetworkInterface;
    String thisIP;
    ZMQAddress zmqAddress;

    public static void main(String[] args) {
        User user = new User();
        user.createInterface();
        user.sendPlans();
    }

    public void createInterface(){
        RealClock clock=new RealClock();
        MeasurementLogger measurementLogger=new MeasurementLogger(clock);
        zmqNetworkInterfaceFactory=new ZMQNetworkInterfaceFactory(measurementLogger);

        thisIP = "127.0.0.1";
        System.out.println( "thisIP : " + thisIP);

        zmqAddress = new ZMQAddress(thisIP,15545);
        System.out.println("zmqAddress : " + zmqAddress );

        zmqNetworkInterface = (ZMQNetworkInterface) zmqNetworkInterfaceFactory.createNewNetworkInterface(measurementLogger, zmqAddress);
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

            public void messageReceived(NetworkInterface networkInterface, NetworkAddress sourceAddress,
                                        Message message) {
                System.out.println("Message received: + " +sourceAddress + " message: "+ message + " messageSize: " + message.getMessageSize());
                if (message instanceof InformUser){
                    InformUser informUser = (InformUser) message;
                    System.out.println("EPOS finished for user: "+informUser.peerID);
                }
            }

            public void messageSent(NetworkInterface networkInterface, NetworkAddress destinationAddress, Message message) {
                System.out.println("Message sent: + " +destinationAddress + " message: "+ message + " messageSize: " + message.getMessageSize());
            }


            public void interfaceUp(NetworkInterface networkInterface) {
                System.out.println( "ZmqTestClient::interfaceUp" );
            }
        });
        zmqNetworkInterface.bringUp();
    }

    public void sendPlans(){
        try {
            String rootPath = System.getProperty("user.dir");
            String confPath = rootPath + File.separator + "conf" + File.separator + "epos.properties";
            Configuration config = Configuration.fromFile(confPath);

            for (int i=0;i<config.numAgents;i++) {
                PlanSetMessage psm = createMessage(config,i);
                sendMessage(psm,i);
            }
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }

    public PlanSetMessage createMessage(Configuration conf, int Index) throws UnknownHostException {
//        List<Plan<Vector>> possiblePlans = conf.getDataset(Configuration.dataset).getPlans(Configuration.mapping.get(Index));

        Dataset gaussianDataset = new GaussianDataset(10,100,10,1,new Random(123));
        List<Plan<Vector>> possiblePlans = gaussianDataset.getPlans(Index);

        PlanSetMessage planSetMessage = new PlanSetMessage();
        planSetMessage.possiblePlans = possiblePlans;
        return planSetMessage;
    }

    public void sendMessage(PlanSetMessage planSetMessage, int Index){
        ZMQAddress destination = new ZMQAddress(thisIP ,3000+Index);
        System.out.println( "destination : " + destination );
        zmqNetworkInterface.sendMessage(destination, planSetMessage);
//        System.exit(0);
    }

}
