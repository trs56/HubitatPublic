## Welcome to my Hubitat Repository!
This is where I will be posting code for my drivers and apps. It is late December 2023 and I am just starting out with HE and Groovy, after 10 years on Vera. But it took me a year after buying buying my HE device to finally make the switch because there was one thing I was doing in Vera that I just knew was going to be a problem. Sure enough it was, but not one that couldn't be solved, and thus was born my first HE device driver. My migration is complete. I'm really glad I finally made the switch.

All of the code I post out here will include good doc to go with it, in the code, and a brief descriptions in here.

Thanks for checking this out and good luck with my drivers! Hope you find them useful. First up... and possibly the only...

### Hikvision Actuator for Input Alarms and Motion Detection
The driver gives you ability to trigger Alarm Input events and enable/disable motion detection features. However, to use the Alarm Input feature, you must be willing and able to jump the wired alarm in/out ports with a short piece of thin wire. This is because the Hikvision API does not allow apps to trigger Alarm In, but it does allow apps to trigger Alarm Out. The only thing that can trigger Alarm In is voltage on the line and that is all triggering Alarm Out does.

This allows you to trigger alarms on your cameras from HE whenever conditions warrant (whether you are armed or not) and use HE to control the arming schedule for Motion Detection and PIR sensors by running its custom commands from your rules and apps. 

A comprehensive user User Guide is dumped to the log when you add a new camera. Please read it BEFORE you Save Preferences for your first camera.
