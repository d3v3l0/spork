package org.apache.pig.backend.hadoop.executionengine.mapReduceLayer;

import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.mapred.JobClient;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.jobcontrol.Job;
import org.apache.hadoop.mapred.jobcontrol.JobControl;

import org.apache.pig.backend.executionengine.ExecException;
import org.apache.pig.backend.executionengine.ExecutionEngine;
import org.apache.pig.backend.hadoop.datastorage.ConfigurationUtil;
import org.apache.pig.impl.PigContext;
import org.apache.pig.backend.hadoop.datastorage.ConfigurationUtil;
import org.apache.pig.backend.hadoop.executionengine.mapReduceLayer.plans.MROperPlan;
import org.apache.pig.backend.hadoop.executionengine.mapReduceLayer.plans.MRPrinter;
import org.apache.pig.backend.hadoop.executionengine.physicalLayer.plans.PhysicalPlan;
import org.apache.pig.impl.plan.PlanException;
import org.apache.pig.impl.plan.VisitorException;
import org.apache.pig.impl.util.ConfigurationValidator;


public class LocalLauncher extends Launcher{
    private static final Log log = LogFactory.getLog(LocalLauncher.class);
    
    @Override
    public boolean launchPig(
            PhysicalPlan php,
            String grpName,
            PigContext pc) throws PlanException, VisitorException,
                                  IOException, ExecException,
                                  JobCreationException {
        long sleepTime = 500;
        MROperPlan mrp = compile(php, pc);
        
        ExecutionEngine exe = pc.getExecutionEngine();
        Properties validatedProperties = ConfigurationValidator.getValidatedProperties(exe.getConfiguration());
        Configuration conf = ConfigurationUtil.toConfiguration(validatedProperties);
        conf.set("mapred.job.tracker", "local");
        JobClient jobClient = new JobClient(new JobConf(conf));

        JobControlCompiler jcc = new JobControlCompiler();
        
        JobControl jc = jcc.compile(mrp, grpName, conf, pc);
        
        
        int numMRJobs = jc.getWaitingJobs().size();
        
        new Thread(jc).start();

        double lastProg = -1;
        while(!jc.allFinished()){
            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {}
            double prog = calculateProgress(jc, jobClient)/numMRJobs;
            if(prog>lastProg)
                log.info((int)(prog * 100) + "% complete");
            lastProg = prog;
        }
        // Look to see if any jobs failed.  If so, we need to report that.
        List<Job> failedJobs = jc.getFailedJobs();
        if (failedJobs != null && failedJobs.size() > 0) {
            log.error("Map reduce job failed");
            for (Job fj : failedJobs) {
                log.error(fj.getMessage());
                getStats(fj, jobClient);
            }
            return false;
        }

        List<Job> succJobs = jc.getSuccessfulJobs();
        if(succJobs!=null)
            for(Job job : succJobs){
                getStats(job,jobClient);
            }

        jc.stop(); 
        
        return true;
    }

    @Override
    public void explain(
            PhysicalPlan php,
            PigContext pc,
            PrintStream ps) throws PlanException, VisitorException,
                                   IOException {
        log.trace("Entering LocalLauncher.explain");
        MROperPlan mrp = compile(php, pc);

        MRPrinter printer = new MRPrinter(ps, mrp);
        printer.visit();
    }
 
    private MROperPlan compile(
            PhysicalPlan php,
            PigContext pc) throws PlanException, IOException, VisitorException {
        MRCompiler comp = new MRCompiler(php, pc);
        comp.randomizeFileLocalizer();
        comp.compile();
        MROperPlan plan = comp.getMRPlan();
        String prop = System.getProperty("pig.exec.nocombiner");
        if (!("true".equals(prop)))  {
            CombinerOptimizer co = new CombinerOptimizer(plan);
            co.visit();
        }
        // figure out the type of the key for the map plan
        // this is needed when the key is null to create
        // an appropriate NullableXXXWritable object
        KeyTypeDiscoveryVisitor kdv = new KeyTypeDiscoveryVisitor(plan);
        kdv.visit();
        return plan;
    }

    //A purely testing method. Not to be used elsewhere
    public boolean launchPigWithCombinePlan(PhysicalPlan php,
            String grpName, PigContext pc, PhysicalPlan combinePlan) throws PlanException,
            VisitorException, IOException, ExecException, JobCreationException {
        long sleepTime = 500;
        MRCompiler comp = new MRCompiler(php, pc);
        comp.compile();

        Configuration conf = new Configuration();
        conf.set("mapred.job.tracker", "local");
        JobClient jobClient = new JobClient(new JobConf(conf));

        MROperPlan mrp = comp.getMRPlan();
        if(mrp.getLeaves().get(0)!=mrp.getRoots().get(0))
            throw new PlanException("Unsupported configuration to test combine plan");
        
        MapReduceOper mro = mrp.getLeaves().get(0);
        mro.combinePlan = combinePlan;
        
        JobControlCompiler jcc = new JobControlCompiler();

        JobControl jc = jcc.compile(mrp, grpName, conf, pc);

        int numMRJobs = jc.getWaitingJobs().size();

        new Thread(jc).start();

        double lastProg = -1;
        while (!jc.allFinished()) {
            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
            }
            double prog = calculateProgress(jc, jobClient) / numMRJobs;
            if (prog > lastProg)
                log.info((int)(prog * 100) + "% complete");
            lastProg = prog;
        }
        lastProg = calculateProgress(jc, jobClient) / numMRJobs;
        if (isComplete(lastProg))
            log.info("Completed Successfully");
        else {
            log.info("Unsuccessful attempt. Completed " + lastProg * 100
                    + "% of the job");
            List<Job> failedJobs = jc.getFailedJobs();
            if (failedJobs == null)
                throw new ExecException(
                        "Something terribly wrong with Job Control.");
            for (Job job : failedJobs) {
                getStats(job, jobClient);
            }
        }
        List<Job> succJobs = jc.getSuccessfulJobs();
        if (succJobs != null)
            for (Job job : succJobs) {
                getStats(job, jobClient);
            }

        jc.stop();

        return isComplete(lastProg);
    }
}
