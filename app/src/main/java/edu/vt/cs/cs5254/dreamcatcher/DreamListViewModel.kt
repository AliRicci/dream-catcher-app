package edu.vt.cs.cs5254.dreamcatcher

import androidx.lifecycle.ViewModel

class DreamListViewModel : ViewModel() {

    private val dreamRepository = DreamRepository.get()
    val dreamListLiveData = dreamRepository.getDreams()

    fun addDreamWithEntries(dreamWithEntries: DreamWithEntries) {
        dreamRepository.addDreamWithEntries(dreamWithEntries)
    }

    fun deleteAllDreams() {
        dreamRepository.deleteAllDreamEntriesInDatabase()
        dreamRepository.deleteAllDreamsInDatabase()
    }

}