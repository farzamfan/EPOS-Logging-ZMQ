package Communication;

import data.Plan;
import data.Vector;
import protopeer.network.Message;
import java.io.Serializable;
import java.util.List;

public class PlanSetMessage extends Message implements Serializable {
    public List<Plan<Vector>> possiblePlans;
}
