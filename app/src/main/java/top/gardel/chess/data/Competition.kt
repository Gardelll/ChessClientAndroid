package top.gardel.chess.data

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel

class Competition(private val state: SavedStateHandle) : ViewModel() {
    val id: MutableLiveData<Int> by lazy {
        MutableLiveData<Int>(0)
    }
    val playerA: MutableLiveData<Player> by lazy {
        MutableLiveData<Player>()
    }
    val playerB: MutableLiveData<Player> by lazy {
        MutableLiveData<Player>()
    }

    val lastPut: MutableLiveData<Player> by lazy {
        MutableLiveData<Player>()
    }
    val playerBLose: MutableLiveData<Int> by lazy {
        MutableLiveData<Int>(0)
    }
    val playerAWin: MutableLiveData<Int> by lazy {
        MutableLiveData<Int>(0)
    }
    val playerBWin: MutableLiveData<Int> by lazy {
        MutableLiveData<Int>(0)
    }
    val playerALose: MutableLiveData<Int> by lazy {
        MutableLiveData<Int>(0)
    }
}