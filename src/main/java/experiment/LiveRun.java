package experiment;

import agent.Agent;
import agent.ModifiableIeposAgent;
import agent.MultiObjectiveIEPOSAgent;
import agent.PlanSelector;
import agent.dataset.Dataset;
import agent.dataset.GaussianDataset;
import agent.logging.AgentLogger;
import agent.logging.AgentLoggingProvider;
import agent.logging.LoggingProvider;
import agent.logging.instrumentation.CustomFormatter;
import agent.planselection.MultiObjectiveIeposPlanSelector;
import config.Configuration;
import config.LiveConfiguration;
import data.Plan;
import data.Vector;
import loggers.EventLog;
import loggers.MemLog;
import loggers.RawLog;
import org.github.jamm.MemoryMeter;
import org.zeromq.ZMQ;
import pgpersist.PersistenceClient;
import protopeer.*;
import protopeer.ZMQExperiment;
import protopeer.network.NetworkAddress;
import protopeer.servers.bootstrap.BootstrapClient;
import protopeer.servers.bootstrap.BootstrapServer;
import protopeer.servers.bootstrap.SimpleConnector;
import protopeer.servers.bootstrap.SimplePeerIdentifierGenerator;
import treestructure.ModifiableTreeArchitecture;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;
import java.util.function.Function;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import static config.Configuration.numAgents;
import static config.Configuration.numChildren;
import static config.Configuration.numIterations;

public class LiveRun extends ZMQExperiment {

    public static void main(String[] args) {

        final int index = Integer.parseInt(args [0]);
        final int port = (args.length >= 2 ? Integer.parseInt(args [1]) : 0 );
        liveRun(index,port);
        }

        public static void liveRun(int index, int port){
            // common ZeroMQ context for the entire application
            ZMQ.Context zmqContext = ZMQ.context(1);
            runEPOSLive EPOSapp = new runEPOSLive();

            String rootPath = System.getProperty("user.dir");
            String confPath = rootPath + File.separator + "conf" + File.separator + "epos.properties";
            Configuration config = Configuration.fromFile(confPath);
            config.printConfiguration();

            LiveConfiguration liveConf = new LiveConfiguration();
            String[] args = new String[2];
            args[0] = String.valueOf(index);
            args[1]= String.valueOf(port);
            liveConf.readConfiguration(args);		// will also parse conf/dias.conf, as well as any arguments passed in the command-line in form key=value

            // set arguments
            liveConf.myIndex = index;
            liveConf.myPort = port;

            System.out.println("my index = " + liveConf.myIndex  + "(" + index + ")");
            System.out.println("my port = " + liveConf.myPort  + "(" + port + ")");
            System.out.println("my IP = " + liveConf.myIP);
            System.out.println("\n---- Configuration ---\n" );
            liveConf.printParameterFile();
            System.out.println("\n---- End Configuration ---\n" );

            protopeer.MainConfiguration			protopeer_conf = protopeer.MainConfiguration.getSingleton();


//         dataset
//        Random random = new Random(0);
//        Dataset<Vector> dataset = new GaussianDataset(16, 100, 0, 1, random);
            //Dataset<Vector> dataset = new FileVectorDataset("/Users/farzamf/Projects/EPOS-master/datasets/bicycle");

            LoggingProvider<MultiObjectiveIEPOSAgent<Vector>> loggingProvider = new LoggingProvider<>();

            for (AgentLogger logger : config.loggers) {
                loggingProvider.add(logger);
            }

            for (AgentLogger al : loggingProvider.getLoggers()) {
                al.setRun(1);
            }


            PlanSelector<MultiObjectiveIEPOSAgent<Vector>, Vector> planSelector = new MultiObjectiveIeposPlanSelector<Vector>();

            Function<Integer, Agent> createAgent = agentIdx -> {

//                List<Plan<Vector>> possiblePlans = dataset.getPlans(index);
//                List<Plan<Vector>> possiblePlans =config.getDataset(Configuration.dataset).getPlans(Configuration.mapping.get(agentIdx));
                AgentLoggingProvider<ModifiableIeposAgent<Vector>> agentLP = loggingProvider.getAgentLoggingProvider(agentIdx, 1);
//                ModifiableIeposAgent<Vector> newAgent = new ModifiableIeposAgent<Vector>(config, possiblePlans, agentLP);
                ModifiableIeposAgent<Vector> newAgent = new ModifiableIeposAgent<Vector>(config, agentLP);
                newAgent.setUnfairnessWeight(Double.parseDouble(config.weights[0]));
                newAgent.setLocalCostWeight(Double.parseDouble(config.weights[1]));
                newAgent.setPlanSelector(planSelector);
                return newAgent;

            };

            EPOSapp.runEPOS(liveConf,protopeer_conf,zmqContext,numChildren,numIterations,numAgents,createAgent,config);
//        loggingProvider.print();
        }

    }
