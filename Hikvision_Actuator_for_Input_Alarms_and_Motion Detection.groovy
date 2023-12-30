//******************************************************************************
//* Hikvision Actuator for Input Alarms and Motion Detection
//******************************************************************************
//*  Copyright 2023 Thomas R Schmidt, Wildwood IL
//*  This program is free software: you can redistribute it and/or modify
//*  it under the terms of the GNU General Public License as published by
//*  the Free Software Foundation.
//******************************************************************************
// This driver allows you to trigger Alarm Input events on the camera and
// enable/disable Motion Detection features by running its custom commands
// from your rules and apps.
//
// The User Guide is required reading.
// https://github.com/trs56/HubitatPublic/blob/main/Hikvision_Actuator_User_Guide.md
//
// Change Log
// Date        Version        Release Notes
// 12-28-2023  BETA 0.1       Original Public Beta Release
// 12-30-2023                 Added Firmware version to device Data and renamed two Data vars
//******************************************************************************
metadata {
    definition (name: "Hikvision Actuator for Input Alarms and Motion Detection", 
                author: "Thomas R Schmidt", namespace: "trs56", // github userid
                singleThreaded: true) // ??
    {
        // Capability to get the device into those with actionable commands
        // that can be run from rules or apss. This capability has no required
        // commands or attributes of its own. Its all custom.
        capability "Actuator"
    
        command "AlarmOff"
        command "AlarmOn"
        command "GetStatus"
        command "MotionOff"
        command "MotionOn"
        command "PIRSensorOff"
        command "PIRSensorOn"
        
        attribute "AlarmOut", "STRING"   // Powered state of wired Alarm Output Port (active/inactive)
        attribute "Intrusion", "STRING"  // Enabled/Disabled state of Intrusion Event
        attribute "LineCross", "STRING"  // ""
        attribute "Motion", "STRING"     // ""
        attribute "PIRSensor", "STRING"  // ""
        attribute "zStatus", "STRING"    // State of this device in HE: OK, ERR, OFF, CRED
        // OK = Everything is groovy
        // ERR = Unexpected get/put errors occurred.
        // OFF = Camera is offline
        // CRED = Authentication failed, credentials on the camera have changed
	}
    preferences 
    {
        input(name: "deviceIP", type: "string", 
              title:"Camera IP Address",
              description: "IP Address:",
              required: true)
        input(name: "devicePort", type: "string",
              title:"HTTP Port", description: "",
              defaultValue: "80",
              required: true)
        input(name: "deviceCred", type: "password",
              title:"Credentials for Basic Auth",
              description: "userid:password",
              required: true)
        input(name: "debug", type: "bool",
              title: "Enable debug logging?",
              defaultValue: false)
    }
}
//******************************************************************************
// Globals available to all methods
String strMsg = " " // Used to pass status (OK or errmsg) back from
//                     SendGet/Put Requests for logging and program
//                     control in the calling methods
Boolean SavingPreferences = false // Signal to Ok2Run method that we are
//                                   saving preferences and allowed to run
//******************************************************************************
// Installing New Camera Device
//******************************************************************************
void installed() {
    def l = []
    l << "IMPORTANT: Please do NOT Save Preferences until your camera has"
    l << "been configured to operate with this driver. The information"
    l << "you need to do that can be found here:"
    l << " "
    l << "https://github.com/trs56/HubitatPublic/blob/main/Hikvision_Actuator_User_Guide.md"
    l << " "
    l << "Thank you. You may now hand over your credentials and proceed."
    def lr = l.reverse()
    lr.each {log.info it}
    sendEvent(name:"zStatus",
              value:"Welcome! If you are adding your first camera, PLEASE OPEN THE LOG NOW, If not, please proceed.")
}
//******************************************************************************
// Preferences Saved
//******************************************************************************
void updated() {
    log.info "Saving Preferences and validating new camera"
    deviceIP = deviceIP.trim()
    devicePort = devicePort.trim()
    deviceCred = deviceCred.trim()
    device.updateSetting("deviceIP", [value:"${deviceIP}", type:"string"])
    device.updateSetting("devicePort", [value:"${devicePort}", type:"string"])
    device.updateSetting("deviceCred", [value:"${deviceCred}", type:"string"])
    // remove all device data values
    device.removeDataValue("Name")
    device.removeDataValue("Model")
    device.removeDataValue("Firmware")
    // save the new credentials
    device.updateDataValue("CamID",deviceCred.bytes.encodeBase64().toString())
    log.info "Pinging IP: " + deviceIP
    if (!PingOK(deviceIP)) {
        sendEvent(name:"zStatus",value:"Ping failed, check IP")
        log.info "Device update failed"
        return    
        }
    log.info "Attempting to GET: http://" + deviceIP + ":" + devicePort + "/System/deviceInfo"
    SendGetRequest("/System/deviceInfo")
    if (strMsg != "OK") {
        String errcd = LogGETError()
        if (errcd == "ERR") {strMsg = "Oh oh, an unexpected error has occurred. Check the log. You may need to call the help desk."}
        if (errcd == "CRED") {strMsg = "Authentication failed, re-enter credentials"}
        if (errcd == "NA") {strMsg = "IP is not a Hikvision camera, /System/deviceInfo not found"}
        sendEvent(name:"zStatus",value:strMsg)
        log.info "Device update failed"
        return    
        }
    // Now get everything
    log.info "Camera found!"
    // Signal Get Status that we are saving preferences,
    // allowing this method to update zStatus at the end
    SavingPreferences = true
    GetStatus()
    SavingPreferences = false
    if (strMsg == "ERR") {strMsg = "Oh oh, unexpected errors have occurred. You may need to call the help desk. Check the log."}
    if (strMsg == "NA") {strMsg = "Yay! Camera found but some features are not available. "}
    if (strMsg == "OK") {strMsg = "Yay! Camera found and all features are available. "}
    strMsg = strMsg + "Please run any command to put zStatus attribute into " + 
    "operational mode. This is the last message you will receive from me here."
    sendEvent(name:"zStatus",value:strMsg)
    log.info "Device update completed"
}
//******************************************************************************
void AlarmOn() {
    log.info "Received request to set Alarm On"
    if (!Ok2Run("AlarmOut")) {return}  // its the feature being passed, not the command
    SetAlarm("active")
}
//******************************************************************************
void AlarmOff() {
    log.info "Received request to set Alarm Off"
    if (!Ok2Run("AlarmOut")) {return}
    SetAlarm("inactive")
}
//******************************************************************************
void MotionOn() {
    log.info "Received request to enable Motion"
    if (!Ok2Run("Motion")) {return}
    SetMotion("true")
}
//******************************************************************************
void MotionOff() {
    log.info "Received request to disable Motion"
    if (!Ok2Run("Motion")) {return}
    SetMotion("false")
}
//******************************************************************************
void PIRSensorOn() {
    log.info "Received request to enable PIR Sensor"
    if (!Ok2Run("PIRSensor")) {return}
    SetPIR("true")
}
//******************************************************************************
void PIRSensorOff() {
    log.info "Received request to disable PIR Sensor"
    if (!Ok2Run("PIRSensor")) {return}
    SetPIR("false")
}
//******************************************************************************
// Determine if it's ok to run any command
//******************************************************************************
def Ok2Run(String Feature) {
    String devstatus = device.currentValue("zStatus")
    if (SavingPreferences && Feature =="GetStatus") {return(true)}
    if (devstatus == "ERR") {
        log.error "Device in ERR state. Fix problem and Save Preferences to reset."
        return(false)}
    if (devstatus == "CRED") {
        log.error "Device in CRED state. Fix credentials and Save Preferences to reset."
        return(false)}
    // This will allow you thru if you made it thru camera validation in saving preferences
    if (devstatus.length() >> 4 &&  devstatus.substring(0,4) == "Yay!") {devstatus = "OK"}
    // And this will catch you clicking buttons if you haven't...
    if (devstatus != "OK" && devstatus != "OFF") {
        log.error "Not allowed to run, zStatus=" + devstatus
        return(false)}
    if (Feature != "GetStatus" && device.currentValue(Feature) == "NA") {
        log.info "Feature not available"
        return(false)}
    if (!PingOK(deviceIP)) {
        log.error "Camera offline, No response from ping"
        sendEvent(name:"zStatus",value:"OFF")
        return(false)    
    }
    return(true)
}
//******************************************************************************
// Get Current Status
//******************************************************************************
def GetStatus() {
    Boolean err = false
    Boolean na = false
    String camstate = " "
    log.info "Running Get Status command and updating attributes"
    if (!SavingPreferences && !Ok2Run("GetStatus")) {return}
    camstate = GetSysInfo("/System/deviceInfo")
    if (camstate == "NA") {na = true} 
    if (camstate == "ERR" || camstate == "CRED") {
        strMsg = camstate
        if (SavingPreferences) {return} // let that method update zStatus
        sendEvent(name:"zStatus",value:camstate)
        return} 
    camstate = GetFeatureState("/IO/status","AlarmOut")
    if (camstate == "NA") {na = true} 
    if (camstate == "ERR" || camstate == "CRED") {
        strMsg = camstate
        if (SavingPreferences) {return}
        sendEvent(name:"zStatus",value:camstate)
        return} 
    camstate = GetFeatureState("/MotionDetection/1","Motion")
    if (camstate == "NA") {na = true} 
    if (camstate == "ERR" || camstate == "CRED") {
        strMsg = camstate
        if (SavingPreferences) {return}
        sendEvent(name:"zStatus",value:camstate)
        return} 
    camstate = GetFeatureState("/ISAPI/WLAlarm/PIR","PIRSensor")
    if (camstate == "NA") {na = true} 
    if (camstate == "ERR" || camstate == "CRED") {
        strMsg = camstate
        if (SavingPreferences) {return}
        sendEvent(name:"zStatus",value:camstate)
        return} 
    camstate = GetFeatureState("/ISAPI/Smart/FieldDetection/1","Intrusion")
    if (camstate == "NA") {na = true} 
    if (camstate == "ERR" || camstate == "CRED") {
        strMsg = camstate
        if (SavingPreferences) {return}
        sendEvent(name:"zStatus",value:camstate)
        return} 
    camstate = GetFeatureState("/ISAPI/Smart/LineDetection/1","LineCross")
    if (camstate == "NA") {na = true} 
    if (camstate == "ERR" || camstate == "CRED") {
        strMsg = camstate
        if (SavingPreferences) {return}
        sendEvent(name:"zStatus",value:camstate)
        return} 
    strMsg = "OK"  // strMsg will be used by the updated method upon return
    if (na) {strMsg = "NA" // will signal the updated method that some features are not available
            log.info "Some features are Not Available"}
    if (SavingPreferences) {return} // let the update method set zStatus with a user friendly message
    sendEvent(name:"zStatus",value:"OK")
    log.info "Get Status request has ended"
}
//******************************************************************************
// Get and Set the Current State of a Feature - return feature state or error code
//******************************************************************************
private GetFeatureState(String Path, String Feature) {
    String camstate = ""
    String devstate = ""
    devstate = device.currentValue(Feature)
    log.info "GET " + Path

    xml = SendGetRequest(Path)

    //  If the response from the GET request is successful, the xml returned
    //  will be presented in GPathResult format and strMsg will be "OK".
    //  Otherwise, xml will be null and strMsg will contain the error message.
    //  This applies to all calls to the SendGet and SendPut Request methods.
    if (strMsg == "OK") {
        if (Feature == "AlarmOut") {
//            camstate = xml.IOPortStatus[0].ioState.text()
//            if (camstate != device.currentValue("AlarmIn")) {sendEvent(name:"AlarmIn", value:camstate)}
//            log.info "Alarm In: " + camstate 
            camstate = xml.IOPortStatus[1].ioState.text()
            if (camstate != devstate) {sendEvent(name:Feature, value:camstate)}
            log.info Feature + ": " + camstate
        } else {
            camstate = xml.enabled.text()
            if (camstate == "true") {camstate = "enabled"} else {camstate = "disabled"}
            if (camstate != devstate) {sendEvent(name:Feature, value:camstate)}
            log.info Feature + ": " + camstate
        }
        return(camstate)
    } else {
        String errcd = LogGETError()
        if (errcd == "NA") {
            log.info Feature + " is not available"
            if (devstate != errcd) {sendEvent(name:Feature, value:errcd)}
        }
        return(errcd)
    }
}
//******************************************************************************
// Log GET Error and return error code
//******************************************************************************
private LogGETError() {
    log.error "GET Error: " + strMsg
    String etype = "ERR"
    if (strMsg.contains("code: 401")) {
        etype = "CRED"
        log.error "Authentication failed, check credentials"
    }
    if (strMsg.contains("code: 403")) {
        etype = "NA"
        log.error "Path not found or Resource is restricted at a higher level."
    }
    if (strMsg.contains("code: 404")) {
        etype = "ERR"
        log.error "IP is not a Hikvision camera."
    }
    return(etype)
}
//******************************************************************************
// Get Camera Info
//******************************************************************************
def GetSysInfo(String Path) {
    log.info "GET " + Path

    def xml = SendGetRequest(Path)
    
    if (strMsg == "OK") {
        def cname = xml.deviceName.text()
        def cmodel = xml.model.text()
        def firmware = xml.firmwareVersion.text()
    
        if (cname != "") {
            log.info "Camera Name: " + cname
            device.updateDataValue("Name",cname)
        } else {
            device.removeDataValue("Name")
        }
        if (cmodel != "") {
            log.info "Camera Model: " + cmodel
            device.updateDataValue("Model",cmodel)
        } else {
            device.removeDataValue("Model")
        }
        if (fwversion != "") {
            log.info "Firmware: " + fwversion
            device.updateDataValue("Firmware",firmware)
        } else {
            device.removeDataValue("Firmware")
        }
        return("OK")
    } else {
        LogGETError()
        return("ERR")       
    }
}
//******************************************************************************
// Set Alarm
//******************************************************************************
private SetAlarm(String newstate) {
    String devstate = device.currentValue("AlarmOut")
    log.info "GET /IO/status"

    def xml = SendGetRequest("/IO/status")

    if (strMsg != "OK") {
        String errcd = LogGETError()
        sendEvent(name:"zStatus",value:errcd)
        return
    }

    camstate = xml.IOPortStatus[1].ioState.text()

    if (debug) {log.info "Extracted: xml.IOPortStatus[1].ioState.text(): " + camstate}

    if (camstate == newstate) {
        log.info "OK, already " + newstate
        // in case they get out of sync
        if (devstate != newstate) {sendEvent(name:"AlarmOut", value:newstate)}
        sendEvent(name:"zStatus",value:"OK")
        return
    }

//  Current state is reported as active/inactive    
//  To send the trigger, we set the outputState to high/low
    if (newstate == "active") {newstate = "high"}
    if (newstate == "inactive") {newstate = "low"}

    // Can't get any easier than this...

    strXML = "<IOPortData version=\"1.0\" xmlns=\"http://www.hikvision.com/ver10/XMLSchema\">" +\
"<outputState>" + newstate + "</outputState></IOPortData>"

    log.info "PUT /IO/outputs/1/trigger/outputState=" + newstate

    xml = SendPutRequest("/IO/outputs/1/trigger", strXML)

    if (strMsg == "OK") {
        if (newstate == "high") {newstate = "active"} else {newstate = "inactive"}
        log.info "OK, Alarm Out is now " + newstate
        sendEvent(name:"AlarmOut", value:newstate)
        sendEvent(name:"zStatus", value:"OK")
    } else {
        log.error "PUT Error: " + strMsg
        if (strMsg.contains("code: 403") && strMsg.contains("Forbidden")) {
            etype = "NA"
            log.error "Operator does not have \"Remote Notify\" option selected in user account settings."
        }
        sendEvent(name:"zStatus",value:"ERR")
    }
}
//******************************************************************************
// Set Motion Detection
//******************************************************************************
private SetMotion(String newstate) {
    String devstate = device.currentValue("Motion")
    log.info "GET /MotionDetection/1"

    def xml = SendGetRequest("/MotionDetection/1")

    if (strMsg != "OK") {
        String errcd = LogGETError()
        sendEvent(name:"zStatus", value:errcd)
        return
    }
    //  Define the attributes we need to carry over with this request:
    def sl = ""
    def rg = ""
    def cg = ""
    //  Bing AI generated the following "for each" statement for me when I asked it
    //  how to print all of node names and values in a GPathResul variable in groovy.
    //  Amazing, so now I'm using AI to help learn another new programming language. :)
    //  I sorta get it but I have no idea what '**' means.
    //
    //  I also needed this loop because I couldn't get anything other than
    //  the enabled state using xml.object.text(). Something 
    //  to do with the odd XML structure of the Motion Detector. Go figure.
    //  Drove me nuts for hours. No choice but to use this method:
    xml.'**'.each { node ->
        if (node.name() == "enabled") {camstate = node.text()}
        if (node.name() == "sensitivityLevel") {sl = node.text()}
        if (node.name() == "rowGranularity") {rg = node.text()}
        if (node.name() == "columnGranularity") {cg = node.text()}
    }
    if (debug) {log.debug "Extracted Gpath: columnGranularity: " + cg}
    if (debug) {log.debug "Extracted Gpath: rowGranularity: " + rg}
    if (debug) {log.debug "Extracted Gpath: sensitivityLevel: " + sl}
    if (debug) {log.debug "Extracted Gpath: enabled.text(): " + camstate}
    
    if (camstate == newstate) {
        if (newstate == "true") {newstate = "enabled"} else {newstate = "disabled"}
        log.info "OK, Motion is already " + newstate
        // in case they get out of sync
        if (devstate != newstate) {sendEvent(name:"Motion", value:newstate)}
        sendEvent(name:"zStatus",value:"OK")
        return
    }
   
    // When using PUT, we must include the entire collection holding the attribute
    // we need to change, which means including other attributes that are subject
    // to change by other means (e.g. web gui). Thus, we need to extract the current
    // attribute values with GET, pull out the values we need to carry over, and
    // include them in the XML string that we construct and provide to the PUT request.

    strXML =\
 "<MotionDetection version=\"1.0\" xmlns=\"http://www.hikvision.com/ver10/XMLSchema\">" +\
 "<id>1</id><enabled>" + newstate + "</enabled>" +\
 "<regionType>grid</regionType><Grid><rowGranularity>" + rg + "</rowGranularity><columnGranularity>" + cg + "</columnGranularity></Grid>" +\
 "<MotionDetectionRegionList><sensitivityLevel>" + sl + "</sensitivityLevel>" +\
 "</MotionDetectionRegionList></MotionDetection>"
    
    log.info "PUT /MotionDetection/1/enabled=" + newstate
    
    xml = SendPutRequest("/MotionDetection/1", strXML)
    
    if (strMsg == "OK") {
        if (newstate == "true") {newstate = "enabled"} else {newstate = "disabled"}
        log.info "OK, Motion is now " + newstate
        sendEvent(name:"Motion", value:newstate)
        sendEvent(name:"zStatus", value:"OK")
    } else {
        log.error "PUT Error: " + strMsg
        if (strMsg.contains("code: 403") && strMsg.contains("Forbidden")) {
            etype = "NA"
            log.error "Operator does not have \"Remote Parameters Settings\" option selected in user account settings."
        }
        sendEvent(name:"zStatus", value:"ERR")
    }
   
}
//******************************************************************************
// Set PIR
//******************************************************************************
private SetPIR(String newstate) {
    def devstate = device.currentValue("PIRSensor")
    log.info "GET /ISAPI/WLAlarm/PIR"
    def xml = SendGetRequest("/ISAPI/WLAlarm/PIR")
    if (strMsg != "OK") {
        def errcd = LogGETError()
        sendEvent(name:"zStatus:", value:errcd)
        return
    }

    def camstate = xml.enabled.text()
    def cname = xml.name.text() // carry over current alarm name

    if (debug) {log.debug "Extracted: xml.enabled.text(): " + camstate}
    if (debug) {log.debug "Extracted: xml.name.text(): " + cname}
    
    if (camstate == newstate) {
        if (newstate == "true") {newstate = "enabled"} else {newstate = "disabled"}
        log.info "OK, PIR is already " + newstate
        // resync if needed
        if (devstate != newstate) {sendEvent(name:"PIRSensor", value:newstate)}
        sendEvent(name:"zStatus",value:"OK")
        return
    }
    
    strXML = "<PIRAlarm version=\"1.0\" xmlns=\"http://www.hikvision.com/ver10/XMLSchema\">" +\
"<enabled>" + newstate + "</enabled><name>" + cname + "</name></PIRAlarm>"

    log.info "PUT /ISAPI/WLAlarm/PIR/enabled=" + newstate

    xml = SendPutRequest("/ISAPI/WLAlarm/PIR", strXML)

    if (strMsg == "OK") {
        if (newstate == "true") {newstate = "enabled"} else {newstate = "disabled"}
        log.info "OK, PIRSensor is now " + newstate
        sendEvent(name:"PIRSensor", value:newstate)
        sendEvent(name:"zStatus",value:"OK")
    } else {
        log.error "PUT Error: " + strMsg
        if (strMsg.contains("code: 403") && strMsg.contains("Forbidden")) {
            etype = "NA"
            log.error "Operator does not have \"Remote Parameters Settings\" option selected in user account settings."
        }
        sendEvent(name:"zStatus",value:"ERR")
    }
}
//******************************************************************************
// Send GET Request - return strMsg=OK with XML or strMsg=Error message
//******************************************************************************
private SendGetRequest(String strPath) {
    def xml = ""
    String credentials = device.getDataValue("CamID")
    def headers = [:] 
    headers.put("HOST", deviceIP + ":" + devicePort)
    headers.put("Authorization", "Basic " + credentials)
//    headers.put("Content-Type", "application/xml")

    def parms = [
        uri: "http://" + deviceIP + ":" + devicePort + strPath,
        headers: headers,
        body: "",
        requestContentType: "application/xml" // THIS was the silver bullet
    ]
    if (debug) {log.debug "GET Parms:" + parms}
    
    try {httpGet(parms) 
        { response ->
            if (debug) {
                log.debug "GET response.status: " + response.getStatus()
                log.debug "GET response.contentType: " + response.getContentType()
            }
            if (response.status == 200) {
                strMsg = "OK"
                xml = response.data
                if (debug) {
                    xml.'**'.each { node ->
                    log.debug "GPATH: " + node.name() + ": " + node.text()}
                }
            } else {
                strMsg = response.status
            }
        }}
    catch (Exception e) {
        strMsg = e.message
    }
    return(xml)
}
//******************************************************************************
// Send PUT Request - return strMsg=OK with XML or strMsg=Error message
//******************************************************************************
private SendPutRequest(String strPath, String strXML) {
    def xml = ""
    def credentials = device.getDataValue("CamID")
    def headers = [:] 
    headers.put("HOST", deviceIP + ":" + devicePort)
    headers.put("Authorization", "Basic " + credentials)
    headers.put("Content-Type", "application/xml") // ***************

    def parms = [
        uri: "http://" + deviceIP + ":" + devicePort + strPath,
        headers: headers,
        body: strXML,
        requestContentType: "application/xml" // THIS was the silver bullet
    ]
    if (debug) {log.debug "PUT Parms:" + parms}
    
    try {httpPut(parms) { response ->
        if (debug) {
            log.debug "PUT response.status: " + response.getStatus()
            log.debug "PUT response.contentType: " + response.getContentType()
        }
        if (response.status == 200) {
            xml = response.data
            strMsg = "OK"}
         else {
            strMsg = response.status
         }
    }}
    catch (Exception e) {
        strMsg = e.message
    }
    return(xml)
}
//******************************************************************************
// Ping the ip entered, return true/false
//******************************************************************************
private PingOK(String ip) {
    try {
        def pingData = hubitat.helper.NetworkUtils.ping(deviceIP,3)
        int pr = pingData.packetsReceived.toInteger()
        if (pr == 3) {
            return(true)
        } else {
            return(false)
        }
    }
    catch (Exception e) {
        strMsg = e.message
        log.info "Ping error: " + strMsg
        return(false)
    }
}
