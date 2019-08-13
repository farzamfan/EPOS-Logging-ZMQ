DIAS-Logging-System
edward - 2019-04-23

# ------------
# introduction
# ------------

The DIAS-Logging-System is primarily used to log activity from a DIAS network of peers to a database. 

However, the DIAS-Logging-System can also be used for any Protopeer-based application (e.g EPOS), as well as any other Java application.

Currently there are three supported mechanisms for logging data.
	1. Rawlog, free-form text logging, used as a replacement to System.out.print or log4j
	
	2. Memlog, used to sample memory footprint of Java objects at regular intervals
    	-	Performs deep traversal of objects in order to obtain total memory size of collections as well as the contained objects, recursively
	
	3. Eventlog, used to understand event sequences within peers and/or amongst sets of peers
    	- This is basically a more structured form of logging, which works well with scripts such as SQL or Python for more complex analyses

All the logging information is stored to Postgres inside tables, which leverages several advantanges of relation databases:
	- Fast retrieval: Indexing and partitioning can be
	- Join: logs that originate from different components, threads or peers within a network can be joined using standard SQL, thus provided support for analysing complex event patterns
	

These tables need to be created once only (step 3 below).

Currently supported database is Postgres (versions 9, 10 and 11).

-----------------
-- quick-start --
-----------------

Follow the instructions below to setup the DIAS-Logging-System onto your computer/server. Currently tested on OSX and Linux Ubuntu.

For more detailed explanations, see doc/DIAS-Logging-System Documentation v0.2.pdf

---------------------------------------
-- Step 0. Check system Requirements --
---------------------------------------
	- OSX or Ubuntu 14 or greater
	- 1Gb Ram

----------------------------------------------------------------------
-- Step 1. Currently, a PostgreSQL database is used for persistence --
---------------------------------------------------------------------- 

If you don't have Postgres installed, follow these steps to install Postgres onto your computer/server; else skip to Step 2

-- 1.a Ubuntu 14 or greater

	sudo apt-get update
	sudo apt-get install postgresql

	# the Postgres server will start automatically 

-- 1.b OSX
	download an installer from EntrepriseDB
		- https://www.enterprisedb.com/downloads/postgres-postgresql-downloads
		- latest version is currently postgresql-11.2-2-osx.dmg

	open the disk image

	run the app; after a while, a Setup wizard will appear

	accept all defaults, except for the password

	important: set password to postgres, unless you wish to choose a different password and then modify the DIAS-Logging-System configuration accordingly

	default port should be set to 5432; if a different value is recommended by the installer you should accept it, and then modify the DIAS-Logging-System configuration accordingly

	# [optional]: create a symbolic link to psql, so that you can interact with the database
	# replace {Postgres.Version} with the version that you installed, e.g 9.5, or 11.2, etc
	sudo ln -s /Library/PostgreSQL/{Postgres.Version}/bin/psql /bin/psql
	

# --1.c test logging into the database
# this will prompt for the password that you entered above
cd /tmp
sudo -u postgres psql


--------------------------------
-- Step 2. Create a database  --
--------------------------------

Login to Postgres using the psql shell provided with Postgres
cd /tmp
sudo -u postgres psql

CREATE DATABASE dias;

-- connect to the dias database
\c dias

--> You are now connected to database "dias" as user "postgres".

------------------------------------------------------
-- Step 3. Create Postgres tables to store messages --
------------------------------------------------------

All the logging information is stored to Postgres inside tables. These tables need to be created once only. 

3.a Core logging tables

	The SQL for the table definitions is located in this folder: DIAS-Logging-System/sql/definitions
	
	Copy each fo the following .sql files and paste into psql:
 		- eventlog.sql
 		- memlog.sql
 		- rawlog.sql
 		- pss.sql
 
3.b [optional] DIAS logging tables
If you are using DIAS and wish to log DIAS specific information to the the database, you will also neeed to create the following tables.

DIAS can be found here: https://github.com/epournaras/DIAS-Development/tree/pilot.2017.f

	The SQL for the table definitions is located in this folder: DIAS-Development/sql/definitions
		- aggregation.sql
		- aggregation_plot.sql
		- aggregation_event.sql
		- aggregation_event_rrd.sql
		- DIAS-visualization-db.sql
		- msgs.sql
		- sessions.sql

---------------------------
-- Step 4. launch daemon --
---------------------------

1. Launch the daemon that listens for messages to be persisted to the database
# note: the daemon is launched inside a new screen session, so that you let the daemon run in the background
cd DIAS-Logging-System
./start.daemon.sh deployments/localhost


----------------------------------
-- [optional] Step 5. run tests --
----------------------------------

a sample program is provided that shows how to use the core logging tables: src/TestBasicLog.java

currently, src/TestBasicLog.java demonstrates the following functionality:
	- RawLog

to launch the program, simply run the script start.test.sh

the program will generate random data that will be logged to the database

1. launch script; opens a new screen session
./start.test.sh

2. observe the data being written
# if you switch back to the daemon screen launched above, you will see the data being written to the database
# this is indicated by the . updates to screen

3. query the data in the database
from the psql shell

# show last 10 entries
SELECT * FROM rawlog ORDER BY seq_id DESC LIMIT 10;


# show last 10 warning or error messages
SELECT * FROM rawlog WHERE error_level >= 2 ORDER BY seq_id DESC LIMIT 10;


# show last 10 messages for peers 5 and 6 or error messages
SELECT * FROM rawlog WHERE peer IN (5,6) ORDER BY seq_id DESC LIMIT 10;


4. To stop the test, press ctrl+c

----------------------
-- Step 6. stop daemon
----------------------

1. To stop the daemon, press ctrl+c


---------------------
-- Step 7. next steps
---------------------

Look at the following samples to see how you could integrate DIAS-Logging-System into your applucation:
	- src/TestBasicLog.java



