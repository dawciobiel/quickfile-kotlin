package io.github.dawciobiel.quickfile.app.component

import com.arkivanov.decompose.ComponentContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class JobsState(
    val summary: String,
)

interface JobsComponent {
    val state: StateFlow<JobsState>
}

class DefaultJobsComponent(
    componentContext: ComponentContext,
) : JobsComponent, ComponentContext by componentContext {
    private val mutableState =
        MutableStateFlow(
            JobsState(
                summary = "No jobs yet. This area will be backed by JobRegistry in Milestone 4.",
            ),
        )

    override val state: StateFlow<JobsState> = mutableState.asStateFlow()
}
