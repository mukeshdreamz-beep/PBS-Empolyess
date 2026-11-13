PBS Employee Attendance Android App

Included:
- Add employees and select an employee for a daily attendance entry.
- Enter date, time-in and time-out using Android time pickers.
- Saves records locally on the phone (no internet or account required).
- Full day is calculated as 8 hours 45 minutes or more, matching the standard 9:30 AM to 6:15 PM shift.
- Overtime is counted from 7:00 PM onward. The 6:15 PM to 7:00 PM gap is not counted as OT.
- Saving a second entry for the same employee/date replaces the previous entry.
- Attendance records can be deleted.

Build an APK:
1. Install Android Studio (latest stable) on a computer.
2. Open this folder as an existing project.
3. Let Gradle sync and install any requested Android SDK (compile SDK 35).
4. Select Build > Build Bundle(s) / APK(s) > Build APK(s).
5. The APK will be in app/build/outputs/apk/debug/.

Important:
This is a starter app project/source code, not a precompiled APK. Data is stored locally on the device.
The full-day rule uses total elapsed time and does not subtract breaks. You can adjust this rule in MainActivity.kt if your company uses a different policy.
