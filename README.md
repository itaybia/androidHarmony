androidHarmony
==============

An Android app for controlling the Logitech Harmony Ultimate Hub without need for the Logitech app or remote.

androidHarmony was developed using the pyharmony library as a guide. It allows android/java developers to use the code to build their own custom application for controlling the hub and perhaps adding functionality to other home automation projects.

Special thanks to jterrace and petele for their effort in discovering the hub's protocols and creating the first open-source code to use it.



Protocol
--------

The harmony protocol is based on xmpp (the Jabber protocol).  With some HTTPS requests to the Logitech myharmony web service.
All protocols are described in the PROTOCOL.md file and also in the pyharmony project:

https://github.com/jterrace/pyharmony/
and
https://github.com/petele/pyharmony/


I would also like to refer users to a C++ executable implementing these protocols at:
http://sourceforge.net/projects/harmonyhubcontrol/



Functionality
--------------

androidHarmony is a sample application that allows users to control and query the Harmony hub. It currently allows users to:

* Authenticate using Logitech's web service
> This is not currently in use, as it seemed like it wasn't really needed for logging into the hub. But the ability is implemented
* Authenticate to the harmony device
> This can also be done from outside the home WIFI in a non-secure way 
* Query for the harmony's entire configuration information
* Request a list of activities and devices from the harmony
* Request the currently selected activity
* Start an activity by ID
* Send a command to a specific device
> Currently implemented commands are: Volume up/down, Channel up/down. But essentially all commands can be sent by parsing the configuration


Requirements
------------

In order to successfully use the app, it is expected that the following are in place:

A Harmony Hub that is pre-configured and working properly on the local network
Your Logitech Harmony login email and password.  These are the same ones used in
the app or online to edit the Harmony's configuration.

The IP address of the Harmony is required.

For use outside the home network, the home network's IP is needed and port-forwarding on the home router might be needed as well.

An android device with API > 10

A few libraries are used within the app and should have their jars added to the libs folder:
* The asmack library for using XMPP - asmack-android-10-0.8.10.jar:
> http://asmack.freakempire.de/0.8.10/
* The Maven HttpClient for Android 4.3.3:
> http://hc.apache.org/downloads.cgi
* The json-simple library - json-simple-1.1.1.jar:
> https://code.google.com/p/json-simple/downloads/list



Usage
-----

Copy the Configuration_template.txt file from the root to the same package as the MainActivity.java and rename it to Configuration.java.
Change the Configuration.java file according to your parameters (IP, WIFI name, etc.)
Compile the app (i am using eclipse) and run it

The get_config button is used to get the hub's configuration for the first time to the app and save it in a local file.
The other buttons are as expected: connect/disconnect, volume up/down, channel up/down.



To-do
--------------------

* Create a library from the Client and Authorization files
* Figure out how the ongoing command press creates a continuous change on a device (I.E, pressing continuously on volume up keeps changing the volume until the button is released)
* Figure out whether the myharmony web service login is really needed
