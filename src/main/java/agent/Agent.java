/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package agent;

import afu.org.checkerframework.checker.oigj.qual.O;
import agent.logging.GlobalCostLogger;
import data.Plan;
import func.CostFunction;
import func.PlanCostFunction;
import agent.logging.AgentLoggingProvider;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.zeromq.ZMQ;
import pgpersist.PersistenceClient;
import pgpersist.SqlDataItem;
import pgpersist.SqlInsertTemplate;
import protopeer.BasePeerlet;
import protopeer.Configuration;
import protopeer.measurement.MeasurementLog;
import protopeer.measurement.MeasurementLoggerListener;
import protopeer.time.Timer;
import protopeer.time.TimerListener;
import protopeer.util.quantities.Time;
import data.DataType;

/**
 * An agent that performs combinatorial optimization.
 * 
 * @author Peter
 * @param <V> the type of the data this agent should handle
 */
public abstract class Agent<V extends DataType<V>> extends BasePeerlet  implements java.io.Serializable {

    // misc
    final Random 							random 				= 		new Random();

    // logging
    private final transient AgentLoggingProvider 		loggingProvider;
    transient Logger 									logger 				= 		Logger.getLogger(Agent.class.getName());
    
    // timings
    protected final int						bootstrapPeriod		=	3000;	//ms
    protected final int						activeStatePeriod	=	2000;	//ms

    // combinatorial optimization variables
    Plan<V> 								selectedPlan;
    int										selectedPlanID;
    V 										globalResponse;
    final transient List<Plan<V>> 					possiblePlans 		= 	new ArrayList<>();
    final transient CostFunction<V> 					globalCostFunc;
    final transient PlanCostFunction<V> 				localCostFunc;

    //For DBLogging
    public transient PersistenceClient persistenceClient;

    // logging stuff
    private int 							numTransmitted;
    private int 							numComputed;
    private int 							cumTransmitted;
    private int 							cumComputed;
    
    int										iterationAfterReorganization =	0;	// iteration at which reorganization was requested and executed

    /**
     * Initializes the agent with the given combinatorial optimization problem
     * definition
     *
     * @param possiblePlans the possible plans of this agent
     * @param globalCostFunc the global cost function
     * @param localCostFunc the local cost function
     * @param loggingProvider the logger for the experiment
     */
    public Agent(List<Plan<V>> possiblePlans, CostFunction<V> globalCostFunc, PlanCostFunction<V> localCostFunc, AgentLoggingProvider<? extends Agent> loggingProvider) {
        this.possiblePlans.addAll(possiblePlans);
//        if(localCostFunc != null) {
//            this.possiblePlans.sort((plan1, plan2) -> (int)Math.signum(localCostFunc.calcCost(plan1) - localCostFunc.calcCost(plan2)));
//        }
        this.globalCostFunc = globalCostFunc;
        this.localCostFunc = localCostFunc;
        this.loggingProvider = loggingProvider;
    }

    /**
     * Initializes the agent with the given combinatorial optimization problem
     * definition
     *
     * @param possiblePlans the possible plans of this agent
     * @param globalCostFunc the global cost function
     * @param localCostFunc the local cost function
     * @param loggingProvider the logger for the experiment
     * @param seed the seed for the RNG used by this agent
     */
    public Agent(List<Plan<V>> possiblePlans, CostFunction<V> globalCostFunc, PlanCostFunction<V> localCostFunc, AgentLoggingProvider<? extends Agent> loggingProvider, long seed) {
        this(possiblePlans, globalCostFunc, localCostFunc, loggingProvider);
        random.setSeed(seed);
    }

    V createValue() {
        return possiblePlans.get(0).getValue().cloneNew();
    }

    Plan<V> createPlan() {
        return possiblePlans.get(0).cloneNew();
    }

    @Override
    public void start() {
        loggingProvider.init(Agent.this);
        this.runBootstrap();
        scheduleMeasurements();
//        testCustomLog();
    }

    @Override
    public void stop() {
    }

    public void addPersistenceClient( PersistenceClient	persistenceClient )
    {
//        if( persistenceClient == null ) {return;}
        this.persistenceClient = persistenceClient;
        System.out.println("persistenceClient set");
    }

    public Plan getSelectedPlan() {
        return selectedPlan;
    }
    
    public int getSelectedPlanID() {
    	return this.selectedPlanID;
    }

    public V getGlobalResponse() {
        return globalResponse;
    }

    public List<Plan<V>> getPossiblePlans() {
        return possiblePlans;
    }

    public CostFunction<V> getGlobalCostFunction() {
        return globalCostFunc;
    }

    public PlanCostFunction<V> getLocalCostFunction() {
        return localCostFunc;
    }

    public int getIteration() {
        return 0;
    }

    public int getNumIterations() {
        return 1;
    }
    
    /**
     * Returns iterations at which reorganization was requested and executed.
     * @return
     */
    public int getIterationAfterReorganization() {
    	return this.iterationAfterReorganization;
    }

    public boolean isRepresentative() {
        return getPeer().getIndexNumber() == 0;
    }

    public int getNumTransmitted() {
        return numTransmitted;
    }
    
    public void setNumTransmitted(int val) {
    	this.numTransmitted = val;
    }

    public int getNumComputed() {
        return numComputed;
    }
    
    public void setNumComputed(int val) {
    	this.numComputed = val;
    }

    public int getCumTransmitted() {
        return cumTransmitted;
    }
    
    public void setCumTransmitted(int val) {
    	this.cumTransmitted = val;
    }

    public int getCumComputed() {
        return cumComputed;
    }
    
    public void setCumComputed(int val) {
    	this.cumComputed = val;
    }

    public PersistenceClient getPersistenceClient() {return persistenceClient; }

//    private void runBootstrap() {
//        Timer loadAgentTimer = getPeer().getClock().createNewTimer();
//        loadAgentTimer.addTimerListener(new TimerListener() {
//            public void timerExpired(Timer timer) {
//                runActiveState();
//            }
//        });
//        loadAgentTimer.schedule(Time.inMilliseconds(this.bootstrapPeriod));
//    }

    protected abstract void runBootstrap();

    protected abstract void runActiveState();

//    void runActiveState() {
//        Timer loadAgentTimer = getPeer().getClock().createNewTimer();
//        loadAgentTimer.addTimerListener((Timer timer) -> {
//            System.out.println("entered active state: peerIndex: "+this.getPeer().getIndexNumber());
//            initPhase();
//            runPhase();
//        });
//        loadAgentTimer.schedule(Time.inMilliseconds(this.activeStatePeriod));
//    }

    private void initPhase() {
        loggingProvider.init(this);
        
        this.log(Level.FINER, "initPhase()");

        numTransmitted = 0;
        numComputed = 0;
        cumTransmitted = 0;
        cumComputed = 0;
    }

    abstract void runPhase();

    private void scheduleMeasurements() {

//        getPeer().getMeasurementLogger().addMeasurementLoggerListener((MeasurementLog log, int epochNumber) -> {
//            loggingProvider.log(log, epochNumber, this);
//        });
//
        getPeer().getMeasurementLogger().addMeasurementLoggerListener(new MeasurementLoggerListener()
        {
            public String getId() {
                return "EPOS"; }

            public void measurementEpochEnded(MeasurementLog log, int epochNumber){
                log.log(epochNumber,this,numComputed);
            }
        });
    }
    
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //																									BY JOVAN: //
    
    public void reset() {
    	this.numTransmitted 	= 	0;
        this.numComputed 		= 	0;
        this.cumTransmitted		= 	0;
        this.cumComputed 		= 	0;
    }
    
    void log(Level level, String message) {
    	this.logger.log(level, "NODE: " + this.getPeer().getIndexNumber() + message);
    }
    
    public boolean isIterationAfterReorganization() {
    	return this.getIteration() == 0;
    }
    
    public int getNumReorganizations() {
    	return 0;
    }

    public void testCustomLog(){

        // -------------------
        // -- Custom Logger --
        // -------------------

        System.out.println("hereNow");

        // step 1. create the table in SQL -> sql/definitions/customlog.sql
        // step 2.  send the template to the Peristence daemon, so that it knows how to write the data to the database
        final String 				sql_insert_template_custom  = "INSERT INTO customlog(dt,run,iteration,dim_0,dim_1) VALUES({dt}, {run}, {iteration}, {dim_0}, {dim_1});";
        // step 3. send that string to the daemon
        persistenceClient.sendSqlInsertTemplate( new SqlInsertTemplate( "custom", sql_insert_template_custom ) );


        // -----------
        // -- start --
        // -----------



        // MockClient
        // requires 3 arguments:
        // 1. port for sending messages to daemon

        // parse arguments
        // parse listen port
        int sleepTimeMilliSeconds = 10;
        final ArrayList<String>				someList = new ArrayList<String>();
        long					counter = 0L;

        boolean 				b_loop = true;

        LinkedHashMap<String,String> outputMap = new LinkedHashMap<String,String> ();
        SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Random randomNumberGenerator = new Random();

        // TODO: create a list and add to memlog

        // Start
//        RawLog.print(1,"Hello!");
//        EventLog.logEvent("TestBasicLog", "main", "loop start");


        while( b_loop )
        {
            ++counter;

            // wait
            try
            {
//                RawLog.print(1,"Hello, oupss... any btw, my counter is " + Long.toString(counter));
//                EventLog.logEvent("TestBasicLog", "main", "counter", Long.toString(counter));

                // send some data to the custom log
                // fields: dt,run,iteration,dim_0,dim_1
                LinkedHashMap<String,String>          record = new LinkedHashMap<String,String>();

                record.put("dt", "'" + dateFormatter.format( System.currentTimeMillis() ) + "'" );
                record.put("run", Long.toString(counter) );
                record.put("iteration", Long.toString(counter + 1000));
                record.put("dim_0", "100" );
                record.put("dim_1", "101" );

                persistenceClient.sendSqlDataItem( new SqlDataItem( "custom", record ) );


                // add some data to the list, so that we can see it's memory increasing
                someList.add(Long.toString(counter));

                // wait a bit
                Thread.currentThread().sleep(sleepTimeMilliSeconds);
            }
            catch (InterruptedException e)
            {
                b_loop = false;
            }


        }// whi
    }
}
