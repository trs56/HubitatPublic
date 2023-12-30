# Hikvision Actuator for Input Alarms and Motion Detection
# User Guide - BETA Version 0.1
### Copyright: 2023 Thomas R Schmidt, Wildwood IL
Unauthorized use, copy or publication is prohibited.
## For BETA Testers
While in BETA, the code is subject to frequent changes. Each commit of the code in my repository will include a brief description of the changes being made. Most will be minor and noted in the change log. If a major change is made, I will post it on the Community Forum, Custom Drivers.
## Prerequisites
This driver is unable to control cameras that are directly connected to a NVR. Your cameras much be on a switch that is connected to your router and configured with a static ip address.

To use the key feature of this driver, which is to trigger Alarm Input Events on your camera using rules in HE, your camera must have wired Alarm I/O ports that are accessible and not in use. Because, the first thing you will need to do is jump the alarm in/out positive ports with a jumper wire. This is required since the driver is only allowed to trigger Alarm Out when using the Hikvision CGI. The CGI does not not allow apps to trigger Alarm In. The only way to trigger Alarm In is to put voltage on the wire, which is all triggering Alarm Out does. You can and should test this yourself first by connecting the ports and triggering a manual alarm from the camera, then turn it off.

This driver also requires specific camera settings, which are described below.
## Introduction
This device driver implements the HE Actuator capability. It allows you to trigger Alarm Input Events and enable/disable Motion Detection and PIR Sensors on your Hikvision cameras by running its custom commands from your rules and apps.
 
So when you call the command to set Alarm On from your rules, the driver will trigger Alarm Out and the voltage will flow. The Alarm Input Event on your camera will then fire, provided it is enabled and armed.  
 
This allows you to trigger alarms on your cameras whenever conditions warrant and use HE to control the arming schedule for Motion Detection and PIR sensors.

This release will report on the enabled/disabled state of Line Crossing and Intrusion Events, but does not allow you to change them. Nor does it allow you to change the enabled/disabled state of the Alarm Input Event. You control that and its arming schedule on the camera. These features are planned for a future release.
 
## Camera Configuration
To use the driver, your camera must be configured as follows:
Please note that the path to the setting on your camera may be different.
 
1: System > Security > Web Authentication = digest/basic
 
2: Network > Advanced > Integration Protocol >
   Hikvision-CGI Enabled, Authentication=digest/basic
 
3: Optional Operator account with the Remote Parameters/Settings and Remote Notify options selected.   
You can use your admin account, but it is recommended that you create an Operator account for Hubitat.
 
4: Basic and Smart Events must not be configured to trigger Alarm Out.   
Check Linkage Methods for all Basic and Smart Events to make sure this option is not selected. HE is now in control.   
 
5: Enable Alarm Input Handling in Basic Events - Alarm Input   
Set Alarm Type to NO (is default, normally open, no voltage)   
Set the Alarm Name (e.g. CamName Alarm)   
Set the Arming Schedule to 24x7 for now. You decide later.   
Set desired Linkage Methods (email, notify, record)   
    
6: Connect the Alarm In/Out ports with a small jumper wire.   

You are now ready to configure your camera for operation with HE using the Hikvision Actuator.
 
## Device Configuration and Operation
1: Add a new Virtual Device using the Hikvision Actuator

2: Enter your camera ip address, port and credentials in userid:password format. Click Save.
 
Whenever you Save Preferences, the driver will validate your camera by first pinging the ip address and then sending a GET request for the data from /System/deviceInfo using the Credentials you entered. When these checks fail, an error message will be displayed in the zStatus attribute and you will need fix it and try again. If you can't get past this step, you may need to call the help desk to report that you have a Hikvision camera that is not being recognized.
 
If all is well, it will say Yay!. Your camera has been validated and is ready for operation.
 
If a motion detection feature is not available on your camera, (i.e the URL Path was not found), its status will be set to NA and the on/off commands for that feature will be disabled.
 
You may now start running commands and create test rules to validate its operation. You should do this before you deploy into your home security setup and the arming of your house for security purposes.

Start with turning your motion sensors on/off and watch the change in state. Confirm the change in state on your camera.

Turn the Alarm On to confirm you receive the notifications you have configured for the Alarm Input Event. If you have a camera with a siren, check that option under Linkage Methods to see how fast the trigger is.

DO NOT forget to turn the Alarm OFF in your rules when conditions go back to normal. So if you have a rule with (conditions=true) that turns it on, you need a second rule with (conditions=false) to turn it off.

Get Status will get the current state of all features from the camera and update the status attributes on the device, if needed. This command is in place for testing and development purposes and may be removed when the driver comes out of beta for general release. Do not call Get Status from any rules.
### In Summary
This driver gives you the flexibility to trigger your cameras with more reliable and consistent PIR sensors, or any other sensor. For example, when you're away and your house is armed, you can trigger all of your cameras if any one of your security sensors goes off. If you want to capture events that are not security related and create convenient playback markers in a continuous recording stream, you can do that, too.

You are now in complete control of when Alarms can be triggerd on your cameras and when Motion Detection features are enabled. Once you're up and running and have validated its operation, this driver will run quietly in the background, so you can rest assured it will perform the actions you need it to when called upon.
 
## State Changes During Operation
The status attributes change when a command is run that triggers it. The driver always gets the current state from the camera first, to determine if a change in state is actually needed, and then updates the status attribute to the new state, as needed, using sendEvent.

During normal operation, zStatus should remain OK at all times. All commands ping the ip first to see if it is online. If the ping fails, zStatus will go to OFF and remain OFF until a command is run that sees it back online.
 
If you change the credentials of the hubitat account on the camera and forget to change them here, the next time a command is run, zStatus will be set to CRED and operation suspended until you Save Preferences to fix the mis-match.
 
If any unexpected HTTP GET/PUT errors occur, zStatus will go to ERR and operation suspended until the problem is resolved and Save Preferences is run. These errors may require a call to the help desk.
## Errors and Troubleshooting
The driver logs all of its activity and catches all errors from the http get/put methods but does not catch Groovy/Java script errors involving bad data.
 
If the driver stops working, check the logs and call the help desk.
 
Debug logging is used for dumping the converted XML data that is returned by the camera in response to a GET request. With this release, the driver is relying on the translation of the XML into a GPath object in Groovy. This will be useful for debugging groovy script errors involving bad data if the structure of the Gpath the driver is expecting to receive changes, perhaps caused by newer or older versions of firmware. 
## Security Warning
This driver uses HTTP Basic Authentication to login to your camera. Your encoded credentials are saved and displayed in the Data section of the device in the format required for this method of authentication.
## Feedback and Contact for Support
Please contact me through the Hubitat Community, TomS or trs56 on github. Please be aware that I may not be able to get back to you instantly. I do look forward to hearing your feedback.  
Thanks for checking this out.
## Disclaimers
USE AT YOUR OWN RISK

And have a wonderful day! :)
