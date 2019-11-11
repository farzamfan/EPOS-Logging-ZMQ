package Communication;

import protopeer.network.NetworkAddress;
import protopeer.network.zmq.ZMQAddress;

public class EPOSPeerStatus {
    int index;
    int run;
    int initRun = 0;
    int leaveRun=Integer.MAX_VALUE;
    String status;
    boolean isleaf;
    NetworkAddress address;
    int peerPort;
    public EPOSPeerStatus(int idx, int currentRun, String stat, boolean leaf, NetworkAddress addr, int port){
        index = idx;
        run = currentRun;
        status = stat;
        isleaf = leaf;
        address = addr;
        peerPort = port;
    }
}
