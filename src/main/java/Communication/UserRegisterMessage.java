package Communication;

import protopeer.network.Message;
import protopeer.network.zmq.ZMQAddress;

import java.io.Serializable;

public class UserRegisterMessage extends Message implements Serializable {
    int index;
    String status;
    ZMQAddress userAddress;
    ZMQAddress assignedPeerAddress;

    public UserRegisterMessage(int idx, String stat, ZMQAddress userAddr){
        index = idx;
        status = stat;
        userAddress = userAddr;
    }

    public UserRegisterMessage(int idx, String stat, ZMQAddress userAddr, ZMQAddress assignedPeerAddr){
        index = idx;
        status = stat;
        userAddress = userAddr;
        assignedPeerAddress = assignedPeerAddr;
    }
}
