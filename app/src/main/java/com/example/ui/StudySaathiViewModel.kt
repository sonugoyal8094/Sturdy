package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.QuestionEntity
import com.example.data.model.PracticeQuestion
import com.example.data.model.SolutionResponse
import com.example.data.model.StepExplanation
import com.example.data.repository.StudySaathiRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray

private const val TAG = "StudySaathiVM"

sealed interface SolverUiState {
    data object Idle : SolverUiState
    data class Loading(val progressText: String) : SolverUiState
    data class Success(val solution: SolutionResponse) : SolverUiState
    data class Error(val message: String) : SolverUiState
}

data class SampleQuestion(
    val title: String,
    val subject: String,
    val difficulty: String,
    val questionText: String
)

class StudySaathiViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: StudySaathiRepository by lazy {
        val db = AppDatabase.getInstance(application)
        StudySaathiRepository(db.questionDao())
    }

    private val _uiState = MutableStateFlow<SolverUiState>(SolverUiState.Idle)
    val uiState: StateFlow<SolverUiState> = _uiState.asStateFlow()

    private val _typedQuestion = MutableStateFlow("")
    val typedQuestion: StateFlow<String> = _typedQuestion.asStateFlow()

    private val _selectedBitmap = MutableStateFlow<Bitmap?>(null)
    val selectedBitmap: StateFlow<Bitmap?> = _selectedBitmap.asStateFlow()

    private val _selectedSubject = MutableStateFlow("Maths")
    val selectedSubject: StateFlow<String> = _selectedSubject.asStateFlow()

    private val _selectedDifficulty = MutableStateFlow("Medium")
    val selectedDifficulty: StateFlow<String> = _selectedDifficulty.asStateFlow()

    private val _simpleLearningMode = MutableStateFlow(true)
    val simpleLearningMode: StateFlow<Boolean> = _simpleLearningMode.asStateFlow()

    private val _explanationLanguage = MutableStateFlow("Hinglish & English")
    val explanationLanguage: StateFlow<String> = _explanationLanguage.asStateFlow()

    private val _activeTab = MutableStateFlow(0)
    val activeTab: StateFlow<Int> = _activeTab.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _onlyBookmarked = MutableStateFlow(false)
    val onlyBookmarked: StateFlow<Boolean> = _onlyBookmarked.asStateFlow()

    // Offline / saved questions flow
    val savedQuestions: StateFlow<List<QuestionEntity>> = combine(
        _searchQuery,
        _onlyBookmarked
    ) { query, bookmarked ->
        Pair(query, bookmarked)
    }.flatMapLatest { (query, bookmarked) ->
        if (query.isNotBlank()) {
            repository.searchQuestions(query)
        } else if (bookmarked) {
            repository.getBookmarkedQuestions()
        } else {
            repository.getAllSavedQuestions()
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    fun onQuestionTextChanged(text: String) {
        _typedQuestion.value = text
    }

    fun onBitmapCaptured(bitmap: Bitmap?) {
        _selectedBitmap.value = bitmap
    }

    fun onSubjectSelected(subject: String) {
        _selectedSubject.value = subject
    }

    fun onDifficultySelected(difficulty: String) {
        _selectedDifficulty.value = difficulty
    }

    fun toggleSimpleLearningMode() {
        _simpleLearningMode.value = !_simpleLearningMode.value
    }

    fun setExplanationLanguage(language: String) {
        _explanationLanguage.value = language
    }

    fun setActiveTab(index: Int) {
        _activeTab.value = index
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleOnlyBookmarked() {
        _onlyBookmarked.value = !_onlyBookmarked.value
    }

    fun clearInputs() {
        _typedQuestion.value = ""
        _selectedBitmap.value = null
        _uiState.value = SolverUiState.Idle
    }

    fun loadSample(sample: SampleQuestion) {
        _typedQuestion.value = sample.questionText
        _selectedSubject.value = sample.subject
        _selectedDifficulty.value = sample.difficulty
        _selectedBitmap.value = null
    }

    fun solveQuestion() {
        val text = _typedQuestion.value.trim()
        val bitmap = _selectedBitmap.value

        if (text.isEmpty() && bitmap == null) {
            _uiState.value = SolverUiState.Error("Please type a question or capture/upload an image to solve.")
            return
        }

        viewModelScope.launch {
            _uiState.value = SolverUiState.Loading(
                if (bitmap != null) "Scanning textbook photo with Gemini Vision..." else "Solving step-by-step with StudySaathi AI..."
            )

            try {
                val result = repository.solveQuestion(
                    typedQuestion = text,
                    imageBitmap = bitmap,
                    selectedSubject = _selectedSubject.value,
                    selectedDifficulty = _selectedDifficulty.value,
                    simpleLearningMode = _simpleLearningMode.value,
                    explanationLanguage = _explanationLanguage.value
                )

                result.fold(
                    onSuccess = { solution ->
                        _uiState.value = SolverUiState.Success(solution)
                    },
                    onFailure = { error ->
                        Log.e(TAG, "Solving failed", error)
                        _uiState.value = SolverUiState.Error(
                            error.localizedMessage ?: "Failed to solve question. Please verify connection and try again."
                        )
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Unexpected error", e)
                _uiState.value = SolverUiState.Error(e.localizedMessage ?: "An unexpected error occurred.")
            }
        }
    }

    fun viewSavedQuestion(entity: QuestionEntity) {
        try {
            val stepsList = mutableListOf<StepExplanation>()
            val stepsArray = JSONArray(entity.stepsJson)
            for (i in 0 until stepsArray.length()) {
                val stepObj = stepsArray.getJSONObject(i)
                stepsList.add(
                    StepExplanation(
                        stepNumber = stepObj.optInt("stepNumber", i + 1),
                        title = stepObj.optString("title", "Step ${i + 1}"),
                        explanation = stepObj.optString("explanation", ""),
                        formulaOrTip = stepObj.optString("formulaOrTip").takeIf { it.isNotBlank() }
                    )
                )
            }

            val practiceList = mutableListOf<PracticeQuestion>()
            val practiceArray = JSONArray(entity.practiceQuestionsJson)
            for (i in 0 until practiceArray.length()) {
                val pObj = practiceArray.getJSONObject(i)
                practiceList.add(
                    PracticeQuestion(
                        id = pObj.optInt("id", i + 1),
                        question = pObj.optString("question", ""),
                        hint = pObj.optString("hint", ""),
                        answer = pObj.optString("answer", ""),
                        explanation = pObj.optString("explanation", "")
                    )
                )
            }

            val solution = SolutionResponse(
                recognizedQuestion = entity.questionText,
                subject = entity.subject,
                difficulty = entity.difficulty,
                conceptSummary = entity.conceptSummary,
                steps = stepsList,
                finalAnswer = entity.finalAnswer,
                simpleLearningExplanation = entity.simpleLearningExplanation,
                practiceQuestions = practiceList,
                didYouKnowFact = entity.didYouKnowFact
            )

            _uiState.value = SolverUiState.Success(solution)
            _selectedSubject.value = entity.subject
            _selectedDifficulty.value = entity.difficulty
            _activeTab.value = 0 // Switch to solver view
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load saved question", e)
        }
    }

    fun toggleBookmark(id: Long, currentStatus: Boolean) {
        viewModelScope.launch {
            repository.toggleBookmark(id, !currentStatus)
        }
    }

    fun deleteQuestion(entity: QuestionEntity) {
        viewModelScope.launch {
            repository.deleteQuestion(entity)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }
}
