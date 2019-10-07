package Communication;

import protopeer.network.Message;
import protopeer.network.zmq.ZMQAddress;

import java.io.Serializable;

public class UserRegisterMessage extends Message implements Serializable {
    int index;
    int currentRun;
    String status;
    ZMQAddress userAddress;
    ZMQAddress assignedPeerAddress;

    public UserRegisterMessage(int idx, int run, String stat, ZMQAddress userAddr){
        index = idx;
        currentRun = run;
        status = stat;
        userAddress = userAddr;
    }

    public UserRegisterMessage(int idx, int run, String stat, ZMQAddress userAddr, ZMQAddress assignedPeerAddr){
        index = idx;
        currentRun = run;
        status = stat;
        userAddress = userAddr;
        assignedPeerAddress = assignedPeerAddr;
    }
}
