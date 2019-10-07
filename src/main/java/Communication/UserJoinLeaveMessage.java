package Communication;

import protopeer.network.Message;
import protopeer.network.zmq.ZMQAddress;

import java.io.Serializable;

public class UserJoinLeaveMessage extends Message implements Serializable {
    int userIndex;
    int currentRun;
    ZMQAddress userAddress;
    ZMQAddress peerAddress;
    String joinLeaveStatus; // true is join, false is leave

    public UserJoinLeaveMessage(int idx, int run, String stat, ZMQAddress userAddr){
        userIndex = idx;
        currentRun = run;
        joinLeaveStatus = stat;
        userAddress = userAddr;
    }

    public UserJoinLeaveMessage(int idx, int run, String stat, ZMQAddress userAddr, ZMQAddress peerAddr){
        userIndex = idx;
        currentRun = run;
        joinLeaveStatus = stat;
        userAddress = userAddr;
        peerAddress = peerAddr;
    }

    public UserJoinLeaveMessage(String stat, int run){
        joinLeaveStatus = stat;
        currentRun = run;
    }
}
