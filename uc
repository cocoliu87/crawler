#!/usr/bin/env bash
#
# UberCarwl Toolkit v0.1
#  This is a tool kit that helps manage the crawler stack.

#set -o errexit
HIDE_UBERCRAWL_MSG="FALSE";

#
# UberCrawl Toolkit
#
function show_ubercarwl_msg() {
  cat <<UBERCRAWL_EOM
 ____ ______.                 _________                      .__
|    |   \_ |__   ___________ \_   ___ \____________ __  _  _|  |
|    |   /| __ \_/ __ \_  __ \/    \  \/\_  __ \__  \\ \/ \/ /   |
|    |  / | \_\ \  ___/|  | \/\     \____|  | \// __ \\     / |  |__
|______/  |___  /\___  >__|    \______  /|__|  (____  /\/\_/ |____/
              \/     \/               \/            \/
UBERCRAWL_EOM
}

# The following two functions need to be defined prior to execution


#
# Check if docker is running
#
function __get_docker_running() {
  docker ps -q &> /dev/null && echo "TRUE" || echo "FALSE";
}


#
# Get timestamp
#
function __get_timestamp() {
  TIMESTAMP=$(date "+%Y_%m_%d--%H_%M_%S");
  echo "${TIMESTAMP}";
}

#
# Stops the cluster and removes any created volumes
#
function start() {
    SERVICE_MODE=$1;
    echo "STARTING SERVICE: ${SERVICE_MODE}";

    case "${SERVICE_MODE}" in
      "frontend")
        docker-compose -f compose.frontend.dev.yaml up -d;
        echo -e "\nReact now running on http://localhost:3000  (it might take a minute to boot)";
        echo -e "To stop: ./uc stop frontend";
      ;;
      "frontend-build")
        docker-compose -f compose.frontend.yaml up -d;
      ;;
      "coordinator")
        docker-compose -f compose.coordinator.yaml up -d;
      ;;
      "worker")
        docker-compose -f compose.coordinator.yaml up -d;
      ;;
      "nginx")
        docker-compose -f compose.nginx.yaml up -d;
      ;;
      *)
        echo "Invalid option ${APPLY_MODE}";
      ;;
    esac;
}

function stop() {
    SERVICE_MODE=$1;
    echo "STOPPING SERVICE: ${SERVICE_MODE}";

    case "${SERVICE_MODE}" in
      "frontend")
        docker-compose -f compose.frontend.dev.yaml  down --volumes --remove-orphans;
      ;;
      "frontend-build")
        docker-compose -f compose.frontend.yaml  down --volumes --remove-orphans;
      ;;
      "coordinator")
        docker-compose -f compose.coordinator.yaml  down --volumes --remove-orphans;
      ;;
      "worker")
        docker-compose -f compose.coordinator.yaml  down --volumes --remove-orphans;
      ;;
      "nginx")
        docker-compose -f compose.nginx.yaml  down --volumes --remove-orphans;
      ;;
      *)
        echo "Invalid option ${APPLY_MODE}";
      ;;
    esac;
    echo "Done";
}

#
# Stops the cluster and removes any created volumes
#
function clean() {
#  docker-compose -f local.compose.yml down --volumes --remove-orphans;
  echo "Cleaning";
}



#
# Stops the cluster and removes any created volumes
#
function help() {
  cat <<UBERCRAWL_EOM
  Helpful Commands:

  ./uc help

  ./uc start  [all (default), coordinator, worker, frontend]
  ./uc restart
  ./uc stop
  ./uc status

  ./uc run crawl <HTTP URL>
  ./uc run index
  ./uc run rank


  ./uc image build
  ./uc image publish
UBERCRAWL_EOM
}



#
# Restarts the cluster
#
function restart() {
  echo "--------------------------------------------------------------------";
  echo " ⭕ Restarting the UberCrawl Cluster...️";
  echo "--------------------------------------------------------------------";
  stop;
  start;
}


#
# Build the
#
function build_images() {
  echo "Building docker images";
}



function show_banner() {
  show_ubercarwl_msg;
  printf " UberCrawl Cluster: \e[32m $1 $2 $3 $4\e[0m\n";
  echo "- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -";
}

#
# Let's make sure docker is running...
#
if [[ "$(__get_docker_running)" != "TRUE" ]]; then
  show_banner "$1" && echo "Docker is not running. Please start docker on your computer"
  exit;
fi;

#
# Start the hasura cluster...
#
if [[ "${HIDE_UBERCRAWL_MSG}" != "TRUE" ]]; then
   show_banner "$@" && $@;
else
  echo "UberCrawl Cluster: $@" && $@;
fi;