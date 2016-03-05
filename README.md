# [genj-code](http://genj.sourceforge.net/wiki/)
[Unofficial source clone] GenealogyJ is a viewer and editor for genealogic data, suitable for hobbyist, family historian and genealogy researcher. GenJ supports the Gedcom standard, is written in Java and offers many views like family tree, table, timeline, geography and more.

## Prerequisites

Please make sure to check your system meets the following requirements

* Java JDK 1.6 for your operating system is installed
* The system variable %JAVA_HOME% (or $JAVA_HOME on Unix) is defined and points to the installation directory of the J2SE

## Running it

To build GenJ on your machine, first change the current directory to the new directory used for checked-out, then run the build-script:

    $ cd genj
    $ ./build run

From:  http://genj.sourceforge.net/wiki/en/development/overview

## Download

GenJ comes in three installer packages

* a windows installer (setup in .zip)
* a Mac Application (.app in .tar.gz)
* a platform independent installer for Linux et al (.jar)

[Download](https://sourceforge.net/projects/genj/files) and run one of these installers. Your system has to have the latest [Java runtime](http://java.com/getjava) installed and a double-click on what you're downloading should initiate the install process which will walk you through the setup (to execute the .jar file the file association should run “javaw.exe -jar *.jar”).

## Troubleshooting

If you can't get GenJ to run as described please verify that a 1.6 compatible (or newer) [Java Virtual Machine](http://java.com/getjava) is installed (output should look similar):

    C:\Program Files\Java\jre1.6.0_10>java -version
    java version "1.6.0_10-rc2"
    Java(TM) SE Runtime Environment (build 1.6.0_10-rc2-b32)
    Java HotSpot(TM) Client VM (build 11.0-b15, mixed mode, sharing)

You can report any problems in the [forum](http://genj.sf.net/forum) - please report with the log file ~/.genj3/genj.log or %APPDATA%\GenJ\genj.log.
