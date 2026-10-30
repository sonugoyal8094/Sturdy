package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_questions")
data class QuestionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val questionText: String,
    val subject: String,
    val difficulty: String,
    val conceptSummary: String,
    val stepsJson: String,
    val finalAnswer: String,
    val simpleLearningExplanation: String,
    val practiceQuestionsJson: String,
    val didYouKnowFact: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isBookmarked: Boolean = false,
    val hasImage: Boolean = false
)
