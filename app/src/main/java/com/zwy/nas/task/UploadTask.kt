package com.zwy.nas.task

import java.util.Timer
import java.util.TimerTask

object UploadTask {
    private var timer: Timer? = null
    private var runStart = false
    private var runEnd = false
    fun startTask(delay: Long, period: Long, task: () -> Unit) {
        if (!runStart) {
            runStart = true
            timer = Timer()
            val timerTask = object : TimerTask() {
                override fun run() {
                    task()
                }
            }
            timer?.schedule(timerTask, delay, period)
            runEnd = true
        }

    }

    fun cancelTask() {
        if (runEnd) {
            timer?.cancel()
            runStart = false
            runEnd = false
        }
    }
}