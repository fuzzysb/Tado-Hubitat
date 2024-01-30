
/**
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
 *	Tado Thermostat
 *
 * Author: Stuart Buchanan, Based on original work by Ian M with thanks. also source for icons was from @tonesto7's excellent Nest Manager.
 *
 *	Updates:
 *	Date: 2023	  v2.4 Included HeatingPower code to show how much heat is being requested		
 *	Date: 2018-04-26  v2.3 Modified for Hubitat
 *	Date: 2016-12-19  v2.2 Changed Icon Location to New Tado Repository
 *	Date: 2016-12-18  v2.1 added missing cpabilities commands which prevented set points from working correctly
 *	Date: 2016-12-03  v2.0 Removed Device Prefs as they are now hosted by the Tado (Connect) SmartApp
 *	Date: 2016-11-28  v1.9 Moved all data collection functions into Tado (Connect) SmartApp, huge changes to device handler, existing devices and handler will need to be uninstalled before installing this version
 *	Date: 2016-07-13  v1.8 Quick dirty workaround to control zones with a single account.
 *  Date: 2016-04-25 	v1.7 Finally found time to update this with the lessons learnt from the Tado Cooling Device Type. will bring better support for RM and Thermostat Director
 *  Date: 2016-04-08 	v1.6 added statusCommand calls to refresh more frequently, also improved compatibility with Rule Machine and Thermostat Mode Director in addition also added default heating temperature where you can set the default temperature for the mode commands.
 *  Date: 2016-04-05 	v1.5 added improved icons and also a manual Mode End function to fall back to Tado Control.
 				Also added preference for how long manual mode runs for either ends at Tado Mode Change (TADO_MODE) or User Control (MANUAL),
                please ensure the default method is Set in the device properties
 *  Date: 2016-04-05 	v1.4 rewrite of complete functions to support Tado API v2
 *  Date: 2016-01-20  v1.3 Updated hvacStatus to include include the correct HomeId for Humidity Value
 *  Date: 2016-01-15  v1.2 Refactored API request code and added querying/display of humidity
 *	Date: 2015-12-23	v1.1 Added functionality to change thermostat settings
 *	Date: 2015-12-04	v1.0 Initial release
 */

preferences {
}

metadata {
	definition (name: "Tado Heating Thermostat", namespace: "fuzzysb", author: "Stuart Buchanan") {
		capability "Actuator"
    capability "Temperature Measurement"
		capability "Thermostat Heating Setpoint"
		capability "Thermostat Setpoint"
		capability "Thermostat Mode"
		capability "Thermostat Operating State"
		capability "Thermostat"
		capability "Relative Humidity Measurement"
        capability "Polling"
		attribute "tadoMode", "string"
        attribute "heatingPower", "number"
        attribute "openWindow", "boolean"
		command "temperatureUp"
        command "temperatureDown"
        command "heatingSetpointUp"
        command "heatingSetpointDown"
		command "on"
        command "endManualControl"
		command "emergencyHeat"

	}

	// simulator metadata
	simulator {
		// status messages

		// reply messages
	}
}


def getWeather(){
	parent.weatherStatusCommand(this)
}

def setCapabilitytadoType(value){
  state.tadoType = value
  log.debug("state.tadoType = ${state.tadoType}")
}

def getCapabilitytadoType() {
  def map = null
  map = [name: "capabilityTadoType", value: state.tadoType]
  return map
}

def setCapabilitySupportsHeat(value){
  state.supportsHeat = value
  log.debug("state.supportsHeat = ${state.supportsHeat}")
}

def getCapabilitySupportsHeat() {
  def map = null
  map = [name: "capabilitySupportsHeat", value: state.supportsHeat]
  return map
}

def getCapabilityMinCoolTemp() {
  def map = null
  map = [name: "capabilityMinCoolTemp", value: state.MinCoolTemp]
  return map
}

def setCapabilityMaxHeatTemp(value){
  state.MaxHeatTemp = value
  log.debug("set state.MaxHeatTemp to : " + state.MaxHeatTemp)
}

def getCapabilityMaxHeatTemp() {
  def map = null
  map = [name: "capabilityMaxHeatTemp", value: state.MaxHeatTemp]
  return map
}

def setCapabilityMinHeatTemp(value){
  state.MinHeatTemp = value
  log.debug("set state.MinHeatTemp to : " + state.MinHeatTemp)
}

def getCapabilityMinHeatTemp() {
  def map = null
  map = [name: "capabilityMinHeatTemp", value: state.MinHeatTemp]
  return map
}

def updated(){
	getInitialDeviceinfo()
}

def installed(){
	getInitialDeviceinfo()
}

def getInitialDeviceinfo(){
	log.debug "Getting 'initial Device info'"
	parent.getCapabilitiesCommand(this, device.deviceNetworkId)
    refresh()
}

def poll() {
	log.debug "Executing 'poll'"
	refresh()
}

def refresh() {
	log.debug "Executing 'refresh'"
    parent.statusCommand(this)
    //getWeather()
}

def auto() {
	log.debug "Executing 'auto'"
	parent.autoCommand(this)
  parent.statusCommand(this)
}

def on() {
	log.debug "Executing 'on'"
	parent.onCommand(this)
    parent.statusCommand(this)
}

def off() {
    log.debug "Executing 'off'"
	parent.offCommand(this)
    parent.statusCommand(this)
}

def setHeatingSetpoint(targetTemperature) {
    log.debug "Executing 'setHeatingSetpoint'"
    log.info "Target Temperature ${targetTemperature}"
    parent.setHeatingTempCommand(this,targetTemperature)
	parent.statusCommand(this)
}

def setThermostatMode(requiredMode){
	switch (requiredMode) {
    	case "heat":
        	heat()
        break
        case "auto":
        	auto()
        break
		case "off":
        	off()
        break
		case "emergency heat":
        	emergencyHeat()
        break
     }
}

def temperatureUp(){
	if (device.currentValue("thermostatMode") == "heat") {
    	heatingSetpointUp()
    } else {
    	log.debug ("temperature setpoint not supported in the current thermostat mode")
    }
}

def temperatureDown(){
	if (device.currentValue("thermostatMode") == "heat") {
    	heatingSetpointDown()
    } else {
    	log.debug ("temperature setpoint not supported in the current thermostat mode")
    }
}

def heatingSetpointUp(){
	log.debug "Current SetPoint Is " + (device.currentValue("thermostatSetpoint")).toString()
    if ((device.currentValue("thermostatSetpoint").toInteger() - 1 ) < state.MinHeatTemp){
    	log.debug("cannot decrease heat setpoint, its already at the minimum level of " + state.MinHeatTemp)
    } else {
		int newSetpoint = (device.currentValue("thermostatSetpoint")).toInteger() + 1
		log.info "Setting heatingSetpoint up to: ${newSetpoint}"
		setHeatingSetpoint(newSetpoint)
    }
}

def heatingSetpointDown(){
	log.debug "Current SetPoint Is " + (device.currentValue("thermostatSetpoint")).toString()
  if ((device.currentValue("thermostatSetpoint").toInteger() + 1 ) > state.MaxHeatTemp){
    log.debug("cannot increase heat setpoint, its already at the maximum level of " + state.MaxHeatTemp)
    } else {
      int newSetpoint = (device.currentValue("thermostatSetpoint")).toInteger() - 1
      log.info "Setting heatingSetpoint down to: ${newSetpoint}"
      setHeatingSetpoint(newSetpoint)
    }
}

// Commands to device
def heat(){
	parent.heatCommand(this)
	parent.statusCommand(this)
}

def emergencyHeat(){
  parent.emergencyHeat(this)
}

def endManualControl(){
	parent.endManualControl(this)
}
