package Communication;

import protopeer.network.NetworkAddress;
import protopeer.network.zmq.ZMQAddress;

public class EPOSPeerStatus {
    int index;
    int run;
    int leaveRun=Integer.MAX_VALUE;
    String status;
    boolean isleaf;
    NetworkAddress address;
    public EPOSPeerStatus(int idx, int currentRun, String stat, boolean leaf, NetworkAddress addr){
        index = idx;
        run = currentRun;
        status = stat;
        isleaf = leaf;
        address = addr;
    }
}
