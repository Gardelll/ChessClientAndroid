package top.gardel.chess.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import com.google.protobuf.Any
import com.google.protobuf.Internal
import com.google.protobuf.MessageLite
import io.netty.bootstrap.Bootstrap
import io.netty.channel.*
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.protobuf.ProtobufDecoder
import io.netty.handler.codec.protobuf.ProtobufEncoder
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender
import top.gardel.chess.proto.*
import java.net.InetSocketAddress
import kotlin.reflect.KClass


class ChessService : Service() {
    private val binder = LocalBinder()
    private lateinit var group: NioEventLoopGroup
    lateinit var socketChannel: SocketChannel
    private val eventHandlers =
        HashMap<KClass<out MessageLite>, (channel: Channel, message: MessageLite) -> Unit>()
    private val uiHandler = Handler()
    private lateinit var host: String
    private var port: Int = -1

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onCreate() {
        group = NioEventLoopGroup()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.hasExtra("host") == true) host = intent.getStringExtra("host")!!
        if (intent?.hasExtra("port") == true) port = intent.getIntExtra("port", 5544)

        if (!::host.isInitialized || port == -1)
            return START_NOT_STICKY

        if (!::socketChannel.isInitialized || !socketChannel.isActive) {
            group.execute {
                Bootstrap().channel(NioSocketChannel::class.java)
                    .group(group)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .handler(object : ChannelInitializer<SocketChannel>() {
                        override fun initChannel(socketChannel: SocketChannel) {
                            val pipeline = socketChannel.pipeline()
                            pipeline.addLast(ProtobufVarint32FrameDecoder())
                            pipeline.addLast(ProtobufDecoder(Response.getDefaultInstance()))

                            pipeline.addLast(ProtobufVarint32LengthFieldPrepender())
                            pipeline.addLast(ProtobufEncoder())
                            pipeline.addLast(object : SimpleChannelInboundHandler<Response>() {
                                override fun channelReadComplete(ctx: ChannelHandlerContext) {
                                    ctx.flush()
                                    super.channelReadComplete(ctx)
                                }

                                override fun exceptionCaught(
                                    ctx: ChannelHandlerContext,
                                    cause: Throwable
                                ) {
                                    cause.printStackTrace()
                                    val errMsg = cause.localizedMessage
                                    if (ctx.channel().isActive) ctx.writeAndFlush(
                                        Response.newBuilder()
                                            .setError(errMsg ?: cause.javaClass.simpleName)
                                            .build()
                                    )
                                }

                                override fun channelRead0(
                                    ctx: ChannelHandlerContext,
                                    msg: Response
                                ) {
                                    if (!msg.error.isNullOrBlank()) {
                                        uiHandler.post {
                                            Toast.makeText(
                                                applicationContext,
                                                msg.error,
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                        return
                                    }
                                    val body = msg.body
                                    Log.d(TAG, body.typeUrl)
                                    val typeName = when (val pos = body.typeUrl.indexOf('/')) {
                                        -1 -> return
                                        else -> body.typeUrl.substring(pos + 1)
                                    }

                                    @Suppress("UNCHECKED_CAST")
                                    val type =
                                        classLoader.loadClass(typeName) as Class<out MessageLite>
                                    AuthInfo.getDefaultInstance()
                                    val instance = Internal.getDefaultInstance(type)
                                    val message = instance.parserForType.parseFrom(body.value)
                                    val handler = eventHandlers[message::class]
                                    if (handler != null) uiHandler.post {
                                        handler.invoke(socketChannel, message)
                                    }
                                }
                            })
                        }
                    })
                    .connect(InetSocketAddress(host, port))
                    .addListener(ChannelFutureListener { future: ChannelFuture ->
                        if (future.isSuccess) {
                            socketChannel = future.channel() as SocketChannel
                            socketChannel.closeFuture().addListener {
                                stopSelf()
                            }
                            uiHandler.postDelayed({
                                if (onConnected != null) {
                                    onConnected?.invoke(socketChannel)
                                    onConnected = null
                                }
                            }, 1000)
                        } else {
                            future.cause().printStackTrace()
                            stopSelf()
                        }
                    })
            }
        }

        return START_REDELIVER_INTENT
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : MessageLite> setEventListener(
        msgType: KClass<T>,
        handler: (channel: Channel, message: T) -> Unit
    ) {
        eventHandlers[msgType] = handler as (Channel, MessageLite) -> Unit
    }

    override fun onDestroy() {
        if (::socketChannel.isInitialized && socketChannel.isActive)
            socketChannel.close()
        group.shutdownGracefully()
    }

    private fun <T : MessageLite> buildAny(message: T): Any {
        return Any.newBuilder()
            .setTypeUrl("type.googleapis.com/${message::class.qualifiedName}")
            .setValue(
                message.toByteString()
            )
            .build()
    }

    fun auth(): ChannelFuture {
        return socketChannel.writeAndFlush(
            Request.newBuilder()
                .setBody(
                    buildAny(
                        AuthInfo.newBuilder()
                            .build()
                    )
                )
        )
    }

    fun createCompetition(id: Int): ChannelFuture {
        return socketChannel.writeAndFlush(
            Request.newBuilder()
                .setBody(
                    buildAny(
                        CompetitionOperation.newBuilder()
                            .setId(id)
                            .setOperation(CompetitionOperation.Operation.Create)
                            .build()
                    )
                )
        )
    }

    fun joinCompetition(id: Int): ChannelFuture {
        return socketChannel.writeAndFlush(
            Request.newBuilder()
                .setBody(
                    buildAny(
                        CompetitionOperation.newBuilder()
                            .setId(id)
                            .setOperation(CompetitionOperation.Operation.Join)
                            .build()
                    )
                )
        )
    }

    fun leaveCompetition(id: Int): ChannelFuture {
        return socketChannel.writeAndFlush(
            Request.newBuilder()
                .setBody(
                    buildAny(
                        CompetitionOperation.newBuilder()
                            .setId(id)
                            .setOperation(CompetitionOperation.Operation.Leave)
                            .build()
                    )
                )
        )
    }

    fun putChess(id: Int, posX: Int, posY: Int): ChannelFuture {
        return socketChannel.writeAndFlush(
            Request.newBuilder()
                .setBody(
                    buildAny(
                        CompetitionOperation.newBuilder()
                            .setId(id)
                            .setOperation(CompetitionOperation.Operation.Put)
                            .setPos(
                                PutChess.newBuilder()
                                    .setX(posX)
                                    .setY(posY)
                                    .build()
                            )
                            .build()
                    )
                )
        )
    }

    fun resetCompetition(id: Int): ChannelFuture {
        return socketChannel.writeAndFlush(
            Request.newBuilder()
                .setBody(
                    buildAny(
                        CompetitionOperation.newBuilder()
                            .setId(id)
                            .setOperation(CompetitionOperation.Operation.Reset)
                            .build()
                    )
                )
        )
    }

    fun requestStatistic(): ChannelFuture {
        return socketChannel.writeAndFlush(
            Request.newBuilder()
                .setBody(
                    buildAny(
                        GetStatistics.newBuilder()
                            .setMyself(true)
                            .build()
                    )
                )
        )
    }

    fun requestSync(): ChannelFuture {
        return socketChannel.writeAndFlush(
            Request.newBuilder()
                .setBody(
                    buildAny(
                        Sync.newBuilder()
                            .build()
                    )
                )
        )
    }

    inner class LocalBinder : Binder() {
        // Return this instance of LocalService so clients can call public methods
        fun getService(): ChessService = this@ChessService
    }

    companion object {
        private const val TAG = "ChessService"
        var onConnected: ((SocketChannel) -> Unit)? = null
    }

}
