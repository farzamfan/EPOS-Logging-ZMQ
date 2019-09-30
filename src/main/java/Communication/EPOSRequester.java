package Communication;

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
import java.util.List;

public class EPOSRequester {

    ZMQNetworkInterfaceFactory zmqNetworkInterfaceFactory;
    String thisIP;
    int numPeers;
    ZMQNetworkInterface zmqNetworkInterface;

    public static void main(String[] args) throws UnknownHostException {
        String rootPath = System.getProperty("user.dir");
        String confPath = rootPath + File.separator + "conf" + File.separator + "epos.properties";
        Configuration config = Configuration.fromFile(confPath);

        EPOSRequester eposRequester = new EPOSRequester();
        eposRequester.numPeers = config.numAgents;
        eposRequester.listen();
        eposRequester.requestEPOS(eposRequester.createMessage(config, eposRequester.numPeers));
    }

    public EPOSRequestMessage createMessage(Configuration conf, int numNodes) throws UnknownHostException {
        EPOSRequestMessage eposRequestMessage = new EPOSRequestMessage(1,numNodes,"EPOSRunRequested");
        System.out.println(conf.numAgents+" "+numNodes);
        return eposRequestMessage;
    }

    public void listen(){
        RealClock clock=new RealClock();
        MeasurementLogger measurementLogger=new MeasurementLogger(clock);
        zmqNetworkInterfaceFactory=new ZMQNetworkInterfaceFactory(measurementLogger);

        thisIP = "127.0.0.1";
        ZMQAddress zmqAddress = new ZMQAddress(thisIP,54321);
        System.out.println("zmqAddress : " + zmqAddress );

        zmqNetworkInterface=(ZMQNetworkInterface)zmqNetworkInterfaceFactory.createNewNetworkInterface(measurementLogger, zmqAddress);
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
                System.out.println("Message received from: + " +message.getSourceAddress() + " message: "+ message + " messageSize: " + message.getMessageSize());
                if (message instanceof EPOSRequestMessage){
                    EPOSRequestMessage eposRequestMessage = (EPOSRequestMessage) message;
                    if (eposRequestMessage.status.equals("finished")){
                        System.out.println("---");
                        System.out.println("EPOS finished successfully!");
                        System.out.println("---");
//                        System.exit(0);
                    }
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

    public void requestEPOS(EPOSRequestMessage ERM){
        ZMQAddress destination = new ZMQAddress(thisIP ,12345);
        System.out.println( "destination : " + destination );
        zmqNetworkInterface.sendMessage(destination, ERM);
    }
}
