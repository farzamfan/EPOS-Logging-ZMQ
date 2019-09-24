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
    public static void main(String[] args) throws UnknownHostException {
        String rootPath = System.getProperty("user.dir");
        String confPath = rootPath + File.separator + "conf" + File.separator + "epos.properties";
        Configuration config = Configuration.fromFile(confPath);

        int numNodes = config.numAgents;

        EPOSRequestMessage ERM = createMessage(config, numNodes);
        requestEPOS(ERM);
    }

    public static EPOSRequestMessage createMessage(Configuration conf, int numNodes) throws UnknownHostException {
        EPOSRequestMessage eposRequestMessage = new EPOSRequestMessage();
        eposRequestMessage.numNodes = numNodes;
        return eposRequestMessage;
    }

    public static void requestEPOS(EPOSRequestMessage eposRequestMessage){
        RealClock clock=new RealClock();
        MeasurementLogger measurementLogger=new MeasurementLogger(clock);
        ZMQNetworkInterfaceFactory zmqNetworkInterfaceFactory=new ZMQNetworkInterfaceFactory(measurementLogger);

        String thisIP = "127.0.0.1";
        System.out.println( "thisIP : " + thisIP);

        ZMQAddress zmqAddress = new ZMQAddress(thisIP,54321);
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
            }

            public void messageSent(NetworkInterface networkInterface, NetworkAddress destinationAddress, Message message) {
                System.out.println("Message sent: + " +destinationAddress + " message: "+ message + " messageSize: " + message.getMessageSize());
            }


            public void interfaceUp(NetworkInterface networkInterface) {
                System.out.println( "ZmqTestClient::interfaceUp" );
            }
        });

        zmqNetworkInterface.bringUp();

        ZMQAddress destination = new ZMQAddress(thisIP ,12345);
        System.out.println( "destination : " + destination );

        zmqNetworkInterface.sendMessage(destination, eposRequestMessage);
    }
}
