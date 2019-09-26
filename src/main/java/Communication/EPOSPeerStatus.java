package Communication;

import protopeer.network.NetworkAddress;
import protopeer.network.zmq.ZMQAddress;

public class EPOSPeerStatus {
    int index;
    String status;
    boolean isleaf;
    NetworkAddress address;
    public EPOSPeerStatus(int idx, String stat, boolean leaf, NetworkAddress addr){
        index = idx;
        status = stat;
        isleaf = leaf;
        address = addr;
    }
}
