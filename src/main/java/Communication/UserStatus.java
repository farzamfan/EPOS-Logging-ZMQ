package Communication;

import protopeer.network.NetworkAddress;
import protopeer.network.zmq.ZMQAddress;

public class UserStatus {
    int index;
    int run;
    int leaveRun = Integer.MAX_VALUE;
    String status;
    String planStatus = "noNewPlans";
    String weightStatus = "noNewWeights";
    ZMQAddress assignedPeerAddress;
    ZMQAddress userAddress;

    public UserStatus(int idx, int currentRun, String stat, ZMQAddress userAddr){
        index = idx;
        run = currentRun;
        status = stat;
        userAddress = userAddr;
    }

    public UserStatus(int idx, int currentRun, String stat, ZMQAddress userAddr, ZMQAddress peerAddress){
        index = idx;
        run = currentRun;
        status = stat;
        userAddress = userAddr;
        assignedPeerAddress = peerAddress;
    }
}
