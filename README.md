Android Things Weather Station sample
=====================================

This sample shows integration of multiple Android Things peripheral to build a connected Weather Station.

Pre-requisites
--------------
- Android Things compatible board
- Android Studio 2.2+
- [Rainbow Hat for Android Things](https://shop.pimoroni.com/products/rainbow-hat-for-android-things) or the following individual components:
    - 1 [bmp280 temperature sensor](https://www.adafruit.com/product/2651)
    - 1 [segment display with I2C backpack](https://www.adafruit.com/product/1270)
    - 1 push button
    - 1 resistor
    - jumper wires
    - 1 breadboard
    - (optional) 1 [APA102 compatible RGB Led strip](https://www.adafruit.com/product/2241)
    - (optional) 1 [Piezo Buzzer](https://www.adafruit.com/products/160)
    - (optional) [Google Cloud Platform](https://cloud.google.com/) project

Schematics
----------

If you have the Raspberry Pi [Rainbow Hat for Android Things](https://shop.pimoroni.com/products/rainbow-hat-for-android-things), just plug it onto your Raspberry Pi 3.

![Schematics for Intel Edison](edison_schematics.png)
![Schematics for Raspberry Pi 3](rpi3_schematics.png)

Build and install
=================
On Android Studio, click on the "Run" button.
If you prefer to run on the command line, type
```bash
./gradlew installDebug
adb shell am start com.example.androidthings.weatherstation/.WeatherStationActivity
```

If you have everything set up correctly:
- The segment display will show the current temperature.
- If the button is pressed, the display will show the current pressure.
- If a Piezo Buzzer is connected, it will plays a funny sound on startup.
- If a APA102 RGB Led strip is connected, it will display a rainbow of 7 pixels indicating the current pressure.
- If a Google Cloud Platform project is configured (see instruction below), it will publish the sensor data to Google Cloug PubSub.
- If you register to IoT-Ignite Devzone and define the configurations it will publish the sensor data to IoT-Ignite Platform. Then you can define CEP rules and see the results.

Google Cloud Platform configuration (optional)
==============================================
0. Go to your project in the [Google Cloud Platform console](https://console.cloud.google.com/)
0. Under *API Manager*, enable the following APIs: Cloud Pub/Sub
0. Under *IAM & Admin*, create a new Service Account, provision a new private key and save the generated json credentials.
0. Under *Pub/Sub*: create a new topic and in the *Permissions* add the service account created in the previous step with the role *Pub/Sub Publisher*.
0. Under *Pub/Sub*: create a new *Pull subscription* on your new topic.
0. Import the project into Android Studio. Add a file named `credentials.json` inside `app/src/main/res/raw/` with the contents of the credentials you downloaded in the previous steps.
0. In `app/build.gradle`, replace the `buildConfigField` values with values from your project setup.

After running the sample, you can check that your data is ingested in Google Cloud Pub/Sub by running the following command:
```
gcloud --project <CLOUD_PROJECT_ID> beta pubsub subscriptions pull <PULL_SUBSCRIBTION_NAME>
```

Note: If there is no `credentials.json` file in `app/src/main/res/raw`, the app will
 run offline and will not send sensor data to the [Google Cloud Pub/Sub](https://cloud.google.com/pubsub/).

Next steps
==========

Now your weather sensor data is continuously being published to [Google Cloud Pub/Sub](https://cloud.google.com/pubsub/):
- process weather data with [Google Cloud Dataflow](https://cloud.google.com/dataflow/) or [Google Cloud Functions](https://cloud.google.com/functions/)
- persist weather data in [Google Cloud Bigtable](https://cloud.google.com/bigtable/) or [BigQuery](https://cloud.google.com/bigquery/)
- create some weather visualization with [Google Cloud Datalab](https://cloud.google.com/datalab/)
- build weather prediction model with [Google Cloud Machine Learning](https://cloud.google.com/ml/)

IoT-Ignite Integration
======================

IoT-Ignite is a Platform as a Service (PaaS) distinctively designed for realization of Internet of Things. It provides a secure, reliable gateway connection between your devices and the web and allows you to manage your devices, data flow, data streaming and rule definitions.

In order to connect your hardware or device to IoT-Ignite platform, IoT-Ignite device SDK is used. This work here demonstrates to create an application development using IoT-Ignite SDK. For this purpose, on android studio "repositories" and "dependencies" part under the build.gradle file are created as below;

```
repositories {
 mavenCentral()
 maven {
     url "https://repo.iot-ignite.com/content/repositories/releases"
 }
}

dependencies {
 compile 'com.ardic.android:IoTIgnite:0.7'
 compile 'com.google.code.gson:gson:2.7'
}
```

In addition, below part should be discarded from AndroidManifest.xml file to use Ignite Agent application which  provides connection with Ignite Cloud;

```
<!-- Launch activity automatically on boot -->
    <intent-filter>
          <action android:name="android.intent.action.MAIN"/>
          <category android:name="android.intent.category.IOT_LAUNCHER"/>
          <category android:name="android.intent.category.DEFAULT"/>
    </intent-filter>
```

Next Steps
======================

Sensor data configuration; Data Reading, sending to cloud, keeping offline data, etc. configurations can be defined as follows.

![Pressure Configuration](sensordata_configuration.png)

CEP Rule; You can define the CEP (Complex Event Processing) rules from IoT-Ignite Devzone  as below. After events are occurred given actions are taken.

The example IoT-Ignite Cloud CEP Flow is defined as follows; When the reading pressure data is between 980hPa and 1000hPa then ring another IoT-Ignite connected gateway and show a given message like "Typical Low Pressure System" on the screen.

![Pressure Configuration](CEP_rule.png)

For more information about IoT-Ignite please visit [https://www.iot-ignite.com](https://www.iot-ignite.com)

IoT-Ignite Devzone
[https://devzone.iot-ignite.com/](https://devzone.iot-ignite.com/)

IoT-Ignite API reference [https://devzone.iot-ignite.com/device-api/iot-ignite/reference/](https://devzone.iot-ignite.com/device-api/iot-ignite/reference/)

License
-------
Copyright 2016 The Android Open Source Project, Inc.
Licensed to the Apache Software Foundation (ASF) under one or more contributor
license agreements.  See the NOTICE file distributed with this work for
additional information regarding copyright ownership.  The ASF licenses this
file to you under the Apache License, Version 2.0 (the "License"); you may not
use this file except in compliance with the License.  You may obtain a copy of
the License at
  http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
License for the specific language governing permissions and limitations under
the License.
