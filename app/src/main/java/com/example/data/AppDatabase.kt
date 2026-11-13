package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

import com.example.domain.AuthUtils

@Database(
    entities = [
        EmployeeEntity::class,
        AttendanceEntryEntity::class,
        ExpenseEntity::class,
        ApprovalRequestEntity::class,
        AdminAccountEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun attendanceDao(): AttendanceDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE attendance_entries ADD COLUMN checkInTimestamp INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE attendance_entries ADD COLUMN checkOutTimestamp INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_attendance_entries_checkInTimestamp ON attendance_entries(checkInTimestamp)"
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE employees ADD COLUMN employeeCode TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE employees ADD COLUMN phone TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    "ALTER TABLE attendance_entries ADD COLUMN status TEXT NOT NULL DEFAULT 'PRESENT'"
                )
                db.execSQL("ALTER TABLE attendance_entries ADD COLUMN note TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE employees ADD COLUMN site TEXT NOT NULL DEFAULT 'Sai Baba Nagar'"
                )
                db.execSQL(
                    "ALTER TABLE employees ADD COLUMN dailyWage REAL NOT NULL DEFAULT 0.0"
                )
                db.execSQL(
                    "ALTER TABLE employees ADD COLUMN otHourlyRate REAL NOT NULL DEFAULT 0.0"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS expenses (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        employeeId INTEGER,
                        employeeName TEXT NOT NULL,
                        site TEXT NOT NULL,
                        title TEXT NOT NULL,
                        category TEXT NOT NULL,
                        amount REAL NOT NULL,
                        date TEXT NOT NULL,
                        status TEXT NOT NULL,
                        note TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS approval_requests (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        employeeId INTEGER NOT NULL,
                        employeeName TEXT NOT NULL,
                        type TEXT NOT NULL,
                        date TEXT NOT NULL,
                        inMinutes INTEGER NOT NULL,
                        outMinutes INTEGER NOT NULL,
                        reason TEXT NOT NULL,
                        status TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY(employeeId) REFERENCES employees(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_approval_requests_employeeId ON approval_requests(employeeId)"
                )
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE expenses ADD COLUMN isClaimed INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE expenses ADD COLUMN approvedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE expenses ADD COLUMN claimedAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE employees ADD COLUMN loginId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE employees ADD COLUMN passwordHash TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE employees ADD COLUMN passwordUpdatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS admin_accounts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        mobile TEXT NOT NULL,
                        passwordHash TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_admin_accounts_mobile ON admin_accounts(mobile)"
                )
                val defaultHash = AuthUtils.hashPassword(AuthUtils.DEFAULT_ADMIN_PASSWORD)
                val now = System.currentTimeMillis()
                db.execSQL(
                    "INSERT OR IGNORE INTO admin_accounts (name, mobile, passwordHash, createdAt) VALUES (?, ?, ?, ?)",
                    arrayOf<Any>(AuthUtils.DEFAULT_ADMIN_NAME, AuthUtils.DEFAULT_ADMIN_MOBILE, defaultHash, now)
                )
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE employees ADD COLUMN remoteId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE employees ADD COLUMN claimedByUid TEXT NOT NULL DEFAULT ''")

                db.execSQL("ALTER TABLE attendance_entries ADD COLUMN remoteId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE attendance_entries ADD COLUMN hasCheckedIn INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE attendance_entries ADD COLUMN hasCheckedOut INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE attendance_entries ADD COLUMN markedByRole TEXT NOT NULL DEFAULT 'ADMIN'")
                db.execSQL("ALTER TABLE attendance_entries ADD COLUMN checkInPhotoBase64 TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE attendance_entries ADD COLUMN checkInLat REAL")
                db.execSQL("ALTER TABLE attendance_entries ADD COLUMN checkInLng REAL")
                db.execSQL("ALTER TABLE attendance_entries ADD COLUMN checkInAddress TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE attendance_entries ADD COLUMN checkOutPhotoBase64 TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE attendance_entries ADD COLUMN checkOutLat REAL")
                db.execSQL("ALTER TABLE attendance_entries ADD COLUMN checkOutLng REAL")
                db.execSQL("ALTER TABLE attendance_entries ADD COLUMN checkOutAddress TEXT NOT NULL DEFAULT ''")

                db.execSQL("ALTER TABLE expenses ADD COLUMN remoteId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE expenses ADD COLUMN employeeUid TEXT NOT NULL DEFAULT ''")

                db.execSQL("ALTER TABLE approval_requests ADD COLUMN remoteId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE approval_requests ADD COLUMN employeeUid TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pbs_attendance.db"
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7
                    )
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
