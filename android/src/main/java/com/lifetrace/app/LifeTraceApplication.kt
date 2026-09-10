package com.lifetrace.app

import android.app.Application
import com.lifetrace.app.data.DiaryRepository
import com.lifetrace.app.data.DurableBackupStore
import com.lifetrace.app.data.LifeTraceDatabase
import com.lifetrace.app.data.MediaStorage

class LifeTraceApplication : Application() {
    val repository: DiaryRepository by lazy {
        DiaryRepository(
            dao = LifeTraceDatabase.getInstance(this).diaryDao(),
            mediaStorage = MediaStorage(this),
            durableBackup = DurableBackupStore(this),
        )
    }
}
