package edu.uw.bonngyn.donut

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log

class ShakeListener : SensorEventListener {

    private var lastAccel: Long = 0;
    private var lastTime: Long = 0
    private var lastShake: Long = 0
    private var shakeCount: Int = 0;

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        // ignore
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val currTime = System.currentTimeMillis()

            // reset shake count if time between current and last acceleration is greater than timeout
            if (currTime - lastAccel > SHAKE_TIMEOUT) {
                shakeCount = 0
            }

            // start calculating if time between current and last shake is more than threshold
            if (currTime - lastTime > TIME_THRESHOLD) {
                val x = event.values[0].toDouble()
                val y = event.values[1].toDouble()
                val z = event.values[2].toDouble()

                // calculate acceleration
                val acceleration = Math.sqrt(Math.pow(x, 2.0)
                        + Math.pow(y, 2.0)
                        + Math.pow(z, 2.0)) - SensorManager.GRAVITY_EARTH

                if (acceleration > SHAKE_THRESHOLD) {
                    // perform action if shake occurs more than three times and is longer than duration
                    if (++shakeCount >= SHAKE_COUNT && currTime - lastShake > SHAKE_DURATION) {
                        lastShake = currTime
                        shakeCount = 0;
                        Log.d("donutshake", "yolo")

                        // TODO calculate shortest distance
                    }
                    lastAccel = currTime
                }
                lastTime = currTime
            }
        }
    }

    companion object {
        private const val SHAKE_THRESHOLD = 3.25f
        private const val TIME_THRESHOLD = 100
        private const val SHAKE_TIMEOUT = 500
        private const val SHAKE_DURATION = 1000
        private const val SHAKE_COUNT = 3
    }
}
