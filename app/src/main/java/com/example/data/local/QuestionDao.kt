package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface QuestionDao {
    @Query("SELECT * FROM saved_questions ORDER BY timestamp DESC")
    fun getAllQuestions(): Flow<List<QuestionEntity>>

    @Query("SELECT * FROM saved_questions WHERE isBookmarked = 1 ORDER BY timestamp DESC")
    fun getBookmarkedQuestions(): Flow<List<QuestionEntity>>

    @Query("SELECT * FROM saved_questions WHERE id = :id LIMIT 1")
    fun getQuestionById(id: Long): Flow<QuestionEntity?>

    @Query("SELECT * FROM saved_questions WHERE questionText LIKE '%' || :query || '%' OR subject LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun searchQuestions(query: String): Flow<List<QuestionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestion(question: QuestionEntity): Long

    @Update
    suspend fun updateQuestion(question: QuestionEntity)

    @Delete
    suspend fun deleteQuestion(question: QuestionEntity)

    @Query("UPDATE saved_questions SET isBookmarked = :isBookmarked WHERE id = :id")
    suspend fun updateBookmarkStatus(id: Long, isBookmarked: Boolean)

    @Query("DELETE FROM saved_questions")
    suspend fun clearAll()
}
