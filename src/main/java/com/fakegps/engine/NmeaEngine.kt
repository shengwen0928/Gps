package com.fakegps.engine

import java.text.SimpleDateFormat
import java.util.*

/**
 * NMEA 原始語句生成引擎 (虛擬硬體級防偵測)
 * 生成標準的 $GPGGA 與 $GPRMC 語句，用以騙過底層反作弊校驗。
 */
class NmeaEngine {

    private val timeFormat = SimpleDateFormat("HHmmss.SSS", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    private val dateFormat = SimpleDateFormat("ddMMyy", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    /**
     * 產生 $GPGGA 語句 (GPS 定位資訊)
     */
    fun generateGpgga(lat: Double, lng: Double, altitude: Double, satellites: Int, time: Long): String {
        val timeStr = timeFormat.format(Date(time))
        val latStr = formatCoordinate(lat, true)
        val lngStr = formatCoordinate(lng, false)
        val satStr = String.format(Locale.US, "%02d", satellites)
        
        // 格式: $GPGGA,時間,緯度,N/S,經度,E/W,定位品質(1=GPS),衛星數,HDOP,海拔,M,大地水準面高度,M,差分時間,差分站ID*校驗和
        val sentence = "GPGGA,$timeStr,$latStr,$lngStr,1,$satStr,0.8,${String.format(Locale.US, "%.1f", altitude)},M,0.0,M,,"
        return "$$sentence*${calculateChecksum(sentence)}"
    }

    /**
     * 產生 $GPRMC 語句 (推薦最小定位資訊，包含速度與方位)
     */
    fun generateGprmc(lat: Double, lng: Double, speedMs: Float, bearing: Float, time: Long): String {
        val timeStr = timeFormat.format(Date(time))
        val dateStr = dateFormat.format(Date(time))
        val latStr = formatCoordinate(lat, true)
        val lngStr = formatCoordinate(lng, false)
        
        // 將 m/s 轉換為節 (Knots)
        val speedKnots = speedMs * 1.94384f
        
        // 格式: $GPRMC,時間,狀態(A=有效),緯度,N/S,經度,E/W,速度(節),方位角,日期,磁偏角,方向*校驗和
        val sentence = "GPRMC,$timeStr,A,$latStr,$lngStr,${String.format(Locale.US, "%.2f", speedKnots)},${String.format(Locale.US, "%.1f", bearing)},$dateStr,,,A"
        return "$$sentence*${calculateChecksum(sentence)}"
    }

    /**
     * 將十進制座標轉換為 NMEA 的度分格式 (DDMM.MMMMM)
     */
    private fun formatCoordinate(coord: Double, isLatitude: Boolean): String {
        val absCoord = Math.abs(coord)
        val degrees = Math.floor(absCoord).toInt()
        val minutes = (absCoord - degrees) * 60.0
        
        val direction = if (isLatitude) {
            if (coord >= 0) "N" else "S"
        } else {
            if (coord >= 0) "E" else "W"
        }
        
        val degreesStr = if (isLatitude) String.format(Locale.US, "%02d", degrees) else String.format(Locale.US, "%03d", degrees)
        return "$degreesStr${String.format(Locale.US, "%07.4f", minutes)},$direction"
    }

    /**
     * 計算 NMEA 校驗和 (XOR)
     */
    private fun calculateChecksum(sentence: String): String {
        var checksum = 0
        for (char in sentence) {
            checksum = checksum xor char.code
        }
        return String.format(Locale.US, "%02X", checksum)
    }
}
