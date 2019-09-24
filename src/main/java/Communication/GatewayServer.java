package Communication;

import config.Configuration;
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

public class GatewayServer {

    public static void main(String[] args) throws UnknownHostException {
        listen();
    }

    public static void listen() throws UnknownHostException {

        String rootPath = System.getProperty("user.dir");
        String confPath = rootPath + File.separator + "conf" + File.separator + "epos.properties";
        Configuration config = Configuration.fromFile(confPath);

        RealClock clock=new RealClock();
        MeasurementLogger measurementLogger=new MeasurementLogger(clock);
        ZMQNetworkInterfaceFactory zmqNetworkInterfaceFactory=new ZMQNetworkInterfaceFactory(measurementLogger);

        String		thisIP = "127.0.0.1";
        System.out.println( "thisIP : " + thisIP );

        ZMQAddress zmqAddress = new ZMQAddress(thisIP,12345);
        System.out.println("zmqAddress : " + zmqAddress );

        ZMQNetworkInterface zmqNetworkInterface = (ZMQNetworkInterface)zmqNetworkInterfaceFactory.createNewNetworkInterface(measurementLogger,zmqAddress);
        zmqNetworkInterface.addNetworkListener(new NetworkListener()
        {

            public void exceptionHappened(NetworkInterface networkInterface, NetworkAddress remoteAddress,
                                          Message message, Throwable cause)
            {
                System.out.println( "ZmqTestServer::exceptionHappened" + cause );
                cause.printStackTrace();
            }

            public void interfaceDown(NetworkInterface networkInterface)
            {
                System.out.println( "ZmqTestServer::interfaceDown" );

            }

            private int runningPeers=0;
            private int finishedPeers=0;
            private int peersWithPlansSet=0;
            private int peersWithTreeViewSet=0;
            private int numPeers=config.numAgents;
            private long previousTime=System.currentTimeMillis();

            public void messageReceived(NetworkInterface networkInterface, NetworkAddress sourceAddress,
                                        Message message)
            {
                System.out.println("Message received: + " +sourceAddress + " message: "+ message);
                if (message instanceof EPOSRequestMessage){
                    EPOSRequestMessage eposRequestMessage = (EPOSRequestMessage) message;
                    numPeers = eposRequestMessage.numNodes;
                    for (int i=0;i<eposRequestMessage.numNodes;i++){
                        System.out.println("liveNode "+i+" initiated");
                        String command = "java -jar tutorial.jar " + String.valueOf(i) +" "+String.valueOf(3000+i);
                        try {
                            Runtime.getRuntime().exec(command);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
                if (message instanceof InformGateway){
                    InformGateway informGateway = (InformGateway) message;
                    if (informGateway.status.equals("treeViewSet")) {
                        System.out.println("peer: "+informGateway.peerID+" is "+ informGateway.status);
                        peersWithTreeViewSet++;
                    }
                    if (informGateway.status.equals("plansSet")) {
                        System.out.println("peer: "+informGateway.peerID+" is "+ informGateway.status);
                        peersWithPlansSet++;
                    }
                    if (informGateway.status.equals("running")) {
                        System.out.println("peer: "+informGateway.peerID+" is "+ informGateway.status);
                        runningPeers++;
                    }
                    if (informGateway.status.equals("finished")) {
                        System.out.println("peer: "+informGateway.peerID+" is "+ informGateway.status);
                        finishedPeers++;
                    }
                }
                if (peersWithTreeViewSet == numPeers){
                    System.out.println("---");
                    System.out.println("all peers have their treeView set");
                    System.out.println("---");
                    peersWithTreeViewSet=0;
                }
                if (peersWithPlansSet == numPeers){
                    System.out.println("---");
                    System.out.println("all peers have their plans set");
                    System.out.println("---");
                    peersWithPlansSet=0;
                }
                if (finishedPeers == numPeers){
                    System.out.println("---");
                    System.out.println("EPOS Successfully executed");
                    System.out.println("---");
                }
                if (runningPeers == numPeers){
                    System.out.println("---");
                    System.out.println("All nodes running, sending ready to run message");
                    System.out.println("---");
                    for (int i=0;i<config.numAgents;i++) {
                        ZMQAddress destination = new ZMQAddress(thisIP ,3000+i);
                        zmqNetworkInterface.sendMessage(destination, new ReadyToRunMessage(i));
                    }
                    runningPeers = 0;
                }
            }

            public void messageSent(NetworkInterface networkInterface, NetworkAddress destinationAddress, Message message)
            {
                System.out.println("Message sent: + " +destinationAddress + " message: "+ message);
            }

            public void interfaceUp(NetworkInterface networkInterface)
            {
                System.out.println( "ZmqTestServer::interfaceUp" );

            }

        });
        zmqNetworkInterface.bringUp();
        System.out.println( "ZmqTestServer: milestone 1" );
    }
}
