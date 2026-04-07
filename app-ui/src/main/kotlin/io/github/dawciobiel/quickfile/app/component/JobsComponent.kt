package io.github.dawciobiel.quickfile.app.component

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.Lifecycle
import io.github.dawciobiel.quickfile.core.jobs.JobRegistry
import io.github.dawciobiel.quickfile.core.jobs.JobSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class JobsState(
    val summary: String,
    val jobs: List<JobSnapshot> = emptyList(),
)

interface JobsComponent {
    val state: StateFlow<JobsState>
}

class DefaultJobsComponent(
    componentContext: ComponentContext,
    jobRegistry: JobRegistry,
) : JobsComponent, ComponentContext by componentContext {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutableState =
        MutableStateFlow(
            JobsState(
                summary = "No jobs yet.",
            ),
        )

    override val state: StateFlow<JobsState> = mutableState.asStateFlow()

    init {
        lifecycle.subscribe(
            object : Lifecycle.Callbacks {
                override fun onDestroy() {
                    scope.cancel()
                }
            },
        )

        scope.launch {
            jobRegistry.allJobsFlow.collect { jobs ->
                mutableState.value =
                    JobsState(
                        summary =
                            if (jobs.isEmpty()) {
                                "No jobs yet."
                            } else {
                                "${jobs.size} tracked job(s)."
                            },
                        jobs = jobs,
                    )
            }
        }
    }
}
