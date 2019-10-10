package Communication;

import data.Plan;
import data.Vector;
import protopeer.network.Message;
import java.io.Serializable;
import java.util.List;

public class WeightSetMessage extends Message implements Serializable {
    public String status;
    public double alpha;
    public double beta;

    public WeightSetMessage(String stat){
        status = stat;
    }
    public WeightSetMessage(String stat, double a, double b){
        status = stat;
        alpha = a;
        beta = b;
    }
}
