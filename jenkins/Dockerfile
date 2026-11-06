# Extends the existing jenkins-gke image with pipeline UI plugins
# (Stage View + Blue Ocean). Built on top of the current image so the
# embedded tools (gcloud/kubectl/docker) and job bootstrap stay intact.
FROM us-central1-docker.pkg.dev/project-d7b18774-cce1-4b87-a4a/order-api/jenkins-gke:lts-jdk21

COPY plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli -f /usr/share/jenkins/ref/plugins.txt

USER root
COPY security-scan-job.groovy /usr/share/jenkins/ref/init.groovy.d/security-scan-job.groovy
COPY shared-library.groovy /usr/share/jenkins/ref/init.groovy.d/shared-library.groovy
RUN chown jenkins:jenkins /usr/share/jenkins/ref/init.groovy.d/*.groovy
USER jenkins
