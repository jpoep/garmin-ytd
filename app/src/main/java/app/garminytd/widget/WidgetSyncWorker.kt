package app.garminytd.widget

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.garminytd.GarminYtdApp
import app.garminytd.auth.GarminAuthException
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Keeps the widget current without opening the app: every few hours, while online, re-download the
 * current year (one or two small requests) and redraw the widget. Runs only while a widget exists.
 */
class WidgetSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val c = (applicationContext as GarminYtdApp).container
        if (!c.auth.isLoggedIn) {
            c.refreshWidgets()
            return Result.success()
        }
        return try {
            c.repository.sync(LocalDate.now(), full = false)
            c.refreshWidgets()
            Result.success()
        } catch (_: GarminAuthException.Blocked) {
            Result.success() // rate-limited: wait for the next period instead of retrying sooner
        } catch (_: GarminAuthException.SessionExpired) {
            c.refreshWidgets() // shows "open the app to connect"
            Result.success()
        } catch (_: IOException) {
            Result.retry()
        }
    }

    companion object {
        private const val NAME = "widget-sync"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WidgetSyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}
