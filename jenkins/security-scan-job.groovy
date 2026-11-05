// Runs at Jenkins startup (init.groovy.d) — creates the order-api-snyk-scan
// pipeline job if it doesn't exist. JENKINS_HOME is ephemeral, so this keeps
// the job reproducible across pod restarts, same as create-job.groovy.
import jenkins.model.Jenkins
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jenkinsci.plugins.workflow.cps.CpsScmFlowDefinition
import hudson.plugins.git.GitSCM

def name = 'order-api-snyk-scan'
def j = Jenkins.instance

if (j.getItem(name) == null) {
    println "--> Creating pipeline job: ${name}"
    def scm = new GitSCM('https://github.com/VEaswaran/Demo_API_Validation.git')
    def job = j.createProject(WorkflowJob, name)
    job.definition = new CpsScmFlowDefinition(scm, 'jenkins/Jenkinsfile.snyk')
    job.save()
    println "--> Pipeline job ${name} created successfully."
} else {
    println "--> Pipeline job ${name} already exists."
}
