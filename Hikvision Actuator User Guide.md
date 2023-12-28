# HikVision Actuator for Input Alarms and Motion Detection
# User Guide - BETA Version 1.0
### Copyright: Thomas R Schmidt, Wildwood IL
Unauthorized use or publication is strictly prohibited.
## Introduction
This device driver implements the HE Actuator capability. It allows you to trigger Alarm Input Events and enable/disable Motion Detection and PIR Sensors on your Hikvision cameras by running its custom commands from your rules and apps.
 
To use the Alarm Input trigger, your camera must have wired Alarm I/O ports that are accessible and not in use. Since the first thing you will need to do is find a short piece of thin wire and connect the two ports, out to in. This is required since the driver is only allowed to trigger Alarm Out when using the Hikvision CGI. It does not not allow apps to trigger Alarm In. The only way is to put voltage on the line, and that is all triggering Alarm Out does. You can test this yourself by connecting the ports and triggering a manual alarm from the camera.
 
So when you call the command to set Alarm On from your rules, the driver will trigger Alarm Out and the voltage will flow. The Alarm Input Event on your camera will then fire, provided it is enabled and armed.
 
This allows you to trigger alarms on your cameras whenever conditions warrant and use HE to control the arming schedule for Motion Detection and PIR sensors. The driver will report on the enabled/disabled state of Line Crossing and Intrusion Events but does not allow you to change their state at this time (next release).
 
Nor with this release does it allow you to change the enabled/disabled state of the Alarm Input Event. You control that and its arming schedule.
 
## Camera Configuration
To use the driver, your camera must be configured as follows:
Please note that the path to the setting on your camera may be different.
 
1: System > Security > Web Authentication = digest/basic
 
2: Network > Advanced > Integration Protocol >
   Hikvision-CGI Enabled, Authentication=digest/basic
 
3: Optional Operator account with the remote parameter/settings and remote notify options selected.  
    You can use your admin account, but I recommend that you create an Operator for Hubitat. You could lock out your admin account if credentials change and you forget to change them here. Create a hubitat account.
 
4: Basic and Smart Events must not be configured to trigger Alarm Out.  
   Check Linkage Methods for all Basic and Smart Events and make sure Trigger Alarm Out is NOT selected. HE is now in control.   
 
5: Enable Alarm Input Handling in Basic Events - Alarm Input   
Set Alarm Type to NO (is default, normally open, no voltage)   
Set the Alarm Name (e.g. CamName Alarm)   
Set the Arming Schedule to 24x7 for now. You decide later.   
Set desired Linkage Methods (email, notify, record)   
    
6: Connect the Alarm In/Out ports with a small jumper wire.
 
You are now ready to configure your camera for operation with HE.
 
## Device Configuration and Operation
1: Add a new Virtual Device using the Hikvision Actuator

2: Enter your camera ip address, port and credentials. Click Save.
 
Whenever you Save Preferences, the driver attempts to validate your camera settings by first pinging the ip address and then sending a GET request for the data from /System/deviceInfo using the Credentials you entered. If these checks fail, an error message will be displayed in the zStatus attribute and you will need fix it and try again. If you can't get past this step, you may need to call the help desk (me:).
 
The driver tries to walk you through this process using feedback in zStatus but as I've come to discover in HE, changes to attributes don't always refresh on screen when the sendEvent is issued. Sometimes you have to refresh or get out and come back in. Once you get it to it say "Yay!", you are good to go.
 
If a Motion Detection or PIR feature is not available on your camera, (i.e the URL Path was not found), its status will be set to NA and the on/off commands for that feature will be disabled.
 
You may now start running commands, just don't click Get Status repeatedly. Instead, turn your sensors on/off and watch the state change. Getting all status is mainly there for when you Save Preferences.
 
Make sure everything works. OK is the normal response. Try enabling a feature that is already enabled on the camera, see what you get. Which is nothing, unless you go to the log, or the states are out of sync.
 
Turn the Alarm On and make sure you get the notifications you have configured for the Alarm Input Event. If you have a camera with a siren, check that option under Linkage Methods to see just how fast the trigger is. Just don't forget to turn the Alarm off. Same thing applies to your rules and apps. Don't forget to turn the lights off.
 
Now, let your imagination run wild and figure out something to do with this thing. I can't offer much more than that.
 
I like it because it gives me the ability to trigger my cameras with more reliable and consistent PIR sensors, or any other sensor. So when I'm away and my house is "fully armed", I can trigger all of my cameras if any one of my security sensors goes off. The possibilities are endless.
 
I also like the Hikvision app and email notifications and having the ability to create playback markers in a continuous recording stream for any event I want. (side note: I use onboard recording, no NVR)
 
## State Changes During Operation
The status attributes change when a command is run that triggers it. The driver always gets the current state from the camera first, to determine if a change in state is actually needed, and then updates the status attribute to the new state, as needed, using sendEvent.

State changes for Intrusion and Line Cross are only updated when Get Status is run. Next release will have code to control them. 
 
During normal operation, zStatus should remain OK at all times. All commands ping the ip first to see if it is online. If the ping fails, zStatus will go to OFF and remain OFF until a command is run that sees it back online.
 
If you change the credentials of the hubitat account on the camera and forget to change them here, the next time a command is run, zStatus will be set to CRED and operation suspended until you Save Preferences to fix the mis-match.
 
If any unexpected errors occur, zStatus will go to ERR and operation suspended until the problem is resolved and Save Preferences is run. These errors may require a call to the help desk.
## Errors and Troubleshooting
The driver logs all of its activity and catches all errors from the http get/put methods but does not catch Groovy/Java script errors involving bad data.
 
So if nothing is working, check the logs. These are the errors I really need to see. The code is making certain assumptions about the data it is receiving and processing. If anything changes in that stream, bad things don't happen, nothing does. And that's a problem when you're talking home security.
 
So when first starting out, check the log frequently to see what it is saying. Debug logging is only used for dumping the XML data that is returned by the camera in response to a GET request. The driver is relying on the translation of the XML data returned by the camera into to a GPath object in Groovy.
 
Fortunately, the XML Schemas provided by the HIKVision CGI won't change, but there may be newer or older cameras out there that send back a slightly different XML response that could change the way Groovy interprets the structure, which could require a change to the way the code references it.
## Security Warning
This driver uses HTTP Basic Authentication to login to your camera. Your encoded credentials are saved and displayed in the Data section of the device in the format required for this method of authentication.
## Feedback and Contact for Support
Please Contact me through the Hubitat Community. I would certainly like to hear your feedback.  
Thanks for checking this out.
## Disclaimers
USE AT YOUR OWN RISK

And have a wonderful day! :)

