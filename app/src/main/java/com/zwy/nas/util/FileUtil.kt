package com.zwy.nas.util

import kotlin.math.log10
import kotlin.math.pow

class FileUtil {
    companion object {
        fun formatFileSize(size: Long): String {
            if (size <= 0) {
                return "0 B"
            }

            val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB")
            val digitGroups = (log10(size.toDouble()) / log10(1024.0)).toInt()

            return String.format(
                "%.2f %s",
                size / 1024.0.pow(digitGroups.toDouble()),
                units[digitGroups]
            )
        }

        fun findFileType(type: Int): String {
            return when (type) {
                0 -> "文件夹"
                1 -> "图片"
                2 -> "音频"
                3 -> "视频"
                else -> {
                    "其他"
                }
            }
        }

        fun findFileType(suffix: String): Int {
            val image = arrayOf(".jpg", ".jpeg", ".png", ".gif", ".svg")
            val radio = arrayOf(".mp3", ".wav", ".aac", ".flac", ".m4a")
            val video = arrayOf(".mp4", ".avi", ".mkv", ".mov", ".wmv")
            return when (suffix) {
                in image -> {
                    1
                }
                in radio -> {
                    2
                }
                in video -> {
                    3
                }
                else -> {
                    4
                }
            }
        }
    }
}