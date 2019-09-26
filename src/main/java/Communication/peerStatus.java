package Communication;

import protopeer.network.NetworkAddress;
import protopeer.network.zmq.ZMQAddress;

public class peerStatus {
    int index;
    String status;
    boolean isleaf;
    NetworkAddress address;
    public peerStatus(int idx, String stat, boolean leaf, NetworkAddress addr){
        index = idx;
        status = stat;
        isleaf = leaf;
        address = addr;
    }
}
