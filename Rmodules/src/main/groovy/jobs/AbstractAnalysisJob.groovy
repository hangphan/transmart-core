package jobs

import jobs.misc.AnalysisConstraints
import jobs.steps.ParametersFileStep
import jobs.steps.Step
import org.quartz.JobExecutionException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired

import javax.annotation.Resource

abstract class AbstractAnalysisJob {

    final Logger log = LoggerFactory.getLogger(this.class)

    static final String PARAM_ANALYSIS_CONSTRAINTS = 'analysisConstraints'

    @Autowired
    UserParameters params

    @Autowired
    AnalysisConstraints analysisConstraints

    @Resource(name = 'jobName')
    String name /* The job instance name */

    /* manually injected properties
     *********************/

    Closure updateStatus

    Closure setStatusList

    File topTemporaryDirectory

    File scriptsDirectory

    /* TODO: Used to build temporary working directory for R processing phase.
             This is called subset1_<study name>. What about subset 2? Is this
             really needed or an arbitrary directory is enough? Is it required
             due to some interaction with clinical data? */
    String studyName

    /* end manually injected properties
     *************************/

    File temporaryDirectory /* the workingDirectory */


    final void run() {
        validateName()
        setupTemporaryDirectory()

        List<Step> stepList = [
                /* we need the parameters file not just for troubleshooting
                 * but also because we need later to read the result instance
                 * ids and determine if we should create the zip with the
                 * intermediate data */
                new ParametersFileStep(
                        temporaryDirectory: temporaryDirectory,
                        params: params)
        ]
        stepList += prepareSteps()

        // build status list
        setStatusList(stepList.collect({ it.statusName }).grep())

        for (Step step in stepList) {
            if (step.statusName) {
                updateStatus step.statusName
            }

            step.execute()
        }

        updateStatus('Completed', forwardPath)
    }

    abstract protected List<Step> prepareSteps()

    abstract protected List<String> getRStatements()

    private void validateName() {
        if (!(name ==~ /^[0-9A-Za-z-]+$/)) {
            throw new JobExecutionException("Job name mangled")
        }
    }

    private void setupTemporaryDirectory() {
        temporaryDirectory = new File(new File(topTemporaryDirectory, name), 'workingDirectory')
        temporaryDirectory.mkdirs()
    }

    abstract protected getForwardPath()
}
