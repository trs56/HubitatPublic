//******************************************************************************
//* Hikvision Camera Controller - Device Driver for Hubitat Elevation
//******************************************************************************
//*  Copyright 2024 Thomas R Schmidt, Wildwood IL
//*  This program is free software: you can redistribute it and/or modify
//*  it under the terms of the GNU General Public License as published by
//*  the Free Software Foundation.
//******************************************************************************
// This driver allows you to trigger Alarm Input events on the camera and
// enable/disable Motion Detection features by running its custom commands
// from your rules and apps.
//
// The User Guide is required reading.
// https://github.com/trs56/HubitatPublic/blob/main/HikvisionCameraController_UserGuide.md
//
// Change Log
// Date        Version    Release Notes
// 01-03-2024  1.0        First Release
//
// Key Credits:
// https://community.hubitat.com/t/can-httpget-read-raw-html/16380
// At the end of this old post, Chuck showed me one way to get raw xml back from httpget.
// Just don't use that escapeXml method or you'll be in for a big surprise.
// But I'm still using it in here for debug logging the xml because it makes it
// look pretty and that is taken straight out of the Apache doc for that method.
//********************************************************************************
metadata {
    definition (name: "Hikvision Camera Controller", 
                author: "Thomas R Schmidt", namespace: "trs56", // github userid
                singleThreaded: true) // ??
    {
        // Capability to get the device into those with actionable commands
        // that can be run from rules or apss. This capability has no required
        // commands or attributes of its own. Its all custom.
        capability "Actuator"

        command "AlarmOff"
        command "AlarmOn"
	command "DisableAlarmIn"
        command "DisableIntrusion"
        command "DisableLineCross"
        command "DisableMotion"
        command "DisablePIR"
    	command "EnableAlarmIn"
        command "EnableIntrusion"
        command "EnableLineCross"
        command "EnableMotion"
        command "EnablePIR"
        command "GetStatus"
        
        attribute "AlarmIn", "STRING"    // Enabled/Disabled state of Alarm Input Handling
        attribute "AlarmInSt", "STRING"  // Powered state of wired Alarm Input Port (active/inactive)
        attribute "AlarmOut", "STRING"   //    "      "                  Output
        attribute "Intrusion", "STRING"  // Enabled/Disabled state of Intrusion Event
        attribute "LineCross", "STRING"  // ""
        attribute "MotionD", "STRING"    // ""
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
    l << "https://github.com/trs56/HubitatPublic/blob/main/HikvisionCameraController_UserGuide.md"
    l << " "
    l << "Thank you. You may now hand over your credentials and proceed."
    def lr = l.reverse()
    lr.each {log.info it}
    sendEvent(name:"zStatus",
              value:"Hello! If you are adding your first camera, PLEASE OPEN THE LOG NOW, If not, please proceed.")
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
    SendGetRequest("/System/deviceInfo", "GPATH")
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
    // Also signals Ok2Run
    SavingPreferences = true
    GetStatus()
    SavingPreferences = false
    if (strMsg == "ERR") {strMsg = "Oh oh, unexpected errors have occurred. You may need to call the help desk. Check the log."}
    if (strMsg == "NA") {strMsg = "Yay! Camera found but some features are not available. "}
    if (strMsg == "OK") {strMsg = "Yay! Camera found and all features are available. "}
    strMsg = strMsg + "Please run any command to put zStatus attribute into " + 
    "operational mode. This is the last message you will receive here."
    sendEvent(name:"zStatus",value:strMsg)
    log.info "Device update completed"
}
//******************************************************************************
// Determine if it's ok to run any command, called by all driver commands below
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
        log.error "Not allowed to run"
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
void AlarmOn() {
    log.info "Received request to Trigger Alarm Out"
    if (!Ok2Run("AlarmOut")) {return}
    SetAlarm("active")
}
//******************************************************************************
void AlarmOff() {
    log.info "Received request to reset Alarm Out"
    if (!Ok2Run("AlarmOut")) {return}
    SetAlarm("inactive")
}
//******************************************************************************
void EnableAlarmIn() {
    String AlarmInPath = "/ISAPI/System/IO/inputs/1"
    log.info "Received request to Enable Alarm Input Handling"
    if (!Ok2Run("AlarmIn")) {return}
    SetFeatureState("AlarmIn","true", AlarmInPath)
}
//******************************************************************************
void DisableAlarmIn() {
    String Path = "/ISAPI/System/IO/inputs/1"
    log.info "Received request to Disable Alarm Input Handling"
    if (!Ok2Run("AlarmIn")) {return}
    SetFeatureState("AlarmIn","false",Path)
}
//******************************************************************************
void EnableIntrusion() {
    String Path = "/ISAPI/Smart/FieldDetection/1"
    log.info "Received request to Enable Intrusion"
    if (!Ok2Run("Intrusion")) {return}
    SetFeatureState("Intrusion","true",Path)
}
//******************************************************************************
void DisableIntrusion() {
    String Path = "/ISAPI/Smart/FieldDetection/1"
    log.info "Received request to Disable Intrusion"
    if (!Ok2Run("Intrusion")) {return}
    SetFeatureState("Intrusion","false",Path)
}
//******************************************************************************
void EnableLineCross() {
    String Path = "/ISAPI/Smart/LineDetection/1"
    log.info "Received request to Enable LineCross"
    if (!Ok2Run("LineCross")) {return}
    SetFeatureState("LineCross","true",Path)
}
//******************************************************************************
void DisableLineCross() {
    String Path = "/ISAPI/Smart/LineDetection/1"
    log.info "Received request to Disable LineCross"
    if (!Ok2Run("LineCross")) {return}
    SetFeatureState("LineCross","false",Path)
}
//******************************************************************************
void EnableMotion() {
    String Path = "/MotionDetection/1"
    log.info "Received request to Enable Motion"
    if (!Ok2Run("MotionD")) {return}
    SetFeatureState("MotionD","true",Path)
}
//******************************************************************************
void DisableMotion() {
    String Path = "/MotionDetection/1"
    log.info "Received request to Disable Motion"
    if (!Ok2Run("MotionD")) {return}
    SetFeatureState("MotionD","false",Path)
}
//******************************************************************************
void EnablePIR() {
    String Path = "/ISAPI/WLAlarm/PIR"
    log.info "Received request to Enable PIR Sensor"
    if (!Ok2Run("PIRSensor")) {return}
    SetFeatureState("PIRSensor","true",Path)
}
//******************************************************************************
void DisablePIR() {
    String Path = "/ISAPI/WLAlarm/PIR"
    log.info "Received request to Disable PIR Sensor"
    if (!Ok2Run("PIRSensor")) {return}
    SetFeatureState("PIRSensor","false",Path)
}
//******************************************************************************
// Get Current State of all Features and Update Attributes
//******************************************************************************
def GetStatus() {
    Boolean err = false
    Boolean na = false
    String AlarmInPath = "/ISAPI/System/IO/inputs/1"
    String AlarmOutPath = "/IO/status"
    String IntrusionPath = "/ISAPI/Smart/FieldDetection/1"
    String LineCrossPath = "/ISAPI/Smart/LineDetection/1"
    String MotionDPath = "/MotionDetection/1"
    String PIRSensorPath = "/ISAPI/WLAlarm/PIR"
    String camstate = " "
    log.info "Request received to Get Status and update attributes"
    if (!SavingPreferences && !Ok2Run("GetStatus")) {return}
    camstate = GetCameraInfo()
    if (camstate == "NA") {na = true} 
    if (camstate == "ERR" || camstate == "CRED") {
        strMsg = camstate
        if (SavingPreferences) {return}
        sendEvent(name:"zStatus",value:camstate)
        return} 
    camstate = GetFeatureState("AlarmIn",AlarmInPath)
    if (camstate == "NA") {na = true} 
    if (camstate == "ERR" || camstate == "CRED") {
        strMsg = camstate
        if (SavingPreferences) {return}
        sendEvent(name:"zStatus",value:camstate)
        return} 
    camstate = GetFeatureState("AlarmOut",AlarmOutPath)
    if (camstate == "NA") {na = true} 
    if (camstate == "ERR" || camstate == "CRED") {
        strMsg = camstate
        if (SavingPreferences) {return}
        sendEvent(name:"zStatus",value:camstate)
        return} 
    camstate = GetFeatureState("MotionD",MotionDPath)
    if (camstate == "NA") {na = true} 
    if (camstate == "ERR" || camstate == "CRED") {
        strMsg = camstate
        if (SavingPreferences) {return}
        sendEvent(name:"zStatus",value:camstate)
        return} 
    camstate = GetFeatureState("PIRSensor",PIRSensorPath)
    if (camstate == "NA") {na = true} 
    if (camstate == "ERR" || camstate == "CRED") {
        strMsg = camstate
        if (SavingPreferences) {return}
        sendEvent(name:"zStatus",value:camstate)
        return} 
    camstate = GetFeatureState("Intrusion",IntrusionPath)
    if (camstate == "NA") {na = true} 
    if (camstate == "ERR" || camstate == "CRED") {
        strMsg = camstate
        if (SavingPreferences) {return}
        sendEvent(name:"zStatus",value:camstate)
        return} 
    camstate = GetFeatureState("LineCross",LineCrossPath)
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
// Get the Current State of a Feature and update the feature Attribute if needed
// Return feature state or error code
//******************************************************************************
private GetFeatureState(String Feature, String Path) {
    String camstate = " "
    String devstate = " "
    devstate = device.currentValue(Feature)
    log.info "GET " + Path
    xml = SendGetRequest(Path, "GPATH")
    //  If the response from the GET request is successful, the xml returned
    //  will be presented in format requested and strMsg will be "OK".
    //  Otherwise, xml will be null and strMsg will contain the error message.
    //  This applies to all calls to the SendGet and SendPut Request methods.
    if (strMsg == "OK") {
        if (Feature == "AlarmOut") {
            camstate = xml.IOPortStatus[0].ioState.text()
            if (camstate != device.currentValue("AlarmInSt")) {sendEvent(name:"AlarmInSt", value:camstate)}
            log.info "Alarm In: " + camstate 
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
// Get Camera Info and update Data fields
//******************************************************************************
private GetCameraInfo() {
    log.info "GET /System/deviceInfo"
    def xml = SendGetRequest("/System/deviceInfo","GPATH")
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
        if (firmware != "") {
            log.info "Firmware: " + firmware
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
// Trigger Alarm Out On/Off (high/low)
//******************************************************************************
private SetAlarm(String newstate) {
    String devstate = device.currentValue("AlarmOut")
    String camstate = " "
    String alistate = " " // alarm input state
    log.info "GET /IO/status"
    def xml = SendGetRequest("/IO/status", "GPATH")
    if (strMsg != "OK") {
        String errcd = LogGETError()
        sendEvent(name:"zStatus",value:errcd)
        return
    }
    camstate = xml.IOPortStatus[1].ioState.text()
    if (debug) {log.info "Extracted: xml.IOPortStatus[1].ioState.text(): " + camstate}
    if (camstate == newstate) {
        log.info "OK, already " + newstate
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
// Enable/Disable Motion Detection Features
//******************************************************************************
private SetFeatureState(String Feature, String newstate, String Path) {
    def devstate = device.currentValue(Feature)
    log.info "GET " + Path
    def xml = SendGetRequest(Path, "XML")
    if (strMsg != "OK") {
        def errcd = LogGETError()
        sendEvent(name:"zStatus:", value:errcd)
        return
    }
    // Find first occurence, Line Cross and Intrusion have sub-features
    // that also include the enabled element.
    int i = xml.indexOf("<enabled>")
    // this should never happen, famous last words
    if (i == -1) {
        strMsg = "Unexpected XML structure, <enabled> element not found"
        log.error strMsg
        sendEvent(name:"zStatus:", value:"ERR")
        return
    }
    // Extract current state from xml
    String camstate = xml.substring(i+9,i+13)
    if (camstate == "fals") {camstate = "false"}
    if (debug) {log.debug "Extracted XML <enabled>: " + camstate}
    // ditto
    if (camstate != "true" && camstate != "false") {
        strMsg = "Unexpected XML value, enabled element in XML is not true/false"
        log.error strMsg
        sendEvent(name:"zStatus:", value:"ERR")
        return
    }
    if (camstate == newstate) {
        if (newstate == "true") {newstate = "enabled"} else {newstate = "disabled"}
        log.info "OK, " + Feature + " is already " + newstate
        if (devstate != newstate) {sendEvent(name:Feature, value:newstate)}
        sendEvent(name:"zStatus",value:"OK")
        return
    }
    if (newstate == "true") {
        xml = xml.replaceFirst("<enabled>false<", "<enabled>true<")
    } else {
        xml = xml.replaceFirst("<enabled>true<", "<enabled>false<")
    }
    log.info "PUT " + Path + "/enabled=" + newstate

    xml = SendPutRequest(Path, xml)

    if (strMsg == "OK") {
        if (newstate == "true") {newstate = "enabled"} else {newstate = "disabled"}
        log.info "OK, " + Feature + " is now " + newstate
        sendEvent(name:Feature, value:newstate)
        sendEvent(name:"zStatus",value:"OK")
    } else {
        log.error "PUT Error: " + strMsg
        if (strMsg.contains("code: 403") && strMsg.contains("Forbidden")) {
            etype = "NA"
            log.error "Operator does not have Remote Notify option selected in user account settings."
        }
        sendEvent(name:"zStatus",value:"ERR")
    }
}
//******************************************************************************
// Send GET Request - return strMsg=OK with XML or strMsg=Error message
//******************************************************************************
private SendGetRequest(String strPath, String rtype) {
    def xml = ""
    String credentials = device.getDataValue("CamID")
    def headers = [:] 
    def parms = [:]
    headers.put("HOST", deviceIP + ":" + devicePort)
    headers.put("Authorization", "Basic " + credentials)
    parms.put("uri", "http://" + deviceIP + ":" + devicePort + strPath)
    parms.put("headers", headers)
    parms.put("requestContentType", "application/xml")
    if (rtype == "XML") {parms.put ("textParser", true)}
    
    try {httpGet(parms) 
        { response ->
            if (debug) {
                log.debug "GET response.status: " + response.getStatus()
                log.debug "GET response.contentType: " + response.getContentType()
            }
            if (response.status == 200) {
                strMsg = "OK"
                if (rtype == "GPATH") {
                    xml = response.data
                    if (debug) {xml.'**'.each { node ->
                                log.debug "GPATH: " + node.name() + ": " + node.text()}}
                } else {
                    xml = response.data.text
                    if (debug) {log.debug groovy.xml.XmlUtil.escapeXml(xml)}
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
// Send PUT Request - return strMsg=OK with XML or GPATH or strMsg=Error message
//******************************************************************************
private SendPutRequest(String strPath, String strXML) {
    def xml = ""
    def credentials = device.getDataValue("CamID")
    def headers = [:] 
    def parms = [:]

    headers.put("HOST", deviceIP + ":" + devicePort)
    headers.put("Authorization", "Basic " + credentials)
    headers.put("Content-Type", "application/xml")

    parms.put("uri", "http://" + deviceIP + ":" + devicePort + strPath)
    parms.put("headers", headers)
    parms.put("body", strXML)
    parms.put("requestContentType", "application/xml")

    try {httpPut(parms) { response ->
        if (debug) {
            log.debug "PUT response.status: " + response.getStatus()
            log.debug "PUT response.contentType: " + response.getContentType()
        }
        if (response.status == 200) {
            xml = response.data
            strMsg = "OK"}
         else {
            strMsg = "ERR:" + response.status
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
