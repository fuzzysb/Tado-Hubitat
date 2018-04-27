Tado-Hubitat integration

Tado (Connect): Smartapp and device Types to enable more smart thermostat's capabilities within Hubitat

Author: Stuart Buchanan


/*********************************************************************************************

Setup time: approximately about 5 minutes

PREREQUISITES

Your Tado Devices fully operational (and connected to wifi)
Your Tado credentials (username/password)
Access to Hubitat (e.g. http://portal.hubitat/)

You need to update from Repo the Tado Connect Smart app and the four Device Types.


/*********************************************************************************************

1) Create new device Handlers

/*********************************************************************************************

a) Go to https://portal.hubitat.com log in and select your hub

b) Hit the "Drivers Code" at the left menu

c) Hit the "New Driver" button at the top right

d) Copy and paste the code from https://github.com/fuzzysb/Tado-Hubitat/blob/master/devicetypes/fuzzysb/tado-heating-thermostat.src/tado-heating-thermostat.groovy

e) Hit the save button at the top

Complete steps b - e again for each of the following device Types

https://github.com/fuzzysb/Tado-Hubitat/blob/master/devicetypes/fuzzysb/tado-cooling-thermostat.src/tado-cooling-thermostat.groovy

https://github.com/fuzzysb/Tado-Hubitat/blob/master/devicetypes/fuzzysb/tado-hot-water-control.src/tado-hot-water-control.groovy

https://github.com/fuzzysb/Tado-Hubitat/blob/master/devicetypes/fuzzysb/tado-user-presence.src/tado-user-presence.groovy


/*********************************************************************************************

2) Create a Smart App (Tado (Connect))

/*********************************************************************************************

a) Go to https://portal.hubitat.com sign in and select your hub

b) Hit the "Apps Code" at the left menu

c) Hit the "New App" button at the top right

d) Copy and paste the code from https://github.com/fuzzysb/Tado-Hubitat/blob/master/smartapps/fuzzysb/tado-connect.src/tado-connect.groovy

e) Hit the Save button at the top

f) click the OAuth button and enable OAuth and click update


/*********************************************************************************************

3) Connect Hubitat to Tado

/*********************************************************************************************

You should already have an tado username and password, if not go to https://my.tado.com and create a new login

Go through the authentication process using Tado (Connect) from the apps section of Hubitat and install Tado (Connect) from the "Your Apps" Section


After being connected, click 'Next' and select your Tado device(s) (Heating, Cooling, Radiator Valves) that you want to control from Hubitat and, then press 'Next'

next enter the default heating and cooling temperatures to be used when a Setpoint has not been selected and also enter the default tado override method, these are Tado-Mode which applies the override only until the next Tado mode change, or manual which will apply the override until cancelled by the User

once complete you now have devices that have been created for each of the devices you selected during setup, you should enter the Tado (Connect) smartapp to add or delete these devices.

/*********************************************************************************************

4) Your device(s) should now be ready to process your commands

/*********************************************************************************************

You should see your devices under

your Devices Page in Hubitat

countless hours have been devoted to developing this smartapp and connected devices. if you use and find useful please donate to aid further development of this product. any and all donations are very much appreciated.

https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=CNRR3ER3CTYDQ
