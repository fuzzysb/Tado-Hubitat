/**
 *  Tado Connect
 *
 *  Copyright 2016 Stuart Buchanan
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 * 22/11/2023 v2.9 Adjusted to OAuth 2 authentication flow & update to logging process including toggle
 * 27/04/2018 v2.8 fixed issue with null values when trying to retrieve current setpoint when null as the try and catch arent working as expected.
 * 27/04/2018 v2.7 Modified for Hubitat
 * 07/02/2018 v2.7 Added some new try catch blocks around parse capability as there were exceptioons after v2.1 occuring for air conditioners, this now works correctly
 * 06/02/2018 v2.6 Fixed Commands for those with Heat Cool that do not support Fan Modes
 * 08/06/2017 v2.5 Amended bug where Hot water type was set to WATER, Instead or HOT_WATER, with thanks to @invisiblemountain
 * 08/06/2017 v2.4 Added Device name to DNI, trying to avaid issue with multiple devices in a single Zone
 * 26/05/2017 v2.3 removed erronous jsonbody statements in the coolCommand Function.
 * 26/05/2017 v2.2 Corrected bug with parseCapability function as this was returning the map instead of the value, this would account for lots of strange behaviour.
 * 25/05/2017 v2.1 Added support for Air Condiioners which have a mandatory swing field for all Commands, corrected prevois bugs in v2.0, thanks again to @Jnick
 * 20/05/2017 v2.0 Added support for Air Condiioners which have a mandatory swing field in the heating & cool Commands, thanks again to @Jnick
 * 17/05/2017 v1.9 Corrected issue with the wrong temp unit being used on some thermostat functions when using Farenheit, many thanks again to @Jnick for getting the logs to help diagnose this.
 * 04/05/2017 v1.8 Corrected issue with scheduling which was introduced in v1.7 with merge of pull request. Many thanks to @Jnick for getting the logs to help diagnose this.
 * 17/04/2017 v1.7 General Bugfixes around Tado user presence with thanks to @sipuncher
 * 14/04/2017 v1.6 fixed defects in user presence device polling
 * 06/04/2017 v1.5 scheduled of tado user status every minute (Thanks to @sipuncher for pointing out my mistake)
 * 03/04/2017 v1.4 Added ability to have your Tado Users created as Smarthings Virtual Presence Sensors for use in routines etc..
 * 03/01/2017 v1.3 Corrected Cooling Commands and Set Points issue with incorrect DNI statement with thanks to Richard Gregg
 * 03/12/2016 v1.2 Corrected Values for Heating and Hot Water set Points
 * 03/12/2016 v1.1 Updated to Support Multiple Hubs, and fixed bug in device discovery and creation, however all device types need updated also.
 * 26/11/2016 V1.0 initial release
 */

import java.text.DecimalFormat
import groovy.json.JsonSlurper
import groovy.json.JsonOutput

private apiUrl() 			{ "https://my.tado.com" }
private getApiUrl()		    { "https://my.tado.com" }
private getOauthTokenUri()  { "https://auth.tado.com" }
private getOauthTokenPath() { "/oauth/token" }
private getVendorName() 	{ "Tado" }
private getVendorIcon()		{ "https://dl.dropboxusercontent.com/s/fvjrqcy5xjxsr31/tado_128.png" }
private getVendorAuthPath()	{ "/oauth2/authorize" }
private getVendorTokenPath(){ "https://auth.tado.com/oauth/token" }
private getClientId() 		{ settings.clientId }
private getClientSecret() 	{ settings.clientSecret }

private getRefreshToken()  { state.refreshToken }

private getCallbackUrl()	{ getServerUrl()+ "/oauth/callback?access_token=${state.accessToken}" }
private getBuildRedirectUrl() { getServerUrl() + "/oauth/initialize?access_token=${state.accessToken}" }
private getServerUrl() 		{ return getFullApiServerUrl() }

 // Automatically generated. Make future change here.
definition(
    name: "Tado (Connect)",
    namespace: "fuzzysb",
    author: "Stuart Buchanan",
    description: "Tado Integration, This SmartApp supports all Tado Products. (Heating Thermostats, Extension Kits, AC Cooling & Radiator Valves.)",
    category: "Heating",
	iconUrl:   "https://dl.dropboxusercontent.com/s/fvjrqcy5xjxsr31/tado_128.png",
	iconX2Url: "https://dl.dropboxusercontent.com/s/jyad58wb28ibx2f/tado_256.png",
	oauth: true,
    singleInstance: false
) {
	appSetting "clientId"
	appSetting "clientSecret"
	appSetting "serverUrl"
}

preferences {
	page(name: "startPage", title: "Tado (Connect) Integration", content: "startPage", install: false)
	page(name: "Credentials", title: "Fetch Tado Credentials", content: "authPage", install: false)
	page(name: "mainPage", title: "Tado (Connect) Integration", content: "mainPage")
	page(name: "completePage", title: "${getVendorName()} is now connected to Hubitat!", content: "completePage")
	page(name: "listDevices", title: "Tado Devices", content: "listDevices", install: false)
    page(name: "listUsers", title: "Tado Users", content: "listUsers", install: false)
	page(name: "advancedOptions", title: "Tado Advanced Options", content: "advancedOptions", install: false)
	page(name: "badCredentials", title: "Invalid Credentials", content: "badAuthPage", install: false)
}
mappings {
	path("/receivedHomeId"){action: [POST: "receivedHomeId", GET: "receivedHomeId"]}
}

def startPage() {
    if (state.homeId) { return mainPage() }
    else { return authPage() }
}

def authPage() {
    logDebug "In authPage"

	def description
	def uninstallAllowed = false
	def oauthTokenProvided = false

	if (!state.accessToken) {
		logDebug "About to create access token."
		state.accessToken = createAccessToken()
		logDebug "Access token is : ${state.accessToken}"
	}

	def redirectUrl = getBuildRedirectUrl()
	logDebug "Redirect url = ${redirectUrl}"

	if (state.authToken) {
		description = "Tap 'Next' to proceed"
		uninstallAllowed = true
		oauthTokenProvided = true
	} else {
		description = "Click to enter Credentials."
	}

	if (!oauthTokenProvided) {
		logDebug "Showing the login page"
		return dynamicPage(name: "Credentials", title: "Authorize Connection", nextPage:mainPage, uninstall: false , install:false) {
			section("Enter Application Details...") {
				paragraph "You can get clientID and clientSecret from Tado. These are used for the initial authtoken request."
				input(name: 'clientId', title: 'Client ID', type: 'text', required: true)
				input(name: 'clientSecret', title: 'Client secret (save this box before pressing the button below)', type: 'text', required: true, submitOnChange: true )
			}
			section("Generate Username and Password") {
				input "username", "text", title: "Your Tado Username", required: true, defaultValue: ""
				input "password", "password", title: "Your Tado Password", required: true
			}
		}
	}


}

def mainPage() {
 	if (!state.accessToken){
  		createAccessToken()
  		getToken()
    }

    if (!state.authToken){
        oauthAccessTokenGrant() }

  	getidCommand()
    getTempUnitCommand()
  	logDebug "Logging debug homeID: ${state.homeId}"
	   if (state.homeId) {
       return completePage()
       } else {
         return badAuthPage()
       }
}

def completePage(){
	def description = "Tap 'Next' to proceed"
			return dynamicPage(name: "completePage", title: "Credentials appear to be accepted, but you can update details below as required", uninstall:true, install:false,nextPage: listDevices) {
	    		section {
                    href url: buildRedirectUrl("receivedHomeId"), style:"embedded", required:false, title:"${getVendorName()} is now connected to Hubitat!", description:description
                }
                section("Logging options"){
	    			input "debugLogging", "bool", title: "Enable debug logging", defaultValue: false
		    	}
			    section(hideable: true, hidden: true, "Authentication information, expand to update") {
				    paragraph "You can get clientID and clientSecret from Tado. These are used for the initial authtoken request."
    				input "clientId","text", title: "Client ID", required: false
	    			input "clientSecret", "text", title: "Client secret (save this box before pressing the button below)", required: true, submitOnChange: true
		    		input "username", "text", title: "Your Tado Username", required: true
			    	input "password", "password", title: "Your Tado Password", required: true
			    }

			}
}

def badAuthPage(){
	logDebug "In badAuthPage"
    log.error "login result false"
       		return dynamicPage(name: "badCredentials", title: "Bad Tado Credentials", install:false, uninstall:true, nextPage: Credentials) {
				section("") {
					paragraph "Please check your username and password"
           		}
            }
}

def advancedOptions() {
	logDebug "In Advanced Options"
	def options = getDeviceList()
	dynamicPage(name: "advancedOptions", title: "Select Advanced Options", install:true) {
    	section("Default temperatures for thermostat actions. Please enter desired temperatures") {
      	input("defHeatingTemp", "number", title: "Default Heating Temperature?", required: true)
      	input("defCoolingTemp", "number", title: "Default Cooling Temperature?", required: true)
		}
		section("Tado Override Method") {
      	input("manualmode", "enum", title: "Default Tado Manual Overide Method", options: ["TADO_MODE","MANUAL"], required: true)
    	}
        section(){
    		if (getHubID() == null){
        		input(
            		name		: "myHub"
            		,type		: "hub"
            		,title		: "Select your hub"
            		,multiple		: false
            		,required		: true
            		,submitOnChange	: true
        		)
     		} else {
        		paragraph("Tap done to finish the initial installation.")
     		}
		}
    }
}

def listDevices() {
	logDebug "In listDevices"
	def options = getDeviceList()
	dynamicPage(name: "listDevices", title: "Choose devices", install:false, uninstall:true, nextPage: listUsers) {
		section("Devices") {
			input "devices", "enum", title: "Select Device(s)", required: false, multiple: true, options: options, submitOnChange: true
		}
	}
}

def listUsers() {
	logDebug "In listUsers"
	def options = getUserList()
	dynamicPage(name: "listUsers", title: "Choose users you wish to create a Virtual Smarthings Presence Sensors for", install:false, uninstall:true, nextPage: advancedOptions) {
		section("Users") {
			input "users", "enum", title: "Select User(s)", required: false, multiple: true, options: options, submitOnChange: true
		}
	}
}

def getToken(){
  if (!state.accessToken) {
		try {
			getAccessToken()
			logDebug "Creating new Access Token:" $state.accessToken
		} catch (ex) {
			logDebug "Did you forget to enable OAuth in SmartApp IDE settings"
            logDebug "Exception :" ex
		}
	}
}

def receivedHomeId() {
	logDebug "In receivedToken"

	def html = """
        <!DOCTYPE html>
        <html>
        <head>
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>${getVendorName()} Connection</title>
        <style type="text/css">
            * { box-sizing: border-box; }
            @font-face {
                font-family: 'Swiss 721 W01 Thin';
                src: url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-thin-webfont.eot');
                src: url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-thin-webfont.eot?#iefix') format('embedded-opentype'),
                     url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-thin-webfont.woff') format('woff'),
                     url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-thin-webfont.ttf') format('truetype'),
                     url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-thin-webfont.svg#swis721_th_btthin') format('svg');
                font-weight: normal;
                font-style: normal;
            }
            @font-face {
                font-family: 'Swiss 721 W01 Light';
                src: url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-light-webfont.eot');
                src: url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-light-webfont.eot?#iefix') format('embedded-opentype'),
                     url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-light-webfont.woff') format('woff'),
                     url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-light-webfont.ttf') format('truetype'),
                     url('https://s3.amazonaws.com/smartapp-icons/Partner/fonts/swiss-721-light-webfont.svg#swis721_lt_btlight') format('svg');
                font-weight: normal;
                font-style: normal;
            }
            .container {
                width: 560px;
                padding: 40px;
                /*background: #eee;*/
                text-align: center;
            }
            img {
                vertical-align: middle;
            }
            img:nth-child(2) {
                margin: 0 30px;
            }
            p {
                font-size: 2.2em;
                font-family: 'Swiss 721 W01 Thin';
                text-align: center;
                color: #666666;
                margin-bottom: 0;
            }
        /*
            p:last-child {
                margin-top: 0px;
            }
        */
            span {
                font-family: 'Swiss 721 W01 Light';
            }
        </style>
        </head>
        <body>
            <div class="container">
                <img src=""" + getVendorIcon() + """ alt="Vendor icon" />
                <img src="https://s3.amazonaws.com/smartapp-icons/Partner/support/connected-device-icn%402x.png" alt="connected device icon" />
                <img src="https://cdn.shopify.com/s/files/1/2575/8806/t/20/assets/logo-image-file.png" alt="Hubitat logo" />
                <p>Tap 'Done' to continue to Devices.</p>
			</div>
        </body>
        </html>
        """
	render contentType: 'text/html', data: html
}

def buildRedirectUrl(endPoint) {
	logDebug "In buildRedirectUrl"
	logDebug("returning: " + getServerUrl() + "/${hubUID}/apps/${app.id}/${endPoint}?access_token=${state.accessToken}")
	return getServerUrl() + "/${hubUID}/apps/${app.id}/${endPoint}?access_token=${state.accessToken}"
}

def getDeviceList() {
  def TadoDevices = getZonesCommand()
  logDebug "In getDeviceList"
  return TadoDevices.sort()

}

def getUserList() {
  def TadoUsers = getMobileDevicesCommand()
  if (TadoUser != null) {
	return TadoUsers.sort()
  }
}

def installed() {
	logDebug "Installed with settings: ${settings}"
	initialize()
}

def updated() {
	logDebug "Updated with settings: ${settings}"
  	unsubscribe()
	unschedule()
	initialize()
}

def uninstalled() {
  logDebug "Uninstalling Tado (Connect)"
  revokeAccessToken()
  removeChildDevices(getChildDevices())
  logDebug "Tado (Connect) Uninstalled"
}

def initialize() {
	log.info "Initialized with settings: ${settings}"
	// Pull the latest device info into state
	getDeviceList();
    def children = getChildDevices()
    if(settings.devices) {
    	settings.devices.each { device ->
        logDebug("Devices Inspected ${device.inspect()}")
		def item = device.tokenize('|')
        def deviceType = item[0]
        def deviceId = item[1]
        def deviceName = item[2]
        def existingDevices = children.find{ d -> d.deviceNetworkId.contains(deviceId + "|" + deviceType) }
        log.info("existingDevices Inspected ${existingDevices.inspect()}")
    	if(!existingDevices) {
          logDebug("Some Devices were not found....creating Child Device ${deviceName}")
          try {
            if (deviceType == "HOT_WATER")
            {
              logDebug("Creating Hot Water Device ${deviceName}")
              createChildDevice("Tado Hot Water Control", deviceId + "|" + deviceType + "|" + state.accessToken + "|" + devicename, "${deviceName}", deviceName)
            }
            if (deviceType == "HEATING")
            {
              logDebug("Creating Heating Device ${deviceName}")
              createChildDevice("Tado Heating Thermostat", deviceId + "|" + deviceType + "|" + state.accessToken + "|" + devicename, "${deviceName}", deviceName)
            }
            if (deviceType == "AIR_CONDITIONING")
            {
              logDebug("Creating Air Conditioning Device ${deviceName}")
              createChildDevice("Tado Cooling Thermostat", deviceId + "|" + deviceType + "|" + state.accessToken + "|" + devicename, "${deviceName}", deviceName)
            }
            if (deviceType == "Home")
            {
              logDebug("Creating Home Device ${deviceName}")
              createChildDevice("Tado Home", deviceId + "|" + deviceType + "|" + state.accessToken + "|" + devicename, "${deviceName}", deviceName)
            }
 			} catch (Exception e)
            {
					log.error "Error creating device: ${e}"
			}
    		}
		}
    }

    getUserList();
       if(settings.users) {
    	settings.users.each { user ->
        logDebug("Devices Inspected ${user.inspect()}")
		def item = user.tokenize('|')
        def userId = item[0]
        def userName = item[1]
        def existingUsers = children.find{ d -> d.deviceNetworkId.contains(userId + "|" + userName) }
        log.info("existingUsers Inspected ${existingUsers.inspect()}")
    	if(!existingUsers) {
          logDebug("Some Users were not found....creating Child presence Device ${userName}")
          try
          	{
              createChildDevice("Tado User Presence", userId + "|" + userName + "|" + state.accessToken, "${userName}", userName)
 			} catch (Exception e)
            {
					log.error "Error creating device: ${e}"
			}
    		}
		}
    }


	// Do the initial poll
    getInititialDeviceInfo()

	// Schedule it to run every 5 minutes
	runEvery5Minutes("poll")
    runEvery5Minutes("userPoll")
}

def getInititialDeviceInfo(){
	logDebug "getInititialDeviceInfo"
	getDeviceList();
	def children = getChildDevices()
	if(settings.devices) {
    settings.devices.each { device ->
      logDebug("Devices Inspected ${device.inspect()}")
      def item = device.tokenize('|')
      def deviceType = item[0]
      def deviceId = item[1]
      def deviceName = item[2]
      def existingDevices = children.find{ d -> d.deviceNetworkId.contains(deviceId + "|" + deviceType) }
      if(existingDevices) {
        existingDevices.getInitialDeviceinfo()
      }
	   }
  }

}
def getHubID(){
	def hubID
    if (myHub){
        hubID = myHub.id
    } else {
        logDebug("hub type is ${location.hubs[0].type}")
        def hubs = location.hubs.findAll{ it.type == "PHYSICAL" }
        if (hubs.size() == 1){
            hubID = hubs[0].id
        }
    }
    logDebug("Returning Hub ID: ${hubID}")
    return hubID
}

def poll() {
	logDebug "In Poll"
	getDeviceList();
    logDebug "returned device list"
  def children = getChildDevices()
    logDebug("Children Inspected ${children}")
  if(settings.devices) {
    settings.devices.each { device ->
      log.info("Devices Inspected ${device.inspect()}")
      def item = device.tokenize('|')
      def deviceType = item[0]
      def deviceId = item[1]
      def deviceName = item[2]
      def existingDevices = children.find{ d -> d.deviceNetworkId.startsWith(deviceId + "|" + deviceType) }
      if(existingDevices) {
        existingDevices.poll()
      }
	}
  }
}

def userPoll() {
	logDebug "In UserPoll"
    def children = getChildDevices();
    if(settings.users) {
    	settings.users.each { user ->
    		log.info("Devices Inspected ${user.inspect()}")
			def item = user.tokenize('|')
        	def userId = item[0]
        	def userName = item[1]
        	def existingUsers = children.find{ d -> d.deviceNetworkId.contains(userId + "|" + userName) }
        	logDebug("existingUsers Inspected ${existingUsers.inspect()}")
    		if(existingUsers) {
          		existingUsers.poll()
        	}
     	}
   }
}

def homePoll() {
	logDebug "In HomePoll"
    def children = getChildDevices();
    if(settings.home) {
   }
}

def createChildDevice(deviceFile, dni, name, label) {
	logDebug "In createChildDevice"
    try{
		def childDevice = addChildDevice("fuzzysb", deviceFile, dni, getHubID(), [name: name, label: label, completedSetup: true])
	} catch (e) {
		log.error "Error creating device: ${e}"
	}
}

private sendCommand(method,childDevice,args = []) {
    logDebug "current timestamp: " + now() + ", token expiry: " + state.authTokenExpires
    if(now() >= state.authTokenExpires) {
		logDebug "Auth token expired"
        refreshToken();
	}
      logDebug "auth token: " + state.authToken

  def methods = [
	'getid': [
        			uri: apiUrl(),
                    path: "/api/v2/me",
                    requestContentType: "application/json",
                    headers: ["Authorization": "Bearer " + state.authToken ]
                    ],
    'gettempunit': [
        			uri: apiUrl(),
                    path: "/api/v2/homes/${state.homeId}",
                    requestContentType: "application/json",
                    headers: ["Authorization": "Bearer " + state.authToken ]
                    ],
    'getzones': [
             uri: apiUrl(),
                    path: "/api/v2/homes/" + state.homeId + "/zones",
                    requestContentType: "application/json",
                    headers: ["Authorization": "Bearer " + state.authToken ]
                    ],
	'getMobileDevices': [
             uri: apiUrl(),
                    path: "/api/v2/homes/" + state.homeId + "/mobileDevices",
                    requestContentType: "application/json",
                    headers: ["Authorization": "Bearer " + state.authToken ]
                    ],
    'getcapabilities': [
        			uri: apiUrl(),
                    path: "/api/v2/homes/" + state.homeId + "/zones/" + args[0] + "/capabilities",
                    requestContentType: "application/json",
                    headers: ["Authorization": "Bearer " + state.authToken ]
                    ],
    'status': [
        			uri: apiUrl(),
                    path: "/api/v2/homes/" + state.homeId + "/zones/" + args[0] + "/state",
                    requestContentType: "application/json",
                    headers: ["Authorization": "Bearer " + state.authToken ]
                    ],
	'userStatus': [
             		uri: apiUrl(),
                    path: "/api/v2/homes/" + state.homeId + "/mobileDevices",
                    requestContentType: "application/json",
                    headers: ["Authorization": "Bearer " + state.authToken ]
                    ],
	'temperature': [
        			uri: apiUrl(),
        			path: "/api/v2/homes/" + state.homeId + "/zones/" + args[0] + "/overlay",
        			requestContentType: "application/json",
                    headers: ["Authorization": "Bearer " + state.authToken ],
                  	body: args[1]
                   	],
	'weatherStatus': [
        			uri: apiUrl(),
        			path: "/api/v2/homes/" + state.homeId + "/weather",
        			requestContentType: "application/json",
                    headers: ["Authorization": "Bearer " + state.authToken ]
                   	],
    'deleteEntry': [
        			uri: apiUrl(),
        			path: "/api/v2/homes/" + state.homeId + "/zones/" + args[0] + "/overlay",
        			requestContentType: "application/json",
                    headers: ["Authorization": "Bearer " + state.authToken ]
                   	]
	]

	def request = methods.getAt(method)
          logDebug "Http Params ("+request+")"
      try{
        logDebug "Executing 'sendCommand' for method" + " " + method
          if (method == "getid"){
            httpGet(request) { resp ->
                parseMeResponse(resp)
            }
          }else if (method == "gettempunit"){
            httpGet(request) { resp ->
                parseTempResponse(resp)
            }
          }else if (method == "getzones"){
            httpGet(request) { resp ->
                parseZonesResponse(resp)
            }
          }else if (method == "getMobileDevices"){
            httpGet(request) { resp ->
                parseMobileDevicesResponse(resp)
            }
       	  }else if (method == "getcapabilities"){
            httpGet(request) { resp ->
                parseCapabilitiesResponse(resp,childDevice)
            }
          }else if (method == "status"){
            httpGet(request) { resp ->
                parseResponse(resp,childDevice)
            }
          }else if (method == "userStatus"){
            httpGet(request) { resp ->
                parseUserResponse(resp,childDevice)
            }
		  }else if (method == "temperature"){
            httpPut(request) { resp ->
                parseputResponse(resp,childDevice)
            }
          }else if (method == "weatherStatus"){
            logDebug "calling weatherStatus Method"
                httpGet(request) { resp ->
                parseweatherResponse(resp,childDevice)
            }
          }else if (method == "deleteEntry"){
            httpDelete(request) { resp ->
                parsedeleteResponse(resp,childDevice)
            }
        }else{
            httpGet(request)
        }
    } catch(Exception e){
        logDebug("___exception sendcommand: " + e)
    }
}

// Parse incoming device messages to generate events
private parseMeResponse(resp) {
    logDebug("Executing parseMeResponse: "+resp.data)
    logDebug("Output status: "+resp.status)
    if(resp.status == 200) {
    	logDebug("Executing parseMeResponse.successTrue")
        state.homeId = resp.data.homes[0].id
        logDebug("Got HomeID Value: " + state.homeId)

    }else if(resp.status == 201){
        logDebug("Something was created/updated")
    }
}

private parseputResponse(resp,childDevice) {
	logDebug("Executing parseputResponse: "+resp.data)
    logDebug("Output status: "+resp.status)
}

private parsedeleteResponse(resp,childDevice) {
	logDebug("Executing parsedeleteResponse: "+resp.data)
    logDebug("Output status: "+resp.status)
}

private parseUserResponse(resp,childDevice) {
  	def item = (childDevice.device.deviceNetworkId).tokenize('|')
  	def userId = item[0]
  	def userName = item[1]
    logDebug("Executing parseUserResponse: "+resp.data)
    logDebug("Output status: "+resp.status)
    if(resp.status == 200) {
      def restUsers = resp.data
      logDebug("Executing parseUserResponse.successTrue")
      logDebug("UserId is ${userId} and userName is ${userName}")
      for (TadoUser in restUsers) {
      	logDebug("TadoUserId is ${TadoUser.id}")
      	if ((TadoUser.id).toString() == (userId).toString())
        {
         logDebug("Entering presence Assesment for User Id: ${userId}")
         if (TadoUser.settings.geoTrackingEnabled == true)
         {
         	logDebug("GeoTracking is Enabled for User Id: ${userId}")
        	if (TadoUser.location.atHome == true)
            {
            	logDebug("Send presence Home Event Fired")
               	childDevice?.sendEvent(name:"presence",value: "present")
            } else if (TadoUser.location.atHome == false)
            {
            	logDebug("Send presence Away Event Fired")
            	childDevice?.sendEvent(name:"presence",value: "not present")
            }
        }

        }
      }
    } else if(resp.status == 201){
        logDebug("Something was created/updated")
    }
}

private parseResponse(resp,childDevice) {
  def item = (childDevice.device.deviceNetworkId).tokenize('|')
  def deviceId = item[0]
  def deviceType = item[1]
  def deviceToken = item[2]
  if (deviceType == "AIR_CONDITIONING")
  {
    logDebug("Executing parseResponse: "+resp.data)
    logDebug("Output status: "+resp.status)
    def temperatureUnit = state.tempunit
    logDebug("Temperature Unit is ${temperatureUnit}")
    def humidityUnit = "%"
    def ACMode
    def ACFanSpeed
    def ACFanMode = "off"
    def thermostatSetpoint
    def tOperatingState
    if(resp.status == 200) {
        logDebug("Executing parseResponse.successTrue")
        def temperature
        if (temperatureUnit == "C") {
        	temperature = (Math.round(resp.data.sensorDataPoints.insideTemperature.celsius *10 ) / 10)
        }
        else if(temperatureUnit == "F"){
        	temperature = (Math.round(resp.data.sensorDataPoints.insideTemperature.fahrenheit * 10) / 10)
        }
        logDebug("Read temperature: " + temperature)
        childDevice?.sendEvent(name:"temperature",value:temperature,unit:temperatureUnit)
        logDebug("Send Temperature Event Fired")
        def autoOperation = "OFF"
        if(resp.data.overlayType == null){
        	autoOperation = resp.data.tadoMode
        }
        else if(resp.data.overlayType == "NO_FREEZE"){
        	autoOperation = "OFF"
        }else if(resp.data.overlayType == "MANUAL"){
        	autoOperation = "MANUAL"
        }
        logDebug("Read tadoMode: " + autoOperation)
        childDevice?.sendEvent(name:"tadoMode",value:autoOperation)
        logDebug("Send thermostatMode Event Fired")

        def humidity
        if (resp.data.sensorDataPoints.humidity.percentage != null){
        	humidity = resp.data.sensorDataPoints.humidity.percentage
        }else{
        	humidity = "--"
        }
        logDebug("Read humidity: " + humidity)
        childDevice?.sendEvent(name:"humidity",value:humidity,unit:humidityUnit)

    	if (resp.data.setting.power == "OFF"){
            tOperatingState = "idle"
            ACMode = "off"
            ACFanMode = "off"
            logDebug("Read thermostatMode: " + ACMode)
            ACFanSpeed = "OFF"
            logDebug("Read tadoFanSpeed: " + ACFanSpeed)
            thermostatSetpoint = "--"
            logDebug("Read thermostatSetpoint: " + thermostatSetpoint)
      }
      else if (resp.data.setting.power == "ON"){
        ACMode = (resp.data.setting.mode).toLowerCase()
        logDebug("thermostatMode: " + ACMode)
        ACFanSpeed = resp.data.setting.fanSpeed
        if (ACFanSpeed == null) {
          ACFanSpeed = "--"
        }
        if (resp.data.overlay != null){
          if (resp.data.overlay.termination.type == "TIMER"){
            if (resp.data.overlay.termination.durationInSeconds == "3600"){
              ACMode = "emergency heat"
              logDebug("thermostatMode is heat, however duration shows the state is: " + ACMode)
            }
          }
        }
            switch (ACMode) {
				case "off":
        			tOperatingState = "idle"
        		break
    			case "heat":
        			tOperatingState = "heating"
        		break
    			case "emergency heat":
        			tOperatingState = "heating"
        		break
        		case "cool":
        			tOperatingState = "cooling"
        		break
                case "dry":
        			tOperatingState = "drying"
        		break
                case "fan":
        			tOperatingState = "fan only"
        		break
                case "auto":
        			tOperatingState = "heating|cooling"
        		break
			}
            logDebug("Read thermostatOperatingState: " + tOperatingState)
        	logDebug("Read tadoFanSpeed: " + ACFanSpeed)

        if (ACMode == "dry" || ACMode == "auto" || ACMode == "fan"){
        	thermostatSetpoint = "--"
        }else if(ACMode == "fan") {
        	ACFanMode = "auto"
        }else{
       		if (temperatureUnit == "C") {
        		thermostatSetpoint = Math.round(resp.data.setting.temperature.celsius)
        	}
        	else if(temperatureUnit == "F"){
        		thermostatSetpoint = Math.round(resp.data.setting.temperature.fahrenheit)
        	}
        }
        logDebug("Read thermostatSetpoint: " + thermostatSetpoint)
      }
    }else{
        logDebug("Executing parseResponse.successFalse")
    }
    childDevice?.sendEvent(name:"thermostatOperatingState",value:tOperatingState)
    logDebug("Send thermostatOperatingState Event Fired")
    childDevice?.sendEvent(name:"tadoFanSpeed",value:ACFanSpeed)
    logDebug("Send tadoFanSpeed Event Fired")
    childDevice?.sendEvent(name:"thermostatFanMode",value:ACFanMode)
    logDebug("Send thermostatFanMode Event Fired")
    childDevice?.sendEvent(name:"thermostatMode",value:ACMode)
    logDebug("Send thermostatMode Event Fired")
    childDevice?.sendEvent(name:"thermostatSetpoint",value:thermostatSetpoint,unit:temperatureUnit)
    logDebug("Send thermostatSetpoint Event Fired")
    childDevice?.sendEvent(name:"heatingSetpoint",value:thermostatSetpoint,unit:temperatureUnit)
    logDebug("Send heatingSetpoint Event Fired")
    childDevice?.sendEvent(name:"coolingSetpoint",value:thermostatSetpoint,unit:temperatureUnit)
    logDebug("Send coolingSetpoint Event Fired")
  }
  if (deviceType == "HEATING")
  {
    logDebug("Executing parseResponse: "+resp.data)
    logDebug("Output status: "+resp.status)
    def temperatureUnit = state.tempunit
    logDebug("Temperature Unit is ${temperatureUnit}")
    def humidityUnit = "%"
    //def ACMode
    //def ACFanSpeed
    def thermostatSetpoint
    def tOperatingState
    if(resp.status == 200) {
        logDebug("Executing parseResponse.successTrue")
        def temperature
        if (temperatureUnit == "C") {
        	temperature = (Math.round(resp.data.sensorDataPoints.insideTemperature.celsius * 10 ) / 10)
        }
        else if(temperatureUnit == "F"){
        	temperature = (Math.round(resp.data.sensorDataPoints.insideTemperature.fahrenheit * 10) / 10)
        }
        logDebug("Read temperature: " + temperature)
        childDevice?.sendEvent(name: 'temperature', value: temperature, unit: temperatureUnit)
        logDebug("Send Temperature Event Fired")
        def autoOperation = "OFF"
        if(resp.data.overlayType == null){
        	autoOperation = resp.data.tadoMode
        }
        else if(resp.data.overlayType == "NO_FREEZE"){
        	autoOperation = "OFF"
        }else if(resp.data.overlayType == "MANUAL"){
        	autoOperation = "MANUAL"
        }
        logDebug("Read tadoMode: " + autoOperation)
        childDevice?.sendEvent(name: 'tadoMode', value: autoOperation)

		if (resp.data.setting.power == "ON"){



			if (temperatureUnit == "C") {
				thermostatSetpoint = resp.data.setting.temperature.celsius
			}
			else if(temperatureUnit == "F"){
				thermostatSetpoint = resp.data.setting.temperature.fahrenheit
			}
			logDebug("Read thermostatSetpoint: " + thermostatSetpoint)

			childDevice?.sendEvent(name: 'thermostatMode', value: "heat")

			if (temperature < thermostatSetpoint) {
				logDebug("Heat mode; setpoint not reached")
				childDevice?.sendEvent(name: 'thermostatOperatingState', value: "heating")
			} else {
				logDebug("Heat mode; setpoint reached")
				childDevice?.sendEvent(name: 'thermostatOperatingState', value: "idle")
			}
			logDebug("Send thermostatMode Event Fired")

		} else if(resp.data.setting.power == "OFF"){
			thermostatSetpoint = "--"
			childDevice?.sendEvent(name: 'thermostatMode', value: "off")
			childDevice?.sendEvent(name: 'thermostatOperatingState', value: "idle")
			logDebug("Send thermostatMode Event Fired")
		}

        def humidity
        if (resp.data.sensorDataPoints.humidity.percentage != null){
        	humidity = resp.data.sensorDataPoints.humidity.percentage
        }else{
        	humidity = "--"
        }
        logDebug("Read humidity: " + humidity)

        childDevice?.sendEvent(name: 'humidity', value: humidity,unit: humidityUnit)


        def heatingPower
        heatingPower = resp.data.activityDataPoints.heatingPower.percentage
        childDevice?.sendEvent(name: 'heatingPower', value: heatingPower)


        def openWindow
        if (resp.data.openwindow != null){
            openWindow = true
        } else {
            openWindow = false
        }
        childDevice?.sendEvent(name: 'openWindow', value: openWindow)
	}

	else{
        logDebug("Executing parseResponse.successFalse")
    }

    childDevice?.sendEvent(name: 'thermostatSetpoint', value: thermostatSetpoint, unit: temperatureUnit)
    logDebug("Send thermostatSetpoint Event Fired")
    childDevice?.sendEvent(name: 'heatingSetpoint', value: thermostatSetpoint, unit: temperatureUnit)
    logDebug("Send heatingSetpoint Event Fired")
  }
  if (deviceType == "HOT_WATER")
  {
    logDebug("Executing parseResponse: "+resp.data)
    logDebug("Output status: "+resp.status)
    def temperatureUnit = state.tempunit
    logDebug("Temperature Unit is ${temperatureUnit}")
    def humidityUnit = "%"
    def ACMode
    def ACFanSpeed
    def thermostatSetpoint
    def tOperatingState
    if(resp.status == 200) {
    logDebug("Executing parseResponse.successTrue")
    def temperature
    if (state.supportsWaterTempControl == "true" && resp.data.tadoMode != null && resp.data.setting.power != "OFF"){
    if (temperatureUnit == "C") {
      temperature = (Math.round(resp.data.setting.temperature.celsius * 10 ) / 10)
    }
    else if(temperatureUnit == "F"){
      temperature = (Math.round(resp.data.setting.temperature.fahrenheit * 10) / 10)
    }
    logDebug("Read temperature: " + temperature)
    childDevice?.sendEvent(name: 'temperature', value: temperature, unit: temperatureUnit)
    logDebug("Send Temperature Event Fired")
    } else {
      childDevice?.sendEvent(name: 'temperature', value: "--", unit: temperatureUnit)
      logDebug("Send Temperature Event Fired")
    }
    def autoOperation = "OFF"
    if(resp.data.overlayType == null){
      autoOperation = resp.data.tadoMode
    }
    else if(resp.data.overlayType == "NO_FREEZE"){
      autoOperation = "OFF"
    }else if(resp.data.overlayType == "MANUAL"){
      autoOperation = "MANUAL"
    }
    logDebug("Read tadoMode: " + autoOperation)
    childDevice?.sendEvent(name: 'tadoMode', value: autoOperation)

    if (resp.data.setting.power == "ON"){
      childDevice?.sendEvent(name: 'thermostatMode', value: "heat")
      childDevice?.sendEvent(name: 'thermostatOperatingState', value: "heating")
      logDebug("Send thermostatMode Event Fired")
      } else if(resp.data.setting.power == "OFF"){
        childDevice?.sendEvent(name: 'thermostatMode', value: "off")
        childDevice?.sendEvent(name: 'thermostatOperatingState', value: "idle")
        logDebug("Send thermostatMode Event Fired")
      }
      logDebug("Send thermostatMode Event Fired")
      if (state.supportsWaterTempControl == "true" && resp.data.tadoMode != null && resp.data.setting.power != "OFF"){
        if (temperatureUnit == "C") {
          thermostatSetpoint = resp.data.setting.temperature.celsius
        }
        else if(temperatureUnit == "F"){
          thermostatSetpoint = resp.data.setting.temperature.fahrenheit
        }
        logDebug("Read thermostatSetpoint: " + thermostatSetpoint)
        } else {
          thermostatSetpoint = "--"
        }
      }

      else{
        logDebug("Executing parseResponse.successFalse")
      }

      childDevice?.sendEvent(name: 'thermostatSetpoint', value: thermostatSetpoint, unit: temperatureUnit)
      logDebug("Send thermostatSetpoint Event Fired")
      childDevice?.sendEvent(name: 'heatingSetpoint', value: thermostatSetpoint, unit: temperatureUnit)
      logDebug("Send heatingSetpoint Event Fired")
  }
}

private parseTempResponse(resp) {
    logDebug("Executing parseTempResponse: "+resp.data)
    logDebug("Output status: "+resp.status)
    if(resp.status == 200) {
    	logDebug("Executing parseTempResponse.successTrue")
        def tempunitname = resp.data.temperatureUnit
        if (tempunitname == "CELSIUS") {
        	logDebug("Setting Temp Unit to C")
        	state.tempunit = "C"
        }
        else if(tempunitname == "FAHRENHEIT"){
        	logDebug("Setting Temp Unit to F")
        	state.tempunit = "F"
        }
    }else if(resp.status == 201){
        logDebug("Something was created/updated")
    }
}

private parseZonesResponse(resp) {
    logDebug("Executing parseZonesResponse: "+resp.data)
    logDebug("Output status: "+resp.status)
    if(resp.status == 200) {
      def restDevices = resp.data
      def TadoDevices = []
      logDebug("Executing parseZoneResponse.successTrue")
      restDevices.each { Tado -> TadoDevices << ["${Tado.type}|${Tado.id}|${Tado.name}":"${Tado.name}"] }
      logDebug(TadoDevices)
      return TadoDevices
    }else if(resp.status == 201){
        logDebug("Something was created/updated")
    }
}

private parseMobileDevicesResponse(resp) {
    logDebug("Executing parseMobileDevicesResponse: "+resp.data)
    logDebug("Output status: "+resp.status)
    if(resp.status == 200) {
      def restUsers = resp.data
      def TadoUsers = []
      logDebug("Executing parseMobileDevicesResponse.successTrue")
      restUsers.each { TadoUser ->
      	if (TadoUser.settings.geoTrackingEnabled == true)
        {
        	TadoUsers << ["${TadoUser.id}|${TadoUser.name}":"${TadoUser.name}"]
        }
      }
      logDebug(TadoUsers)
      return TadoUsers
    }else if(resp.status == 201){
        logDebug("Something was created/updated")
    }
}

private parseCapabilitiesResponse(resp,childDevice) {
    logDebug("Executing parseCapabilitiesResponse: "+resp.data)
    logDebug("Output status: " + resp.status)
    if(resp.status == 200) {
    	try
      {
    	logDebug("Executing parseResponse.successTrue")
       	childDevice?.setCapabilitytadoType(resp.data.type)
        logDebug("Tado Type is ${resp.data.type}")
        if(resp.data.type == "AIR_CONDITIONING")
        {
          try
          {
            if(resp.data.AUTO || (resp.data.AUTO).toString() == "[:]"){
              logDebug("settingautocapability state true")
              childDevice?.setCapabilitySupportsAuto("true")
            } else {
              logDebug("settingautocapability state false")
              childDevice?.setCapabilitySupportsAuto("false")
            }
            if(resp.data.AUTO.swings || (resp.data.AUTO.swings).toString() == "[:]")
            {
              logDebug("settingautoswingcapability state true")
            childDevice?.setCapabilitySupportsAutoSwing("true")
            }
            else
            {
              logDebug("settingautoswingcapability state false")
              childDevice?.setCapabilitySupportsAutoSwing("false")
            }
          }
          catch(Exception e)
          {
            logDebug("___exception parsing Auto Capabiity: " + e)
          }
          try
          {
              if(resp.data.COOL || (resp.data.COOL).toString() == "[:]"){
              logDebug("setting COOL capability state true")
              childDevice?.setCapabilitySupportsCool("true")
                def coolfanmodelist = resp.data.COOL.fanSpeeds
                if(resp.data.COOL.swings || (resp.data.COOL.swings).toString() == "[:]")
                {
                  logDebug("settingcoolswingcapability state true")
                  childDevice?.setCapabilitySupportsCoolSwing("true")
                }
                else
                {
                  logDebug("settingcoolswingcapability state false")
                  childDevice?.setCapabilitySupportsCoolSwing("false")
                }
                if(resp.data.COOL.fanSpeeds || (resp.data.COOL.fanSpeeds).toString() == "[:]")
                {
                  childDevice?.setCapabilitySupportsCoolFanSpeed("true")
                }
                else
                {
                  childDevice?.setCapabilitySupportsCoolFanSpeed("false")
                }
                if(coolfanmodelist.find { it == 'AUTO' }){
                  logDebug("setting COOL Auto Fan Speed capability state true")
                  childDevice?.setCapabilitySupportsCoolAutoFanSpeed("true")
                } else {
                  logDebug("setting COOL Auto Fan Speed capability state false")
                  childDevice?.setCapabilitySupportsCoolAutoFanSpeed("false")
                }
                if (state.tempunit == "C"){
                  childDevice?.setCapabilityMaxCoolTemp(resp.data.COOL.temperatures.celsius.max)
                  childDevice?.setCapabilityMinCoolTemp(resp.data.COOL.temperatures.celsius.min)
                } else if (state.tempunit == "F") {
                  childDevice?.setCapabilityMaxCoolTemp(resp.data.COOL.temperatures.fahrenheit.max)
                  childDevice?.setCapabilityMinCoolTemp(resp.data.COOL.temperatures.fahrenheit.min)
                }
            } else {
              logDebug("setting COOL capability state false")
              childDevice?.setCapabilitySupportsCool("false")
            }
          }
          catch(Exception e)
          {
            logDebug("___exception parsing Cool Capabiity: " + e)
          }
          try
          {
            if(resp.data.DRY || (resp.data.DRY).toString() == "[:]"){
              logDebug("setting DRY capability state true")
              childDevice?.setCapabilitySupportsDry("true")
            } else {
              logDebug("setting DRY capability state false")
              childDevice?.setCapabilitySupportsDry("false")
            }
            if(resp.data.DRY.swings || (resp.data.DRY.swings).toString() == "[:]")
            {
              logDebug("settingdryswingcapability state true")
            childDevice?.setCapabilitySupportsDrySwing("true")
            }
            else
            {
              logDebug("settingdryswingcapability state false")
              childDevice?.setCapabilitySupportsDrySwing("false")
            }
          }
          catch(Exception e)
          {
            logDebug("___exception parsing Dry Capabiity: " + e)
          }
          try
          {
            if(resp.data.FAN || (resp.data.FAN).toString() == "[:]"){
              logDebug("setting FAN capability state true")
              childDevice?.setCapabilitySupportsFan("true")
            } else {
              logDebug("setting FAN capability state false")
              childDevice?.setCapabilitySupportsFan("false")
            }
            if(resp.data.FAN.swings || (resp.data.FAN.swings).toString() == "[:]")
            {
              logDebug("settingfanswingcapability state true")
            childDevice?.setCapabilitySupportsFanSwing("true")
            }
            else
            {
              logDebug("settingfanswingcapability state false")
              childDevice?.setCapabilitySupportsFanSwing("false")
            }
          }
          catch(Exception e)
          {
            logDebug("___exception parsing Fan Capabiity: " + e)
          }
          try
          {
            if(resp.data.HEAT || (resp.data.HEAT).toString() == "[:]"){
              logDebug("setting HEAT capability state true")
              childDevice?.setCapabilitySupportsHeat("true")
                def heatfanmodelist = resp.data.HEAT.fanSpeeds
                if(resp.data.HEAT.swings || (resp.data.HEAT.swings).toString() == "[:]")
                {
                  logDebug("settingheatswingcapability state true")
                  childDevice?.setCapabilitySupportsHeatSwing("true")
                }
                else
                {
                  logDebug("settingheatswingcapability state false")
                  childDevice?.setCapabilitySupportsHeatSwing("false")
                }
                if(resp.data.HEAT.fanSpeeds || (resp.data.HEAT.fanSpeeds).toString() == "[:]")
                {
                  childDevice?.setCapabilitySupportsHeatFanSpeed("true")
                }
                else
                {
                  childDevice?.setCapabilitySupportsHeatFanSpeed("false")
                }
                if(heatfanmodelist.find { it == 'AUTO' }){
                  logDebug("setting HEAT Auto Fan Speed capability state true")
                  childDevice?.setCapabilitySupportsHeatAutoFanSpeed("true")
                } else {
                  logDebug("setting HEAT Auto Fan Speed capability state false")
                  childDevice?.setCapabilitySupportsHeatAutoFanSpeed("false")
                }
                if (state.tempunit == "C"){
                  childDevice?.setCapabilityMaxHeatTemp(resp.data.HEAT.temperatures.celsius.max)
                  childDevice?.setCapabilityMinHeatTemp(resp.data.HEAT.temperatures.celsius.min)
                } else if (state.tempunit == "F") {
                  childDevice?.setCapabilityMaxHeatTemp(resp.data.HEAT.temperatures.fahrenheit.max)
                  childDevice?.setCapabilityMinHeatTemp(resp.data.HEAT.temperatures.fahrenheit.min)
                }
            } else {
              logDebug("setting HEAT capability state false")
              childDevice?.setCapabilitySupportsHeat("false")
            }
          }catch(Exception e)
          {
            logDebug("___exception parsing Heat Capabiity: " + e)
          }
        }
        if(resp.data.type == "HEATING")
        {
          if(resp.data.type == "HEATING")
          {
          	logDebug("setting HEAT capability state true")
          	childDevice?.setCapabilitySupportsHeat("true")
            if (state.tempunit == "C")
            {
              childDevice?.setCapabilityMaxHeatTemp(resp.data.temperatures.celsius.max)
              childDevice?.setCapabilityMinHeatTemp(resp.data.temperatures.celsius.min)
            }
            else if (state.tempunit == "F")
            {
              childDevice?.setCapabilityMaxHeatTemp(resp.data.temperatures.fahrenheit.max)
              childDevice?.setCapabilityMinHeatTemp(resp.data.temperatures.fahrenheit.min)
            }
          }
          else
          {
          	logDebug("setting HEAT capability state false")
          	childDevice?.setCapabilitySupportsHeat("false")
          }
        }
        if(resp.data.type == "HOT_WATER")
        {
            if(resp.data.type == "HOT_WATER"){
              logDebug("setting WATER capability state true")
              dchildDevice?.setCapabilitySupportsWater("true")
              if (resp.data.canSetTemperature == true){
                childDevice?.setCapabilitySupportsWaterTempControl("true")
                if (state.tempunit == "C")
                {
                  childDevice?.setCapabilityMaxHeatTemp(resp.data.temperatures.celsius.max)
                  childDevice?.setCapabilityMinHeatTemp(resp.data.temperatures.celsius.min)
                }
                else if (state.tempunit == "F")
                {
                  childDevice?.setCapabilityMaxHeatTemp(resp.data.temperatures.fahrenheit.max)
                  childDevice?.setCapabilityMinHeatTemp(resp.data.temperatures.fahrenheit.min)
                }
              }
              else
              {
                childDevice?.setCapabilitySupportsWaterTempControl("false")
              }
            }
            else
            {
            logDebug("setting Water capability state false")
            childDevice?.setCapabilitySupportsWater("false")
            }
        }
      }
      catch(Exception e)
      {
        logDebug("___exception: " + e)
      }
    }
    else if(resp.status == 201)
    {
      logDebug("Something was created/updated")
    }
}

private parseweatherResponse(resp,childDevice) {
    logDebug("Executing parseweatherResponse: "+resp.data)
    logDebug("Output status: "+resp.status)
	def temperatureUnit = state.tempunit
    logDebug("Temperature Unit is ${temperatureUnit}")
    if(resp.status == 200) {
    	logDebug("Executing parseResponse.successTrue")
        def outsidetemperature
        if (temperatureUnit == "C") {
        	outsidetemperature = resp.data.outsideTemperature.celsius
        }
        else if(temperatureUnit == "F"){
        	outsidetemperature = resp.data.outsideTemperature.fahrenheit
        }
        logDebug("Read outside temperature: " + outsidetemperature)
        childDevice?.sendEvent(name: 'outsidetemperature', value: outsidetemperature, unit: temperatureUnit)
        logDebug("Send Outside Temperature Event Fired")
        return result

    }else if(resp.status == 201){
        logDebug("Something was created/updated")
    }
}

def getidCommand(){
	logDebug "Executing 'sendCommand.getidCommand'"
	sendCommand("getid",null,[])
}

def getTempUnitCommand(){
	logDebug "Executing 'sendCommand.getidCommand'"
	sendCommand("gettempunit",null,[])
}

def getZonesCommand(){
	logDebug "Executing 'sendCommand.getzones'"
	sendCommand("getzones",null,[])
}

def getMobileDevicesCommand(){
	logDebug "Executing 'sendCommand.getMobileDevices'"
	sendCommand("getMobileDevices",null,[])
}

def weatherStatusCommand(childDevice){
  def item = (childDevice.device.deviceNetworkId).tokenize('|')
  def deviceId = item[0]
  def deviceType = item[1]
  def deviceToken = item[2]
	logDebug "Executing 'sendCommand.weatherStatusCommand'"
	def result = sendCommand("weatherStatus",childDevice,[deviceId])
}

def getCapabilitiesCommand(childDevice, deviceDNI){
	logDebug("childDevice is: " + childDevice.inspect())
	logDebug("deviceDNI is: " + deviceDNI.inspect())
	def item = deviceDNI.tokenize('|')
	def deviceId = item[0]
	def deviceType = item[1]
	def deviceToken = item[2]
	logDebug "Executing 'sendCommand.getcapabilities'"
	sendCommand("getcapabilities",childDevice,[deviceId])
}

private removeChildDevices(delete) {
	try {
    	delete.each {
        	deleteChildDevice(it.deviceNetworkId)
            log.info "Successfully Removed Child Device: ${it.displayName} (${it.deviceNetworkId})"
    		}
   		}
    catch (e) { log.error "There was an error (${e}) when trying to delete the child device" }
}

def parseCapabilityData(Map results){
  logDebug "in parseCapabilityData"
  def result
  results.each { name, value ->

    if (name == "value")
    {
    logDebug "Map Name Returned, ${name} and Value is ${value}"
    result = value.toString()
    logDebug "Result is ${result}"
    //return result
    }
  }
  return result
}


//Device Commands Below Here


def autoCommand(childDevice){
  logDebug "Executing 'sendCommand.autoCommand' on device ${childDevice.device.name}"
  def terminationmode = settings.manualmode
  def traperror
  def item = (childDevice.device.deviceNetworkId).tokenize('|')
  def deviceId = item[0]
  def deviceType = item[1]
  def deviceToken = item[2]
  if (deviceType == "AIR_CONDITIONING")
  {
    def capabilitySupportsAuto = parseCapabilityData(childDevice.getCapabilitySupportsAuto())
    def capabilitySupportsAutoSwing = parseCapabilityData(childDevice.getCapabilitySupportsAutoSwing())
    def capabilitysupported = capabilitySupportsAuto
    if (capabilitysupported == "true"){
    log.info "Executing 'sendCommand.autoCommand' on device ${childDevice.device.name}"
    def jsonbody
   	if (capabilitySupportsAutoSwing == "true")
    {
        jsonbody = new groovy.json.JsonOutput().toJson([setting:[mode:"AUTO", power:"ON", swing:"OFF", type:"AIR_CONDITIONING"], termination:[type:terminationmode]])
    }
    else
    {
      	jsonbody = new groovy.json.JsonOutput().toJson([setting:[mode:"AUTO", power:"ON", type:"AIR_CONDITIONING"], termination:[type:terminationmode]])
    }
    sendCommand("temperature",dchildDevice,[deviceId,jsonbody])
    statusCommand(device)
    } else {
      logDebug("Sorry Auto Capability not supported on device ${childDevice.device.name}")
    }
  }
  if(deviceType == "HEATING")
  {
    def initialsetpointtemp
    if(childDevice.device.currentValue("thermostatSetpoint"))
    {
        traperror = ((childDevice.device.currentValue("thermostatSetpoint")).intValue())
    }
    else
    {
       logDebug "Existing Setpoint is not set"
       traperror = 0
    }
    if(traperror == 0){
      initialsetpointtemp = settings.defHeatingTemp
    } else {
    	initialsetpointtemp = childDevice.device.currentValue("thermostatSetpoint")
    }
  	def jsonbody = new groovy.json.JsonOutput().toJson([setting:[power:"ON", temperature:[celsius:initialsetpointtemp], type:"HEATING"], termination:[type:terminationmode]])
    sendCommand("temperature",childDevice,[deviceId,jsonbody])
    statusCommand(childDevice)
  }
  if (deviceType == "HOT_WATER")
  {
    logDebug "Executing 'sendCommand.autoCommand'"
    def initialsetpointtemp
    def jsonbody
    def capabilitySupportsWaterTempControl = parseCapabilityData(childDevice.getCapabilitySupportsWaterTempControl())
    if(capabilitySupportsWaterTempControl == "true"){
    if(childDevice.device.currentValue("thermostatSetpoint"))
    {
        traperror = ((childDevice.device.currentValue("thermostatSetpoint")).intValue())
    }
    else
    {
       logDebug "Existing Setpoint is not set"
       traperror = 0
    }
      if(traperror == 0){
        initialsetpointtemp = settings.defHeatingTemp
      } else {
        initialsetpointtemp = childDevice.device.currentValue("thermostatSetpoint")
      }
      jsonbody = new groovy.json.JsonOutput().toJson([setting:[power:"ON", temperature:[celsius:initialsetpointtemp], type:"HOT_WATER"], termination:[type:terminationmode]])
    } else {
      jsonbody = new groovy.json.JsonOutput().toJson([setting:[power:"ON", type:"HOT_WATER"], termination:[type:terminationmode]])
    }
    sendCommand("temperature",childDevice,[deviceId,jsonbody])
    statusCommand(childDevice)
  }
}

def dryCommand(childDevice){
  def item = (childDevice.device.deviceNetworkId).tokenize('|')
  def deviceId = item[0]
  def deviceType = item[1]
  def deviceToken = item[2]
  def capabilitySupportsDry = parseCapabilityData(childDevice.getCapabilitySupportsDry())
  def capabilitySupportsDrySwing = parseCapabilityData(childDevice.getCapabilitySupportsDrySwing())
  def capabilitysupported = capabilitySupportsDry
  if (capabilitysupported == "true"){
  	def terminationmode = settings.manualmode
  	logDebug "Executing 'sendCommand.dryCommand' on device ${childDevice.device.name}"
  	def jsonbody
      	if (capabilitySupportsDrySwing == "true")
        {
			jsonbody = new groovy.json.JsonOutput().toJson([setting:[mode:"DRY", power:"ON", swing:"OFF", type:"AIR_CONDITIONING"], termination:[type:terminationmode]])
        }
        else
        {
        	jsonbody = new groovy.json.JsonOutput().toJson([setting:[mode:"DRY", power:"ON", type:"AIR_CONDITIONING"], termination:[type:terminationmode]])
        }
  	sendCommand("temperature",childDevice,[deviceId,jsonbody])
  	statusCommand(childDevice)
  } else {
    logDebug("Sorry Dry Capability not supported on device ${childDevice.device.name}")
  }
}

def fanAuto(childDevice){
  def item = (childDevice.device.deviceNetworkId).tokenize('|')
  def deviceId = item[0]
  def deviceType = item[1]
  def deviceToken = item[2]
  def capabilitySupportsFan = parseCapabilityData(childDevice.getCapabilitySupportsFan())
  def capabilitySupportsFanSwing = parseCapabilityData(childDevice.getCapabilitySupportsFanSwing())
  def capabilitysupported = capabilitySupportsFan
  if (capabilitysupported == "true"){
    def terminationmode = settings.manualmode
		logDebug "Executing 'sendCommand.fanAutoCommand' on device ${childDevice.device.name}"
      def jsonbody
      	if (capabilitySupportsFanSwing == "true")
        {
			jsonbody = new groovy.json.JsonOutput().toJson([setting:[mode:"FAN", power:"ON", swing:"OFF", type:"AIR_CONDITIONING"], termination:[type:terminationmode]])
        }
        else
        {
        	jsonbody = new groovy.json.JsonOutput().toJson([setting:[mode:"FAN", power:"ON", type:"AIR_CONDITIONING"], termination:[type:terminationmode]])
        }

    sendCommand("temperature",childDevice,[deviceId,jsonbody])
  	statusCommand(childDevice)
  } else {
    logDebug("Sorry Fan Capability not supported by your HVAC Device")
  }
}

def endManualControl(childDevice){
	logDebug "Executing 'sendCommand.endManualControl' on device ${childDevice.device.name}"
  def item = (childDevice.device.deviceNetworkId).tokenize('|')
  def deviceId = item[0]
  def deviceType = item[1]
  def deviceToken = item[2]
	sendCommand("deleteEntry",childDevice,[deviceId])
	statusCommand(childDevice)
}

def cmdFanSpeedAuto(childDevice){
  def supportedfanspeed
  def terminationmode = settings.manualmode
  def item = (childDevice.device.deviceNetworkId).tokenize('|')
  def deviceId = item[0]
  def deviceType = item[1]
  def deviceToken = item[2]
  def jsonbody
  def capabilitySupportsCool = parseCapabilityData(childDevice.getCapabilitySupportsCool())
  def capabilitysupported = capabilitySupportsCool
  def capabilitySupportsCoolAutoFanSpeed = parseCapabilityData(childDevice.getCapabilitySupportsCoolAutoFanSpeed())
  def fancapabilitysupported = capabilitySupportsCoolAutoFanSpeed
  if (fancapabilitysupported == "true"){
    supportedfanspeed = "AUTO"
    } else {
      supportedfanspeed = "HIGH"
    }
	def curSetTemp = (childDevice.device.currentValue("thermostatSetpoint"))
	def curMode = ((childDevice.device.currentValue("thermostatMode")).toUpperCase())
	if (curMode == "COOL" || curMode == "HEAT"){
    	if (capabilitySupportsCoolSwing == "true" || capabilitySupportsHeatSwing == "true")
        {
    		if (state.tempunit == "C") {
      			jsonbody = new groovy.json.JsonOutput().toJson([setting:[fanSpeed:supportedfanspeed, mode:curMode, power:"ON", swing:"OFF", temperature:[celsius:curSetTemp], type:"AIR_CONDITIONING"], termination:[type:terminationmode]])
    		}
    		else if(state.tempunit == "F"){
      			jsonbody = new groovy.json.JsonOutput().toJson([setting:[fanSpeed:supportedfanspeed, mode:curMode, power:"ON", swing:"OFF", temperature:[fahrenheit:curSetTemp], type:"AIR_CONDITIONING"], termination:[type:terminationmode]])
    		}
        }
        else
        {
        	if (state.tempunit == "C") {
      			jsonbody = new groovy.json.JsonOutput().toJson([setting:[fanSpeed:supportedfanspeed, mode:curMode, power:"ON", temperature:[celsius:curSetTemp], type:"AIR_CONDITIONING"], termination:[type:terminationmode]])
    		}
    		else if(state.tempunit == "F"){
      			jsonbody = new groovy.json.JsonOutput().toJson([setting:[fanSpeed:supportedfanspeed, mode:curMode, power:"ON", temperature:[fahrenheit:curSetTemp], type:"AIR_CONDITIONING"], termination:[type:terminationmode]])
    		}
        }
		log.info "Executing 'sendCommand.fanSpeedAuto' to ${supportedfanspeed}"
    sendCommand("temperature",childDevice,[deviceId,jsonbody])
    statusCommand(childDevice)
	}
}

def cmdFanSpeedHigh(childDevice){
  def item = (childDevice.device.deviceNetworkId).tokenize('|')
  def deviceId = item[0]
  def deviceType = item[1]
  def deviceToken = item[2]
  def jsonbody
  def supportedfanspeed = "HIGH"
  def terminationmode = settings.manualmode
	def curSetTemp = (childDevice.device.currentValue("thermostatSetpoint"))
	def curMode = ((childDevice.device.currentValue("thermostatMode")).toUpperCase())
	if (curMode == "COOL" || curMode == "HEAT"){
    	if (capabilitySupportsCoolSwing == "true" || capabilitySupportsHeatSwing == "true")
        {
    		if (state.tempunit == "C") {
      			jsonbody = new groovy.json.JsonOutput().toJson([setting:[fanSpeed:supportedfanspeed, mode:curMode, power:"ON", swing:"OFF", temperature:[celsius:curSetTemp], type:"AIR_CONDITIONING"], termination:[type:terminationmode]])
    		}
    		else if(state.tempunit == "F"){
      			jsonbody = new groovy.json.JsonOutput().toJson([setting:[fanSpeed:supportedfanspeed, mode:curMode, power:"ON", swing:"OFF", temperature:[fahrenheit:curSetTemp], type:"AIR_CONDITIONING"], termination:[type:terminationmode]])
    		}
        }
        else
        {
        	if (state.tempunit == "C") {
      			jsonbody = new groovy.json.JsonOutput().toJson([setting:[fanSpeed:supportedfanspeed, mode:curMode, power:"ON", temperature:[celsius:curSetTemp], type:"AIR_CONDITIONING"], termination:[type:terminationmode]])
    		}
    		else if(state.tempunit == "F"){
      			jsonbody = new groovy.json.JsonOutput().toJson([setting:[fanSpeed:supportedfanspeed, mode:curMode, power:"ON", temperature:[fahrenheit:curSetTemp], type:"AIR_CONDITIONING"], termination:[type:terminationmode]])
    		}
        }
		log.info "Executing 'sendCommand.fanSpeedAuto' to ${supportedfanspeed}"
    sendCommand("temperature",childDevice,[deviceId,jsonbody])
    statusCommand(childDevice)
	}
}

def cmdFanSpeedMid(childDevice){
  def item = (childDevice.device.deviceNetworkId).tokenize('|')
  def deviceId = item[0]
  def deviceType = item[1]
  def deviceToken = item[2]
  def supportedfanspeed = "MIDDLE"
  def terminationmode = settings.manualmode
  def jsonbody
	def curSetTemp = (childDevice.device.currentValue("thermostatSetpoint"))
	def curMode = ((childDevice.device.currentValue("thermostatMode")).toUpperCase())
	if (curMode == "COOL" || curMode == "HEAT"){
    	if (capabilitySupportsCoolSwing == "true" || capabilitySupportsHeatSwing == "true")
        {
    		if (state.tempunit == "C") {
      			jsonbody = new groovy.json.JsonOutput().toJson([setting:[fanSpeed:supportedfanspeed, mode:curMode, power:"ON", swing:"OFF", temperature:[celsius:curSetTemp], type:"AIR_CONDITIONING"], termination:[type:terminationmode]])
    		}
    		else if(state.tempunit == "F"){
      			jsonbody = new groovy.json.JsonOutput().toJson([setting:[fanSpeed:supportedfanspeed, mode:curMode, power:"ON", swing:"OFF", temperature:[fahrenheit:curSetTemp], type:"AIR_CONDITIONING"], termination:[type:terminationmode]])
    		}
        }
        else
        {
        	if (state.tempunit == "C") {
      			jsonbody = new groovy.json.JsonOutput().toJson([setting:[fanSpeed:supportedfanspeed, mode:curMode, power:"ON", temperature:[celsius:curSetTemp], type:"AIR_CONDITIONING"], termination:[type:terminationmode]])
    		}
    		else if(state.tempunit == "F"){
      			jsonbody = new groovy.json.JsonOutput().toJson([setting:[fanSpeed:supportedfanspeed, mode:curMode, power:"ON", temperature:[fahrenheit:curSetTemp], type:"AIR_CONDITIONING"], termination:[type:terminationmode]])
    		}
        }
		log.info "Executing 'sendCommand.fanSpeedMid' to ${supportedfanspeed}"
		sendCommand("temperature",childDevice,[deviceId,jsonbody])
    statusCommand(childDevice)
	}
}

def cmdFanSpeedLow(childDevice){
  def item = (childDevice.device.deviceNetworkId).tokenize('|')
  def deviceId = item[0]
  def deviceType = item[1]
  def deviceToken = item[2]
  def capabilitySupportsCoolSwing = parseCapabilityData(childDevice.getCapabilitySupportsCoolSwing())
  def capabilitySupportsHeatSwing = parseCapabilityData(childDevice.getCapabilitySupportsHeatSwing())
  def supportedfanspeed = "LOW"
  def terminationmode = settings.manualmode
  def jsonbody
	def curSetTemp = (childDevice.device.currentValue("thermostatSetpoint"))
	def curMode = ((childDevice.device.currentValue("thermostatMode")).toUpperCase())
	if (curMode == "COOL" || curMode == "HEAT"){
    	if (capabilitySupportsCoolSwing == "true" || capabilitySupportsHeatSwing == "true")
        {
    		if (state.tempunit == "C") {
      			jsonbody = new groovy.json.JsonOutput().toJson([setting:[fanSpeed:supportedfanspeed, mode:curMode, power:"ON", swing:"OFF", temperature:[celsius:curSetTemp], type:"AIR_CONDITIONING"], termination:[type:terminationmode]])
    		}
    		else if(state.tempunit == "F"){
      			jsonbody = new groovy.json.JsonOutput().toJson([setting:[fanSpeed:supportedfanspeed, mode:curMode, power:"ON", swing:"OFF", temperature:[fahrenheit:curSetTemp], type:"AIR_CONDITIONING"], termination:[type:terminationmode]])
    		}
        }
        else
        {
        	if (state.tempunit == "C") {
      			jsonbody = new groovy.json.JsonOutput().toJson([setting:[fanSpeed:supportedfanspeed, mode:curMode, power:"ON", temperature:[celsius:curSetTemp], type:"AIR_CONDITIONING"], termination:[type:terminationmode]])
    		}
    		else if(state.tempunit == "F"){
      			jsonbody = new groovy.json.JsonOutput().toJson([setting:[fanSpeed:supportedfanspeed, mode:curMode, power:"ON", temperature:[fahrenheit:curSetTemp], type:"AIR_CONDITIONING"], termination:[type:terminationmode]])
    		}
        }
		log.info "Executing 'sendCommand.fanSpeedLow' to ${supportedfanspeed}"
		sendCommand("temperature",childDevice,[deviceId,jsonbody])
    statusCommand(childDevice)
	}
}

def setCoolingTempCommand(childDevice,targetTemperature){
  def terminationmode = settings.manualmode
  def item = (childDevice.device.deviceNetworkId).tokenize('|')
  def deviceId = item[0]
  def deviceType = item[1]
  def deviceToken = item[2]
  def supportedfanspeed
  def capabilitySupportsCool = parseCapabilityData(childDevice.getCapabilitySupportsCool())
  def capabilitySupportsCoolSwing = parseCapabilityData(childDevice.getCapabilitySupportsCoolSwing())
  def capabilitysupported = capabilitySupportsCool
  def capabilitySupportsCoolFanSpeed = parseCapabilityData(childDevice.getCapabilitySupportsCoolFanSpeed())
  def capabilitySupportsCoolAutoFanSpeed = parseCapabilityData(childDevice.getCapabilitySupportsCoolAutoFanSpeed())
  def fancapabilitysupported = capabilitySupportsCoolAutoFanSpeed
  def jsonbody
    if (fancapabilitysupported == "true"){
    	supportedfanspeed = "AUTO"
    } else {
        supportedfanspeed = "HIGH"
    }
    if (capabilitySupportsCoolSwing == "true" && capabilitySupportsCoolFanSpeed == "true")
    {
 		if (state.tempunit == "C") {
    		jsonbody = new groovy.json.JsonOutput().toJson([setting:[fanSpeed:supportedfanspeed, mode:"COOL", power:"ON", swing:"OFF", temperature:[celsius:targetTemperature], type:"AIR_CONDITIONING"], termination:[type:terminationmode]])
  		}
  		else if(state.tempunit == "F"){
            jsonbody = new groovy.json.JsonOutput().toJson([setting:[fanSpeed:supportedfanspeed, mode:"COOL", power:"ON", swing:"OFF", temperature:[fahrenheit:targetTemperature], type:"AIR_CONDITIONING"], termination:[type:terminationmode]])
  		}
    }
    else if(capabilitySupportsCoolSwing == "true" && capabilitySupportsCoolFanSpeed == "false"){
      if (state.tempunit == "C") {
    		jsonbody = new groovy.json.JsonOutput().toJson([setting:[mode:"COOL", power:"ON", swing:"OFF", temperature:[celsius:targetTemperature], type:"AIR_CONDITIONING"], termination:[type:terminationmode]])
  		}
  		else if(state.tempunit == "F"){
            jsonbody = new groovy.json.JsonOutput().toJson([setting:[mode:"COOL", power:"ON", swing:"OFF", temperature:[fahrenheit:targetTemperature], type:"AIR_CONDITIONING"], termination:[type:terminationmode]])
  		}
    }
    else if(capabilitySupportsCoolSwing == "false" && capabilitySupportsCoolFanSpeed == "false"){
      if (state.tempunit == "C") {
    		jsonbody = new groovy.json.JsonOutput().toJson([setting:[mode:"COOL", power:"ON", temperature:[celsius:targetTemperature], type:"AIR_CONDITIONING"], termination:[type:terminationmode]])
  		}
  		else if(state.tempunit == "F"){
            jsonbody = new groovy.json.JsonOutput().toJson([setting:[mode:"COOL", power:"ON", temperature:[fahrenheit:targetTemperature], type:"AIR_CONDITIONING"], termination:[type:terminationmode]])
  		}
    }
    else
    {
 		if (state.tempunit == "C") {
    		jsonbody = new groovy.json.JsonOutput().toJson([setting:[fanSpeed:supportedfanspeed, mode:"COOL", power:"ON", temperature:[celsius:targetTemperature], type:"AIR_CONDITIONING"], termination:[type:terminationmode]])
  		}
  		else if(state.tempunit == "F"){
    		jsonbody = new groovy.json.JsonOutput().toJson([setting:[fanSpeed:supportedfanspeed, mode:"COOL", power:"ON", temperature:[fahrenheit:targetTemperature], type:"AIR_CONDITIONING"], termination:[type:terminationmode]])
  		}
    }

	log.info "Executing 'sendCommand.setCoolingTempCommand' to ${targetTemperature} on device ${childDevice.device.name}"
	sendCommand("temperature",childDevice,[deviceId,jsonbody])
}

def setHeatingTempCommand(childDevice,targetTemperature){
  def terminationmode = settings.manualmode
  def item = (childDevice.device.deviceNetworkId).tokenize('|')
  def deviceId = item[0]
  def deviceType = item[1]
  def deviceToken = item[2]
  if(deviceType == "AIR_CONDITIONING")
  {
    def capabilitySupportsHeat = parseCapabilityData(childDevice.getCapabilitySupportsHeat())
    def capabilitysupported = capabilitySupportsHeat
    def capabilitySupportsHeatSwing = parseCapabilityData(childDevice.getCapabilitySupportsHeatSwing())
    def capabilitySupportsHeatAutoFanSpeed = parseCapabilityData(childDevice.getCapabilitySupportsHeatAutoFanSpeed())
    def capabilitySupportsHeatFanSpeed = parseCapabilityData(childDevice.getCapabilitySupportsHeatFanSpeed())
    def fancapabilitysupported = capabilitySupportsHeatAutoFanSpeed
    def supportedfanspeed
    def jsonbody
    if (fancapabilitysupported == "true")
    {
      supportedfanspeed = "AUTO"
    }
    else
    {
      supportedfanspeed = "HIGH"
    }
    if (capabilitySupportsHeatSwing == "true" && capabilitySupportsHeatFanSpeed == "true")
    {
 		if (state.tempunit == "C") {
    		jsonbody = new groovy.json.JsonOutput().toJson([setting:[fanSpeed:supportedfanspeed, mode:"HEAT", power:"ON", swing:"OFF", temperature:[celsius:targetTemperature], type:"AIR_CONDITIONING"], termination:[type:terminationmode]])
  		}
  		else if(state.tempunit == "F"){
            jsonbody = new groovy.json.JsonOutput().toJson([setting:[fanSpeed:supportedfanspeed, mode:"HEAT", power:"ON", swing:"OFF", temperature:[fahrenheit:targetTemperature], type:"AIR_CONDITIONING"], termination:[type:terminationmode]])
  		}
    }
    else if(capabilitySupportsHeatSwing == "true" && capabilitySupportsHeatFanSpeed == "false"){
      if (state.tempunit == "C") {
    		jsonbody = new groovy.json.JsonOutput().toJson([setting:[mode:"HEAT", power:"ON", swing:"OFF", temperature:[celsius:targetTemperature], type:"AIR_CONDITIONING"], termination:[type:terminationmode]])
  		}
  		else if(state.tempunit == "F"){
            jsonbody = new groovy.json.JsonOutput().toJson([setting:[mode:"HEAT", power:"ON", swing:"OFF", temperature:[fahrenheit:targetTemperature], type:"AIR_CONDITIONING"], termination:[type:terminationmode]])
  		}
    }
    else if(capabilitySupportsHeatSwing == "false" && capabilitySupportsHeatFanSpeed == "false"){
      if (state.tempunit == "C") {
    		jsonbody = new groovy.json.JsonOutput().toJson([setting:[mode:"HEAT", power:"ON", temperature:[celsius:targetTemperature], type:"AIR_CONDITIONING"], termination:[type:terminationmode]])
  		}
  		else if(state.tempunit == "F"){
            jsonbody = new groovy.json.JsonOutput().toJson([setting:[mode:"HEAT", power:"ON", temperature:[fahrenheit:targetTemperature], type:"AIR_CONDITIONING"], termination:[type:terminationmode]])
  		}
    }
    else
    {
 		if (state.tempunit == "C") {
    		jsonbody = new groovy.json.JsonOutput().toJson([setting:[fanSpeed:supportedfanspeed, mode:"HEAT", power:"ON", temperature:[celsius:targetTemperature], type:"AIR_CONDITIONING"], termination:[type:terminationmode]])
  		}
  		else if(state.tempunit == "F"){
    		jsonbody = new groovy.json.JsonOutput().toJson([setting:[fanSpeed:supportedfanspeed, mode:"HEAT", power:"ON", temperature:[fahrenheit:targetTemperature], type:"AIR_CONDITIONING"], termination:[type:terminationmode]])
  		}
    }
  	log.info "Executing 'sendCommand.setHeatingTempCommand' to ${targetTemperature} on device ${childDevice.device.name}"
    sendCommand("temperature",childDevice,[deviceId,jsonbody])
  }
  if(deviceType == "HEATING")
  {
    def jsonbody
    if (state.tempunit == "C") {
      jsonbody = new groovy.json.JsonOutput().toJson([setting:[power:"ON", temperature:[celsius:targetTemperature], type:"HEATING"], termination:[type:terminationmode]])
    }
    else if(state.tempunit == "F"){
      jsonbody = new groovy.json.JsonOutput().toJson([setting:[power:"ON", temperature:[fahrenheit:targetTemperature], type:"HEATING"], termination:[type:terminationmode]])
    }
    log.info "Executing 'sendCommand.setHeatingTempCommand' to ${targetTemperature} on device ${childDevice.device.name}"
    sendCommand("temperature",childDevice,[deviceId,jsonbody])
  }
  if(deviceType == "HOT_WATER")
  {
    def jsonbody
    def capabilitySupportsWaterTempControl = parseCapabilityData(childDevice.getCapabilitySupportsWaterTempControl())
    if(capabilitySupportsWaterTempControl == "true"){
      if (state.tempunit == "C") {
        jsonbody = new groovy.json.JsonOutput().toJson([setting:[power:"ON", temperature:[celsius:targetTemperature], type:"HOT_WATER"], termination:[type:terminationmode]])
			}
			else if(state.tempunit == "F"){
				jsonbody = new groovy.json.JsonOutput().toJson([setting:[power:"ON", temperature:[fahrenheit:targetTemperature], type:"HOT_WATER"], termination:[type:terminationmode]])
			}
		log.info "Executing 'sendCommand.setHeatingTempCommand' to ${targetTemperature} on device ${childDevice.device.name}"
		sendCommand("temperature",[jsonbody])
	  } else {
		    logDebug "Hot Water Temperature Capability Not Supported on device ${childDevice.device.name}"
	  }
  }
}

def offCommand(childDevice){
	log.info "Executing 'sendCommand.offCommand' on device ${childDevice.device.name}"
  def terminationmode = settings.manualmode
  def item = (childDevice.device.deviceNetworkId).tokenize('|')
  def deviceId = item[0]
  def deviceType = item[1]
  def deviceToken = item[2]
  def jsonbody = new groovy.json.JsonOutput().toJson([setting:[type:deviceType, power:"OFF"], termination:[type:terminationmode]])
  sendCommand("temperature",childDevice,[deviceId,jsonbody])
}

def onCommand(childDevice){
  log.info "Executing 'sendCommand.onCommand'"
  def item = (childDevice.device.deviceNetworkId).tokenize('|')
  def deviceId = item[0]
  def deviceType = item[1]
  def deviceToken = item[2]
  if(deviceType == "AIR_CONDITIONING")
  {
    coolCommand(childDevice)
  }
  if(deviceType == "HEATING" || deviceType == "HOT_WATER")
  {
    heatCommand(childDevice)
  }
}

def coolCommand(childDevice){
	log.info "Executing 'sendCommand.coolCommand'"
    def terminationmode = settings.manualmode
    def item = (childDevice.device.deviceNetworkId).tokenize('|')
    def deviceId = item[0]
    def deviceType = item[1]
    def deviceToken = item[2]
    def initialsetpointtemp
    def supportedfanspeed
    def capabilitySupportsCool = parseCapabilityData(childDevice.getCapabilitySupportsCool())
    def capabilitySupportsCoolSwing = parseCapabilityData(childDevice.getCapabilitySupportsCoolSwing())
    def capabilitysupported = capabilitySupportsCool
    def capabilitySupportsCoolAutoFanSpeed = parseCapabilityData(childDevice.getCapabilitySupportsCoolAutoFanSpeed())
    def capabilitySupportsCoolFanSpeed = parseCapabilityData(childDevice.getCapabilitySupportsCoolFanSpeed())
    def fancapabilitysupported = capabilitySupportsCoolAutoFanSpeed
    def traperror
	if(childDevice.device.currentValue("thermostatSetpoint"))
    {
        traperror = ((childDevice.device.currentValue("thermostatSetpoint")).intValue())
    }
    else
    {
       logDebug "Existing Setpoint is not set"
       traperror = 0
    }
    if (fancapabilitysupported == "true"){
    	supportedfanspeed = "AUTO"
        } else {
        supportedfanspeed = "HIGH"
        }
    if(traperror == 0){
    	initialsetpointtemp = settings.defCoolingTemp
    } else {
    	initialsetpointtemp = childDevice.device.currentValue("thermostatSetpoint")
    }
    def jsonbody
    if (capabilitySupportsCoolSwing == "true" && capabilitySupportsCoolFanSpeed == "true")
    {
    	if (state.tempunit == "C") {
    		jsonbody = new groovy.json.JsonOutput().toJson([setting:[fanSpeed:supportedfanspeed, mode:"COOL", power:"ON", swing:"OFF", temperature:[celsius:initialsetpointtemp], type:"AIR_CONDITIONING"], termination:[type:terminationmode]])
    	}
    	else if (state.tempunit == "F"){
    		jsonbody = new groovy.json.JsonOutput().toJson([setting:[fanSpeed:supportedfanspeed, mode:"COOL", power:"ON", swing:"OFF", temperature:[fahrenheit:initialsetpointtemp], type:"AIR_CONDITIONING"], termination:[type:terminationmode]])
    	}
    }
    else if(capabilitySupportsCoolSwing == "true" && capabilitySupportsCoolFanSpeed == "false"){
      if (state.tempunit == "C") {
    		jsonbody = new groovy.json.JsonOutput().toJson([setting:[mode:"COOL", power:"ON", swing:"OFF", temperature:[celsius:initialsetpointtemp], type:"AIR_CONDITIONING"], termination:[type:terminationmode]])
  		}
  		else if(state.tempunit == "F"){
            jsonbody = new groovy.json.JsonOutput().toJson([setting:[mode:"COOL", power:"ON", swing:"OFF", temperature:[fahrenheit:initialsetpointtemp], type:"AIR_CONDITIONING"], termination:[type:terminationmode]])
  		}
    }
    else if(capabilitySupportsCoolSwing == "false" && capabilitySupportsCoolFanSpeed == "false"){
      if (state.tempunit == "C") {
    		jsonbody = new groovy.json.JsonOutput().toJson([setting:[mode:"COOL", power:"ON", temperature:[celsius:initialsetpointtemp], type:"AIR_CONDITIONING"], termination:[type:terminationmode]])
  		}
  		else if(state.tempunit == "F"){
            jsonbody = new groovy.json.JsonOutput().toJson([setting:[mode:"COOL", power:"ON", temperature:[fahrenheit:initialsetpointtemp], type:"AIR_CONDITIONING"], termination:[type:terminationmode]])
  		}
    }
    else
    {
    	if (state.tempunit == "C") {
    		jsonbody = new groovy.json.JsonOutput().toJson([setting:[fanSpeed:supportedfanspeed, mode:"COOL", power:"ON", temperature:[celsius:initialsetpointtemp], type:"AIR_CONDITIONING"], termination:[type:terminationmode]])
    	}
    	else if (state.tempunit == "F"){
    		jsonbody = new groovy.json.JsonOutput().toJson([setting:[fanSpeed:supportedfanspeed, mode:"COOL", power:"ON", temperature:[fahrenheit:initialsetpointtemp], type:"AIR_CONDITIONING"], termination:[type:terminationmode]])
    	}
    }
    sendCommand("temperature",childDevice,[deviceId,jsonbody])
}

def heatCommand(childDevice){
  log.info "Executing 'sendCommand.heatCommand' on device ${childDevice.device.name}"
  def terminationmode = settings.manualmode
  def item = (childDevice.device.deviceNetworkId).tokenize('|')
  def deviceId = item[0]
  def deviceType = item[1]
  def deviceToken = item[2]
  if(deviceType == "AIR_CONDITIONING")
    {
      def initialsetpointtemp
      def supportedfanspeed
      def traperror
      def capabilitySupportsHeat = parseCapabilityData(childDevice.getCapabilitySupportsHeat())
      def capabilitySupportsHeatSwing = parseCapabilityData(childDevice.getCapabilitySupportsHeatSwing())
      def capabilitysupported = capabilitySupportsHeat
      def capabilitySupportsHeatAutoFanSpeed = parseCapabilityData(childDevice.getCapabilitySupportsHeatAutoFanSpeed())
      def capabilitySupportsHeatFanSpeed = parseCapabilityData(childDevice.getCapabilitySupportsHeatFanSpeed())
      def fancapabilitysupported = capabilitySupportsHeatAutoFanSpeed
      if(childDevice.device.currentValue("thermostatSetpoint"))
    	{
        	traperror = ((childDevice.device.currentValue("thermostatSetpoint")).intValue())
    	}
    	else
    	{
       		logDebug "Existing Setpoint is not set"
       		traperror = 0
    	}
      if (fancapabilitysupported == "true")
      {
        supportedfanspeed = "AUTO"
      }
      else
      {
        supportedfanspeed = "HIGH"
      }
      if(traperror == 0)
      {
        initialsetpointtemp = settings.defHeatingTemp
      }
      else
      {
        initialsetpointtemp = childDevice.device.currentValue("thermostatSetpoint")
      }
      def jsonbody
      if (capabilitySupportsHeatSwing == "true" && capabilitySupportsHeatFanSpeed == "true")
      {
      	if (state.tempunit == "C") {
      		jsonbody = new groovy.json.JsonOutput().toJson([setting:[fanSpeed:supportedfanspeed, mode:"HEAT", power:"ON", swing:"OFF", temperature:[celsius:initialsetpointtemp], type:"AIR_CONDITIONING"], termination:[type:terminationmode]])
      	}
      	else if (state.tempunit == "F"){
    		jsonbody = new groovy.json.JsonOutput().toJson([setting:[fanSpeed:supportedfanspeed, mode:"HEAT", power:"ON", swing:"OFF", temperature:[fahrenheit:initialsetpointtemp], type:"AIR_CONDITIONING"], termination:[type:terminationmode]])
      	}
      }
      else if(capabilitySupportsHeatSwing == "true" && capabilitySupportsHeatFanSpeed == "false"){
        if (state.tempunit == "C") {
          jsonbody = new groovy.json.JsonOutput().toJson([setting:[mode:"HEAT", power:"ON", swing:"OFF", temperature:[celsius:initialsetpointtemp], type:"AIR_CONDITIONING"], termination:[type:terminationmode]])
        }
        else if(state.tempunit == "F"){
              jsonbody = new groovy.json.JsonOutput().toJson([setting:[mode:"HEAT", power:"ON", swing:"OFF", temperature:[fahrenheit:initialsetpointtemp], type:"AIR_CONDITIONING"], termination:[type:terminationmode]])
        }
      }
      else if(capabilitySupportsHeatSwing == "false" && capabilitySupportsHeatFanSpeed == "false"){
        if (state.tempunit == "C") {
          jsonbody = new groovy.json.JsonOutput().toJson([setting:[mode:"HEAT", power:"ON", temperature:[celsius:initialsetpointtemp], type:"AIR_CONDITIONING"], termination:[type:terminationmode]])
        }
        else if(state.tempunit == "F"){
              jsonbody = new groovy.json.JsonOutput().toJson([setting:[mode:"HEAT", power:"ON", temperature:[fahrenheit:initialsetpointtemp], type:"AIR_CONDITIONING"], termination:[type:terminationmode]])
        }
      }
      else
      {
      	if (state.tempunit == "C") {
      		jsonbody = new groovy.json.JsonOutput().toJson([setting:[fanSpeed:supportedfanspeed, mode:"HEAT", power:"ON", temperature:[celsius:initialsetpointtemp], type:"AIR_CONDITIONING"], termination:[type:terminationmode]])
      	}
      	else if (state.tempunit == "F"){
    		jsonbody = new groovy.json.JsonOutput().toJson([setting:[fanSpeed:supportedfanspeed, mode:"HEAT", power:"ON", temperature:[fahrenheit:initialsetpointtemp], type:"AIR_CONDITIONING"], termination:[type:terminationmode]])
      	}
      }

      sendCommand("temperature",childDevice,[deviceId,jsonbody])
    }
    if(deviceType == "HEATING")
    {
      def initialsetpointtemp
      def traperror
      if(childDevice.device.currentValue("thermostatSetpoint"))
    	{
        	traperror = ((childDevice.device.currentValue("thermostatSetpoint")).intValue())
    	}
    	else
    	{
       		logDebug "Existing Setpoint is not set"
       		traperror = 0
    	}
        if(traperror == 0)
        {
          initialsetpointtemp = settings.defHeatingTemp
        }
        else
        {
          initialsetpointtemp = childDevice.device.currentValue("thermostatSetpoint")
        }
        def jsonbody
        if (state.tempunit == "C") {
      		jsonbody = new groovy.json.JsonOutput().toJson([setting:[power:"ON", temperature:[celsius:initialsetpointtemp], type:"HEATING"], termination:[type:terminationmode]])
      	}
        else if (state.tempunit == "F"){
        	jsonbody = new groovy.json.JsonOutput().toJson([setting:[power:"ON", temperature:[fahrenheit:initialsetpointtemp], type:"HEATING"], termination:[type:terminationmode]])
      	}
        sendCommand("temperature",childDevice,[deviceId,jsonbody])
    }
    if(deviceType == "HOT_WATER")
    {
      def jsonbody
      def initialsetpointtemp
      def traperror
      def capabilitySupportsWaterTempControl = parseCapabilityData(childDevice.getCapabilitySupportsWaterTempControl())
      if(capabilitySupportsWaterTempControl == "true"){
        if(childDevice.device.currentValue("thermostatSetpoint"))
    	{
        	traperror = ((childDevice.device.currentValue("thermostatSetpoint")).intValue())
    	}
    	else
    	{
       		logDebug "Existing Setpoint is not set"
       		traperror = 0
    	}
        if(traperror == 0){
          initialsetpointtemp = settings.defHeatingTemp
        } else {
          initialsetpointtemp = childDevice.device.currentValue("thermostatSetpoint")
        }
        if (state.tempunit == "C") {
      		jsonbody = new groovy.json.JsonOutput().toJson([setting:[power:"ON", temperature:[celsius:initialsetpointtemp], type:"HOT_WATER"], termination:[type:terminationmode]])
      	}
        else if (state.tempunit == "F"){
        	jsonbody = new groovy.json.JsonOutput().toJson([setting:[power:"ON", temperature:[fahrenheit:initialsetpointtemp], type:"HOT_WATER"], termination:[type:terminationmode]])
      	}
      } else {
        jsonbody = new groovy.json.JsonOutput().toJson([setting:[power:"ON", type:"HOT_WATER"], termination:[type:terminationmode]])
      }
      sendCommand("temperature",childDevice,[deviceId,jsonbody])
    }
}

def emergencyHeat(childDevice){
  log.info "Executing 'sendCommand.heatCommand' on device ${childDevice.device.name}"
  def traperror
  def item = (childDevice.device.deviceNetworkId).tokenize('|')
  def deviceId = item[0]
  def deviceType = item[1]
  def deviceToken = item[2]
  if(deviceType == "AIR_CONDITIONING")
  {
    def capabilitySupportsHeat = parseCapabilityData(childDevice.getCapabilitySupportsHeat())
    def capabilitysupported = capabilitySupportsHeat
    def capabilitySupportsHeatSwing = parseCapabilityData(childDevice.getCapabilitySupportsHeatSwing())
    def capabilitySupportsHeatAutoFanSpeed = parseCapabilityData(childDevice.getCapabilitySupportsHeatAutoFanSpeed())
    def capabilitySupportsHeatFanSpeed = parseCapabilityData(childDevice.getCapabilitySupportsHeatFanSpeed())
    def fancapabilitysupported = capabilitySupportsHeatAutoFanSpeed
    if(childDevice.device.currentValue("thermostatSetpoint"))
    {
        traperror = ((childDevice.device.currentValue("thermostatSetpoint")).intValue())
    }
    else
    {
       logDebug "Existing Setpoint is not set"
       traperror = 0
    }
    if (capabilitysupported == "true")
    {
      def initialsetpointtemp
      def supportedfanspeed
      if (fancapabilitysupported == "true")
      {
        supportedfanspeed = "AUTO"
      }
      else
      {
        supportedfanspeed = "HIGH"
      }
      if(traperror == 0)
      {
        initialsetpointtemp = settings.defHeatingTemp
      }
      else
      {
        initialsetpointtemp = childDevice.device.currentValue("thermostatSetpoint")
      }
      def jsonbody
	  if (capabilitySupportsHeatSwing == "true" && capabilitySupportsHeatFanSpeed == "true")
      {
      	if (state.tempunit == "C") {
      		jsonbody = new groovy.json.JsonOutput().toJson([setting:[fanSpeed:supportedfanspeed, mode:"HEAT", power:"ON", swing:"OFF", temperature:[celsius:initialsetpointtemp], type:"AIR_CONDITIONING"], termination:[durationInSeconds:"3600", type:"TIMER"]])
      	}
      	else if (state.tempunit == "F"){
      		jsonbody = new groovy.json.JsonOutput().toJson([setting:[fanSpeed:supportedfanspeed, mode:"HEAT", power:"ON", swing:"OFF", temperature:[fahrenheit:initialsetpointtemp], type:"AIR_CONDITIONING"], termination:[durationInSeconds:"3600", type:"TIMER"]])
      	}
      }
      else if(capabilitySupportsHeatSwing == "true" && capabilitySupportsHeatFanSpeed == "false"){
        if (state.tempunit == "C") {
          jsonbody = new groovy.json.JsonOutput().toJson([setting:[mode:"HEAT", power:"ON", swing:"OFF", temperature:[celsius:initialsetpointtemp], type:"AIR_CONDITIONING"], termination:[type:terminationmode]])
        }
        else if(state.tempunit == "F"){
              jsonbody = new groovy.json.JsonOutput().toJson([setting:[mode:"HEAT", power:"ON", swing:"OFF", temperature:[fahrenheit:initialsetpointtemp], type:"AIR_CONDITIONING"], termination:[type:terminationmode]])
        }
      }
      else if(capabilitySupportsHeatSwing == "false" && capabilitySupportsHeatFanSpeed == "false"){
        if (state.tempunit == "C") {
          jsonbody = new groovy.json.JsonOutput().toJson([setting:[mode:"HEAT", power:"ON", temperature:[celsius:initialsetpointtemp], type:"AIR_CONDITIONING"], termination:[type:terminationmode]])
        }
        else if(state.tempunit == "F"){
              jsonbody = new groovy.json.JsonOutput().toJson([setting:[mode:"HEAT", power:"ON", temperature:[fahrenheit:initialsetpointtemp], type:"AIR_CONDITIONING"], termination:[type:terminationmode]])
        }
      }
      else
      {
		if (state.tempunit == "C") {
      		jsonbody = new groovy.json.JsonOutput().toJson([setting:[fanSpeed:supportedfanspeed, mode:"HEAT", power:"ON", temperature:[celsius:initialsetpointtemp], type:"AIR_CONDITIONING"], termination:[durationInSeconds:"3600", type:"TIMER"]])
      	}
      	else if (state.tempunit == "F"){
      		jsonbody = new groovy.json.JsonOutput().toJson([setting:[fanSpeed:supportedfanspeed, mode:"HEAT", power:"ON", temperature:[fahrenheit:initialsetpointtemp], type:"AIR_CONDITIONING"], termination:[durationInSeconds:"3600", type:"TIMER"]])
      	}
      }
      sendCommand("temperature",childDevice,[deviceId,jsonbody])
      statusCommand(device)
    }
    else
    {
      logDebug("Sorry Heat Capability not supported on device ${childDevice.device.name}")
    }
  }
  if(deviceType == "HEATING")
  {
      def initialsetpointtemp
      if(childDevice.device.currentValue("thermostatSetpoint"))
    	{
        	traperror = ((childDevice.device.currentValue("thermostatSetpoint")).intValue())
    	}
    	else
    	{
       		logDebug "Existing Setpoint is not set"
       		traperror = 0
    	}
      if(traperror == 0)
      {
        initialsetpointtemp = settings.defHeatingTemp
      }
      else
      {
        initialsetpointtemp = childDevice.device.currentValue("thermostatSetpoint")
      }
      def jsonbody
      if (state.tempunit == "C") {
      	jsonbody = new groovy.json.JsonOutput().toJson([setting:[power:"ON", temperature:[celsius:initialsetpointtemp], type:"HEATING"], termination:[durationInSeconds:"3600", type:"TIMER"]])
      }
      else if (state.tempunit == "F"){
      	jsonbody = new groovy.json.JsonOutput().toJson([setting:[power:"ON", temperature:[fahrenheit:initialsetpointtemp], type:"HEATING"], termination:[durationInSeconds:"3600", type:"TIMER"]])
      }
      sendCommand("temperature",childDevice,[deviceId,jsonbody])
      statusCommand(childDevice)
  }
  (deviceType == "HOT_WATER")
  {
    def initialsetpointtemp
    def jsonbody
    def capabilitySupportsWaterTempControl = parseCapabilityData(childDevice.getCapabilitySupportsWaterTempControl())
    if(capabilitySupportsWaterTempControl == "true"){
      if(childDevice.device.currentValue("thermostatSetpoint"))
    	{
        	traperror = ((childDevice.device.currentValue("thermostatSetpoint")).intValue())
    	}
    	else
    	{
       		logDebug "Existing Setpoint is not set"
       		traperror = 0
    	}
      if(traperror == 0)
      {
        initialsetpointtemp = settings.defHeatingTemp
      }
      else
      {
        initialsetpointtemp = childDevice.device.currentValue("thermostatSetpoint")
      }
      if (state.tempunit == "C") {
      	jsonbody = new groovy.json.JsonOutput().toJson([setting:[power:"ON", temperature:[celsius:initialsetpointtemp], type:"HOT_WATER"], termination:[durationInSeconds:"3600", type:"TIMER"]])
      }
      else if (state.tempunit == "F"){
      	jsonbody = new groovy.json.JsonOutput().toJson([setting:[power:"ON", temperature:[fahrenheit:initialsetpointtemp], type:"HOT_WATER"], termination:[durationInSeconds:"3600", type:"TIMER"]])
      }
    }
    else
    {
      jsonbody = new groovy.json.JsonOutput().toJson([setting:[power:"ON", type:"HOT_WATER"], termination:[durationInSeconds:"3600", type:"TIMER"]])
    }
    sendCommand("temperature",childDevice,[deviceId,jsonbody])
    statusCommand(childDevice)
  }
}

def statusCommand(childDevice){
  def item = (childDevice.device.deviceNetworkId).tokenize('|')
  def deviceId = item[0]
  def deviceType = item[1]
  def deviceToken = item[2]
	log.info "Executing status for: " + childDevice
	sendCommand("status",childDevice,[deviceId])
}

def userStatusCommand(childDevice){
	try{
		log.info "Executing status for: " + childDevice
		sendCommand("userStatus",childDevice,[])
    	} catch(Exception e) { logDebug("Failed in setting userStatusCommand: " + e)
    }
}

// -------------------------------------------------------------------------------------------------------------------------------------------------------------------
// OAuth methods
// -------------------------------------------------------------------------------------------------------------------------------------------------------------------

//mine
def oauthAccessTokenGrant() {
    log.info "Executing OAuth 2 Autorization Flow: Request Access Token"
	def params = [
		uri: getOauthTokenUri(),
		path: getOauthTokenPath(),
		body: [
			client_id: getClientId(),
			client_secret: getClientSecret(),
			username: settings.username,
			password: settings.password,
			grant_type: "password"
		]
    ]
	try {
		logDebug "Parameters for PWD token request:" + " " + params
		httpPost(params)
		{ resp ->
            logDebug "PWD Response data: " + resp.data
            logDebug "PWD Response success: " + resp.success
			if (resp && resp.data && resp.success) {
				state.refreshToken = resp.data.refresh_token
				logDebug "PWD Refresh token now : " + state.refreshToken
				state.authToken = resp.data.access_token
				logDebug "PWD Auth token now : " + state.authToken
				state.authTokenExpires = (now() + (resp.data.expires_in * 1000)) - 60000
				logDebug "Token expires:" + " " + state.authTokenExpires
			} else { log.error "Failed to retreive access token" }
		}
	}
	catch (e) {
		log.error "OAuth error: ${e}"
	}
}

def refreshToken() {
	def result = false
    log.info "Refreshing OAuth token"
    //clear current auth token as we are going to try for a new one
    logDebug "F5 auth token at the start is : " + state.authToken
    state.authToken = ""
    logDebug "F5 auth token now blanked : " + state.authToken
    if (state.refreshToken == "" | state.refreshToken == null){
        logDebug "Empty refresh token so using password"
        oauthAccessTokenGrant()
        return
    }

	try {
        def params = [
                uri: getOauthTokenUri(),
                path: getOauthTokenPath(),
				body: [
                    client_id: getClientId(),
                    client_secret: getClientSecret(),
                    refresh_token: getRefreshToken(),
                    grant_type: "refresh_token"
				]
        ]
        logDebug "Parameters for token refresh:" + " " + params

		httpPost(params) { resp ->
            logDebug "F5 Response data:" + " " + resp.data
            logDebug "F5 Response succes:" + " " + resp.success
			if (resp && resp.data && resp.success) {
                state.refreshToken = resp.data.refresh_token
                logDebug "F5 Refresh token now: " +  state.refreshToken
                state.authToken = resp.data.access_token
                logDebug "F5 Access token now: " +  state.authToken
                state.authTokenExpires = now() + (resp.data.expires_in * 1000) - 60000
                logDebug "F5 Token expires:" + " " + state.authTokenExpires
				result = true
			}
		}
        if (state.authToken == "" | state.authToken == null) {
            log.error "Failed to refresh token"
            //attempt to get new token using original creds
            oauthAccessTokenGrant()
            if (state.authToken == "") {
                result = false
            } else {
                result = true
            }
        } else {
            result = true
        }
	} catch (e) {
		log.error "Failed to refresh token: ${e}"
		state.authToken = ""
		//attempt to get new token using original creds
		oauthAccessTokenGrant()
		if (state.authToken == "" | state.authToken == null) {
			result = true
		} else {
			result = false
		}
	}
	//return result
    logDebug "F5 auth token at end is : " + state.authToken
}

def logDebug(message) {
	if (debugLogging == true){
		log.debug message
	}
}
