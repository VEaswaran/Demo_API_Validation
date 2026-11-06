import jenkins.model.Jenkins
import org.jenkinsci.plugins.workflow.libs.GlobalLibraries
import org.jenkinsci.plugins.workflow.libs.LibraryConfiguration
import org.jenkinsci.plugins.workflow.libs.SCMSourceRetriever
import jenkins.plugins.git.GitSCMSource
import jenkins.plugins.git.traits.BranchDiscoveryTrait

def libName = 'enterprise-cicd'
def repoUrl = 'https://github.com/VEaswaran/jenkins-shared-library.git'

def globalLibs = GlobalLibraries.get()
def existing = globalLibs.libraries.find { it.name == libName }

if (!existing) {
    println "--> Registering Global Shared Library: ${libName} (${repoUrl})"
    def scm = new GitSCMSource(repoUrl)
    scm.traits = [new BranchDiscoveryTrait()]
    def retriever = new SCMSourceRetriever(scm)
    def libConfig = new LibraryConfiguration(libName, retriever)
    libConfig.defaultVersion = 'main'
    libConfig.implicit = false
    libConfig.allowVersionOverride = true
    def updated = new ArrayList(globalLibs.libraries)
    updated.add(libConfig)
    globalLibs.libraries = updated
    globalLibs.save()
    println "--> Global Shared Library '${libName}' registered successfully."
} else {
    println "--> Global Shared Library '${libName}' already registered."
}
