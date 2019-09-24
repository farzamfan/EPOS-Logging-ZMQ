package Communication;

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

import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

public class User {

    public static void main(String[] args) {
        sendPlans();
    }

    public static void sendPlans(){
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

    public static PlanSetMessage createMessage(Configuration conf, int Index) throws UnknownHostException {
        List<Plan<Vector>> possiblePlans = conf.getDataset(Configuration.dataset).getPlans(Configuration.mapping.get(Index));

        PlanSetMessage planSetMessage = new PlanSetMessage();
        planSetMessage.possiblePlans = possiblePlans;
        return planSetMessage;
    }

    public static void sendMessage(PlanSetMessage planSetMessage, int Index){
        RealClock clock=new RealClock();
        MeasurementLogger measurementLogger=new MeasurementLogger(clock);
        ZMQNetworkInterfaceFactory zmqNetworkInterfaceFactory=new ZMQNetworkInterfaceFactory(measurementLogger);

        String thisIP = "127.0.0.1";
        System.out.println( "thisIP : " + thisIP);

        ZMQAddress zmqAddress = new ZMQAddress(thisIP,15545+Index);
        System.out.println("zmqAddress : " + zmqAddress );

        ZMQNetworkInterface zmqNetworkInterface=(ZMQNetworkInterface)zmqNetworkInterfaceFactory.createNewNetworkInterface(measurementLogger, zmqAddress);
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

        ZMQAddress destination = new ZMQAddress(thisIP ,3000+Index);
        System.out.println( "destination : " + destination );

        zmqNetworkInterface.sendMessage(destination, planSetMessage);
//        System.exit(0);
    }

}
