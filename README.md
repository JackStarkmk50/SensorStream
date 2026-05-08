# SensorStream IMU+GPS (Modernized)

SensorStream is a high-performance Android application designed to stream real-time telemetry data from a mobile device's hardware sensors (IMU) and GPS directly to a computer, server, or robotics middleware (like ROS2) over a local network via UDP.

This modernized version uses a clean Kotlin architecture with Coroutines and Jetpack Compose, completely eliminating the battery drain and UI blocking issues present in the legacy version, while maintaining **100% backward compatibility** with the original UDP packet structure.

---

## How It Works

The app continuously reads data from the phone's hardware chips (accelerometer, gyroscope, GPS, etc.) at high frequencies. It aggregates all these readings into a single "snapshot" at a given microsecond, formats them into a comma-separated string (CSV), and broadcasts them over the WiFi network to a target IP address using the UDP protocol.

### Setting Up the Stream

1. **Network**: Ensure your computer and your Android phone are on the exact same network. (e.g., connected to the same WiFi router, or your computer is connected to the phone's mobile hotspot).
2. **Start the Receiver**: Run the provided Python script on your computer:
   ```bash
   python udp_receiver.py
   ```
3. **Configure the App**:
   - Find your computer's local IPv4 address (e.g., `192.168.0.102`).
   - Open the SensorStream App on your phone and go to the **Settings** tab.
   - Enter your computer's IP address into the **Target IP Address** field.
   - Set the **Target Port** to `5555`.
   - Enable whichever sensors you need using the toggle switches.
4. **Stream**: Go back to the **Dashboard** tab and press the floating "Play" button. You will immediately see data printing in your computer's terminal.

---

## Sensor ID Reference Table

When the app streams data, it identifies each sensor using a unique ID. Here is the reference table of all supported sensors and their respective data types:

| Sensor ID | Sensor Name | Value 1 (X) | Value 2 (Y) | Value 3 (Z) | Units |
| :---: | :--- | :--- | :--- | :--- | :--- |
| **3** | Accelerometer | Accel X | Accel Y | Accel Z | m/s² |
| **4** | Gyroscope | Rot Speed X | Rot Speed Y | Rot Speed Z | rad/s |
| **5** | Magnetometer | Mag Field X | Mag Field Y | Mag Field Z | μT |
| **81** | Orientation | Azimuth | Pitch | Roll | Degrees (°) |
| **82** | Linear Acceleration | Accel X | Accel Y | Accel Z | m/s² |
| **83** | Gravity | Gravity X | Gravity Y | Gravity Z | m/s² |
| **84** | Rotation Vector | Vector X | Vector Y | Vector Z | Unitless |
| **85** | Pressure | Pressure Value | *N/A* | *N/A* | hPa |
| **1** | GPS BLH | Latitude | Longitude | Altitude | Deg, Deg, m |
| **6** | GPS XYZ | ECEF X | ECEF Y | ECEF Z | Meters (m) |
| **7** | GPS Velocity | ECEF Vx | ECEF Vy | ECEF Vz | m/s |

*(Note: Not all Android phones have hardware support for every sensor. For example, many modern phones do not include a Barometer chip, so ID 85 (Pressure) will not be transmitted).*

---

## UDP Packet Data Format Explained

The data received by the `udp_receiver.py` script is a single line of text encoded as UTF-8. It is formatted as a continuous comma-separated value (CSV) string. 

A single packet will look something like this:
`16836472.12345, 1, 37.7749,-122.4194,15.2, 3, 0.123,9.810,-0.054, 4, 0.001,0.002,-0.001`

### Breaking down the packet:
Every packet strictly follows this sequence: `[System Timestamp], [Sensor 1 ID], [Sensor 1 Values...], [Sensor 2 ID], [Sensor 2 Values...], etc.`

Using the example string above:
1. **`16836472.12345`** → The very first value is always the **System Timestamp** in seconds.
2. **`1`** → The ID for GPS BLH (Latitude, Longitude, Altitude).
3. **`37.7749, -122.4194, 15.2`** → The Latitude, Longitude, and Altitude values belonging to ID `1`.
4. **`3`** → The ID for the Accelerometer.
5. **`0.123, 9.810, -0.054`** → The X, Y, and Z acceleration values belonging to ID `3`.
6. **`4`** → The ID for the Gyroscope.
7. **`0.001, 0.002, -0.001`** → The X, Y, and Z rotation speed values belonging to ID `4`.

### Parsing the Data in Python
If you want to parse this data in Python to actually use the numbers (instead of just printing them), you can simply split the string by commas in your `udp_receiver.py` script:

```python
text = data.decode('utf-8').strip()
parts = text.split(',')

timestamp = float(parts[0])

# Example: Loop through the remaining parts to extract sensor chunks
index = 1
while index < len(parts):
    sensor_id = int(parts[index].strip())
    
    if sensor_id == 3: # Accelerometer (has 3 values)
        accel_x = float(parts[index+1])
        accel_y = float(parts[index+2])
        accel_z = float(parts[index+3])
        print(f"Accel: {accel_x}, {accel_y}, {accel_z}")
        index += 4
        
    elif sensor_id == 85: # Pressure (has only 1 value)
        pressure = float(parts[index+1])
        print(f"Pressure: {pressure}")
        index += 2
        
    # ... handle other IDs based on the table above ...
    else:
        # Fallback for unknown or 3-value sensors
        index += 4
```
