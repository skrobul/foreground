package me.bgregos.foreground.network

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.bgregos.foreground.di.CustomWorkerFactory
import me.bgregos.foreground.util.NotificationRepository
import me.bgregos.foreground.tasklist.TaskViewModel
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Worker for handling automatic TaskChampion sync operations
 */
class TaskChampionSyncWorker(
    context: Context,
    workerParams: WorkerParameters,
    private val notificationRepository: NotificationRepository,
    private val taskViewModel: TaskViewModel
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = coroutineScope {
        try {
            // Load tasks before sync
            taskViewModel.load()

            // Perform sync with TaskChampion
            val result = taskViewModel.sync()

            // Schedule notifications for tasks
            notificationRepository.scheduleNotificationForTasks(taskViewModel.tasks.value)

            // Update sync status in UI or perform other operations after sync completes successfully
            Result.success()
        } catch (e: Exception) {
            // Log the error and return failure
            android.util.Log.e("TaskChampionSyncWorker", "Sync failed", e)
            Result.failure()
        }
    }

    class Factory(
        private val notificationRepository: () -> NotificationRepository,
        private val taskViewModel: () -> TaskViewModel,
    ) : CustomWorkerFactory {
        override fun create(appContext: Context, params: WorkerParameters): ListenableWorker {
            return TaskChampionSyncWorker(
                appContext,
                params,
                notificationRepository(),
                taskViewModel()
            )
        }
    }
