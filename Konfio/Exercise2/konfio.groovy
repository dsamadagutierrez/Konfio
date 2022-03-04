def call(body) {
    def pipelineParams= [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = pipelineParams
    body()

	pipeline {
		agent {
			label {
				label 'docker-slave'
			}
		}
		environment {
			APP_NAME = 'konfio'
			BITBUCKET_PROJECT = 'konfio'
			BRANCH_TAG = "${GIT_BRANCH_NAME ? GIT_BRANCH_NAME.trim() : (TAG ? TAG.trim() : 'no_branch_no_tag')}"
			SONARQUBE_BRANCH = "${GIT_BRANCH_NAME && GIT_BRANCH_NAME.trim() == 'master' ? 'master' : 'develop'}"
		}
		stages {
			stage('Configure pipeline') {
				steps {			
					script {
						config = utils.getConfigByNS(pipelineParams.targetNS)
						dockerImage = 
						  "${config.account}.dkr.ecr.${config.region}.amazonaws.com/${APP_NAME}-${pipelineParams.targetNS}"
					}
				}
			}
 			stage('Checkout project git') {
				steps {
					container('mgmt-utils') {
						withCredentials([
							usernamePassword(credentialsId: 'BBsvcAccnt', usernameVariable: 'BB_USERNAME', passwordVariable: 'BB_PASSWORD'),
							]) {
							script {	
							encodedPasswd = java.net.URLEncoder.encode(BB_PASSWORD, "UTF-8")
									sh """
										git clone -b ${BRANCH_TAG} --depth 1 https://${BB_USERNAME}:${encodedPasswd}@bitbucket.org/agrofydev/${BITBUCKET_PROJECT}.git builds
										git log -1 --pretty=format:"%n%h - %an, %ar: %s%n%n"
										rsync -r builds/ .
									"""
							}			
						}
					}
				}
			}	
			
			stage('Build docker image') {
				steps {
					container('dind') {
						script {
							image = docker.build(dockerImage, "${pipelineParams.buildArgs} .")
						}
					}
				}
			}
			stage('Push docker image') {
				steps {
					container('dind') {
						withAWS(region: config.region, credentials: config.awsCred) {
							sh ecrLogin()
							script {
								random_suffix = utils.getRandomString(5)
								image.push("${BUILD_NUMBER}-${random_suffix}")
								image.push('latest')
							}
						}
					}
				}
			}
			stage('Upload new bundle to S3') {   
				steps {
					container('dind') {
						sh """
						  mkdir -p .next/static
						  docker create --name container ${dockerImage}:${BUILD_NUMBER}-${random_suffix}
						  docker cp container:/.next/static .next/static
						  docker rm -f container
						"""
					}
					container('mgmt-utils') {
						withAWS(credentials: config.awsCred) {
							script {
								sh """
								  aws s3 sync .next/static s3://agrofy-bundles-${config.envType}/${APP_NAME}-${pipelineParams.targetNS}/_next \\
								    --metadata-directive REPLACE --cache-control "max-age=31536000" --acl public-read
								"""
							}
						}
					}
				}
			}
			stage('Deploy EKS') {   
				steps {
					container('mgmt-utils') {
						withAWS(credentials: config.awsCred) {
							script {
								deployK8s cluster: config.targetCluster, namespace: pipelineParams.targetNS, 
								  app: APP_NAME, image: dockerImage, tag: "${BUILD_NUMBER}-${random_suffix}"
							}
						}
					}
				}
			}
			stage('Remove old bundle from S3') {   
				steps {
					container('mgmt-utils') {
						withAWS(credentials: config.awsCred) {
							script {
								sh """
								  aws s3 sync .next/static s3://agrofy-bundles-${config.envType}/${APP_NAME}-${pipelineParams.targetNS}/_next \\
								    --metadata-directive REPLACE --cache-control "max-age=31536000" --acl public-read --delete
								"""
							}
						}
					}
				}
			}
		}
		post {
			success {
				script {
					currentBuild.displayName = "#${BUILD_NUMBER}"
					wrap([$class: 'BuildUser']) {
						utils.notifyBuild(currentBuild.result, BUILD_USER)
						currentBuild.description = 
							"BRANCH-TAG: ${BRANCH_TAG}\n DOCKER TAG: ${BUILD_NUMBER}-${random_suffix}\n USER: ${BUILD_USER}"
					}
				}
			}
		}
	}
}