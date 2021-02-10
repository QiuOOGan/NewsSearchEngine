Moore instructions

If you haven't set up Maven, do the following. Otherwise, skip these two steps.

$ cd ~
$ wget https://archive.apache.org/dist/maven/maven-3/3.6.2/binaries/apache-maven-3.6.2-bin.tar.gz
$ tar xzvf apache-maven-3.6.2-bin.tar.gz

Run script:

$ cd search-api-test

... copy your search-api-1.0-SNAPSHOT.war into this folder ...

$ python3 -m venv virtualenv
$ . virtualenv/bin/activate

Note: pip install only needs to be run once

$ pip install -r requirements.txt

$ python testscript.py

If you want to do some testing on your own machine please change variables
JAVA_HOME and MVN as instructed inside the script.
However, the script uses unix tools so it won't work on Windows.  Just test on moore.

Note: this script has been tested on Python3.6.9 and Python3.7.3.
