/*
 * Thanks to Zigbee2MQTT authors and iamtrep/PJ for device technical details
 */
import hubitat.zigbee.zcl.DataType

metadata {
    definition(
        name: 'Sinope Switch SW2500ZB (Full)',
        namespace: 'sorsme',
        author: 'SORS'
    ) {
        capability 'Switch'
        capability 'Configuration'
        capability 'Initialize'
        capability 'Refresh'
        capability 'EnergyMeter'
        capability 'TemperatureMeasurement'
        capability 'DoubleTapableButton'
        capability 'HoldableButton'
        capability 'PushableButton'

        // FF01 attributes
        attribute 'keypadLock',      'string'
        attribute 'firmwareNumber',  'string'
        attribute 'firmwareVersion', 'string'
        attribute 'onLedColor',      'string'
        attribute 'offLedColor',     'string'
        attribute 'onLedIntensity',  'number'
        attribute 'offLedIntensity', 'number'
        attribute 'phaseControl',    'string'
        attribute 'timer',           'number'
        attribute 'timerCountDown',  'number'
        attribute 'connectedLoad',   'number'
        attribute 'status',          'number'

        // standard
        attribute 'numberOfButtons', 'number'
        attribute 'temperature', 'number'

        command 'lockKeypad'
        command 'unlockKeypad'
        command 'setOnLedColor',      [[name: '0xRGB ', type: 'STRING']]
        command 'setOffLedColor',     [[name: '0xRGB ', type: 'STRING']]
        command 'setOnLedIntensity',  [[name: '1-100% ', type: 'NUMBER']]
        command 'setOffLedIntensity', [[name: '1-100% ', type: 'NUMBER']]
        command 'setTimer',    [[name: '1–10800sec, 0 to Disable ', type: 'NUMBER']]
        command 'refreshTimer'
        command 'setConnectedLoad', [[name:'Watts ', type:'NUMBER']]
    }

    preferences {
        input name: 'powerReportInterval', type: 'number',
            title: 'Power Δ to report (W)', defaultValue: 50, range: '1..*'
        input name: 'energyReportInterval', type: 'number',
            title: 'Energy Δ to report (Wh)', defaultValue: 10, range: '1..*'
        input name: 'logEnable', type: 'bool',
            title: 'Enable debug logging', defaultValue: true
    }
}

def installed()  { initialize() }
def updated()    { unschedule(); initialize() }

def initialize() {
    sendEvent(name: 'numberOfButtons', value: 2, displayed: false)
    configure()
    runIn(2, refresh)
}

def configure() {
    if (logEnable) log.debug 'Configuring reporting...'
    def cmds = []
    cmds += zigbee.configureReporting(0x0002, 0x0000, DataType.INT16, 0, 3600)
    cmds += zigbee.configureReporting(0x0006, 0x0000, DataType.BOOLEAN, 0, 3600, 1)
    cmds += zigbee.configureReporting(0x0B04, 0x0505, DataType.UINT16, 30, 600, 10)
    cmds += zigbee.configureReporting(0x0B04, 0x050B, DataType.UINT24, 30, 600, (powerReportInterval ?: 50) as Integer)
    cmds += zigbee.configureReporting(0x0702, 0x0000, DataType.UINT48, 60, 3600, (energyReportInterval ?: 10) as Integer)
    cmds += zigbee.configureReporting(0xFF01, 0x0054, DataType.ENUM8, 0, 3600, 0)
    sendZigbeeCommands(cmds)
}

def refresh() {
    if (logEnable) log.debug 'Refreshing all attributes'
    def cmds = []
    cmds += zigbee.readAttribute(0x0002,   0x0000)
    cmds += zigbee.readAttribute(0x0006,   0x0000)
    cmds += zigbee.readAttribute(0x0008,   0x0000)
    cmds += zigbee.readAttribute(0x0008,   0x0011)
    cmds += zigbee.readAttribute(0x0B04,   0x0505)
    cmds += zigbee.readAttribute(0x0B04,   0x050B)
    cmds += zigbee.readAttribute(0x0702,   0x0000)
    cmds += zigbee.readAttribute(0xFF01,   0x0002) 
    cmds += zigbee.readAttribute(0xFF01,   0x0050) 
    cmds += zigbee.readAttribute(0xFF01,   0x0051) 
    cmds += zigbee.readAttribute(0xFF01,   0x0052) 
    cmds += zigbee.readAttribute(0xFF01,   0x0053) 
    cmds += zigbee.readAttribute(0xFF01,   0x0055) 
    cmds += zigbee.readAttribute(0xFF01,   0x0056) 
    cmds += zigbee.readAttribute(0xFF01,   0x0058) 
    cmds += zigbee.readAttribute(0xFF01,   0x00A0) 
    cmds += zigbee.readAttribute(0xFF01,   0x00A1) 
    cmds += zigbee.readAttribute(0xFF01,   0x0119) // [mfgCode: 0x119C]

    sendZigbeeCommands(cmds)
}

def on()  {
    if (logEnable) log.debug 'Sending ON'
    def cmds = []
    cmds += zigbee.command(0x0006, 0x01)
    sendZigbeeCommands(cmds)
}

def off() {
    if (logEnable) log.debug 'Sending OFF'
    def cmds = []
    cmds += zigbee.command(0x0006, 0x00)
    sendZigbeeCommands(cmds)
}

def parse(String description) {
    if (logEnable) log.trace "parse() ← ${description}"
    def desc = zigbee.parseDescriptionAsMap(description) ?: [:]
    switch (desc.clusterInt) {
        case 0x0002:
            if (desc.attrInt == 0x0000) {
                sendEvent(name: 'temperature', value: Integer.parseInt(desc.value, 16))
            }
            break        
        case 0x0006:
            if (desc.attrInt == 0x0000) {
                sendEvent(name: 'switch', value: desc.value == '01' ? 'on' : 'off')
            }
            break
        case 0x0B04:
            if (desc.attrInt == 0x0505) {
                def v = Integer.parseInt(desc.value, 16) / 100
                sendEvent(name: 'voltage', value: v, unit: 'V')
            } else if (desc.attrInt == 0x050B) {
                def p = Integer.parseInt(desc.value, 16) / 10
                sendEvent(name: 'power', value: p, unit: 'W')
            }
            break
        case 0x0702:
            if (desc.attrInt == 0x0000) {
                def wh = new BigInteger(desc.value, 16)
                def kwh = (wh / 1000).setScale(3, BigDecimal.ROUND_HALF_UP)
                sendEvent(name: 'energy', value: kwh, unit: 'kWh')
            }
            break
        case 0xFF01:
            switch (desc.attrInt) {
                case 0x0002:
                    sendEvent(name: 'keypadLock', value: desc.value == '01' ? 'Locked' : 'Unlocked')
                    break
                case 0x0003:
                    sendEvent(name: 'firmwareNumber', value: Integer.parseInt(desc.value,16).toString())
                    break
                case 0x0004:
                    sendEvent(name: 'firmwareVersion', value: desc.value)
                    break
                case 0x0050:
                    sendEvent(name: 'onLedColor', value: String.format('%06X', Integer.parseInt(desc.value,16)))
                    break
                case 0x0051:
                    sendEvent(name: 'offLedColor', value: String.format('%06X', Integer.parseInt(desc.value,16)))
                    break
                case 0x0052:
                    sendEvent(name: 'onLedIntensity', value: Integer.parseInt(desc.value,16))
                    break
                case 0x0053:
                    sendEvent(name: 'offLedIntensity', value: Integer.parseInt(desc.value,16))
                    break
                case 0x0054:
                    dispatchTap(desc.value.toInteger())
                    break
                case 0x0090:
                    def wh = Integer.parseInt(desc.value,16)
                    def kwh = (wh/1000).setScale(3, BigDecimal.ROUND_HALF_UP)
                    sendEvent(name: 'energy', value: kwh, unit: 'kWh')
                    break
                case 0x00A0:
                    sendEvent(name: 'timer', value: Integer.parseInt(desc.value,32))
                    break
                case 0x00A1:
                    sendEvent(name: 'timerCountDown', value: Integer.parseInt(desc.value,32))
                    break
                case 0x0119:
                    sendEvent(name: 'connectedLoad', value: Integer.parseInt(desc.value,16), unit: 'W')
                    break
                case 0x0200:
                    sendEvent(name: 'status', value: Integer.parseInt(desc.value,16))
                    break
                default:
                    if (logEnable) log.debug "Unhandled FF01 attr ${desc.attrInt}"
            }
            break
        default:
            if (logEnable) log.debug "Ignored cluster ${desc.clusterInt}"
    }
}

private void dispatchTap(int code) {
    Integer btn; String evt
    switch (code) {
        case 2:  btn=1; evt='pushed';       break
        case 4:  btn=1; evt='doubleTapped'; break
        case 3:  btn=1; evt='held';         break
        case 12: btn=2; evt='pushed';       break
        case 14: btn=2; evt='doubleTapped'; break
        case 13: btn=2; evt='held';         break
    }
    if (btn) {
        if (logEnable) log.info "Button ${btn == 1 ? '1 (ON)' : '2 (OFF)'} - ${evt}"
        sendEvent(name: evt, value: btn, isStateChange: true)
    }
}

def lockKeypad()      { setKeypadLock('Locked') }
def unlockKeypad()    { setKeypadLock('Unlocked') }
def setKeypadLock(state) {
    def v = state.toLowerCase()=='locked'?1:0
    writeFF01(0x0002, DataType.ENUM8, v)
}

def setOnLedColor(hexStr)      { writeFF01(0x0050, DataType.UINT24, Integer.parseInt(hexStr,16)) }
def setOffLedColor(hexStr)     { writeFF01(0x0051, DataType.UINT24, Integer.parseInt(hexStr,16)) }
def setOnLedIntensity(pct)     { writeFF01(0x0052, DataType.UINT8, pct as Integer) }
def setOffLedIntensity(pct)    { writeFF01(0x0053, DataType.UINT8, pct as Integer) }
def setTimer(sec)              { writeFF01(0x00A0, DataType.UINT32, sec as Integer) }

def refreshTimer() {
    def cmds = []
    cmds += zigbee.readAttribute(0xFF01, 0x00A1) //, [mfgCode: 0x119C])
    sendZigbeeCommands(cmds)
}

def setConnectedLoad(watts)    { writeFF01(0x0119, DataType.UINT16, watts as Integer) }

private void writeFF01(attr, type, value) {
    def cmds = []
    cmds += zigbee.writeAttribute(0xFF01, attr, type, value, [mfgCode: 0x119C])
    sendZigbeeCommands(cmds)
}

private void sendZigbeeCommands(List cmds) {
    if (!cmds) return
    def action = new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.ZIGBEE)
    sendHubCommand(action)
}
