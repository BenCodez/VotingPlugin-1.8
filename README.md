# This is for 1.8 and is NOT actively supported!!!!
Get builds here: https://bencodez.com/job/VotingPlugin-1.8/

# VotingPlugin
Plugin on SpigotMC
https://www.spigotmc.org/resources/votingplugin.15358/

## License
### Creative Commons Arttribution 3.0 Unported
https://github.com/BenCodez/VotingPlugin/blob/master/VotingPlugin/Resources/LICENSE.txt

### Maven:

    <repository>
	    <id>BenCodez Repo</id>
	    <url>https://nexus.bencodez.com/repository/maven-public/</url>
    </repository>

    <dependency>
        <groupId>com.bencodez</groupId>
	    <artifactId>votingplugin</artifactId>
	    <version>LATEST</version>
	    <scope>provided</scope>
    </dependency>
  
  Versions:  
  LATEST - latest stable release  
 
    


Java 8 synchronization decisions and validation: [docs/java8-sync.md](docs/java8-sync.md).

## Pull-request JAR builds

GitHub Actions builds and tests pull requests using Temurin Java 8 and Maven
`clean verify`. Download the shaded plugin JAR from the successful run's
Artifacts section; artifacts are retained for 14 days. Builds also run on
`master` pushes and can be started manually after the workflow reaches `master`.

The workflow first builds and installs AdvancedCore-1.8 from the exact commit
in `.github/advancedcore-ref`. Update that pin when adopting a newer producer;
it deliberately does not use a moving branch or an older published artifact.

Backend configuration compatibility: [docs/backend-config-alignment.md](docs/backend-config-alignment.md).

Current-proxy backend compatibility and upgrade limits: [docs/current-proxy-backend.md](docs/current-proxy-backend.md).
