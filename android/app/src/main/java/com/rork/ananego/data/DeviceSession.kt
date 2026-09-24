package com.rork.ananego.data

import android.content.Context
import android.content.SharedPreferences
import com.rork.ananego.data.model.DriverProfile
import java.util.UUID

/**
 * Persistent identity for this installation plus the document checklist the
 * driver completed locally (photos never leave the device in this version).
 */
class DeviceSession(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("ananego_session", Context.MODE_PRIVATE)

    /** Stable id that makes this phone the same account across restarts. */
    val userId: String
        get() {
            prefs.getString(KEY_USER_ID, null)?.let { return it }
            val fresh = "u_${UUID.randomUUID().toString().replace("-", "").take(20)}"
            prefs.edit().putString(KEY_USER_ID, fresh).apply()
            return fresh
        }

    var lastRole: String
        get() = prefs.getString(KEY_ROLE, "PASSENGER") ?: "PASSENGER"
        set(value) = prefs.edit().putString(KEY_ROLE, value).apply()

    /** Which registration documents the driver already attached on device. */
    fun readDocuments(): DocumentFlags = DocumentFlags(
        hasProfilePhoto = prefs.getBoolean(KEY_DOC_PHOTO, false),
        hasDniFront = prefs.getBoolean(KEY_DOC_DNI_FRONT, false),
        hasDniBack = prefs.getBoolean(KEY_DOC_DNI_BACK, false),
        hasVehicleCard = prefs.getBoolean(KEY_DOC_VEHICLE, false)
    )

    fun writeDocuments(profile: DriverProfile) {
        prefs.edit()
            .putBoolean(KEY_DOC_PHOTO, profile.hasProfilePhoto)
            .putBoolean(KEY_DOC_DNI_FRONT, profile.hasDniFront)
            .putBoolean(KEY_DOC_DNI_BACK, profile.hasDniBack)
            .putBoolean(KEY_DOC_VEHICLE, profile.hasVehicleCard)
            .apply()
    }

    data class DocumentFlags(
        val hasProfilePhoto: Boolean,
        val hasDniFront: Boolean,
        val hasDniBack: Boolean,
        val hasVehicleCard: Boolean
    )

    private companion object {
        const val KEY_USER_ID = "user_id"
        const val KEY_ROLE = "role"
        const val KEY_DOC_PHOTO = "doc_photo"
        const val KEY_DOC_DNI_FRONT = "doc_dni_front"
        const val KEY_DOC_DNI_BACK = "doc_dni_back"
        const val KEY_DOC_VEHICLE = "doc_vehicle"
    }
}
