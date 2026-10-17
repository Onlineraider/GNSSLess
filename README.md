# Inertial Path Tracker (Android)

High-precision, offline dead-reckoning route recorder and Android mock location provider using purely on-device motion and environmental MEMS sensors. 

---

## 1. Decisions Taken on the User's Behalf

1. **Mock Location Publish Rate (P41):** 5 Hz in Maximum Accuracy profile (responsive navigation), 1 Hz in Power Saving profile (battery longevity).
2. **"Good Quality" Start GPS Fix (P30):** Threshold set to $\le 15.0\,\text{m}$ horizontal accuracy before automatically locking and turning off GPS; users can override and accept any current fix immediately via "Accept Current Fix Now".
3. **P43 Mock Feedback Conflict Resolution:** The mock test provider is explicitly stopped and removed before acquiring any initial fix or manual re-anchor; all incoming GPS readings are checked for `location.isFromMockProvider` / `isMock` and discarded if spoofed.
4. **Pause Behavior (P44):** During pause, the mock provider injects a stationary fix with speed $0\,\text{m/s}$ and frozen bearing, clearly signaling to other navigation apps that the device is at rest.
5. **Offline Map Tile Format (P54):** Standard OpenStreetMap XYZ Slippy Map tile archive (`{z}/{x}/{y}.png`) packaged as a standard `.zip` file, imported directly into private storage without network access.
6. **Raw Sensor Logging Format (P52):** JSON-Lines (`.jsonl`) with ISO timestamps, sensor types (`LIN_ACC`, `GYRO`, `ROT_VEC`, `BARO`, `STEP`), attitude quaternions, and ground truth references for 100% offline path recomputation.
7. **Zero-Velocity Update (ZUPT) Thresholds (P10–P14):** Variance threshold of $0.08\,\text{m/s}^2$ for car/transit and $0.18\,\text{m/s}^2$ for pedestrian stance phases, eliminating quadratic velocity runaway during stops.
8. **Altitude Estimation (P04):** Hypsometric pressure formula with sea-level baseline when barometer is present ($\pm 2.5\,\text{m}$ uncertainty); vertical acceleration leaky integration when barometer is absent ($\pm 15\,\text{m}$ uncertainty).
9. **Interface & Theming (U01, U02, A1):** Bilingual interface (English and German) following system language, with an aerospace-grade Material 3 slate/cyan theme adhering to system light/dark settings.
10. **Build & Signing (A4):** Standalone installable release/debug APKs signed with standard development keystore; no Play Store or proprietary services required.

---

## 2. Technical Note: How the Estimation Works

### 2.1 Sensor Input & Strapdown Inertial Navigation
The tracker implements strapdown inertial navigation. The device coordinate frame $(x, y, z)$ is converted to the Earth-referenced East-North-Up (ENU) frame via the rotation matrix $R$ derived continuously from `Sensor.TYPE_ROTATION_VECTOR` (or `GAME_ROTATION_VECTOR`):
$$\begin{bmatrix} a_E \\ a_N \\ a_U \end{bmatrix} = R \begin{bmatrix} a_x - b_{a,x} \\ a_y - b_{a,y} \\ a_z - b_{a,z} \end{bmatrix}$$
where $b_a$ is the calibrated accelerometer null bias.

### 2.2 Mode Kinematics & Drift Handling
Double integration of noisy MEMS acceleration without external aiding results in quadratic velocity error and cubic position error ($e(t) \propto \frac{1}{6} b_a t^3$). To achieve practical accuracy across hours of tracking, the engine applies:
- **Zero-Velocity Updates (ZUPT):** In walking mode, stance phases between steps have near-zero acceleration variance; the velocity is clamped. In vehicle/transit modes, when the vehicle stops at traffic lights or stations ($\text{variance} < 0.08\,\text{m/s}^2$ and angular rate $< 0.02\,\text{rad/s}$ for $>0.2\,\text{s}$), velocity is reset to zero.
- **Non-Holonomic Constraints:** In vehicle and bicycle mounts, lateral slip velocity is constrained to zero ($v_{\text{lateral}} \approx 0$). Forward acceleration alone propagates along the vehicle heading.
- **Arbitrary Phone Orientation in Walking Mode:** For pedestrian mode, heading is continuously updated using the principal axis of horizontal acceleration variance over step cycles, independent of how the phone is turned in a hand or pocket.
- **Leaky Velocity Damping:** Mode-specific velocity damping factors ($\lambda \in [0.92, 0.995]$) prevent integration runaway during sustained constant-velocity phases.

### 2.3 Altitude Estimation
When a barometric pressure sensor is present, altitude is computed using the hypsometric formula calibrated against the starting GPS anchor:
$$h = 44330 \cdot \left(1 - \left(\frac{p}{p_0}\right)^{\frac{1}{5.255}}\right) - h_{\text{offset}}$$
When no barometer is present, vertical acceleration is integrated with higher damping, and the reported vertical uncertainty is scaled from $\pm 2.5\,\text{m}$ to $\pm 15\,\text{m}$.

### 2.4 Honest Error Radius Model (P05, A2)
The reported error radius $\sigma(t)$ reflects accumulated physical uncertainty and is never hidden:
$$\sigma(t) = \sigma_{\text{GPS\_anchor}} + c_{\text{time}} \cdot \sqrt{t} + c_{\text{dist}} \cdot s(t) + \sigma_{\text{gyro}} \cdot t^{1.2}$$
- **Time Component ($c_{\text{time}} \sqrt{t}$):** Accounts for random walk integration drift ($0.015\,\text{m/s}^{1/2}$ when stationary with ZUPT, $0.04 - 0.12\,\text{m/s}^{1/2}$ in motion).
- **Distance Component ($c_{\text{dist}} \cdot s$):** Accounts for scale-factor and stride/odometry deviation ($3.5\% - 7\%$ of distance traveled).
- **Gyro Heading Drift ($\sigma_{\text{gyro}} t^{1.2}$):** Models heading divergence over time.
- **Manual Re-anchor (P31):** Resetting with a GPS fix snaps $\sigma(t)$ back to the new fix accuracy.

---

## 3. Mock Location Setup (One-Time Setup)

The app broadcasts its sensor-derived position system-wide via Android's Mock Location framework.

### Setup Instructions
1. Open **Settings** on your phone.
2. Go to **About phone** and tap **Build number** 7 times to enable Developer Options.
3. Open **System > Developer options** (or use the shortcut in the app's Settings tab).
4. Scroll to the **Debugging** section and tap **Select mock location app**.
5. Select **Inertial Tracker**.
6. Return to Inertial Tracker. The warning banner will disappear, and "Mock Location: Active" will display once tracking begins.

### How Apps Receive the Position
Any installed app (Google Maps, OsmAnd, Komoot, Strava, browsers) requesting location will receive the dead-reckoning position, speed, altitude, bearing, and honest error radius directly from Inertial Tracker as if it were a hardware GNSS fix.

---

## 4. How to Import Offline Map Tiles (P54)

The app is completely offline (0 internet permissions). To view your track over map tiles:

1. Obtain standard Slippy Map XYZ tiles (e.g., from OpenStreetMap via tools like MOBAC, QGIS, or tile downloaders).
2. The folder structure inside the zip must follow:
   ```
   {z}/{x}/{y}.png
   ```
   (e.g., `14/8520/5621.png`). Zoom levels 12 through 17 are recommended.
3. Zip the tiles directory into a single `.zip` file and transfer it to your phone via USB or local file manager.
4. In Inertial Tracker, open the **Settings** tab.
5. Under **Offline Map Tiles**, tap **Import ZIP** and select your `.zip` file.
6. On the Tracker screen, tap the Map icon overlay to switch between vector coordinate grid and the imported offline tile map.

---

## 5. How to Run Calibration for Each Mode (P20)

Calibration measures sensor zero-rate bias and gravity offset to prevent dead-reckoning runaway.

1. Navigate to the **Calibrate** tab.
2. Select your target mode:
   - **Walking:** Place the phone flat on a table.
   - **Car:** Place the phone in your vehicle dashboard/windshield mount while parked.
   - **Bicycle (Fixed):** Secure the phone in your handlebar mount with the bike stationary.
   - **Transit (Fixed):** Rest the phone flat on a table or window ledge.
3. Tap **Start Calibration**.
4. Keep the device completely still for 10 seconds. The on-screen stability indicator will verify that no motion is present.
5. Once complete, review the calculated Gyro Bias and Accel Bias and tap **Save Profile**.
6. The profile is permanently saved in the local database and applied automatically whenever that mode is active.

---

## 6. How to Use the Test Mode (P60, P61)

Test Mode enables parallel ground-truth GNSS logging to evaluate sensor drift without feeding the estimate.

1. In the **Settings** tab, toggle **Test Mode (GNSS Comparison)** ON.
2. Return to the **Tracker** tab and start a session.
3. The app will acquire the start fix, then continue logging true GNSS fixes in the background *strictly* for error comparison. The tracking engine itself runs 100% on motion sensors.
4. Stop the recording when done.
5. Open the **Routes** tab and tap on your recording (marked with a `TEST MODE` badge).
6. Tap **View Drift & Error Evaluation**:
   - **Mean & Max Drift:** Average and peak distance error between sensor estimate and GPS.
   - **Drift / Distance Ratio:** Exact percentage drift per 100m or 1km.
   - **Error Radius Coverage:** Percentage of ground-truth GPS points that fell within the app's predicted error radius bubble (verifies honest uncertainty).
   - **Drift over Time Chart:** Graph showing error accumulation over seconds.
   - **Map Overlay:** Visual overlay of the cyan inertial path versus the amber GPS ground-truth track.

---

## 7. Raw Sensor Data Format (P52)

Every recording session creates an accompanying `.jsonl` (JSON-Lines) file in internal storage (`/data/user/0/com.aistudio.inertialtracker.pxkydq/files/raw_recordings/route_<id>_raw.jsonl`).

### Schema Definitions
- **Header:**
  ```json
  {"version":1,"format":"inertial_raw_jsonl","routeId":1,"created":1729170000000}
  ```
- **GPS Start Anchor & Re-anchors:**
  ```json
  {"t":1729170000000,"type":"GPS_ANCHOR","lat":52.5200,"lon":13.4050,"alt":45.2,"acc":8.5}
  ```
- **Linear Acceleration (m/s² without gravity):**
  ```json
  {"t":123456789000,"type":"LIN_ACC","x":0.021,"y":-0.154,"z":0.012}
  ```
- **Gyroscope (rad/s):**
  ```json
  {"t":123456789000,"type":"GYRO","x":0.0012,"y":-0.0008,"z":0.0154}
  ```
- **Orientation Rotation Vector (Unit Quaternion):**
  ```json
  {"t":123456789000,"type":"ROT_VEC","x":0.012,"y":0.034,"z":0.056,"w":0.998}
  ```
- **Barometer Pressure (hPa):**
  ```json
  {"t":123456789000,"type":"BARO","p":1013.25}
  ```
- **Step Detector:**
  ```json
  {"t":123456789000,"type":"STEP"}
  ```
- **Test Mode Ground Truth:**
  ```json
  {"t":1729170010000,"type":"GPS_TEST_GROUND_TRUTH","lat":52.5201,"lon":13.4053,"alt":45.5,"acc":5.0}
  ```

---

## 8. Known Limitations & Physical Reality (Section 2 & 8)

1. **MEMS Drift Growth:** Without external reference, consumer phone IMUs naturally accumulate heading drift ($0.5^\circ - 3^\circ/\text{min}$) and acceleration noise. Expected unassisted error growth:
   - *Walking (with ZUPT):* $20 - 50\,\text{m}$ per kilometer.
   - *Car (fixed mount with highway stops):* $30 - 80\,\text{m}$ over 5 minutes.
   - *Train/Transit (smooth track, few stops):* Centrifugal estimation helps on curves, but long straight runs drift linearly with accelerometer bias.
2. **Magnetic Disturbances:** Inside steel vehicles (trains, buses, electric cars), ambient magnetic fields warp magnetometer readings. The app prioritizes gyro rate integration and Game Rotation Vector to minimize magnetic interference.
3. **Android 17 / 14+ Power Management:** Background sensor rates in deep sleep are maintained via the foreground service with `FOREGROUND_SERVICE_TYPE_LOCATION` and a partial CPU `WakeLock`.
4. **Mock Provider Compatibility:** Certain banking and high-security enterprise apps may detect and reject mock locations. Standard navigation, fitness, mapping, and browser apps function seamlessly.
