[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)
![Build & Test](https://github.com/infor-cloud/acme-corp-extensions/workflows/Java%20CI/badge.svg?event=push)

# Ruiz Foods XtendM3 Extensions
Repository for Ruiz Foods XtendM3 Extensions

![Ruiz Foods](/images/RuizLogo.png)&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;![Doppio Group](/images/DoppioLogo.png)

## Introduction
This repository is for Ruiz Foods XtendM3 Extensions. The idea is to keep all Ruiz Foods' extension in one repository and collaborate to create, maintain and update Extensions in a central repository and move to Ruiz Foods' environment when needed.

This lets the developers work with keeping track of modifications to the extensions as well as a big advantage of being able to build, test and run them locally with a mocked M3 environment and create unit test cases to ensure the quality of the developed extensions 

## Setup
The project is a standard Maven project using Mockito and JUnit 4. The source directory structure is similar to any other Maven directory structure except for the Groovy source roots like below  

```
.
├── mvnw
├── mvnw.cmd
├── pom.xml
├── README.md
└── src
    ├── main
    │   ├── groovy
    │   │   ├── xxx.groovy
    │   │   └── yyy.groovy
    │   ├── java
    │   └── resources
    │       └── metadata.yaml
    └── test
        ├── groovy
        │   ├── xxxTest.groovy
        │   └── xxxTest.groovy
        └── java
```

### Project Structure Descriptions  

| File/directory name  | Description                                                                                    |
|:---------------------|:-----------------------------------------------------------------------------------------------|
| `mvnw`               | Maven wrapper executive for *nix environments                                                  |
| `mvnw.cmd`           | Maven wrapper executive for windows environments                                               |
| `pom.xml`            | Maven project definition file                                                                  |
| `README.md`          | Readme file for project documentation                                                          |
| `src/main/groovy`    | Groovy Extensions source directory                                                             |
| `xxx.groovy`         | Groovy Extension                                                                               |
| `src/main/java`      | Java source directory, **must  always be empty**                                               |
| `src/main/resources` | Resource directory                                                                             |
| `metadata.yaml`      | Extension metadata file, used for trigger definition, **must always be named `metadata.yaml`** |
| `src/test/groovy`    | Groovy Extensions unit test source directory                                                   |
| `xxxTest.groovy`     | Groovy Extension unit test, **name must always follow format `<extension name>Test.groovy`**   |
| `src/test/java`      | Java unit test source directory, **must always be empty**                                      |

### Prerequisites
- An IDE of choice, e.g. Eclipse, Intellij IDEA, etc.
- Git
- Groovy SDK version 2.5.6
- Groovy plugin for IDE
- Editorconfig plugin for IDE
- A terminal of choice Command Prompt, PowerShell, Cygwin or any *nix based terminal

### Instructions
To set up the project locally, perform the following:
- Clone/Download the latest version of project from repository
- Import Maven project project
	- On Eclipse there's an option of importing Maven projects directly
	- On choose either New Project from existing sources or Import project

## Issues
When setting up this project for the first time you might run into some known problems 

### NoSuchMethodError when running unit tests
When running unit tests for the first time in Eclipse, you might see an error like this:

```
java.lang.NoSuchMethodError: org.junit.platform.launcher.Launcher.execute(Lorg/junit/platform/launcher/TestPlan;[Lorg/junit/platform/launcher/TestExecutionListener;)V
	at org.eclipse.jdt.internal.junit5.runner.JUnit5TestReference.run(JUnit5TestReference.java:89)
	at org.eclipse.jdt.internal.junit.runner.TestExecution.run(TestExecution.java:41)
	at org.eclipse.jdt.internal.junit.runner.RemoteTestRunner.runTests(RemoteTestRunner.java:541)
	at org.eclipse.jdt.internal.junit.runner.RemoteTestRunner.runTests(RemoteTestRunner.java:763)
	at org.eclipse.jdt.internal.junit.runner.RemoteTestRunner.run(RemoteTestRunner.java:463)
	at org.eclipse.jdt.internal.junit.runner.RemoteTestRunner.main(RemoteTestRunner.java:209)
```

This is due to Eclipse choosing JUnit 5 as the test runner instead of JUnit 4. To fix this issue open the Run Configurations and select JUnit 4 as your Test Runner.
