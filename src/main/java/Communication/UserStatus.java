package Communication;

import protopeer.network.NetworkAddress;
import protopeer.network.zmq.ZMQAddress;

public class UserStatus {
    int index;
    String status;
    ZMQAddress assignedPeerAddress;
    ZMQAddress userAddress;

    public UserStatus(int idx, String stat, ZMQAddress userAddr){
        index = idx;
        status = stat;
        userAddress = userAddr;
    }

    public UserStatus(int idx, String stat, ZMQAddress userAddr, ZMQAddress peerAddress){
        index = idx;
        status = stat;
        userAddress = userAddr;
        assignedPeerAddress = peerAddress;
    }
}
