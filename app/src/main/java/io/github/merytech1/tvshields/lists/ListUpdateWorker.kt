package io.github.merytech1.tvshields.lists

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.merytech1.tvshields.TvShieldsApplication
import java.util.concurrent.TimeUnit

/**
 * Updates the filter lists every 24 h when there is a network (architecture §5.4). Unchanged
 * lists cost a 304 each; changed ones rebuild the engine in the background and swap it in.
 * Only the catalog URLs are contacted (rules C6).
 */
class ListUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as TvShieldsApplication).container
        container.engineLoader.refresh()
        // Failed sources keep their copy and are retried on the next run, so the work never fails.
        return Result.success()
    }

    companion object {
        private const val NAME = "list-update"
        private const val INTERVAL_HOURS = 24L

        /** Schedules the update once; later launches keep the existing schedule. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ListUpdateWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                // The first launch has just downloaded the lists.
                .setInitialDelay(INTERVAL_HOURS, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
