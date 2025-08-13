# semanticanary
![logo of semanticanary](semanticanary.png "Semanticanary")

## Overview

Semanticanary is a Java-based project designed to detect semantic changes in dependency updates.
It was developed as part of the Master's thesis of [Leonard Husmann](https://www.github.com/leonardhusmann) "Detecting Semantic Changes in Dependency Updates" at the [KTH Royal Institute of Technology](https://www.kth.se).

## Prerequisites
- Java 17 or higher
- Maven 3.6.3 or higher
- Docker (for running the updated packages in a containerized environment)
- make sure you can clone the Docker images from the chains-group's image registry, e.g.:
  ```bash
  docker pull ghcr.io/chains-project/breaking-updates:jsoup-1.7.1
  ```

## Setup
1. Clone this repository:
   ```bash
   git clone https://github.com/chains-project/semanticanary
   ```
2. Clone the `semantic-agent` repo and follow the instructions for building it 
   ```bash
   git clone https://github.com/chains-project/semantic-agent
   ```
3. Build the project using Maven:
   ```bash
   mvn clean install
   ```
4. Run `Semanticanary.java` with the desired parameters. For example, to compare the `jsoup` library versions 1.7.1 and 1.7.3, specify the following arguments:
   ```
   -a /path/to/semantic-agent/target/semantic-agent-1.0-SNAPSHOT.jar 
   -pre ghcr.io/chains-project/breaking-updates:jsoup-1.7.1 
   -post ghcr.io/chains-project/breaking-updates:jsoup-1.7.3 
   -m org.jsoup.nodes.Element:prepend(java.lang.String) 
   -o /your/output/path/.tmp/differences
   ```