package top.gardel.chess

import android.content.*
import android.os.Bundle
import android.os.IBinder
import android.text.InputType
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.isDigitsOnly
import androidx.core.widget.addTextChangedListener
import top.gardel.chess.data.Competition
import top.gardel.chess.data.Player
import top.gardel.chess.proto.*
import top.gardel.chess.service.ChessService
import top.gardel.chess.view.ChessBoard
import java.util.*

class MainActivity : AppCompatActivity() {
    private lateinit var chessBoard: ChessBoard
    private lateinit var myUidText: TextView
    private lateinit var myScore: TextView
    private lateinit var otherUidText: TextView
    private lateinit var otherScore: TextView
    private lateinit var competitionNumber: TextView
    private lateinit var leaveButton: Button
    private lateinit var chessService: ChessService
    private lateinit var alertDialog: AlertDialog
    private var mBound = false
    private val competition: Competition by viewModels()
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as ChessService.LocalBinder
            chessService = binder.getService()
            setHandlers()
            mBound = true
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            mBound = false
        }
    }
    private lateinit var resetDialog: AlertDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        chessBoard = findViewById(R.id.chess_board)
        myUidText = findViewById(R.id.my_uid)
        myScore = findViewById(R.id.my_score)
        otherUidText = findViewById(R.id.other_uid)
        otherScore = findViewById(R.id.other_score)
        competitionNumber = findViewById(R.id.number)
        leaveButton = findViewById(R.id.leave)
        competition.id.observe(this, { id ->
            if (id == 0) {
                competitionNumber.text = "未加入"
                alertDialog.show()
            } else {
                competitionNumber.text = "$id"
                alertDialog.dismiss()
            }
        })
        competition.playerA.observe(this, { player ->
            myUidText.text = player?.uuid?.toString()?.substring(0..7) ?: "未知"
        })
        competition.playerB.observe(this, { player ->
            otherUidText.text = player?.uuid?.toString()?.substring(0..7) ?: "未知"
        })
        competition.playerAWin.observe(this, { score ->
            myScore.text = "$score"
        })
        competition.playerBWin.observe(this, { score ->
            otherScore.text = "$score"
        })
        leaveButton.setOnClickListener {
            chessService.leaveCompetition(competition.id.value ?: 0)
        }
        chessBoard.setOnClickChessGridListener { _, x, y ->
            //chessBoard.putChess(if (switchCompat.isChecked) ChessBoard.Chess.FIRSTHAND else ChessBoard.Chess.BACKHAND, x, y)
            Log.d(TAG, String.format("click event ... x: %d, y: %d", x, y))
            chessService.putChess(competition.id.value ?: 0, x, y)
            true
        }

        val editText = EditText(this)
        editText.inputType = InputType.TYPE_CLASS_NUMBER
        editText.hint = "号码"
        alertDialog = AlertDialog.Builder(this)
            .setTitle("对局")
            .setView(editText)
            .setPositiveButton("创建") { dialog, _ ->
                val str = editText.text.toString()
                if (!chessService.socketChannel.isActive) startService(
                    Intent(
                        this,
                        ChessService::class.java
                    )
                )
                if (str.isDigitsOnly() && str.length == 4) {
                    val id = str.toInt()
                    if (competition.id.value ?: 0 != 0)
                        chessService.leaveCompetition(competition.id.value ?: 0)
                    chessService.createCompetition(id)
                    dialog.dismiss()
                }
            }
            .setNegativeButton("加入") { dialog, _ ->
                val str = editText.text.toString()
                if (!chessService.socketChannel.isActive) startService(
                    Intent(
                        this,
                        ChessService::class.java
                    )
                )
                if (str.isDigitsOnly() && str.length == 4) {
                    val id = str.toInt()
                    if (competition.id.value ?: 0 != 0)
                        chessService.leaveCompetition(competition.id.value ?: 0)
                    chessService.joinCompetition(id)
                    dialog.dismiss()
                }
            }
            .create()
        alertDialog.getButton(DialogInterface.BUTTON_POSITIVE)?.isEnabled = false
        alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE)?.isEnabled = false
        editText.addTextChangedListener(afterTextChanged = { editable ->
            if (editable?.length ?: 0 > 4) {
                editable?.delete(4, editable.length)
            }
            if (editable?.length ?: 0 == 4) {
                alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).isEnabled = true
                alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE).isEnabled = true
                editText.error = null
            } else {
                alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).isEnabled = false
                alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE).isEnabled = false
                editText.error = "四位数字"
            }
        })
        resetDialog = AlertDialog.Builder(this)
            .setTitle("重置")
            .setMessage("再来一局")
            .setPositiveButton("确定") { dialog, _ ->
                competition.id.value?.also {
                    if (it != 0) {
                        chessService.resetCompetition(it)
                        chessService.requestStatistic()
                    } else {
                        chessService.leaveCompetition(0)
                        alertDialog.show()
                    }
                }
                dialog.cancel()
            }
            .setNegativeButton("取消") { _, _ -> }
            .create()
        ChessService.onConnected = { channel ->
            Log.d(TAG, "连接成功")
            alertDialog.show()
            chessService.auth()
            channel.closeFuture().addListener {
                competition.id.postValue(0)
            }
        }
    }

    private fun bindService() {
        if (!mBound) {
            // Bind to Service
            Intent(this, ChessService::class.java).also { intent ->
                intent.putExtra("host", "coding.gardel.top")
                intent.putExtra("port", 5544)
                startService(intent)
                bindService(intent, connection, Context.BIND_AUTO_CREATE)
            }
        }
    }

    private fun setHandlers() {
        chessService.setEventListener(AuthInfo::class) { _, message ->
            Log.d(TAG, "注册成功 uuid=${message.uuid}")
            competition.playerA.postValue(Player(UUID.fromString(message.uuid)))
        }
        chessService.setEventListener(CompetitionOperation::class) { _, message ->
            when (message.operation) {
                CompetitionOperation.Operation.Create -> {
                    Toast.makeText(applicationContext, "创建成功", Toast.LENGTH_SHORT).show()
                    competition.id.postValue(message.id)
                }
                CompetitionOperation.Operation.Join -> {
                    chessBoard.reset()
                    chessService.requestSync()
                    if (message.hasPlayerB()) {
                        competition.playerB.postValue(Player(UUID.fromString(message.playerB.uuid)))
                        Toast.makeText(applicationContext, "开始", Toast.LENGTH_SHORT).show()
                    } else {
                        competition.id.postValue(message.id)
                        if (message.hasPlayerA()) competition.playerB.postValue(
                            Player(
                                UUID.fromString(
                                    message.playerA.uuid
                                )
                            )
                        )
                        Toast.makeText(applicationContext, "加入成功", Toast.LENGTH_SHORT).show()
                    }
                }
                CompetitionOperation.Operation.Leave -> {
                    Toast.makeText(applicationContext, "退出", Toast.LENGTH_SHORT).show()
                    if (!message.hasPlayerB())
                        competition.id.postValue(0)
                }
                CompetitionOperation.Operation.Reset -> {
                    Log.d(TAG, "重置棋盘")
                    chessBoard.reset()
                    resetDialog.dismiss()
                }
                else -> {
                }
            }
        }
        chessService.setEventListener(CompetitionFinish::class) { _, message ->
            val playerA = competition.playerA.value
            val playerB = competition.playerB.value
            when {
                playerA?.uuid?.toString().equals(message.winner) -> {
                    Toast.makeText(applicationContext, "你赢了", Toast.LENGTH_SHORT).show()
                }
                playerB?.uuid?.toString().equals(message.winner) -> {
                    Toast.makeText(applicationContext, "对方赢了", Toast.LENGTH_SHORT).show()
                }
                "N" == message.winner -> {
                    Toast.makeText(applicationContext, "平局", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    Toast.makeText(applicationContext, "没人赢", Toast.LENGTH_SHORT).show()
                }
            }
            resetDialog.show()
        }
        chessService.setEventListener(PutChess::class) { _, message ->
            val posX = message.x
            val posY = message.y
            Log.d(TAG, "放置棋子 ($posX, $posY)")
            chessBoard.putChess(
                if (message.myself) ChessBoard.Chess.FIRSTHAND else ChessBoard.Chess.BACKHAND,
                posX,
                posY
            )
        }
        chessService.setEventListener(Statistics::class) { _, message ->
            if (message.myself) {
                competition.playerAWin.postValue(message.winTime)
                competition.playerALose.postValue(message.loseTime)
            } else {
                competition.playerBWin.postValue(message.winTime)
                competition.playerALose.postValue(message.loseTime)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_reset ->
                resetDialog.show()
            R.id.action_competition ->
                alertDialog.show()
        }
        return false
    }

    override fun onStart() {
        super.onStart()
        bindService()
    }

    override fun onResume() {
        super.onResume()
        if (::chessService.isInitialized && competition.id.value ?: 0 != 0) {
            chessBoard.reset()
            chessService.requestSync()
        }
    }

    override fun onStop() {
        super.onStop()
        unbindService(connection)
        mBound = false
    }

    companion object {
        const val TAG = "MainActivity"
    }
}