package edu.vt.cs.cs5254.dreamcatcher.database

import androidx.lifecycle.LiveData
import androidx.room.*
import edu.vt.cs.cs5254.dreamcatcher.Dream
import edu.vt.cs.cs5254.dreamcatcher.DreamEntry
import edu.vt.cs.cs5254.dreamcatcher.DreamWithEntries
import java.util.UUID

@Dao
interface DreamDao {

    // ----------------------------------------------------------
    // Dream functions
    // ----------------------------------------------------------

    @Query("SELECT * FROM dream")
    fun getDreams(): LiveData<List<Dream>>

    @Query("SELECT * FROM dream WHERE id=(:dreamId)")
    fun getDream(dreamId: UUID): LiveData<Dream?>

    @Insert
    fun addDream(dream: Dream)

    @Update
    fun updateDream(dream: Dream)

    @Query("DELETE FROM dream WHERE id=(:dreamId)")
    fun deleteDream(dreamId: UUID)

    // ----------------------------------------------------------
    // DreamEntry functions
    // ----------------------------------------------------------

    @Query("SELECT * FROM dream_entry WHERE dreamId=(:dreamId)")
    fun getDreamEntries(dreamId: UUID): LiveData<List<DreamEntry>>

    @Insert
    fun addDreamEntry(dreamEntry: DreamEntry)

    @Update
    fun updateDreamEntry(dreamEntry: DreamEntry)

    @Query("DELETE FROM dream_entry WHERE dreamId=(:dreamId)")
    fun deleteDreamEntries(dreamId: UUID)

    // ----------------------------------------------------------
    // DreamWithEntries functions
    // ----------------------------------------------------------

    @Query("SELECT * FROM dream WHERE id=(:dreamId)")
    fun getDreamWithEntries(dreamId: UUID): LiveData<DreamWithEntries>

    @Transaction
    fun updateDreamWithEntries(dreamWithEntries: DreamWithEntries) {
        val theDream = dreamWithEntries.dream
        val theEntries = dreamWithEntries.dreamEntries
        updateDream(dreamWithEntries.dream)
        deleteDreamEntries(theDream.id)
        theEntries.forEach { e -> addDreamEntry(e) }
    }

    @Transaction
    fun addDreamWithEntries(dreamWithEntries: DreamWithEntries) {
        addDream(dreamWithEntries.dream)
        dreamWithEntries.dreamEntries.forEach { e -> addDreamEntry(e) }
    }

    // ----------------------------------------------------------
    // Clear Dream and Entries (used for testing purposes)
    // ----------------------------------------------------------

    @Query("DELETE FROM dream")
    fun deleteAllDreamsInDatabase()

    @Query("DELETE FROM dream_entry")
    fun deleteAllDreamEntriesInDatabase()

}