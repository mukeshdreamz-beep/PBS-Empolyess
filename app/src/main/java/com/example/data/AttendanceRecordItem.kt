package com.example.data

data class AttendanceRecordItem(
    val id: Long,
    val employeeId: Long,
    val employeeName: String,
    val employeeCode: String,
    val employeeSite: String,
    val employeeRole: String,
    val employeePhone: String,
    val employeeLoginId: String = "",
    val employeeRemoteId: String = "",
    val date: String,
    val inMinutes: Int,
    val outMinutes: Int,
    val status: String,
    val note: String,
    val checkInTimestamp: Long,
    val checkOutTimestamp: Long,
    val remoteId: String = "",
    val hasCheckedIn: Boolean = true,
    val hasCheckedOut: Boolean = true,
    val markedByRole: String = "ADMIN",
    val checkInPhotoBase64: String = "",
    val checkInLat: Double? = null,
    val checkInLng: Double? = null,
    val checkInAddress: String = "",
    val checkOutPhotoBase64: String = "",
    val checkOutLat: Double? = null,
    val checkOutLng: Double? = null,
    val checkOutAddress: String = "",
    val updatedAt: Long
) {
    val hasAnyProof: Boolean
        get() = checkInPhotoBase64.isNotBlank() ||
            checkOutPhotoBase64.isNotBlank() ||
            checkInLat != null ||
            checkOutLat != null ||
            checkInAddress.isNotBlank() ||
            checkOutAddress.isNotBlank()
}
